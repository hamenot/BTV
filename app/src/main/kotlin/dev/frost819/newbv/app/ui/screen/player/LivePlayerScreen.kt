package dev.frost819.newbv.app.ui.screen.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.frost819.newbv.app.ui.component.player.LivePlayerController
import dev.frost819.newbv.app.ui.component.rememberDoublePressExit
import dev.frost819.newbv.app.viewmodel.live.LivePlayerState
import dev.frost819.newbv.app.viewmodel.live.LivePlayerViewModel
import dev.frost819.newbv.app.viewmodel.player.DanmakuViewModel
import dev.frost819.newbv.danmaku.component.DanmakuPlayerCompose
import dev.frost819.newbv.player.BvVideoPlayer

/**
 * 直播播放器页面。
 *
 * 使用 [LivePlayerController] 作为根控制器，整合 [LivePlayerViewModel]（播放控制）
 * 和 [DanmakuViewModel]（弹幕配置）。
 *
 * UI 层负责协调两者：
 * - 播放/暂停 → 同步弹幕播放/暂停
 * - 弹幕开关 → 调用 DanmakuViewModel.toggleDanmaku()
 * - 弹幕设置 → 调用 DanmakuViewModel.updateDanmakuState()
 */
@Composable
fun LivePlayerScreen(
    navController: NavController,
    viewModel: LivePlayerViewModel,
    danmakuViewModel: DanmakuViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val danmakuState by danmakuViewModel.danmakuState.collectAsStateWithLifecycle()
    val debugInfo by viewModel.debugInfo.collectAsStateWithLifecycle()
    val videoPlayer = viewModel.videoPlayer
    val danmakuPlayer = danmakuViewModel.danmakuPlayer

    val danmakuEnabled = danmakuState.enabledTypes.isNotEmpty()

    // 双击退出：TV 遥控器（Controller onBack）和非 TV（BackHandler）共用同一计时器。
    // pop 前先清空视频画面：SurfaceView 的最后一帧不受页面淡出影响，不清会残留残影。
    // 仅 clearVideoSurface 不足以让 SurfaceView 立即消失（竖屏直播间窄条画面实测仍残留），
    // 还需把视频画面从组合中移除——View 脱离窗口时 surface 会被确定性销毁。
    var playerSurfaceVisible by remember { mutableStateOf(true) }
    val handleBack =
        rememberDoublePressExit(
            onExit = {
                playerSurfaceVisible = false
                viewModel.clearVideoSurface()
                navController.popBackStack()
            },
            message = "再按一次退出播放",
        )
    BackHandler { handleBack() }

    LaunchedEffect(uiState.playerState) {
        when (uiState.playerState) {
            LivePlayerState.Playing -> danmakuViewModel.play()
            LivePlayerState.Paused, LivePlayerState.Error, LivePlayerState.Ended -> danmakuViewModel.pause()
            else -> {}
        }
    }

    LaunchedEffect(uiState.isBuffering) {
        if (uiState.isBuffering) {
            danmakuViewModel.pause()
        } else if (uiState.playerState == LivePlayerState.Playing) {
            danmakuViewModel.play()
        }
    }

    LivePlayerController(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        isPlaying = uiState.playerState == LivePlayerState.Playing,
        isBuffering = uiState.isBuffering,
        isError = uiState.playerState == LivePlayerState.Error,
        errorMessage = uiState.errorMessage,
        title = uiState.title,
        areaName = uiState.areaName,
        onlineCount = uiState.onlineCount,
        clock = getClock(),
        danmakuEnabled = danmakuEnabled,
        danmakuState = danmakuState,
        availableQualities = uiState.availableQualities,
        currentQuality = uiState.currentQuality,
        availableLines = uiState.availableLines,
        currentLine = uiState.currentLine,
        onBack = handleBack,
        onPlayPause = { viewModel.togglePlayPause() },
        onRefresh = { viewModel.refresh() },
        onToggleDanmaku = { danmakuViewModel.toggleDanmaku() },
        onQualityChange = { viewModel.changeQuality(it) },
        onLineChange = { viewModel.changeLine(it) },
        onDanmakuSettingChange = { danmakuViewModel.updateDanmakuState(it) },
        debugInfo = debugInfo,
    ) {
        if (videoPlayer != null && playerSurfaceVisible) {
            val aspectRatio =
                if (videoPlayer.videoWidth > 0 && videoPlayer.videoHeight > 0) {
                    videoPlayer.videoWidth.toFloat() / videoPlayer.videoHeight.toFloat()
                } else {
                    16f / 9f
                }
            BvVideoPlayer(
                modifier = Modifier.align(Alignment.Center).fillMaxHeight().aspectRatio(aspectRatio),
                videoPlayer = videoPlayer,
            )
        }

        if (danmakuPlayer != null && danmakuEnabled) {
            DanmakuPlayerCompose(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .alpha(danmakuState.alpha),
                danmakuPlayer = danmakuPlayer,
            )
        }
    }
}

/**
 * 获取当前时钟（小时，分钟）。
 */
private fun getClock(): Pair<Int, Int> {
    val calendar = java.util.Calendar.getInstance()
    return calendar.get(java.util.Calendar.HOUR_OF_DAY) to calendar.get(java.util.Calendar.MINUTE)
}
