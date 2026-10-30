package dev.frost819.newbv.app.ui.component.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.frost819.newbv.app.ui.component.player.panel.PlayerPanel
import dev.frost819.newbv.app.ui.component.player.panel.PlayerPanelDefaults
import dev.frost819.newbv.app.ui.component.videocard.SmallVideoCard
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.core.theme.BVTheme

/**
 * 相关视频覆盖层。
 *
 * 全屏淡入/淡出动画，中央显示一行相关视频卡片。
 * 背景为垂直渐变（上下透明，中间半透明黑色）。
 *
 * @param modifier 修饰符
 * @param show 是否显示
 * @param relatedVideos 相关视频列表
 * @param onVideoClicked 点击视频卡片回调
 */
@Composable
fun RelatedVideosController(
    modifier: Modifier = Modifier,
    show: Boolean,
    relatedVideos: List<VideoCardData>,
    onVideoClicked: (VideoCardData) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(show) {
        if (show) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(focusRequester),
        contentAlignment = Alignment.Center,
    ) {
        // 与设置面板同一套卡片外观
        AnimatedVisibility(
            visible = show,
            enter = fadeIn(tween(PlayerPanelDefaults.EnterDurationMillis)),
            exit = fadeOut(tween(PlayerPanelDefaults.ExitDurationMillis)),
            label = "RelatedVideosPanel",
        ) {
            PlayerPanel(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerPanelDefaults.ScreenPadding),
                title = "相关视频",
                icon = Icons.Rounded.VideoLibrary,
                subtitle = "${relatedVideos.size} 个推荐",
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    items(
                        items = relatedVideos,
                        key = { it.avid },
                    ) { video ->
                        SmallVideoCard(
                            modifier = Modifier.width(240.dp),
                            data = video,
                            onClick = { onVideoClicked(video) },
                        )
                    }
                }
            }
        }
    }
}

// region Previews

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun RelatedVideosControllerPreview() {
    val sampleVideos =
        listOf(
            VideoCardData(
                avid = 1,
                cid = 101,
                title = "相关视频标题 1",
                cover = "",
                upName = "UP主A",
                playString = "1.2万播放",
                danmakuString = "300弹幕",
                timeString = "10:30",
            ),
            VideoCardData(
                avid = 2,
                cid = 102,
                title = "相关视频标题 2",
                cover = "",
                upName = "UP主B",
                playString = "5000播放",
                danmakuString = "100弹幕",
                timeString = "5:20",
            ),
        )

    BVTheme {
        RelatedVideosController(
            show = true,
            relatedVideos = sampleVideos,
            onVideoClicked = {},
        )
    }
}

// endregion
