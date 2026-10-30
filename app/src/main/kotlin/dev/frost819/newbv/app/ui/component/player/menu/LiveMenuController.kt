package dev.frost819.newbv.app.ui.component.player.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Wifi
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import dev.frost819.newbv.app.ui.action.player.DanmakuSettingAction
import dev.frost819.newbv.app.ui.component.player.menu.component.PlayerTwoLevelMenu
import dev.frost819.newbv.app.ui.component.player.panel.PlayerPanel
import dev.frost819.newbv.app.ui.component.player.panel.PlayerPanelDefaults
import dev.frost819.newbv.app.viewmodel.player.LocalMenuFocusStateData
import dev.frost819.newbv.app.viewmodel.player.MenuFocusState
import dev.frost819.newbv.app.viewmodel.player.MenuFocusStateData
import dev.frost819.newbv.biliapi.repositories.LivePlayLine
import dev.frost819.newbv.biliapi.repositories.LiveQuality
import dev.frost819.newbv.danmaku.config.DanmakuState
import dev.frost819.newbv.data.datastore.DanmakuType
import kotlin.math.roundToInt

/**
 * 直播播放器设置菜单导航 Tab。
 */
enum class LivePlayerMenuNavItem(
    val displayName: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Picture("画质", Icons.Outlined.Image),
    Line("线路", Icons.Outlined.Wifi),
    Danmaku("弹幕", Icons.Outlined.ClearAll),
}

/**
 * 直播播放器设置菜单控制器。
 *
 * 从右侧滑入的半透明面板，包含 3 个 Tab（画质/线路/弹幕）。
 * 三态焦点模型与视频播放器一致：MenuNav ↔ Menu ↔ Items。
 * 画质、线路 Tab 简化为二级：MenuNav ↔ Items（跳过 Menu 层级）。
 *
 * @param show 是否显示
 * @param initialNavItem 打开时停在哪个分类。控制条上画质/线路/弹幕各有独立入口，
 *   点哪个就直接落到哪一页
 * @param availableQualities 可用画质列表（HDR 档经本机解码能力过滤）
 * @param currentQuality 当前画质档位标识（[LiveQuality.key]）
 * @param onQualityChange 画质变化回调（回传 [LiveQuality.key]）
 * @param availableLines 可用线路列表
 * @param currentLine 当前线路序号（从 1 开始）
 * @param onLineChange 线路变化回调
 * @param danmakuState 弹幕配置状态
 * @param onDanmakuSettingChange 弹幕设置变化回调
 */
@Composable
@Suppress("LongParameterList")
fun LiveMenuController(
    modifier: Modifier = Modifier,
    show: Boolean,
    initialNavItem: LivePlayerMenuNavItem = LivePlayerMenuNavItem.Picture,
    onDismiss: () -> Unit = {},
    anchorFromRight: Dp = 48.dp,
    availableQualities: List<LiveQuality>,
    currentQuality: Int,
    onQualityChange: (Int) -> Unit,
    availableLines: List<LivePlayLine>,
    currentLine: Int,
    onLineChange: (Int) -> Unit,
    danmakuState: DanmakuState,
    onDanmakuSettingChange: (DanmakuSettingAction) -> Unit,
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
        contentAlignment = Alignment.BottomEnd,
    ) {
        // 与点播侧同一套：从"打开它的那个按钮"正上方长出来
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
            label = "LiveSettingsPanel",
        ) {
            LiveMenuControllerContent(
                initialNavItem = initialNavItem,
                onDismiss = onDismiss,
                availableQualities = availableQualities,
                currentQuality = currentQuality,
                onQualityChange = onQualityChange,
                availableLines = availableLines,
                currentLine = currentLine,
                onLineChange = onLineChange,
                danmakuState = danmakuState,
                onDanmakuSettingChange = onDanmakuSettingChange,
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun LiveMenuControllerContent(
    initialNavItem: LivePlayerMenuNavItem,
    onDismiss: () -> Unit,
    availableQualities: List<LiveQuality>,
    currentQuality: Int,
    onQualityChange: (Int) -> Unit,
    availableLines: List<LivePlayLine>,
    currentLine: Int,
    onLineChange: (Int) -> Unit,
    danmakuState: DanmakuState,
    onDanmakuSettingChange: (DanmakuSettingAction) -> Unit,
) {
    // 以入口传来的分类为初值；换一个入口打开（initialNavItem 变化）时重新落位
    val selectedNavItem = initialNavItem
    // 没有分类导航列了，MenuNav 改作「焦点离开面板」的信号：按到它就把面板收起来
    var focusState by remember { mutableStateOf(MenuFocusState.Menu) }

    LaunchedEffect(focusState) {
        if (focusState == MenuFocusState.MenuNav) onDismiss()
    }
    val navFocusRequester = remember { FocusRequester() }

    Surface(colors = SurfaceDefaults.colors(containerColor = Color.Transparent)) {
        CompositionLocalProvider(
            LocalMenuFocusStateData provides MenuFocusStateData(focusState = focusState),
        ) {
            // 每个入口开自己那块面板：卡里只放本分类的选项
            // 弹幕面板是三列表格版式，与点播侧统一钉成固定尺寸，切分类时不跳高度
            val fixedPanel = selectedNavItem == LivePlayerMenuNavItem.Danmaku
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
                LiveMenuList(
                    selectedNavMenu = selectedNavItem,
                    availableQualities = availableQualities,
                    currentQuality = currentQuality,
                    onQualityChange = onQualityChange,
                    availableLines = availableLines,
                    currentLine = currentLine,
                    onLineChange = onLineChange,
                    danmakuState = danmakuState,
                    onDanmakuSettingChange = onDanmakuSettingChange,
                    navFocusRequester = navFocusRequester,
                    onFocusStateChange = { focusState = it },
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
private fun LiveMenuList(
    selectedNavMenu: LivePlayerMenuNavItem,
    availableQualities: List<LiveQuality>,
    currentQuality: Int,
    onQualityChange: (Int) -> Unit,
    availableLines: List<LivePlayLine>,
    currentLine: Int,
    onLineChange: (Int) -> Unit,
    danmakuState: DanmakuState,
    onDanmakuSettingChange: (DanmakuSettingAction) -> Unit,
    navFocusRequester: FocusRequester,
    onFocusStateChange: (MenuFocusState) -> Unit,
) {
    Box(contentAlignment = Alignment.Center) {
        when (selectedNavMenu) {
            LivePlayerMenuNavItem.Picture ->
                PlayerTwoLevelMenu(
                    items = availableQualities,
                    selected = availableQualities.firstOrNull { it.key == currentQuality },
                    itemLabel = { it.displayDesc },
                    onItemSelected = { onQualityChange(it.key) },
                    navFocusRequester = navFocusRequester,
                    onFocusStateChange = onFocusStateChange,
                )

            LivePlayerMenuNavItem.Line ->
                PlayerTwoLevelMenu(
                    items = availableLines,
                    selected = availableLines.firstOrNull { it.order == currentLine },
                    itemLabel = { "线路 ${it.order}" },
                    onItemSelected = { onLineChange(it.order) },
                    navFocusRequester = navFocusRequester,
                    onFocusStateChange = onFocusStateChange,
                )

            LivePlayerMenuNavItem.Danmaku -> {
                val dataTypes =
                    danmakuState.enabledTypes.mapNotNull { entity ->
                        runCatching { DanmakuType.entries[entity.ordinal] }.getOrNull()
                    }
                DanmakuMenuList(
                    currentEnabledTypes = dataTypes,
                    currentScale = danmakuState.scale,
                    currentOpacity = danmakuState.opacity,
                    currentSpeedFactor = danmakuState.speedFactor,
                    currentArea = danmakuState.area,
                    currentMaskEnabled = danmakuState.maskEnabled,
                    onDanmakuSwitchChange = { types ->
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
                    onFocusStateChange = onFocusStateChange,
                    onDanmakuMaskChange = { onDanmakuSettingChange(DanmakuSettingAction.SetMaskEnabled(it)) },
                )
            }
        }
    }
}
