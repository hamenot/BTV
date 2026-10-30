package dev.frost819.newbv.app.ui.component.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import dev.frost819.newbv.app.entity.player.SponsorBlockSegment
import dev.frost819.newbv.app.entity.player.VideoAspectRatio
import dev.frost819.newbv.app.entity.player.VideoListItem
import dev.frost819.newbv.app.entity.player.markersFor
import dev.frost819.newbv.app.entity.player.shortcut.PlayerCustomShortcutAction
import dev.frost819.newbv.app.entity.player.shortcut.PlayerCustomShortcutCatalog
import dev.frost819.newbv.app.entity.player.shortcut.PlayerCustomShortcutKeys
import dev.frost819.newbv.app.entity.player.shortcut.PlayerCustomShortcutsStore
import dev.frost819.newbv.app.entity.player.shortcut.pgcUnsupportedShortcutActions
import dev.frost819.newbv.app.ui.action.player.DanmakuSettingAction
import dev.frost819.newbv.app.ui.action.player.MediaProfileSettingAction
import dev.frost819.newbv.app.ui.action.player.SubtitleSettingAction
import dev.frost819.newbv.app.ui.component.player.menu.MenuController
import dev.frost819.newbv.app.ui.state.player.PlayerState
import dev.frost819.newbv.app.ui.state.player.PlayerUiState
import dev.frost819.newbv.app.ui.state.player.SeekerState
import dev.frost819.newbv.app.util.VideoShotImageCache
import dev.frost819.newbv.app.viewmodel.player.VideoPlayerMenuNavItem
import dev.frost819.newbv.biliapi.entity.video.Subtitle
import dev.frost819.newbv.bilisubtitle.entity.SubtitleItem
import dev.frost819.newbv.core.theme.BVAccentPresets
import dev.frost819.newbv.core.theme.BVTheme
import dev.frost819.newbv.core.theme.ThemeMode
import dev.frost819.newbv.data.datastore.Prefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 播放器根控制器。
 *
 * 管理所有覆盖层的可见性和焦点路由，处理 D-pad 按键事件、seek 加速、
 * 自定义快捷键、以及视频列表/菜单/相关信息控制器之间的协调。
 *
 * 布局层次（从底到顶）：
 * 1. content() — 视频画面 + 弹幕层
 * 2. BottomSubtitle — 字幕文本
 * 3. SkipTips — 跳转提示
 * 4. PlayStateTips — 播放状态提示
 * 5. RelatedVideosController — 相关视频
 * 6. ControllerVideoInfo — 信息栏 + 进度条 + 按钮
 * 7. VideoListController — 分集列表
 * 8. MenuController — 设置菜单
 */
@Composable
@Suppress("LongParameterList", "CyclomaticComplexMethod")
fun VideoPlayerController(
    modifier: Modifier = Modifier,
    isPgc: Boolean,
    isLooping: Boolean,
    videoShotCache: VideoShotImageCache,
    uiState: PlayerUiState,
    seekerState: State<SeekerState>,
    sponsorSegments: List<SponsorBlockSegment> = emptyList(),
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onExit: () -> Unit,
    onGoTime: (time: Long) -> Unit,
    onBackToStart: () -> Unit,
    onCancelSkipToNextEp: () -> Unit,
    onPlayNewVideo: (VideoListItem) -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleLoop: () -> Unit,
    onToggleSubtitle: () -> Unit,
    onGoToUpPage: () -> Unit,
    onGoToVideoDetail: () -> Unit,
    onShowInteraction: () -> Unit,
    onShowComments: () -> Unit,
    onMediaProfileSettingChange: (MediaProfileSettingAction) -> Unit,
    onAspectRatioChange: (VideoAspectRatio) -> Unit,
    onPlaySpeedChange: (Float) -> Unit,
    onDanmakuSettingChange: (DanmakuSettingAction) -> Unit,
    onSubtitleChange: (Subtitle) -> Unit,
    onSubtitleSettingChange: (SubtitleSettingAction) -> Unit,
    onRelatedVideoClicked: (dev.frost819.newbv.app.ui.component.videocard.VideoCardData) -> Unit,
    onToggleDanmaku: () -> Unit,
    onShowShortcutTip: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 覆盖层可见性
    var showListController by remember { mutableStateOf(false) }
    var showMenuController by remember { mutableStateOf(false) }

    // 设置面板打开时落在哪一页：控制条上画质/倍速/弹幕/字幕各有入口，
    // 点哪个就带着对应分类进去，不用再在面板里挪一次导航
    var menuNavItem by remember { mutableStateOf(VideoPlayerMenuNavItem.PlaySpeed) }

    // 右组按钮总数：4 个设置入口 + (非番剧才有 UP 主页) + 交互 + 评论。
    // 面板要锚到按钮正上方，得知道它在这排按钮里排第几个。
    val rightGroupCount = if (isPgc) 6 else 7
    val menuAnchorFromRight =
        PlayerControlsDefaults.rightButtonCenterFromRight(
            index =
                when (menuNavItem) {
                    VideoPlayerMenuNavItem.Picture -> 0
                    VideoPlayerMenuNavItem.PlaySpeed -> 1
                    VideoPlayerMenuNavItem.Danmaku -> 2
                    VideoPlayerMenuNavItem.ClosedCaption -> 3
                },
            count = rightGroupCount,
        )

    var showInfoSeekController by remember { mutableStateOf(false) }

    // 浮层关掉后要把焦点送回去，否则方向键会彻底失灵（见 ControllerVideoInfo）。
    // refocusTarget = 触发它的那个按钮的 contentDescription；null 表示"没有明确来源"（如按上键开的分集），
    // 此时退回进度条。
    var seekRefocusTick by remember { mutableIntStateOf(0) }
    var refocusTarget by remember { mutableStateOf<String?>(null) }
    var showRelatedVideosController by remember { mutableStateOf(false) }
    val showClickableControllers by remember {
        derivedStateOf {
            showListController ||
                showMenuController ||
                showInfoSeekController ||
                showRelatedVideosController
        }
    }

    // Seek 加速状态
    var goTime by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekChangeCount by remember { mutableLongStateOf(0L) }
    var lastSeekChangeTime by remember { mutableLongStateOf(0L) }
    var seekCountdown: Job? by remember { mutableStateOf(null) }

    // 常显进度条：直接订阅偏好 Flow，设置页与播放器快捷键任何一边改动都实时生效。
    // 早先是在组合时把 Prefs 值快照进本地 state——播放器界面与设置页读写同一偏好，
    // 但快照不会跟随变化，出现"设置里开了开关、播放界面毫无反应"的失联。
    val showPersistentSeek by Prefs.showPersistentSeekFlow.collectAsState()

    // 空降片段标记：按「显示标记」开关与启用类别过滤后画到进度条上。
    val sponsorMarkers =
        remember(sponsorSegments) {
            if (Prefs.sponsorBlockShowMarkers) {
                sponsorSegments.markersFor(Prefs.sponsorBlockCategories)
            } else {
                emptyList()
            }
        }

    // 手势状态
    val gestureTipState = rememberGestureTipState()
    var currentBrightness by remember { mutableFloatStateOf(-1f) }

    fun calCoefficient(): Long =
        if (System.currentTimeMillis() - lastSeekChangeTime < 200) {
            seekChangeCount++
            seekChangeCount / 5
        } else {
            seekChangeCount = 0
            0
        }

    fun onTimeForward() {
        isSeeking = true
        val coefficient = calCoefficient()
        val step = 10_000L + coefficient * 5_000L
        goTime = (goTime + step).coerceAtMost(seekerState.value.totalDuration)
        lastSeekChangeTime = System.currentTimeMillis()
    }

    fun onTimeBack() {
        isSeeking = true
        val coefficient = calCoefficient()
        val step = 10_000L + coefficient * 5_000L
        goTime = (goTime - step).coerceAtLeast(0L)
        lastSeekChangeTime = System.currentTimeMillis()
    }

    /** 隐藏信息栏 + 进度条控制器。 */
    fun hideController() {
        showInfoSeekController = false
    }

    // 控制器自动隐藏：可见时 5 秒无交互自动收起，输入入口调用 onInteraction 续期
    val onInteraction =
        rememberControllerAutoHide(
            visible = showInfoSeekController,
            onHide = { hideController() },
        )

    fun startSeekCountdown() {
        seekCountdown?.cancel()
        seekCountdown =
            scope.launch {
                delay(1000)
                onGoTime(goTime)
                if (uiState.playerState != PlayerState.Playing) onPlay()
                isSeeking = false
                hideController()
            }
    }

    fun onDirectionLeft() {
        if (!isSeeking) goTime = seekerState.value.currentTime
        onTimeBack()
        startSeekCountdown()
    }

    fun onDirectionRight() {
        if (!isSeeking) goTime = seekerState.value.currentTime
        onTimeForward()
        startSeekCountdown()
    }

    fun onSeekGoTime() {
        seekCountdown?.cancel()
        onGoTime(goTime)
        if (uiState.playerState != PlayerState.Playing) onPlay()
        isSeeking = false
        hideController()
    }

    fun onSeekToPosition(positionMs: Long) {
        isSeeking = true
        goTime = positionMs.coerceIn(0L, seekerState.value.totalDuration)
        lastSeekChangeTime = System.currentTimeMillis()
        startSeekCountdown()
    }

    fun closeAllControllers() {
        showListController = false
        showMenuController = false
        showRelatedVideosController = false
        // 控制器本身保持可见，只把焦点送回进度条
        seekRefocusTick++
    }

    /**
     * 显示自定义快捷键触发提示浮层。
     *
     * 复用 [PlayerTip] 组件显示动作名称，定时结束后自动消失。
     * 快捷键提示始终显示，新的提示会覆盖旧提示。
     *
     * 对于开关类/参数类动作，[status] 会追加在动作名称后面（如"字幕：开"）。
     */
    fun showShortcutTip(
        action: PlayerCustomShortcutAction,
        status: String? = null,
    ) {
        val name = PlayerCustomShortcutCatalog.getActionDisplayName(action)
        val tip = if (status != null) "$name：$status" else name
        onShowShortcutTip(tip)
    }

    fun executeCustomShortcut(action: PlayerCustomShortcutAction) {
        // PGC 默认走详情页且无 UP/相关视频，对应的路由类快捷键直接禁用并提示
        if (isPgc && action in pgcUnsupportedShortcutActions) {
            showShortcutTip(action, "番剧不支持")
            return
        }
        var status: String? = null
        when (action) {
            PlayerCustomShortcutAction.OpenSettings -> showMenuController = true
            PlayerCustomShortcutAction.OpenRelatedVideos -> showRelatedVideosController = true
            PlayerCustomShortcutAction.PlayPrevious -> onPlayPrevious()
            PlayerCustomShortcutAction.PlayNext -> onPlayNext()
            PlayerCustomShortcutAction.OpenVideoDetail -> onGoToVideoDetail()
            PlayerCustomShortcutAction.OpenUpPage -> onGoToUpPage()
            PlayerCustomShortcutAction.OpenComments -> onShowComments()
            PlayerCustomShortcutAction.OpenInteraction -> onShowInteraction()
            PlayerCustomShortcutAction.ToggleLoop -> {
                status = if (!isLooping) "开" else "关"
                onToggleLoop()
            }
            PlayerCustomShortcutAction.ToggleDanmaku -> {
                status = if (uiState.danmakuState.enabledTypes.isEmpty()) "开" else "关"
                onToggleDanmaku()
            }
            PlayerCustomShortcutAction.ToggleSubtitle -> {
                status = if (uiState.subtitleId == -1L) "开" else "关"
                onToggleSubtitle()
            }
            PlayerCustomShortcutAction.TogglePersistentBottomProgress -> {
                val newValue = !Prefs.showPersistentSeek
                Prefs.showPersistentSeek = newValue
                status = if (newValue) "开" else "关"
            }

            is PlayerCustomShortcutAction.TogglePlaybackSpeed -> {
                val targetSpeed = if (uiState.playSpeed == action.speed) 1f else action.speed
                status = "${targetSpeed}x"
                onPlaySpeedChange(targetSpeed)
            }

            is PlayerCustomShortcutAction.ToggleDanmakuMask -> {
                val newMaskEnabled = !uiState.danmakuState.maskEnabled
                status = if (newMaskEnabled) "开" else "关"
                onDanmakuSettingChange(
                    DanmakuSettingAction.SetMaskEnabled(newMaskEnabled),
                )
            }
        }
        showShortcutTip(action, status)
    }

    fun handleCustomShortcut(event: KeyEvent): Boolean {
        if (showClickableControllers) return false
        val keyCode = event.nativeKeyEvent.keyCode
        if (!PlayerCustomShortcutKeys.isAllowedKeyCode(keyCode)) return false
        val shortcut = PlayerCustomShortcutsStore.getByKey()[keyCode] ?: return false
        if (event.type == KeyEventType.KeyUp) return true
        if (event.type != KeyEventType.KeyDown) return false
        if (event.nativeKeyEvent.repeatCount != 0) return true
        executeCustomShortcut(shortcut.action)
        return true
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        val confirmKeys = listOf(Key.DirectionCenter, Key.Enter, Key.Spacebar)

        // 非 confirm 键的 KeyUp 事件消费掉
        if (event.type == KeyEventType.KeyUp && event.key !in confirmKeys) {
            return true
        }

        // 自定义快捷键
        if (handleCustomShortcut(event)) return true

        // 始终生效的按键（KeyUp 已被顶层过滤，此处均为 KeyDown）
        when (event.key) {
            Key.Back -> {
                // 三级：先关浮层 → 再收起控制条 → 最后才退出播放页。
                // 早先这里是「有浮层就 closeAllControllers，否则 onExit」，但 closeAllControllers
                // 后来为了保住控制条把 hideController 去掉了，于是「控制条可见但没浮层」这一态
                // 按返回什么都不发生，看起来就是返回键失灵。
                when {
                    showListController || showMenuController || showRelatedVideosController ->
                        closeAllControllers()

                    showInfoSeekController -> hideController()

                    else -> onExit()
                }
                return true
            }

            Key.Menu, Key(763) -> {
                hideController()
                showMenuController = !showMenuController
                return true
            }

            Key.MediaPlayPause -> {
                onPlay()
                return true
            }

            Key.MediaPlay -> {
                if (uiState.playerState != PlayerState.Playing) onPlay()
                return true
            }

            Key.MediaPause -> {
                if (uiState.playerState == PlayerState.Playing) onPause()
                return true
            }
        }

        // 覆盖层未打开时的按键（KeyUp 已被顶层过滤，此处均为 KeyDown）
        if (!showClickableControllers) {
            when (event.key) {
                in confirmKeys -> {
                    if (event.type == KeyEventType.KeyDown) {
                        if (event.nativeKeyEvent.isLongPress) {
                            showMenuController = true
                        }
                        return true
                    } else {
                        if (uiState.showBackToStart) {
                            onBackToStart()
                        } else {
                            onPlay()
                        }
                        return true
                    }
                }

                Key.DirectionUp -> {
                    refocusTarget = null
                    showListController = true
                    return true
                }

                Key.DirectionDown -> {
                    showInfoSeekController = true
                    return true
                }

                Key.DirectionLeft, Key.MediaRewind -> {
                    if (uiState.showSkipToNextEp) {
                        onCancelSkipToNextEp()
                        return true
                    }
                    showInfoSeekController = true
                    onDirectionLeft()
                    return true
                }

                Key.DirectionRight, Key.MediaFastForward -> {
                    showInfoSeekController = true
                    onDirectionRight()
                    return true
                }
            }
        }

        return false
    }

    Box(
        modifier =
            modifier
                .background(Color.Black)
                .focusable()
                .onPreviewKeyEvent { event ->
                    onInteraction()
                    handleKeyEvent(event)
                }.playerGestures(
                    totalDuration = { seekerState.value.totalDuration },
                    controllerVisible = { showInfoSeekController },
                    callbacks =
                        PlayerGestureCallbacks(
                            onSingleTap = {
                                if (!showClickableControllers) {
                                    if (showInfoSeekController) {
                                        hideController()
                                    } else {
                                        showInfoSeekController = true
                                    }
                                } else {
                                    closeAllControllers()
                                }
                            },
                            onDoubleTap = { onPlay() },
                            onSeekDelta = { deltaMs ->
                                if (!isSeeking) goTime = seekerState.value.currentTime
                                goTime = (goTime + deltaMs).coerceIn(0L, seekerState.value.totalDuration)
                                isSeeking = true
                                showInfoSeekController = true
                                startSeekCountdown()
                            },
                            onSeekCommit = { },
                            onBrightnessChange = { deltaY ->
                                val activity = context as? android.app.Activity
                                if (activity != null) {
                                    currentBrightness = adjustBrightness(activity, deltaY, currentBrightness)
                                    gestureTipState.value =
                                        GestureTipState(
                                            isActive = true,
                                            type = GestureTipType.Brightness,
                                            value = currentBrightness,
                                        )
                                }
                            },
                            onVolumeChange = { deltaY ->
                                val audioManager =
                                    context.getSystemService(android.content.Context.AUDIO_SERVICE)
                                        as? android.media.AudioManager
                                if (audioManager != null) {
                                    val volumePercent = adjustVolume(audioManager, deltaY)
                                    gestureTipState.value =
                                        GestureTipState(
                                            isActive = true,
                                            type = GestureTipType.Volume,
                                            value = volumePercent.toFloat(),
                                        )
                                }
                            },
                            onUserInteraction = onInteraction,
                        ),
                    gestureTipState = gestureTipState,
                ),
    ) {
        // 播放器画面与覆盖层始终基于黑色背景，固定使用深色主题，
        // 避免浅色应用下默认取色变成深色文字叠在黑底上不可见。
        // surfaceColor = Black：BVTheme 内部的 TvSurface 默认会用 colorScheme.surface
        // （深灰 #222222）铺满整屏，导致 4:3 视频在 16:9 屏上左右留白呈灰色。
        // accentSeed 必须显式透传：嵌套主题不传会回落到品牌默认强调色，
        // 导致进度条/控件状态色与全局强调色脱节。
        val accentArgb by Prefs.accentColorFlow.collectAsState()
        BVTheme(
            themeMode = ThemeMode.Dark,
            density = LocalDensity.current.density,
            surfaceColor = Color.Black,
            accentSeed = BVAccentPresets.colorOf(accentArgb),
        ) {
            // 视频画面 + 弹幕层
            content()

            // 调试信息（独立叶子：只在它自己内部订阅 debugInfo）
            if (Prefs.showPlayerDebugInfo) {
                PlayerDebugInfoOverlay(seekerState = seekerState)
            }

            // 常显进度条（独立叶子：只在它自己内部订阅高频进度）
            if (showPersistentSeek && !showInfoSeekController) {
                PersistentProgressBar(
                    seekerState = seekerState,
                    sponsorMarkers = sponsorMarkers,
                )
            }

            // 字幕（独立叶子：只在它自己内部订阅高频进度）
            if (uiState.subtitleId != -1L) {
                PlayerSubtitleLayer(
                    subtitleData = uiState.subtitleData,
                    seekerState = seekerState,
                    fontSize =
                        androidx.compose.ui.unit.TextUnit(
                            uiState.subtitleState.fontSize.toFloat(),
                            androidx.compose.ui.unit.TextUnitType.Sp,
                        ),
                    opacity = uiState.subtitleState.opacity,
                    padding =
                        androidx.compose.ui.unit
                            .Dp(uiState.subtitleState.bottomPadding.toFloat()),
                )
            }

            // 跳转提示
            SkipTips(
                showBackToStart = uiState.showBackToStart,
                showSkipToNextEp = uiState.showSkipToNextEp,
                showPreviewTip = uiState.showPreviewTip,
                shortcutTipText = uiState.shortcutTipText,
            )

            // 播放状态提示
            PlayStateTips(
                isPlaying = uiState.playerState == PlayerState.Playing,
                isBuffering = uiState.isBuffering,
                isError = uiState.playerState is PlayerState.Error,
                errorMessage = (uiState.playerState as? PlayerState.Error)?.message,
            )

            // 手势提示（亮度/音量/倍速反馈）
            GestureTip(
                state = gestureTipState.value,
                modifier = Modifier.align(Alignment.Center),
            )

            // 相关视频
            RelatedVideosController(
                show = showRelatedVideosController,
                relatedVideos = uiState.relatedVideos,
                onVideoClicked = { video ->
                    // 选中即收起浮层；否则切视频后浮层仍停留在屏幕上，
                    // 焦点与旧列表会残留在新视频画面上
                    showRelatedVideosController = false
                    onRelatedVideoClicked(video)
                },
            )

            // 信息栏 + 进度条 + 按钮
            ControllerVideoInfo(
                modifier = Modifier.focusable(),
                show = showInfoSeekController,
                isSeeking = isSeeking,
                goTime = goTime,
                seekerState = seekerState,
                title = uiState.title,
                clock = uiState.clock,
                onlineWatching = uiState.onlineWatching,
                sponsorSegments = sponsorMarkers,
                videoShot = uiState.videoShot,
                videoShotCache = videoShotCache,
                isPgc = isPgc,
                danmakuEnabled = uiState.danmakuState.enabledTypes.isNotEmpty(),
                isLooping = isLooping,
                refocusTick = seekRefocusTick,
                refocusTarget = refocusTarget,
                onDirectionLeft = ::onDirectionLeft,
                onDirectionRight = ::onDirectionRight,
                onSeekGoTime = ::onSeekGoTime,
                onSeekToPosition = ::onSeekToPosition,
                onPlayPause = { onPlay() },
                onDanmakuSwitchChange = { onToggleDanmaku() },
                onOpenSetting = { navItem ->
                    menuNavItem = navItem
                    // 记住是哪个按钮弹出的，关掉面板后焦点回到它（见 ControllerVideoInfo 的 buttonRequester）
                    refocusTarget = navItem.controlButtonDescription()
                    showMenuController = true
                },
                onToggleLoop = { onToggleLoop() },
                onGoToUpPage = onGoToUpPage,
                onShowInteraction = onShowInteraction,
                onShowComments = onShowComments,
            )

            // 分集列表
            VideoListController(
                show = showListController,
                currentCid = uiState.cid,
                videoList = uiState.videoList,
                onPlayNewVideo = { item ->
                    onPlayNewVideo(item)
                    showListController = false
                },
            )

            // 设置菜单：锚定到"打开它的那个按钮"上方
            MenuController(
                show = showMenuController,
                uiState = uiState,
                initialNavItem = menuNavItem,
                onDismiss = {
                    showMenuController = false
                    seekRefocusTick++
                },
                anchorFromRight = menuAnchorFromRight,
                onResolutionChange = { onMediaProfileSettingChange(MediaProfileSettingAction.SetQuality(it)) },
                onCodecChange = { onMediaProfileSettingChange(MediaProfileSettingAction.SetVideoCodec(it)) },
                onAspectRatioChange = onAspectRatioChange,
                onPlaySpeedChange = onPlaySpeedChange,
                onAudioChange = { onMediaProfileSettingChange(MediaProfileSettingAction.SetAudio(it)) },
                onDanmakuSwitchChange = { types ->
                    // data DanmakuType → danmaku entity DanmakuType
                    val entityTypes =
                        types.mapNotNull {
                            runCatching { dev.frost819.newbv.danmaku.entity.DanmakuType.entries[it.ordinal] }
                                .getOrNull()
                        }
                    onDanmakuSettingChange(DanmakuSettingAction.SetEnabledTypes(entityTypes))
                },
                onDanmakuSizeChange = { onDanmakuSettingChange(DanmakuSettingAction.SetScale(it)) },
                onDanmakuOpacityChange = { onDanmakuSettingChange(DanmakuSettingAction.SetOpacity(it)) },
                onDanmakuSpeedFactorChange = { onDanmakuSettingChange(DanmakuSettingAction.SetSpeedFactor(it)) },
                onDanmakuAreaChange = { onDanmakuSettingChange(DanmakuSettingAction.SetArea(it)) },
                onDanmakuMaskChange = { onDanmakuSettingChange(DanmakuSettingAction.SetMaskEnabled(it)) },
                onSubtitleChange = { subtitle -> onSubtitleChange(subtitle) },
                onSubtitleSizeChange = { onSubtitleSettingChange(SubtitleSettingAction.SetFontSize(it)) },
                onSubtitleBackgroundOpacityChange = { onSubtitleSettingChange(SubtitleSettingAction.SetOpacity(it)) },
                onSubtitleBottomPadding = { onSubtitleSettingChange(SubtitleSettingAction.SetBottomPadding(it)) },
            )
        }
    }
}

// ── 高频进度订阅的叶子组件 ────────────────────────────────────────────────────
//
// 播放进度每 100ms 更新一次。若在 VideoPlayerController 的组合体里直接读
// `seekerState.value`，本组件的 composition scope 会记录快照依赖 —— 于是画面层、
// 信息栏、菜单、视频列表、相关推荐整棵覆盖层每秒失效重组 10 次。
// 下面三个叶子把订阅限制在各自作用域内，父级因此完全不随进度重组。

/** 只订阅 debugInfo 的叶子。 */
@Composable
private fun BoxScope.PlayerDebugInfoOverlay(seekerState: State<SeekerState>) {
    val debugInfo = remember(seekerState) { derivedStateOf { seekerState.value.debugInfo } }.value
    Box(
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(Color.Black.copy(alpha = 0.5f)),
    ) {
        Text(
            modifier = Modifier.padding(8.dp),
            text = debugInfo,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** 常显进度条：订阅时长 / 当前位置 / 缓冲百分比。 */
@Composable
private fun BoxScope.PersistentProgressBar(
    seekerState: State<SeekerState>,
    sponsorMarkers: List<SponsorBlockSegment>,
) {
    val duration =
        remember(seekerState) { derivedStateOf { seekerState.value.totalDuration } }.value
    val position =
        remember(seekerState) { derivedStateOf { seekerState.value.currentTime } }.value
    val buffered =
        remember(seekerState) { derivedStateOf { seekerState.value.bufferedPercentage } }.value
    VideoProgressSeek(
        modifier = Modifier.align(Alignment.BottomCenter),
        duration = duration,
        position = position,
        bufferedPercentage = buffered,
        isPersistentSeek = true,
        sponsorSegments = sponsorMarkers,
    )
}

/** 字幕层：只订阅当前播放位置。 */
@Composable
private fun PlayerSubtitleLayer(
    subtitleData: List<SubtitleItem>,
    seekerState: State<SeekerState>,
    fontSize: TextUnit,
    opacity: Float,
    padding: Dp,
) {
    val currentTime =
        remember(seekerState) { derivedStateOf { seekerState.value.currentTime } }.value
    BottomSubtitle(
        subtitleData = subtitleData,
        currentTime = currentTime,
        fontSize = fontSize,
        opacity = opacity,
        padding = padding,
    )
}
