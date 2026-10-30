package dev.frost819.newbv.app.ui.component.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.frost819.newbv.R
import dev.frost819.newbv.app.ui.component.player.menu.LivePlayerMenuNavItem
import kotlinx.coroutines.delay

/**
 * 直播播放器控制器信息层。
 *
 * 版式与点播 [ControllerVideoInfo] 完全一致（同一套 [PlayerControlsDefaults]）：
 * 顶部标题栏 + 底部控制条各自一块单一方向的黑色渐变，中间是纯画面；
 * 底部留 48dp 安全边距，图标按钮 44dp 正圆、聚焦整块反色、不做缩放。
 *
 * 直播没有进度可拖，所以底部只有一行图标。
 *
 * @param title 直播间标题
 * @param areaName 分区名
 * @param onlineCount 人气值
 * @param clock 时钟（hour, minute）
 * @param isPlaying 是否正在播放
 * @param danmakuEnabled 弹幕是否开启
 * @param onPlayPause 播放/暂停回调
 * @param onRefresh 刷新回调
 * @param onDanmakuSwitchChange 弹幕开关回调
 * @param onOpenSetting 直接打开某一类设置面板（画质/线路/弹幕）
 */
@Composable
fun LiveControllerInfo(
    modifier: Modifier = Modifier,
    show: Boolean,
    title: String,
    areaName: String,
    onlineCount: String,
    clock: Pair<Int, Int>,
    isPlaying: Boolean,
    danmakuEnabled: Boolean,
    onPlayPause: () -> Unit,
    onRefresh: () -> Unit,
    onDanmakuSwitchChange: () -> Unit,
    onOpenSetting: (LivePlayerMenuNavItem) -> Unit,
) {
    // 遮罩单独做透明度动画：它不跟着内容一起滑，只在整体显隐时淡入淡出。
    val scrimAlpha by animateFloatAsState(
        targetValue = if (show) 1f else 0f,
        animationSpec =
            tween(
                if (show) PlayerControlsDefaults.EnterDurationMillis else PlayerControlsDefaults.ExitDurationMillis,
            ),
        label = "live-bottom-scrim",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(PlayerControlsDefaults.LiveBottomScrimHeight)
                    .alpha(scrimAlpha)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to Color.Black.copy(alpha = 0.55f),
                            1f to Color.Black.copy(alpha = 0.86f),
                        ),
                    ),
        )

        AnimatedVisibility(
            modifier = Modifier.align(Alignment.TopCenter),
            visible = show,
            enter =
                fadeIn(tween(PlayerControlsDefaults.EnterDurationMillis)) +
                    slideInVertically(tween(PlayerControlsDefaults.EnterDurationMillis)) { -it / 10 },
            exit =
                fadeOut(tween(PlayerControlsDefaults.ExitDurationMillis)) +
                    slideOutVertically(tween(PlayerControlsDefaults.ExitDurationMillis)) { -it / 10 },
            label = "LiveControllerTop",
        ) {
            LiveControllerInfoTop(
                title = title,
                areaName = areaName,
                onlineCount = onlineCount,
                clock = clock,
            )
        }

        AnimatedVisibility(
            modifier = Modifier.align(Alignment.BottomCenter),
            visible = show,
            enter =
                fadeIn(tween(PlayerControlsDefaults.EnterDurationMillis)) +
                    slideInVertically(tween(PlayerControlsDefaults.EnterDurationMillis)) { it / 10 },
            exit =
                fadeOut(tween(PlayerControlsDefaults.ExitDurationMillis)) +
                    slideOutVertically(tween(PlayerControlsDefaults.ExitDurationMillis)) { it / 10 },
            label = "LiveControllerBottom",
        ) {
            LiveControllerInfoBottom(
                isPlaying = isPlaying,
                danmakuEnabled = danmakuEnabled,
                onPlayPause = onPlayPause,
                onRefresh = onRefresh,
                onDanmakuSwitchChange = onDanmakuSwitchChange,
                onOpenSetting = onOpenSetting,
            )
        }
    }
}

/**
 * 直播控制器顶部信息：标题 + 一行副信息（分区 · 人气）+ 时钟。
 *
 * 与点播页一样是「主标题 + 一行副信息」的两行结构，文字直接压在渐变的黑幕上，
 * 不叠阴影。
 */
@Composable
private fun LiveControllerInfoTop(
    modifier: Modifier = Modifier,
    title: String,
    areaName: String,
    onlineCount: String,
    clock: Pair<Int, Int>,
) {
    val meta =
        listOfNotNull(
            areaName.takeIf { it.isNotBlank() },
            onlineCount.takeIf { it.isNotBlank() }?.let { "人气 $it" },
        ).joinToString(" · ")

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.85f),
                        0.65f to Color.Black.copy(alpha = 0.65f),
                        1f to Color.Transparent,
                    ),
                ).padding(
                    start = PlayerControlsDefaults.HorizontalPadding,
                    end = PlayerControlsDefaults.HorizontalPadding,
                    top = PlayerControlsDefaults.TitleTopPadding,
                    bottom = PlayerControlsDefaults.TitleBottomPadding,
                ),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = PlayerControlsDefaults.Content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = PlayerControlsDefaults.SecondaryContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            modifier = Modifier.padding(start = 24.dp, top = 4.dp),
            text = "${clock.first.pad2()}:${clock.second.pad2()}",
            style = MaterialTheme.typography.titleMedium,
            color = PlayerControlsDefaults.SecondaryContent,
        )
    }
}

/**
 * 直播控制器底部：只有一行图标（播放/刷新/弹幕 · 右侧各类设置入口）。
 *
 * 画质 / 线路 / 弹幕三个设置分类直接摊到控制条上（[LivePlayerMenuNavItem]），
 * 点哪个就直接落到对应面板，不用先进"设置"再挪导航。
 */
@Composable
private fun LiveControllerInfoBottom(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    danmakuEnabled: Boolean,
    onPlayPause: () -> Unit,
    onRefresh: () -> Unit,
    onDanmakuSwitchChange: () -> Unit,
    onOpenSetting: (LivePlayerMenuNavItem) -> Unit,
) {
    val buttonsFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(50)
        runCatching { buttonsFocusRequester.requestFocus() }
    }

    val playbackIcons =
        listOf(
            drawableIcon(R.drawable.play_pause_24px, "播放/暂停", onPlayPause),
            drawableIcon(R.drawable.baseline_refresh_24, "刷新", onRefresh),
            drawableIcon(
                if (danmakuEnabled) R.drawable.danmaku_on_24px else R.drawable.danmaku_off_24px,
                "弹幕开关",
                onDanmakuSwitchChange,
                active = danmakuEnabled,
            ),
        )
    val settingIcons =
        listOf(
            vectorIcon(Icons.Outlined.Image, "画质") { onOpenSetting(LivePlayerMenuNavItem.Picture) },
            vectorIcon(Icons.Outlined.Wifi, "线路") { onOpenSetting(LivePlayerMenuNavItem.Line) },
            vectorIcon(Icons.Outlined.ClearAll, "弹幕设置") { onOpenSetting(LivePlayerMenuNavItem.Danmaku) },
        )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = PlayerControlsDefaults.HorizontalPadding,
                    end = PlayerControlsDefaults.HorizontalPadding,
                    bottom = PlayerControlsDefaults.BottomContentPadding,
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(buttonsFocusRequester)
                    .onKeyEvent {
                        if (it.key == Key.DirectionUp) {
                            if (it.type == KeyEventType.KeyUp) return@onKeyEvent true
                            return@onKeyEvent false
                        }
                        false
                    },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(PlayerControlsDefaults.LeftGroupSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                playbackIcons.forEach { item ->
                    key(item.description) { PlayerIconButton(item) }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(PlayerControlsDefaults.RightGroupSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                settingIcons.forEach { item ->
                    key(item.description) { PlayerIconButton(item) }
                }
            }
        }
    }
}

/** 两位补零。 */
private fun Int.pad2(): String = if (this < 10) "0$this" else toString()
