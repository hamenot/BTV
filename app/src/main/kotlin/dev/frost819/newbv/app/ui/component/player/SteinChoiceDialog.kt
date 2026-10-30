package dev.frost819.newbv.app.ui.component.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.frost819.newbv.biliapi.entity.stein.SteinEdgeChoice
import dev.frost819.newbv.core.focus.focusInvertedColors
import dev.frost819.newbv.core.focus.touchClickable
import dev.frost819.newbv.core.theme.BVTheme

/**
 * 弹窗底边距屏幕底缘的高度：与播放器面板一致（不压进度条与控制条）。
 */
private val SteinChoiceBottomInset = 94.dp

/** 弹窗与屏幕左右缘的安全距离。 */
private val SteinChoiceScreenPadding = 24.dp

/**
 * 弹窗描边色：onSurface 低透明度（随主题明暗自适应）。
 *
 * tv 的 ColorScheme 没有 outlineVariant，这里取等价做法。
 */
private val cardBorderColor
    @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

/**
 * 选项聚焦放大的安全边距：滚动视口会**裁剪**越界内容，聚焦时 tv Surface 的
 * 放大（focusedScale）会超出选项自身边界，内侧四周留白保证放大后的边框
 * 不被视口 / 弹窗边缘切掉。
 */
private val ChoiceFocusBreathingRoom = 10.dp

/** 单个选项的尺寸约束：并列排布，窄选项不无限撑宽、长选项封顶换行。 */
private val ChoiceMinWidth = 168.dp
private val ChoiceMaxWidth = 340.dp
private val ChoiceMinHeight = 60.dp

/**
 * 互动视频选择弹窗。
 *
 * 互动视频（Stein Gate）当前分段临近结束时弹出，**贴着画面底部居中**出现
 * （视频下方，不压进度条），选项**横向并列**排布，遥控器左右移动 + OK 键选择，
 * 选项过多时横向滚动。
 *
 * 配色跟随应用主题：卡片底/描边/选项走 colorScheme，聚焦用应用统一的
 * [focusInvertedColors]（inverseSurface 反色），不再使用播放器面板的固定深色。
 *
 * @param choices 当前模块的可选选项
 * @param onChoiceSelected 选中某个选项
 * @param onDismiss 关闭弹窗（不选择）
 */
@Composable
fun SteinChoiceDialog(
    choices: List<SteinEdgeChoice>,
    onChoiceSelected: (SteinEdgeChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // 底部居中：互动选择是关键决策，贴画面底部出现不挡剧情画面主体
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(bottom = SteinChoiceBottomInset)
                        .padding(horizontal = SteinChoiceScreenPadding)
                        .widthIn(max = 960.dp)
                        .shadow(elevation = 16.dp, shape = MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                        .border(1.dp, cardBorderColor, MaterialTheme.shapes.large)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 标题行：主题色淡底圆片 + 标题（配色全部走主题）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        SteinChoiceTitleIcon(icon = Icons.Rounded.TouchApp)
                    }
                    Text(
                        text = "互动选择",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(choices) {
                    // 弹窗出现即抓取焦点，遥控器可直接左右选择
                    runCatching { focusRequester.requestFocus() }
                }

                Row(
                    modifier =
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            // padding 在 horizontalScroll 之后 = 滚动视口内侧留白：
                            // 两端选项聚焦放大的边框有处可去，不会被视口裁掉
                            .padding(ChoiceFocusBreathingRoom),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    choices.forEachIndexed { index, choice ->
                        SteinChoiceItem(
                            text = choice.text,
                            modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                            onClick = { onChoiceSelected(choice) },
                        )
                    }
                }
            }
        }
    }
}

/** 标题图标，画在主题色淡底圆片里。 */
@Composable
private fun SteinChoiceTitleIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SteinChoiceItem(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            modifier
                .widthIn(min = ChoiceMinWidth, max = ChoiceMaxWidth)
                .heightIn(min = ChoiceMinHeight)
                .touchClickable(onClick = onClick),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        colors =
            focusInvertedColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    ) {
        // 文案居中：并列卡片短文案居中比左对齐更像「按钮」而非「列表项」
        Box(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// region Previews

@Preview(device = "id:tv_1080p", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SteinChoiceDialogPreview() {
    BVTheme {
        SteinChoiceDialog(
            choices =
                listOf(
                    SteinEdgeChoice(choiceId = 1, cid = 11, text = "A 说出真相"),
                    SteinEdgeChoice(choiceId = 2, cid = 12, text = "B 保持沉默"),
                    SteinEdgeChoice(choiceId = 3, cid = 13, text = "C 转身离开"),
                ),
            onChoiceSelected = {},
            onDismiss = {},
        )
    }
}

// endregion
