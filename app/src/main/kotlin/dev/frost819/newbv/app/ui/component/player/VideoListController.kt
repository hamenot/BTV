package dev.frost819.newbv.app.ui.component.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import dev.frost819.newbv.app.entity.player.VideoListItem
import dev.frost819.newbv.app.ui.component.player.panel.PlayerPanel
import dev.frost819.newbv.app.ui.component.player.panel.PlayerPanelDefaults
import dev.frost819.newbv.app.ui.component.player.panel.PlayerPanelItem
import dev.frost819.newbv.biliapi.entity.video.VideoPage
import dev.frost819.newbv.core.theme.BVTheme

/**
 * 分集列表覆盖层。
 *
 * 从左侧滑入的半透明面板，显示视频分集列表。
 * 支持嵌套 UGC 分页（有 ugcPages 的视频可展开/折叠子分集）。
 * 显示时自动滚动到当前播放项并请求焦点。
 *
 * @param modifier 修饰符
 * @param show 是否显示
 * @param currentCid 当前播放视频的 CID
 * @param videoList 视频列表
 * @param onPlayNewVideo 点击播放新视频回调
 */
@Composable
fun VideoListController(
    modifier: Modifier = Modifier,
    show: Boolean,
    currentCid: Long,
    videoList: List<VideoListItem>,
    onPlayNewVideo: (VideoListItem) -> Unit,
) {
    val listState = rememberLazyListState()
    val parentFocusRequester = remember { FocusRequester() }
    val childFocusRequester = remember { FocusRequester() }

    // 显示时自动滚动到当前集并请求焦点
    LaunchedEffect(show) {
        if (show) {
            val currentIndex =
                videoList.indexOfFirst { video ->
                    video.cid == currentCid ||
                        video.ugcPages?.any { it.cid == currentCid } == true
                }

            if (currentIndex != -1) {
                listState.animateScrollToItem(currentIndex)

                val isChild =
                    videoList
                        .getOrNull(currentIndex)
                        ?.ugcPages
                        ?.any { it.cid == currentCid } == true

                if (isChild) {
                    childFocusRequester.requestFocus()
                } else {
                    parentFocusRequester.requestFocus()
                }
            }
        }
    }

    // 与设置面板同一套：浮在画面左侧的圆角卡，淡入 + 轻微放大
    AnimatedVisibility(
        modifier = modifier.fillMaxSize(),
        visible = show,
        enter =
            fadeIn(tween(PlayerPanelDefaults.EnterDurationMillis)) +
                scaleIn(
                    animationSpec = tween(PlayerPanelDefaults.EnterDurationMillis),
                    initialScale = 0.96f,
                    transformOrigin = TransformOrigin(0f, 0.5f),
                ),
        exit =
            fadeOut(tween(PlayerPanelDefaults.ExitDurationMillis)) +
                scaleOut(
                    animationSpec = tween(PlayerPanelDefaults.ExitDurationMillis),
                    targetScale = 0.98f,
                    transformOrigin = TransformOrigin(0f, 0.5f),
                ),
        label = "PlayerVideoListPanel",
    ) {
        // 面板在左侧垂直居中：AnimatedVisibility 铺满全屏，靠 Box 对齐到起始边
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart,
        ) {
            PlayerPanel(
                modifier =
                    Modifier
                        .padding(start = PlayerPanelDefaults.ScreenPadding)
                        .width(PlayerPanelDefaults.PanelListWidth),
                title = "分集",
                icon = Icons.Rounded.PlaylistPlay,
                subtitle = "${videoList.size} 个分集",
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = PlayerPanelDefaults.PanelMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(PlayerPanelDefaults.ItemSpacing),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    items(
                        items = videoList,
                        key = { it.cid },
                    ) { video ->
                        VideoListItemRow(
                            video = video,
                            currentCid = currentCid,
                            parentFocusRequester = parentFocusRequester,
                            childFocusRequester = childFocusRequester,
                            onPlayNewVideo = onPlayNewVideo,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个视频列表项（支持嵌套 UGC 分页）。
 */
@Composable
private fun VideoListItemRow(
    video: VideoListItem,
    currentCid: Long,
    parentFocusRequester: FocusRequester,
    childFocusRequester: FocusRequester,
    onPlayNewVideo: (VideoListItem) -> Unit,
) {
    val hasSubPages = !video.ugcPages.isNullOrEmpty()
    val isParentSelected = video.cid == currentCid
    val isChildSelected = video.ugcPages?.any { it.cid == currentCid } == true

    var expanded by remember(video.cid) { mutableStateOf(isChildSelected) }

    // 当前播放的是子分页时自动展开父项
    LaunchedEffect(isChildSelected) {
        if (isChildSelected) expanded = true
    }

    Column(modifier = Modifier.animateContentSize()) {
        // 父级视频项
        val parentModifier =
            if (isParentSelected) {
                Modifier.focusRequester(parentFocusRequester)
            } else {
                Modifier
            }

        PlayerListItem(
            modifier = parentModifier,
            text = video.title,
            selected = isParentSelected && !isChildSelected,
            trailingContent =
                if (hasSubPages) {
                    {
                        Icon(
                            imageVector =
                                if (expanded) {
                                    Icons.Default.KeyboardArrowUp
                                } else {
                                    Icons.Default.KeyboardArrowDown
                                },
                            contentDescription = null,
                        )
                    }
                } else {
                    null
                },
            onClick = {
                if (hasSubPages) {
                    expanded = !expanded
                } else if (!isParentSelected) {
                    onPlayNewVideo(video)
                }
            },
        )

        // 子分页列表
        if (expanded && hasSubPages) {
            Column(
                modifier = Modifier.padding(start = 20.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                video.ugcPages?.forEach { page ->
                    val isPageSelected = page.cid == currentCid
                    val childModifier =
                        if (isPageSelected) {
                            Modifier.focusRequester(childFocusRequester)
                        } else {
                            Modifier
                        }

                    PlayerListItem(
                        modifier = childModifier,
                        text = page.title,
                        selected = isPageSelected,
                        onClick = {
                            if (!isPageSelected) {
                                onPlayNewVideo(video.copy(cid = page.cid))
                            }
                        },
                    )
                }
            }
        }

        // 折叠时恢复焦点到父项
        LaunchedEffect(expanded) {
            if (!expanded && isParentSelected) {
                parentFocusRequester.requestFocus()
            }
        }
    }
}

/**
 * 播放器列表项。
 *
 * 通用可聚焦列表项，支持选中状态、尾部图标和自定义对齐。
 *
 * @param modifier 修饰符
 * @param text 文本内容
 * @param selected 是否选中
 * @param trailingContent 尾部内容（如展开/折叠图标）
 * @param onFocus 获得焦点回调
 * @param onClick 点击回调
 */
@Composable
fun PlayerListItem(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    trailingContent: (@Composable () -> Unit)? = null,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    PlayerPanelItem(
        modifier = modifier.fillMaxWidth(),
        text = text,
        selected = selected,
        onClick = onClick,
        onFocus = onFocus,
        trailingContent = trailingContent,
    )
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun VideoListControllerPreview() {
    val sampleList =
        listOf(
            VideoListItem(
                aid = 1,
                cid = 101,
                title = "第一集",
                ugcPages =
                    listOf(
                        VideoPage(
                            cid = 201,
                            index = 1,
                            title = "P1 上半",
                            duration = 600,
                            dimension =
                                dev.frost819.newbv.biliapi.entity.video
                                    .Dimension(1920, 1080),
                        ),
                        VideoPage(
                            cid = 202,
                            index = 2,
                            title = "P1 下半",
                            duration = 600,
                            dimension =
                                dev.frost819.newbv.biliapi.entity.video
                                    .Dimension(1920, 1080),
                        ),
                    ),
            ),
            VideoListItem(aid = 2, cid = 102, title = "第二集"),
        )

    BVTheme {
        VideoListController(
            show = true,
            currentCid = 201L,
            videoList = sampleList,
            onPlayNewVideo = {},
        )
    }
}

// endregion
