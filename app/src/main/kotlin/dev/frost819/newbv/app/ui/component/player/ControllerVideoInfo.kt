package dev.frost819.newbv.app.ui.component.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.frost819.newbv.R
import dev.frost819.newbv.app.entity.player.SponsorBlockSegment
import dev.frost819.newbv.app.ui.state.player.SeekerState
import dev.frost819.newbv.app.util.VideoShotImageCache
import dev.frost819.newbv.app.util.formatHourMinSec
import dev.frost819.newbv.app.viewmodel.player.VideoPlayerMenuNavItem
import dev.frost819.newbv.biliapi.entity.video.VideoShot
import dev.frost819.newbv.core.theme.BVTheme
import kotlinx.coroutines.delay

/** 播放/暂停按钮的 contentDescription，同时用作它的焦点请求器登记键。 */
internal const val PLAY_PAUSE_BUTTON_DESCRIPTION = "播放/暂停"

/**
 * 分类 → 控制条上对应按钮的 contentDescription。
 *
 * 设置面板是从某一个具体按钮弹出来的，关掉之后焦点要回到那个按钮，
 * 所以两边要用同一个字符串当键（[ControllerVideoInfo] 里按 contentDescription 登记请求器）。
 */
internal fun VideoPlayerMenuNavItem.controlButtonDescription(): String =
    when (this) {
        VideoPlayerMenuNavItem.Picture -> "画质"
        VideoPlayerMenuNavItem.PlaySpeed -> "倍速"
        VideoPlayerMenuNavItem.Danmaku -> "弹幕设置"
        VideoPlayerMenuNavItem.ClosedCaption -> "字幕"
    }

/**
 * 播放器控制层风格规格。
 *
 * 版式与取色对齐 Animeko TV 的播放器界面：
 *
 * - 顶部标题栏与底部控制区各自一块单一方向的黑色渐变，中间是纯画面，不给整屏铺遮罩；
 * - 底部自下而上只有两行：**进度行**（当前时间 · 进度条 · 总时长）与
 *   **图标行**（左侧播放系控制、右侧杂项，中间用弹性空白顶开）；
 * - 焦点视觉统一为**整块反色**（浅底 + 深色内容），且**不做缩放**——
 *   播放器里缩放会让控件边缘糊到画面上，反色已经足够醒目；
 * - 开关类按钮（弹幕/循环）的"已开启"状态统一用**主色字形**标记，
 *   不换字形——换字形容易出现描边体与实心块混排，一行图标轻重不一；
 * - 内容统一留 48dp 水平安全边距（overscan）。
 */
internal object PlayerControlsDefaults {
    /** 内容水平安全边距（overscan）。 */
    val HorizontalPadding = 48.dp

    /** 顶部标题栏上下留白。 */
    val TitleTopPadding = 28.dp

    /** 标题栏向下渐隐的长度：文字下方要留一段，避免渐变在字底硬切。 */
    val TitleBottomPadding = 40.dp

    /**
     * 底部遮罩高度（点播）。
     *
     * 只覆盖"进度行 + 图标行"这两行文字的实际范围再往上留一点渐变，
     * 早先统一给 300dp，等于把半屏都压黑了。
     */
    val VideoBottomScrimHeight = 220.dp

    /** 底部遮罩高度（直播）：直播只有一行图标，比点播更矮。 */
    val LiveBottomScrimHeight = 140.dp

    /** 底部内容区下留白。 */
    val BottomContentPadding = 28.dp

    /** 进度行与图标行之间的间距。 */
    val RowSpacing = 14.dp

    /** 进度行内部间距（时间 ↔ 进度条）。 */
    val ProgressRowSpacing = 16.dp

    /** 图标按钮触达尺寸（圆形）。 */
    val IconButtonSize = 44.dp

    /** 图标字形尺寸。 */
    val IconSize = 24.dp

    /** 图标与按钮边缘的间距：(44 - 24) / 2。 */
    val IconPadding = 10.dp

    /** 左侧播放系按钮之间的间距。 */
    val LeftGroupSpacing = 8.dp

    /** 右侧杂项按钮之间的间距。 */
    val RightGroupSpacing = 8.dp

    /** 主内容色。 */
    val Content = Color.White

    /** 次要内容色（副标题、时钟）。 */
    val SecondaryContent = Color.White.copy(alpha = 0.72f)

    /** 控制层进出的时长；淡入比淡出慢一档，收起时不拖沓。 */
    const val EnterDurationMillis = 220
    const val ExitDurationMillis = 160

    /**
     * 右侧按钮组里第 [index] 个按钮的中心，距离屏幕右缘多远。
     *
     * 右组是右对齐的（终点固定在 `内容右缘 = 屏宽 - HorizontalPadding`），所以只要知道
     * 组内序号和总数就能算出位置，不必真的去测量——弹出的设置面板靠这个值锚定到按钮上方。
     */
    fun rightButtonCenterFromRight(
        index: Int,
        count: Int,
    ): Dp =
        HorizontalPadding +
            (IconButtonSize + RightGroupSpacing) * (count - 1 - index) +
            IconButtonSize / 2
}

/**
 * 播放器控制器信息层。
 *
 * 包含顶部标题栏和底部控制区两部分，中间保持为纯净的 video surface。
 * 底部遮罩是**静止**的：控制层淡入/滑动时遮罩不动，避免整块黑色跟着一起推。
 *
 * @param modifier 修饰符
 * @param show 是否显示
 * @param isSeeking 是否正在 seek
 * @param goTime seek 预览位置（毫秒）
 * @param seekerState 进度条状态
 * @param title 视频标题
 * @param clock 时钟（hour, minute）
 * @param onlineWatching 同时观看人数文案（空串时不显示）
 * @param videoShot 缩略图数据（为 null 时不显示预览）
 * @param videoShotCache 缩略图缓存
 * @param isPgc 是否来自番剧（为 true 时隐藏 UP 主页按钮）
 * @param danmakuEnabled 弹幕是否开启
 * @param isLooping 是否循环播放
 * @param onDirectionLeft seek 左移回调
 * @param onDirectionRight seek 右移回调
 * @param onSeekGoTime 确认 seek 回调
 * @param onSeekToPosition 触屏拖拽/点击进度条时 seek 到指定位置（毫秒）
 * @param onPlayPause 播放/暂停回调
 * @param onDanmakuSwitchChange 弹幕开关回调
 * @param onOpenSetting 直接打开某一类设置面板（画质/倍速/弹幕/字幕）
 * @param onToggleLoop 切换循环回调
 * @param onGoToUpPage 跳转 UP 主页面回调
 * @param onShowInteraction 打开视频交互弹窗回调
 * @param onShowComments 打开评论弹窗回调
 */
@Composable
fun ControllerVideoInfo(
    modifier: Modifier = Modifier,
    show: Boolean,
    isSeeking: Boolean,
    goTime: Long,
    seekerState: State<SeekerState>,
    title: String,
    clock: Pair<Int, Int>,
    onlineWatching: String,
    sponsorSegments: List<SponsorBlockSegment> = emptyList(),
    videoShot: VideoShot?,
    videoShotCache: VideoShotImageCache,
    isPgc: Boolean,
    danmakuEnabled: Boolean,
    isLooping: Boolean,
    refocusTick: Int = 0,
    refocusTarget: String? = null,
    onDirectionLeft: () -> Unit,
    onDirectionRight: () -> Unit,
    onSeekGoTime: () -> Unit,
    onSeekToPosition: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onDanmakuSwitchChange: () -> Unit,
    onOpenSetting: (VideoPlayerMenuNavItem) -> Unit,
    onToggleLoop: () -> Unit,
    onGoToUpPage: () -> Unit,
    onShowInteraction: () -> Unit,
    onShowComments: () -> Unit,
) {
    // 遮罩单独做透明度动画：它不跟着内容一起滑，只在整体显隐时淡入淡出。
    val scrimAlpha by animateFloatAsState(
        targetValue = if (show) 1f else 0f,
        animationSpec =
            tween(
                if (show) PlayerControlsDefaults.EnterDurationMillis else PlayerControlsDefaults.ExitDurationMillis,
            ),
        label = "player-bottom-scrim",
    )

    Box(modifier = modifier.fillMaxSize()) {
        // 静止的底部遮罩
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(PlayerControlsDefaults.VideoBottomScrimHeight)
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
            label = "PlayerTopBar",
        ) {
            ControllerVideoInfoTop(
                title = title,
                clock = clock,
                onlineWatching = onlineWatching,
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
            label = "PlayerBottomBar",
        ) {
            ControllerVideoInfoBottom(
                isSeeking = isSeeking,
                goTime = goTime,
                seekerState = seekerState,
                sponsorSegments = sponsorSegments,
                videoShot = videoShot,
                videoShotCache = videoShotCache,
                isPgc = isPgc,
                danmakuEnabled = danmakuEnabled,
                isLooping = isLooping,
                refocusTick = refocusTick,
                refocusTarget = refocusTarget,
                onDirectionLeft = onDirectionLeft,
                onDirectionRight = onDirectionRight,
                onSeekGoTime = onSeekGoTime,
                onSeekToPosition = onSeekToPosition,
                onPlayPause = onPlayPause,
                onDanmakuSwitchChange = onDanmakuSwitchChange,
                onOpenSetting = onOpenSetting,
                onToggleLoop = onToggleLoop,
                onGoToUpPage = onGoToUpPage,
                onShowInteraction = onShowInteraction,
                onShowComments = onShowComments,
            )
        }
    }
}

/**
 * 控制器顶部标题栏。
 *
 * 两行结构（对齐 Animeko TV）：主标题 + 一行副信息，时钟靠右对齐。
 * 文字直接压在上方渐变的黑幕上，不再叠阴影——阴影在纯黑渐变上是一层多余的脏边。
 *
 * @param onlineWatching 同时观看人数文案（服务端预格式化，如 "9.4万+"），空串时不显示
 */
@Composable
fun ControllerVideoInfoTop(
    modifier: Modifier = Modifier,
    title: String,
    clock: Pair<Int, Int>,
    onlineWatching: String,
) {
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
            if (onlineWatching.isNotEmpty()) {
                Text(
                    text = "${onlineWatching}人正在看",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = PlayerControlsDefaults.SecondaryContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Clock(
            modifier = Modifier.padding(start = 24.dp, top = 4.dp),
            clock = clock,
        )
    }
}

/**
 * 控制器底部控制区：缩略图预览 + 进度行 + 图标行。
 *
 * 只负责"画"，焦点与按键语义都挂在进度条 / 图标行自己身上（见各分支注释）。
 */
@Composable
fun ControllerVideoInfoBottom(
    modifier: Modifier = Modifier,
    isSeeking: Boolean,
    goTime: Long,
    seekerState: State<SeekerState>,
    sponsorSegments: List<SponsorBlockSegment> = emptyList(),
    videoShot: VideoShot?,
    videoShotCache: VideoShotImageCache,
    isPgc: Boolean,
    danmakuEnabled: Boolean,
    isLooping: Boolean,
    refocusTick: Int = 0,
    refocusTarget: String? = null,
    onDirectionLeft: () -> Unit,
    onDirectionRight: () -> Unit,
    onSeekGoTime: () -> Unit,
    onSeekToPosition: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onDanmakuSwitchChange: () -> Unit,
    onOpenSetting: (VideoPlayerMenuNavItem) -> Unit,
    onToggleLoop: () -> Unit,
    onGoToUpPage: () -> Unit,
    onShowInteraction: () -> Unit,
    onShowComments: () -> Unit,
) {
    val seekFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // 每个图标按钮一个请求器，按 contentDescription 登记。
    // 浮层（设置/交互…）是从某个按钮弹出的，关掉后焦点要回到**那个按钮**，
    // 而不是一律扔回进度条——否则每次都要重新在图标行里挪一遍。
    val buttonFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    fun buttonRequester(description: String): FocusRequester =
        buttonFocusRequesters.getOrPut(description) { FocusRequester() }

    // 图标行第一个按钮（播放/暂停）的请求器：进度条按下方向键的固定落点。
    // 进度条是通栏的，交给焦点系统做方向搜索会按"中心距离"挑，落点会随上次的横向位置漂移，
    // 有时落在中间的弹幕开关上、有时落在右侧设置组，手感不确定。
    // 注意：同一个节点只能挂一个 focusRequester，所以这里复用登记表里的那一个，别再新建。
    val firstButtonFocusRequester = buttonRequester(PLAY_PAUSE_BUTTON_DESCRIPTION)

    var isSeekFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 收起态刚展开时节点可能还没上树，重试几次直到真正拿到焦点
        repeat(5) {
            delay(50)
            if (runCatching { seekFocusRequester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }

    // 浮层关掉后，焦点原本停在被销毁的浮层里，整层只剩一个不可聚焦的空壳 →
    // 方向键彻底没反应。这里把焦点送回去：有明确来源按钮就回那个按钮，否则回进度条。
    LaunchedEffect(refocusTick, refocusTarget) {
        if (refocusTick > 0) {
            delay(60)
            val target =
                refocusTarget
                    ?.let { buttonFocusRequesters[it] }
                    ?: seekFocusRequester
            runCatching { target.requestFocus() }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Bottom,
    ) {
        // Seek 缩略图预览
        if (isSeeking && videoShot != null) {
            SeekThumbnail(
                videoShot = videoShot,
                imageCache = videoShotCache,
                goTime = goTime,
                seekerState = seekerState,
            )
        }

        Column(
            modifier =
                Modifier.padding(
                    start = PlayerControlsDefaults.HorizontalPadding,
                    end = PlayerControlsDefaults.HorizontalPadding,
                    bottom = PlayerControlsDefaults.BottomContentPadding,
                ),
        ) {
            // 进度行：当前时间 · 进度条 · 总时长（seek 预览时左侧时间转主色）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PlayerControlsDefaults.ProgressRowSpacing),
            ) {
                SeekTimeText(
                    isSeeking = isSeeking,
                    goTime = goTime,
                    seekerState = seekerState,
                )

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            // 焦点不靠描边框表达（方框套细线永远是四个角对不上），
                            // 而是让进度条自己在聚焦时浮出一个拇指圆点，见 VideoProgressSeek。
                            //
                            // 顺序有讲究：
                            // - focusRequester 必须在 focusable 之前，否则请求器绑定不到焦点目标；
                            // - onFocusChanged 也必须在 focusable 之前——焦点事件是从焦点目标
                            //   向外冒泡的，观察者写在 focusable 之后等于挂在内侧，永远收不到 isFocused，
                            //   表现就是"焦点明明在进度条上，圆点却不出来"。
                            .onFocusChanged { isSeekFocused = it.isFocused }
                            .focusRequester(seekFocusRequester)
                            .focusable()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val firstDown =
                                        awaitFirstDown(
                                            requireUnconsumed = false,
                                            pass = PointerEventPass.Initial,
                                        )
                                    firstDown.consume()

                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull() ?: break
                                        val w = this.size.width.toFloat()

                                        if (!change.pressed) {
                                            // 手指抬起：seek 到最终位置
                                            if (w > 0 && seekerState.value.totalDuration > 0) {
                                                val ratio = (change.position.x / w).coerceIn(0f, 1f)
                                                val targetTime = (ratio * seekerState.value.totalDuration).toLong()
                                                onSeekToPosition(targetTime)
                                            }
                                            change.consume()
                                            break
                                        }

                                        if (change.positionChanged()) {
                                            // 拖拽中：持续 seek 到触摸位置
                                            if (w > 0 && seekerState.value.totalDuration > 0) {
                                                val ratio = (change.position.x / w).coerceIn(0f, 1f)
                                                val targetTime = (ratio * seekerState.value.totalDuration).toLong()
                                                onSeekToPosition(targetTime)
                                            }
                                            change.consume()
                                        }
                                    }
                                }
                            }.onKeyEvent {
                                when (it.key) {
                                    Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                                        if (it.type == KeyEventType.KeyUp) return@onKeyEvent true
                                        if (isSeeking) onSeekGoTime() else onPlayPause()
                                        true
                                    }

                                    Key.DirectionLeft, Key.MediaRewind -> {
                                        if (it.type == KeyEventType.KeyUp) return@onKeyEvent true
                                        onDirectionLeft()
                                        true
                                    }

                                    Key.DirectionRight, Key.MediaFastForward -> {
                                        if (it.type == KeyEventType.KeyUp) return@onKeyEvent true
                                        onDirectionRight()
                                        true
                                    }

                                    Key.DirectionDown -> {
                                        if (it.type == KeyEventType.KeyUp) return@onKeyEvent true
                                        // 固定落到图标行第一个控件；万一那一刻它还没上树，
                                        // 再退回焦点系统的方向搜索兜底。
                                        val moved =
                                            runCatching {
                                                firstButtonFocusRequester.requestFocus()
                                            }.getOrDefault(false)
                                        if (!moved) focusManager.moveFocus(FocusDirection.Down)
                                        true
                                    }

                                    else -> false
                                }
                            },
                ) {
                    SeekProgressBar(
                        isSeeking = isSeeking,
                        goTime = goTime,
                        seekerState = seekerState,
                        sponsorSegments = sponsorSegments,
                        focused = isSeekFocused || isSeeking,
                    )
                }

                TotalDurationText(seekerState = seekerState)
            }

            // 图标行：左侧播放系控制 · 弹性空白 · 右侧杂项
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onKeyEvent {
                            if (it.key == Key.DirectionUp) {
                                if (it.type == KeyEventType.KeyUp) return@onKeyEvent true
                                seekFocusRequester.requestFocus()
                                return@onKeyEvent true
                            }
                            false
                        }.padding(top = PlayerControlsDefaults.RowSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PlayerControlsDefaults.LeftGroupSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    playbackIcons(
                        danmakuEnabled = danmakuEnabled,
                        isLooping = isLooping,
                        onPlayPause = onPlayPause,
                        onDanmakuSwitchChange = onDanmakuSwitchChange,
                        onToggleLoop = onToggleLoop,
                    ).forEach { item ->
                        PlayerIconButton(
                            item = item,
                            modifier =
                                Modifier.focusRequester(buttonRequester(item.description)),
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(PlayerControlsDefaults.RightGroupSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    extraIcons(
                        isPgc = isPgc,
                        onOpenSetting = onOpenSetting,
                        onGoToUpPage = onGoToUpPage,
                        onShowInteraction = onShowInteraction,
                        onShowComments = onShowComments,
                    ).forEach { item ->
                        PlayerIconButton(
                            item = item,
                            modifier = Modifier.focusRequester(buttonRequester(item.description)),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 左侧播放系图标：播放/暂停、弹幕开关、循环。
 *
 * 两个开关都**只在开启时把字形染成主色**，不换字形：之前循环用的是实心方块版
 * 图标，和这一行的描边图标混排，一开就显得比别的按钮重一截。
 */
private fun playbackIcons(
    danmakuEnabled: Boolean,
    isLooping: Boolean,
    onPlayPause: () -> Unit,
    onDanmakuSwitchChange: () -> Unit,
    onToggleLoop: () -> Unit,
): List<PlayerIcon> =
    listOf(
        drawableIcon(R.drawable.play_pause_24px, PLAY_PAUSE_BUTTON_DESCRIPTION, onPlayPause),
        drawableIcon(
            if (danmakuEnabled) R.drawable.danmaku_on_24px else R.drawable.danmaku_off_24px,
            "弹幕开关",
            onDanmakuSwitchChange,
            active = danmakuEnabled,
        ),
        drawableIcon(R.drawable.repeat_one_24px, "循环播放", onToggleLoop, active = isLooping),
    )

/**
 * 右侧图标：设置的四类子项直接摊到控制条上，后接页面级跳转项。
 *
 * 设置面板里的子项只有画质 / 倍速 / 弹幕 / 字幕四类，藏在"设置 → 导航 → 面板"
 * 三层后面要按三次键才够得着。这里直接用四个按钮各开对应面板，省掉导航这一层。
 *
 * 相关视频、视频详情已经从控制条上移除：它们都是离开当前播放页的跳转，
 * 和"调节当前播放"不是一类操作，混在一行里容易被误触。
 */
private fun extraIcons(
    isPgc: Boolean,
    onOpenSetting: (VideoPlayerMenuNavItem) -> Unit,
    onGoToUpPage: () -> Unit,
    onShowInteraction: () -> Unit,
    onShowComments: () -> Unit,
): List<PlayerIcon> =
    buildList {
        add(vectorIcon(Icons.Outlined.Image, "画质") { onOpenSetting(VideoPlayerMenuNavItem.Picture) })
        add(vectorIcon(Icons.Outlined.Speed, "倍速") { onOpenSetting(VideoPlayerMenuNavItem.PlaySpeed) })
        add(vectorIcon(Icons.Outlined.ClearAll, "弹幕设置") { onOpenSetting(VideoPlayerMenuNavItem.Danmaku) })
        add(vectorIcon(Icons.Outlined.ClosedCaption, "字幕") { onOpenSetting(VideoPlayerMenuNavItem.ClosedCaption) })
        if (!isPgc) {
            add(drawableIcon(R.drawable.contact_page_24px, "up主页", onGoToUpPage))
        }
        add(drawableIcon(R.drawable.interaction_24px, "交互", onShowInteraction))
        add(drawableIcon(R.drawable.comment, "评论", onShowComments))
    }

/**
 * 时钟。
 *
 * 对齐 Animeko TV：与副标题同级的小号次要色文字，不做加粗/描边/大字号——
 * 播放器里最该突出的是标题，时钟只是角落的环境信息。
 */
@Composable
private fun Clock(
    modifier: Modifier = Modifier,
    clock: Pair<Int, Int>,
) {
    Text(
        modifier = modifier,
        text = "${clock.first.pad2()}:${clock.second.pad2()}",
        style = MaterialTheme.typography.titleMedium,
        color = PlayerControlsDefaults.SecondaryContent,
    )
}

/** 两位补零。 */
private fun Int.pad2(): String = if (this < 10) "0$this" else toString()

// ── 高频进度订阅的叶子组件 ────────────────────────────────────────────────────
//
// 播放进度以 100ms 的节奏更新（PlayerViewModel.startSeekerUpdater）。
// 若在父级组合体里直接 `seekerState.value`，父级的 composition scope 会记录快照依赖，
// 于是整棵播放器覆盖层（画面层、菜单、视频列表、相关推荐）每秒失效重组 10 次。
// 下面几个叶子把读取限制在自己的作用域内：只有它们跟着进度重组。

/** 只订阅「总时长」的叶子：缩略图预览。 */
@Composable
private fun SeekThumbnail(
    videoShot: VideoShot,
    imageCache: VideoShotImageCache,
    goTime: Long,
    seekerState: State<SeekerState>,
) {
    val totalDuration =
        remember(seekerState) { derivedStateOf { seekerState.value.totalDuration } }.value
    VideoShot(
        modifier = Modifier.padding(horizontal = PlayerControlsDefaults.HorizontalPadding),
        videoShot = videoShot,
        imageCache = imageCache,
        position = goTime,
        duration = totalDuration,
        coercedOffset = (-24).dp,
    )
}

/** 只订阅「当前时间」的叶子：进度行左侧时间文本。 */
@Composable
private fun SeekTimeText(
    isSeeking: Boolean,
    goTime: Long,
    seekerState: State<SeekerState>,
) {
    val currentTime =
        remember(seekerState) { derivedStateOf { seekerState.value.currentTime } }.value
    Text(
        text = (if (isSeeking) goTime else currentTime).formatHourMinSec(),
        style = MaterialTheme.typography.titleSmall,
        color =
            if (isSeeking) {
                MaterialTheme.colorScheme.primary
            } else {
                PlayerControlsDefaults.Content
            },
    )
}

/** 只订阅进度三元组（时长/当前/缓冲）的叶子：主进度条。 */
@Composable
private fun SeekProgressBar(
    isSeeking: Boolean,
    goTime: Long,
    seekerState: State<SeekerState>,
    sponsorSegments: List<SponsorBlockSegment>,
    focused: Boolean,
) {
    val totalDuration =
        remember(seekerState) { derivedStateOf { seekerState.value.totalDuration } }.value
    val currentTime =
        remember(seekerState) { derivedStateOf { seekerState.value.currentTime } }.value
    val bufferedPercentage =
        remember(seekerState) { derivedStateOf { seekerState.value.bufferedPercentage } }.value
    VideoProgressSeek(
        modifier = Modifier.fillMaxWidth(),
        duration = totalDuration,
        position = if (isSeeking) goTime else currentTime,
        bufferedPercentage = bufferedPercentage,
        isPersistentSeek = false,
        // 与 Animeko 一致：聚焦或正在拖动时都显示拇指圆点
        focused = focused,
        sponsorSegments = sponsorSegments,
    )
}

/** 只订阅「总时长」的叶子：进度行右侧总时长文本。 */
@Composable
private fun TotalDurationText(seekerState: State<SeekerState>) {
    val totalDuration =
        remember(seekerState) { derivedStateOf { seekerState.value.totalDuration } }.value
    Text(
        text = totalDuration.formatHourMinSec(),
        style = MaterialTheme.typography.titleSmall,
        color = PlayerControlsDefaults.Content,
    )
}

// region Previews

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ControllerVideoInfoPreview() {
    BVTheme {
        ControllerVideoInfo(
            show = true,
            isSeeking = false,
            goTime = 0L,
            seekerState =
                remember {
                    mutableStateOf(
                        SeekerState(
                            totalDuration = 600_000L,
                            currentTime = 120_000L,
                            bufferedPercentage = 50,
                        ),
                    )
                },
            title = "示例视频标题",
            clock = Pair(14, 30),
            onlineWatching = "9.4万+",
            videoShot = null,
            videoShotCache = VideoShotImageCache(),
            isPgc = false,
            danmakuEnabled = true,
            isLooping = false,
            onDirectionLeft = {},
            onDirectionRight = {},
            onSeekGoTime = {},
            onSeekToPosition = {},
            onPlayPause = {},
            onDanmakuSwitchChange = {},
            onOpenSetting = {},
            onToggleLoop = {},
            onGoToUpPage = {},
            onShowInteraction = {},
            onShowComments = {},
        )
    }
}

// endregion
