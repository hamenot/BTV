package dev.frost819.newbv.app.ui.component.player.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import dev.frost819.newbv.app.entity.player.VideoAspectRatio
import dev.frost819.newbv.app.ui.component.player.panel.PlayerPanel
import dev.frost819.newbv.app.ui.component.player.panel.PlayerPanelDefaults
import dev.frost819.newbv.app.ui.state.player.PlayerUiState
import dev.frost819.newbv.app.viewmodel.player.LocalMenuFocusStateData
import dev.frost819.newbv.app.viewmodel.player.MenuFocusState
import dev.frost819.newbv.app.viewmodel.player.MenuFocusStateData
import dev.frost819.newbv.app.viewmodel.player.VideoPlayerMenuNavItem
import dev.frost819.newbv.biliapi.entity.video.Subtitle
import dev.frost819.newbv.biliapi.entity.video.SubtitleAiStatus
import dev.frost819.newbv.biliapi.entity.video.SubtitleAiType
import dev.frost819.newbv.biliapi.entity.video.SubtitleType
import dev.frost819.newbv.data.datastore.Audio
import dev.frost819.newbv.data.datastore.DanmakuType
import dev.frost819.newbv.data.datastore.VideoCodec
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 设置菜单控制器。
 *
 * 从右侧滑入的半透明面板，包含导航列表和菜单面板。
 * 三态焦点模型：MenuNav ↔ Menu ↔ Items。
 *
 * @param modifier 修饰符
 * @param show 是否显示
 * @param uiState 播放器 UI 状态
 * @param initialNavItem 打开时停在哪个分类。控制条上画质/倍速/弹幕/字幕各有独立入口，
 *   点哪个就直接落到哪一页，不用再在面板里挪一次导航
 * @param onResolutionChange 画质变化回调
 * @param onCodecChange 编码变化回调
 * @param onAspectRatioChange 宽高比变化回调
 * @param onPlaySpeedChange 倍速变化回调
 * @param onAudioChange 音轨变化回调
 * @param onDanmakuSwitchChange 弹幕类型变化回调
 * @param onDanmakuSizeChange 弹幕大小变化回调
 * @param onDanmakuOpacityChange 弹幕透明度变化回调
 * @param onDanmakuSpeedFactorChange 弹幕速度变化回调
 * @param onDanmakuAreaChange 弹幕区域变化回调
 * @param onDanmakuMaskChange 弹幕蒙版变化回调
 * @param onSubtitleChange 字幕轨道变化回调
 * @param onSubtitleSizeChange 字幕大小变化回调
 * @param onSubtitleBackgroundOpacityChange 字幕透明度变化回调
 * @param onSubtitleBottomPadding 字幕间距变化回调
 */
@Composable
@Suppress("LongParameterList")
fun MenuController(
    modifier: Modifier = Modifier,
    show: Boolean,
    uiState: PlayerUiState,
    initialNavItem: VideoPlayerMenuNavItem = VideoPlayerMenuNavItem.PlaySpeed,
    onDismiss: () -> Unit = {},
    anchorFromRight: Dp = 48.dp,
    onResolutionChange: (Int) -> Unit = {},
    onCodecChange: (VideoCodec) -> Unit = {},
    onAspectRatioChange: (VideoAspectRatio) -> Unit,
    onPlaySpeedChange: (Float) -> Unit = {},
    onAudioChange: (Audio) -> Unit,
    onDanmakuSwitchChange: (List<DanmakuType>) -> Unit,
    onDanmakuSizeChange: (Float) -> Unit,
    onDanmakuOpacityChange: (Float) -> Unit,
    onDanmakuSpeedFactorChange: (Float) -> Unit,
    onDanmakuAreaChange: (Float) -> Unit,
    onDanmakuMaskChange: (Boolean) -> Unit = {},
    onSubtitleChange: (Subtitle) -> Unit,
    onSubtitleSizeChange: (Int) -> Unit,
    onSubtitleBackgroundOpacityChange: (Float) -> Unit,
    onSubtitleBottomPadding: (Int) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(show) {
        if (show) {
            // 面板内容在 AnimatedVisibility 入场动画中组合，show 置位的同一帧
            // 子项尚未挂载完成，立即 requestFocus 会静默失败（触屏点击场景
            // 焦点原本就不在面板路径上）→ 表现为"焦点没跟着进弹窗"。
            // 延迟到内容组合+布局稳定后再请求，落在面板首个可聚焦项。
            delay(100)
            runCatching { focusRequester.requestFocus() }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onFocusChanged {},
        contentAlignment = Alignment.BottomEnd,
    ) {
        // 面板从"打开它的那个按钮"正上方长出来：右缘对齐按钮中心，底边压在控制条上方。
        // 四个分类各占各的位置，不再挤在同一个右侧停靠点。
        val anchorShiftPx = with(LocalDensity.current) { anchorFromRight.toPx() }
        AnimatedVisibility(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = PlayerPanelDefaults.AnchorBottomInset)
                    .offset { IntOffset(-anchorShiftPx.roundToInt(), 0) },
            visible = show,
            enter =
                fadeIn(tween(PlayerPanelDefaults.EnterDurationMillis)) +
                    scaleIn(
                        animationSpec = tween(PlayerPanelDefaults.EnterDurationMillis),
                        initialScale = 0.96f,
                        transformOrigin = TransformOrigin(1f, 0.5f),
                    ),
            exit =
                fadeOut(tween(PlayerPanelDefaults.ExitDurationMillis)) +
                    scaleOut(
                        animationSpec = tween(PlayerPanelDefaults.ExitDurationMillis),
                        targetScale = 0.98f,
                        transformOrigin = TransformOrigin(1f, 0.5f),
                    ),
            label = "PlayerSettingsPanel",
        ) {
            MenuControllerContent(
                uiState = uiState,
                initialNavItem = initialNavItem,
                onDismiss = onDismiss,
                onResolutionChange = onResolutionChange,
                onCodecChange = onCodecChange,
                onAspectRatioChange = onAspectRatioChange,
                onPlaySpeedChange = onPlaySpeedChange,
                onAudioChange = onAudioChange,
                onDanmakuSwitchChange = onDanmakuSwitchChange,
                onDanmakuSizeChange = onDanmakuSizeChange,
                onDanmakuOpacityChange = onDanmakuOpacityChange,
                onDanmakuSpeedFactorChange = onDanmakuSpeedFactorChange,
                onDanmakuAreaChange = onDanmakuAreaChange,
                onDanmakuMaskChange = onDanmakuMaskChange,
                onSubtitleChange = onSubtitleChange,
                onSubtitleSizeChange = onSubtitleSizeChange,
                onSubtitleBackgroundOpacityChange = onSubtitleBackgroundOpacityChange,
                onSubtitleBottomPadding = onSubtitleBottomPadding,
            )
        }
    }
}

/**
 * 菜单控制器内容（不含动画包裹层）。
 */
@Composable
@Suppress("LongParameterList")
private fun MenuControllerContent(
    uiState: PlayerUiState,
    initialNavItem: VideoPlayerMenuNavItem,
    onDismiss: () -> Unit,
    onResolutionChange: (Int) -> Unit,
    onCodecChange: (VideoCodec) -> Unit,
    onAspectRatioChange: (VideoAspectRatio) -> Unit,
    onPlaySpeedChange: (Float) -> Unit,
    onAudioChange: (Audio) -> Unit,
    onDanmakuSwitchChange: (List<DanmakuType>) -> Unit,
    onDanmakuSizeChange: (Float) -> Unit,
    onDanmakuOpacityChange: (Float) -> Unit,
    onDanmakuSpeedFactorChange: (Float) -> Unit,
    onDanmakuAreaChange: (Float) -> Unit,
    onDanmakuMaskChange: (Boolean) -> Unit,
    onSubtitleChange: (Subtitle) -> Unit,
    onSubtitleSizeChange: (Int) -> Unit,
    onSubtitleBackgroundOpacityChange: (Float) -> Unit,
    onSubtitleBottomPadding: (Int) -> Unit,
) {
    // 以入口传来的分类为初值；换一个入口打开（initialNavItem 变化）时重新落位
    val selectedNavItem = initialNavItem
    // 面板已经没有"分类导航列"了（分类就是控制条上的按钮），
    // 所以 MenuNav 这一态改作「焦点离开面板」的信号：按到它就把面板收起来。
    var focusState by remember { mutableStateOf(MenuFocusState.Menu) }

    LaunchedEffect(focusState) {
        if (focusState == MenuFocusState.MenuNav) onDismiss()
    }

    Surface(colors = SurfaceDefaults.colors(containerColor = Color.Transparent)) {
        CompositionLocalProvider(
            LocalMenuFocusStateData provides MenuFocusStateData(focusState = focusState),
        ) {
            // 每个按钮开的是**自己那一块**面板：卡里只放本分类的选项，不再挂公用的分类导航列
            //
            // 画质 / 弹幕 / 字幕 是三列表格版式，行数各不相同（4/6/4），
            // 卡片跟着内容走就会忽高忽低；统一钉成同一尺寸，切分类时面板不长不短也不跳位置。
            // 倍速是单列窄列表，单独走内容自适应。
            val fixedPanel = selectedNavItem != VideoPlayerMenuNavItem.PlaySpeed
            PlayerPanel(
                modifier =
                    if (fixedPanel) {
                        Modifier
                            .width(PlayerPanelDefaults.FixedPanelWidth)
                            .height(PlayerPanelDefaults.FixedPanelHeight)
                    } else {
                        Modifier
                    },
                title = selectedNavItem.displayName,
                icon = selectedNavItem.icon,
            ) {
                MenuList(
                    modifier = if (fixedPanel) Modifier.fillMaxWidth().weight(1f) else Modifier,
                    uiState = uiState,
                    selectedNavMenu = selectedNavItem,
                    onResolutionChange = onResolutionChange,
                    onCodecChange = onCodecChange,
                    onPlaySpeedChange = onPlaySpeedChange,
                    onAspectRatioChange = onAspectRatioChange,
                    onAudioChange = onAudioChange,
                    onDanmakuSwitchChange = onDanmakuSwitchChange,
                    onDanmakuSizeChange = onDanmakuSizeChange,
                    onDanmakuOpacityChange = onDanmakuOpacityChange,
                    onDanmakuSpeedFactorChange = onDanmakuSpeedFactorChange,
                    onDanmakuAreaChange = onDanmakuAreaChange,
                    onDanmakuMaskChange = onDanmakuMaskChange,
                    onFocusStateChange = { focusState = it },
                    onSubtitleChange = onSubtitleChange,
                    onSubtitleSizeChange = onSubtitleSizeChange,
                    onSubtitleBackgroundOpacityChange = onSubtitleBackgroundOpacityChange,
                    onSubtitleBottomPadding = onSubtitleBottomPadding,
                )
            }
        }
    }
}

/**
 * 根据选中的导航项渲染对应菜单面板。
 */
@Composable
@Suppress("LongParameterList")
private fun MenuList(
    uiState: PlayerUiState,
    selectedNavMenu: VideoPlayerMenuNavItem,
    modifier: Modifier = Modifier,
    onResolutionChange: (Int) -> Unit,
    onCodecChange: (VideoCodec) -> Unit,
    onAspectRatioChange: (VideoAspectRatio) -> Unit,
    onPlaySpeedChange: (Float) -> Unit,
    onAudioChange: (Audio) -> Unit,
    onDanmakuSwitchChange: (List<DanmakuType>) -> Unit,
    onDanmakuSizeChange: (Float) -> Unit,
    onDanmakuOpacityChange: (Float) -> Unit,
    onDanmakuSpeedFactorChange: (Float) -> Unit,
    onDanmakuAreaChange: (Float) -> Unit,
    onDanmakuMaskChange: (Boolean) -> Unit,
    onFocusStateChange: (MenuFocusState) -> Unit,
    onSubtitleChange: (Subtitle) -> Unit,
    onSubtitleSizeChange: (Int) -> Unit,
    onSubtitleBackgroundOpacityChange: (Float) -> Unit,
    onSubtitleBottomPadding: (Int) -> Unit,
) {
    // 不在这里写死宽度：倍速这类是单列（216dp），画质/弹幕/字幕是三列里的两列
    // （子项列 + 值列），写死 216dp 会把第二列挤成 0 宽、整块消失。
    Box(
        modifier =
            modifier.heightIn(max = PlayerPanelDefaults.PanelMaxHeight),
    ) {
        when (selectedNavMenu) {
            VideoPlayerMenuNavItem.Picture ->
                PictureMenuList(
                    availableQualityIds = uiState.availableQuality.keys.toList(),
                    availableAudio = uiState.availableAudio,
                    availableVideoCodec = uiState.availableVideoCodec,
                    currentResolution = uiState.mediaProfileState.qualityId,
                    currentVideoCodec = uiState.mediaProfileState.videoCodec,
                    currentVideoAspectRatio = uiState.aspectRatio,
                    currentAudio = uiState.mediaProfileState.audio,
                    onResolutionChange = onResolutionChange,
                    onCodecChange = onCodecChange,
                    onAspectRatioChange = onAspectRatioChange,
                    onAudioChange = onAudioChange,
                    onFocusStateChange = onFocusStateChange,
                )

            VideoPlayerMenuNavItem.PlaySpeed ->
                PlaySpeedMenuList(
                    currentSelectedPlaySpeedItem = PlaySpeedItem.fromSpeed(uiState.playSpeed),
                    onPlaySpeedChange = onPlaySpeedChange,
                    onFocusStateChange = onFocusStateChange,
                )

            VideoPlayerMenuNavItem.Danmaku -> {
                // 将 danmaku 模块 DanmakuType 映射为 data 模块 DanmakuType
                val dataTypes =
                    uiState.danmakuState.enabledTypes.mapNotNull { entity ->
                        runCatching { dev.frost819.newbv.data.datastore.DanmakuType.entries[entity.ordinal] }
                            .getOrNull()
                    }
                DanmakuMenuList(
                    currentEnabledTypes = dataTypes,
                    currentScale = uiState.danmakuState.scale,
                    currentOpacity = uiState.danmakuState.opacity,
                    currentSpeedFactor = uiState.danmakuState.speedFactor,
                    currentArea = uiState.danmakuState.area,
                    currentMaskEnabled = uiState.danmakuState.maskEnabled,
                    onDanmakuSwitchChange = onDanmakuSwitchChange,
                    onDanmakuSizeChange = onDanmakuSizeChange,
                    onDanmakuOpacityChange = onDanmakuOpacityChange,
                    onDanmakuSpeedFactorChange = onDanmakuSpeedFactorChange,
                    onDanmakuAreaChange = onDanmakuAreaChange,
                    onFocusStateChange = onFocusStateChange,
                    onDanmakuMaskChange = onDanmakuMaskChange,
                )
            }

            VideoPlayerMenuNavItem.ClosedCaption -> {
                val subtitleTracks =
                    buildList {
                        add(
                            Subtitle(
                                id = -1,
                                lang = "",
                                langDoc = "关闭",
                                url = "",
                                type = SubtitleType.CC,
                                aiType = SubtitleAiType.Normal,
                                aiStatus = SubtitleAiStatus.None,
                            ),
                        )
                        addAll(uiState.subtitleList)
                        sortBy { it.id }
                    }
                ClosedCaptionMenuList(
                    currentSubtitleId = uiState.subtitleId,
                    availableSubtitleTracks = subtitleTracks,
                    currentFontSize = uiState.subtitleState.fontSize,
                    currentOpacity = uiState.subtitleState.opacity,
                    currentPadding = uiState.subtitleState.bottomPadding,
                    onSubtitleChange = onSubtitleChange,
                    onSubtitleSizeChange = onSubtitleSizeChange,
                    onSubtitleBackgroundOpacityChange = onSubtitleBackgroundOpacityChange,
                    onSubtitleBottomPadding = onSubtitleBottomPadding,
                    onFocusStateChange = onFocusStateChange,
                )
            }
        }
    }
}
