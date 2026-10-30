package dev.frost819.newbv.app.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.frost819.newbv.app.ui.component.settings.SettingsOptionRow

/** 顶栏编辑弹窗的尺寸。 */
private object TopNavEditorDefaults {
    /** 弹窗宽度：容纳"体育运动"这类 4 字分区名 + 序号 + 右侧状态。 */
    val Width = 520.dp

    /**
     * 列表最大高度。
     *
     * 不能取太大：弹窗整体居中，标题 + 提示 + 内边距已经占掉不少高度，
     * 列表再顶满会把最后一行挤到窗口外（表现为"第一/最后一行被裁成一条缝"）。
     */
    val ListMaxHeight = 320.dp
}

/**
 * 顶栏编辑弹窗：调整分区顺序、隐藏不关心的分区。
 *
 * 交互约定（遥控器）：
 * - 上下：在分区之间移动焦点；
 * - 左右：把当前分区往前/往后挪一位；
 * - 确认：切换显示 / 隐藏；
 * - 返回：关闭（改动即时生效并持久化，不需要"保存"）。
 *
 * 列表里只包含真实分区；顶栏末尾的"修改"入口本身不可排序、不可隐藏，
 * 因此不进入这个列表。
 *
 * @param title 弹窗标题。
 * @param items 全部顶栏项，已按当前顺序排列（含被隐藏的项）。
 * @param hiddenKeys 被隐藏项的 key 集合。
 * @param keyOf 取某项的稳定 key（用于持久化、隐藏判断与列表 diff）。
 * @param onMove 移动回调：把 [items] 中 `index` 处向前（-1）或向后（+1）挪一位。
 * @param onToggleVisible 切换某项的显示状态（参数为该项的 key）。
 * @param onDismiss 关闭回调。
 */
@Composable
fun TopNavEditorDialog(
    title: String,
    items: List<TopNavItem>,
    hiddenKeys: Set<String>,
    keyOf: (TopNavItem) -> String,
    onMove: (index: Int, delta: Int) -> Unit,
    onToggleVisible: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstRequester = remember { FocusRequester() }

    // 弹窗是独立 Window，不会自动获得焦点，需要主动聚焦首项。
    LaunchedEffect(Unit) {
        runCatching { firstRequester.requestFocus() }
    }
    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .6f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.width(TopNavEditorDefaults.Width),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "上下选择 · 左右调整顺序 · 确认显示/隐藏 · 返回关闭",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = TopNavEditorDefaults.ListMaxHeight),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // key 必须稳定：调序后同一个 node 跟着项走，焦点不会留在原来的行号上。
                        itemsIndexed(items, key = { _, item -> keyOf(item) }) { index, item ->
                            val hidden = keyOf(item) in hiddenKeys
                            SettingsOptionRow(
                                title = "${index + 1}. ${item.displayName}",
                                modifier =
                                    Modifier
                                        .then(
                                            if (index == 0) {
                                                Modifier.focusRequester(firstRequester)
                                            } else {
                                                Modifier
                                            },
                                        ).onPreviewKeyEvent { event ->
                                            if (event.type != KeyEventType.KeyDown) {
                                                return@onPreviewKeyEvent false
                                            }
                                            when (event.key) {
                                                // 左右调序：这两个方向在弹窗里没有其它用途，直接接管。
                                                Key.DirectionLeft -> {
                                                    onMove(index, -1)
                                                    true
                                                }
                                                Key.DirectionRight -> {
                                                    onMove(index, 1)
                                                    true
                                                }
                                                else -> false
                                            }
                                        },
                                value = if (hidden) "已隐藏" else "显示",
                                trailingIcon = if (hidden) null else Icons.Rounded.Check,
                                onClick = { onToggleVisible(keyOf(item)) },
                            )
                        }
                    }
                }
            }
        }
    }
}
