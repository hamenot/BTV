package dev.frost819.newbv.core.focus

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.material3.ShapeDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import dev.frost819.newbv.core.interaction.InputMethod
import dev.frost819.newbv.core.interaction.LocalInteractionTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 获取焦点时显示边框。
 *
 * 仅在 [InputMethod.DPad] 模式下显示焦点边框，
 * 触屏模式下隐藏（触屏点击自带视觉反馈，无需额外焦点提示）。
 *
 * 当 [LocalInteractionTracker] 未注入时，始终显示边框（兼容未接入 tracker 的场景）。
 *
 * @param shape 边框形状，默认 [ShapeDefaults.Large]。
 * @param animate 是否启用呼吸动画。
 * @param color 边框颜色，默认使用当前主题的边框色。
 * @param width 边框宽度，默认 3dp。
 */
fun Modifier.focusedBorder(
    shape: Shape = ShapeDefaults.Large,
    animate: Boolean = false,
    color: Color? = null,
    width: Dp = 3.dp,
): Modifier =
    composed {
        val tracker = LocalInteractionTracker.current
        val resolvedColor = color ?: MaterialTheme.colorScheme.border
        var hasFocus by remember { mutableStateOf(false) }

        val showBorder =
            if (tracker != null) {
                val method by tracker.inputMethod.collectAsState()
                hasFocus && method == InputMethod.DPad
            } else {
                hasFocus
            }

        // 只在真正需要呼吸动画时才建立 InfiniteTransition。
        // 旧实现无条件创建，animate=false（也是默认值）时动画对象照样进入 applier 并
        // 每帧计算一次颜色，而 borderColor 实际取的是 resolvedColor 或 Transparent。
        // TV 端信息流一个列表几十上百个 item 都挂这个修饰符，等于每帧几十次无意义插值。
        val borderColor =
            if (!showBorder) {
                Color.Transparent
            } else if (animate) {
                breathingBorderColor(resolvedColor)
            } else {
                resolvedColor
            }

        onFocusChanged { hasFocus = it.hasFocus }
            .border(width = width, color = borderColor, shape = shape)
    }

/** 呼吸边框色：仅在 [animate] 为 true 时才被组合进来。 */
@Composable
private fun breathingBorderColor(base: Color): Color {
    val transition = rememberInfiniteTransition(label = "focused-border-transition")
    return transition
        .animateColor(
            initialValue = base.copy(alpha = 1f),
            targetValue = base.copy(alpha = 0.1f),
            animationSpec =
                infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "focused-border-color",
        ).value
}

/**
 * 未获焦点时缩小，获焦点时恢复原大小。
 *
 * 触屏模式下保持显示缩放效果（D-Pad 用户看到放大，触屏用户看到缩小）。
 * 注意：与 [focusedBorder] 不同，[focusedScale] 不会根据输入方式隐藏。
 *
 * @param scale 未获焦点时的缩放比例，默认 0.9。
 */
fun Modifier.focusedScale(scale: Float = 0.9f): Modifier =
    composed {
        var hasFocus by remember { mutableStateOf(false) }
        val scaleValue by animateFloatAsState(
            targetValue = if (hasFocus) 1f else scale,
            label = "focused-scale",
        )

        onFocusChanged { hasFocus = it.hasFocus }
            .scale(scaleValue)
    }

/**
 * 把某个方向键的焦点去向显式指定为 [target]。
 *
 * 空间搜索只在「候选目标与当前焦点落在同一束内」时可靠。详情页就是反例：
 * UP 名在右栏、封面在左栏，两者水平不重叠，按上时搜索找不到封面，焦点会卡在 UP 名上。
 * 在父容器上挂本修饰符即可覆盖该方向的默认行为。
 *
 * @param target 该方向上的目标焦点。
 * @param key 要接管的方向键，默认上方向。
 */
fun Modifier.focusOnDirection(
    target: FocusRequester,
    key: Key = Key.DirectionUp,
): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == key) {
            // target 尚未附着到树上时 requestFocus 会抛异常，此时回退默认空间搜索。
            runCatching { target.requestFocus() }.isSuccess
        } else {
            false
        }
    }

/**
 * 改进的请求焦点方法。
 *
 * 首次请求失败后等待 100ms 重试一次，处理 Compose 焦点系统时序问题。
 *
 * @param scope 协程作用域，在 Main 调度器执行。
 */
fun FocusRequester.requestFocus(scope: CoroutineScope) {
    scope.launch(Dispatchers.Main) {
        runCatching {
            requestFocus()
        }.onFailure {
            delay(100)
            runCatching { requestFocus() }
        }
    }
}

/**
 * 构建「聚焦反色」的可点击 Surface 配色。
 *
 * TV 遥控器场景焦点必须足够醒目：聚焦时容器切换为
 * [androidx.tv.material3.ColorScheme.inverseSurface]，
 * 内容切换为 [androidx.tv.material3.ColorScheme.inverseOnSurface]，
 * 实现整体明暗反色。未聚焦时使用调用方传入的容器/内容色，
 * 以便表达「已选中 / 已激活」等常态。
 *
 * 注意：Surface 的内容色通过 [androidx.tv.material3.LocalContentColor] 下传，
 * 子组件（[androidx.tv.material3.Text] / [androidx.tv.material3.Icon]）**不要**再硬编码颜色，
 * 否则聚焦时不会随容器一起反色。
 *
 * @param containerColor 未聚焦时的容器色。
 * @param contentColor 未聚焦时的内容色。
 */
@Composable
@ReadOnlyComposable
fun focusInvertedColors(
    containerColor: Color,
    contentColor: Color,
): ClickableSurfaceColors =
    ClickableSurfaceDefaults.colors(
        containerColor = containerColor,
        contentColor = contentColor,
        focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
        focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
    )
