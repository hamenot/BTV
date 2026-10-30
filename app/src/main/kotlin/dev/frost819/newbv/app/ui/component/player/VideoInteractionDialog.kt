package dev.frost819.newbv.app.ui.component.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.frost819.newbv.app.data.VideoSharedState
import dev.frost819.newbv.app.ui.component.player.panel.PlayerPanel
import dev.frost819.newbv.app.ui.component.player.panel.PlayerPanelDefaults
import dev.frost819.newbv.core.focus.touchClickable
import dev.frost819.newbv.core.theme.BVTheme

/**
 * 「交互」按钮中心距屏幕右缘的距离。
 *
 * 右组是右对齐的：UGC 7 个按钮时交互排第 6，番剧 6 个时排第 5，
 * 两种排法算出来都落在 122dp，所以直接用配置里的换算函数取值即可。
 */
private val InteractionAnchorFromRight = PlayerControlsDefaults.rightButtonCenterFromRight(index = 5, count = 7)

/**
 * 交互面板尺寸：**固定**，不跟内容自适应。
 *
 * 这块面板只有三个磁贴，内容永远不变，如果跟着内容撑开，一旦文案从"点赞"变成"已点赞"、
 * 或者磁贴尺寸微调，卡片就会比旁边那几个设置面板小一圈、看着不像一套。
 * 固定成 380 × 204：内容区 324dp（3 × 100 + 2 × 12）居中、上下各留 18dp。
 */
private val InteractionPanelWidth = 380.dp
private val InteractionPanelHeight = 204.dp

/** 面板里单块磁贴的边长。 */
private val InteractionTileSize = 100.dp

/**
 * 播放器视频交互弹窗。
 *
 * @param actionState 当前视频操作状态
 * @param onLike 点赞/取消点赞
 * @param onCoin 投币
 * @param onFavorite 收藏/取消收藏
 * @param onOneClickTriple 长按点赞触发一键三连
 * @param onDismiss 关闭回调
 */
@Composable
fun VideoInteractionDialog(
    actionState: VideoSharedState?,
    onLike: () -> Unit,
    onCoin: () -> Unit,
    onFavorite: () -> Unit,
    onOneClickTriple: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // 和设置面板一样：从"交互"按钮正上方长出来的一块卡片，不再铺满屏的黑色蒙层。
        // 四个分类面板各在各自按钮上方，交互自己占这一格，谁也不跟谁抢右侧那块位置。
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd,
        ) {
            PlayerPanel(
                modifier =
                    Modifier
                        .padding(
                            end = InteractionAnchorFromRight,
                            bottom = PlayerPanelDefaults.AnchorBottomInset,
                        ).width(InteractionPanelWidth)
                        .height(InteractionPanelHeight),
                title = "视频交互",
                icon = Icons.Rounded.TouchApp,
            ) {
                // 磁贴在固定高度的内容区里居中，卡片尺寸不随文案变化
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InteractionAction(
                            text = if (actionState?.liked == true) "已点赞" else "点赞",
                            icon =
                                if (actionState?.liked == true) {
                                    Icons.Rounded.ThumbUp
                                } else {
                                    Icons.Outlined.ThumbUp
                                },
                            active = actionState?.liked == true,
                            onClick = onLike,
                            onLongClick = onOneClickTriple,
                        )

                        InteractionAction(
                            text = if (actionState?.coined == true) "已投币" else "投币",
                            icon =
                                if (actionState?.coined == true) {
                                    Icons.Rounded.Paid
                                } else {
                                    Icons.Outlined.Paid
                                },
                            active = actionState?.coined == true,
                            onClick = onCoin,
                        )

                        InteractionAction(
                            text = if (actionState?.favorited == true) "已收藏" else "收藏",
                            icon =
                                if (actionState?.favorited == true) {
                                    Icons.Rounded.Star
                                } else {
                                    Icons.Outlined.StarBorder
                                },
                            active = actionState?.favorited == true,
                            onClick = onFavorite,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractionAction(
    text: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Surface(
        modifier =
            Modifier
                .size(InteractionTileSize)
                .touchClickable(onClick = onClick, onLongClick = onLongClick),
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(shape = PlayerPanelDefaults.ItemShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = PlayerPanelDefaults.Raised,
                contentColor =
                    if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        PlayerPanelDefaults.Content
                    },
                focusedContainerColor = PlayerPanelDefaults.FocusedContainer,
                focusedContentColor = PlayerPanelDefaults.FocusedContent,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(imageVector = icon, contentDescription = text, modifier = Modifier.size(28.dp))
            Text(
                modifier = Modifier.padding(top = 6.dp),
                text = text,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

// region Previews

@Preview(device = "id:tv_1080p", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun VideoInteractionDialogPreview() {
    BVTheme {
        VideoInteractionDialog(
            actionState =
                VideoSharedState(
                    aid = 1L,
                    liked = false,
                    coined = false,
                    favorited = false,
                ),
            onLike = {},
            onCoin = {},
            onFavorite = {},
            onOneClickTriple = {},
            onDismiss = {},
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun VideoInteractionDialogAllDonePreview() {
    BVTheme {
        VideoInteractionDialog(
            actionState =
                VideoSharedState(
                    aid = 1L,
                    liked = true,
                    coined = true,
                    favorited = true,
                ),
            onLike = {},
            onCoin = {},
            onFavorite = {},
            onOneClickTriple = {},
            onDismiss = {},
        )
    }
}

// endregion
