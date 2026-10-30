package dev.frost819.newbv.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/** 恢复焦点前的首次等待（毫秒）：让 composition 与首帧测量先跑完。 */
private const val RESTORE_FOCUS_INITIAL_DELAY_MILLIS = 50L

/** 恢复焦点的重试间隔（毫秒）。 */
private const val RESTORE_FOCUS_RETRY_INTERVAL_MILLIS = 100L

/**
 * 恢复焦点的最大尝试次数。
 *
 * 需要覆盖「详情页返回时的过渡动画 + 数据回填 + 懒列表首帧组合」三段耗时，
 * 取 10 次（约 1 秒）留足余量；命中即提前返回，正常情况一次就够。
 */
private const val RESTORE_FOCUS_MAX_ATTEMPTS = 10

/**
 * 焦点恢复器。
 *
 * 保存最后获得焦点的元素 key，在 composition 恢复后（如从详情页返回）
 * 通过 [FocusRequester] 重新请求焦点到对应元素。
 *
 * 统一使用 String key，适用于列表/网格（key = `"item_$index"`）
 * 和混合布局（key = `"cover"`、`"like"` 等）。
 *
 * 用法：
 * ```
 * val focusSaver = rememberFocusSaver()
 * focusSaver.RestoreFocus()
 * // 在可聚焦元素上：
 * Card(
 *     modifier = Modifier.focusSaverItem(focusSaver, "cover"),
 * )
 * ```
 */
class FocusSaver(
    private val savedKey: MutableState<String>,
) {
    private val requesters = mutableMapOf<String, FocusRequester>()

    /**
     * 获取指定 key 的 [FocusRequester]。
     * 每个 key 独立一个 requester，在组合恢复后仍有效。
     */
    fun focusRequesterFor(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    /**
     * 保存当前聚焦的 key。
     */
    fun saveFocusedKey(key: String) {
        savedKey.value = key
    }

    /**
     * 获取当前保存的 key（可能为空）。
     * 用于首次进入页面时判断是否需要手动聚焦默认元素。
     */
    fun savedKeyValue(): String = savedKey.value

    /**
     * 清除保存的焦点 key。
     *
     * 用于首次进入页面时丢弃"启动阶段系统自动聚焦"产生的 key，
     * 避免 [RestoreFocus] 将焦点错误恢复到该元素（如左侧栏头像）。
     */
    fun clearFocusedKey() {
        savedKey.value = ""
    }

    /**
     * 恢复焦点到之前保存的 key。
     *
     * 应在 composition 恢复后调用（通常在屏幕顶层调一次）。
     *
     * 三重保障，缺一不可：
     * 1. **生命周期触发**：从详情页 pop 回来时，本页的 composition 未必被销毁
     *    （Navigation-Compose 会保留返回栈内的条目），此时 [savedKey] 没有变化，
     *    只靠它做 [LaunchedEffect] 的 key 不会重启。因此额外监听 `ON_RESUME`，
     *    每次回到前台都重新走一遍恢复流程。
     * 2. **延迟**：懒列表（LazyColumn/LazyGrid）的子项在 composition 之后的测量
     *    阶段才完成子组合，requester 那时才注册进 [requesters]。
     * 3. **重试**：requestFocus 在目标节点尚未挂上树时会抛异常。因此每次重试都
     *    重新查一次表（而不是只查一次），首个成功即返回。
     */
    @Composable
    fun RestoreFocus() {
        val lifecycleOwner = LocalLifecycleOwner.current
        var resumeCount by remember { mutableIntStateOf(0) }

        DisposableEffect(lifecycleOwner) {
            val observer =
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) resumeCount++
                }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(savedKey.value, resumeCount) {
            val key = savedKey.value
            if (key.isEmpty()) return@LaunchedEffect

            delay(RESTORE_FOCUS_INITIAL_DELAY_MILLIS)
            repeat(RESTORE_FOCUS_MAX_ATTEMPTS) {
                val requester = requesters[key]
                if (requester != null && runCatching { requester.requestFocus() }.isSuccess) {
                    return@LaunchedEffect
                }
                delay(RESTORE_FOCUS_RETRY_INTERVAL_MILLIS)
            }
        }
    }
}

/**
 * 创建 [FocusSaver]，使用 [rememberSaveable] 持久化焦点 key。
 *
 * 焦点 key 在跨导航（页面移出再恢复 composition）和配置变更（如旋转）后保持。
 */
@Composable
fun rememberFocusSaver(): FocusSaver {
    val savedKey = rememberSaveable { mutableStateOf("") }
    return remember { FocusSaver(savedKey) }
}

// region Modifier 扩展

/**
 * 将 [FocusSaver] 绑定到可聚焦元素。
 *
 * 封装 `.focusRequester()` + `.onFocusChanged()` 两行 boilerplate 为一行。
 * 适用于列表/网格和混合布局中所有可聚焦元素的焦点追踪。
 *
 * 用法：
 * ```
 * SmallVideoCard(
 *     modifier = Modifier.focusSaverItem(focusSaver, "rcmd_$index"),
 *     ...
 * )
 * ```
 *
 * @param focusSaver 焦点恢复器。
 * @param key 元素的唯一标识（如 `"cover"`、`"tag_${tag.id}"`、`"rcmd_$index"`）。
 */
fun Modifier.focusSaverItem(
    focusSaver: FocusSaver,
    key: String,
): Modifier =
    this
        .focusRequester(focusSaver.focusRequesterFor(key))
        .onFocusChanged { if (it.hasFocus) focusSaver.saveFocusedKey(key) }

// endregion
