package dev.frost819.newbv.app.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.rememberFocusSaver
import dev.frost819.newbv.app.ui.component.settings.SettingsOptionDefaults
import dev.frost819.newbv.app.ui.component.settings.SettingsOptionRow
import dev.frost819.newbv.app.ui.screen.settings.content.AboutSetting
import dev.frost819.newbv.app.ui.screen.settings.content.AudioVideoSetting
import dev.frost819.newbv.app.ui.screen.settings.content.InfoSetting
import dev.frost819.newbv.app.ui.screen.settings.content.OtherSetting
import dev.frost819.newbv.app.ui.screen.settings.content.StorageSetting
import dev.frost819.newbv.app.ui.screen.settings.content.UISetting

/** 设置页的布局规格，取值对齐 Animeko TV 的 `TvSettingsDefaults`。 */
private object SettingsLayoutDefaults {
    /** 左栏最大宽度。 */
    val NavMaxWidth = 320.dp

    /** 左栏占屏比例上限（窄屏时让位给内容）。 */
    const val NavWidthFraction = .38f

    /** 页面左右内边距。 */
    val HorizontalPadding = 40.dp

    /** 页面上下内边距。 */
    val VerticalPadding = 40.dp

    /** 标题与分组列表之间的间距。 */
    val TitleSpacing = 28.dp

    /** 右栏标题、描述与选项列表之间的间距。 */
    val PaneSpacing = 12.dp

    /** 左栏与右栏之间的间距。 */
    val PaneGap = 16.dp
}

/**
 * 设置页主屏幕。
 *
 * 布局对齐 Animeko TV 的 `TvSettingsLayout`：左栏（≤320dp）为「设置」大标题 + 分类列表，
 * 右栏为当前分类的小标题 + 一行说明 + 选项行。分类列表获焦即切换右栏内容，
 * 内容区按 D-Pad Left 回到分类列表。
 *
 * @param onNavigateToMediaCodec 跳转编解码信息页。
 * @param onNavigateToLogViewer 跳转日志查看页。
 * @param onBack 返回上一页。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onNavigateToMediaCodec: () -> Unit = {},
    onNavigateToLogViewer: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    var currentMenu by rememberSaveable { mutableStateOf(SettingsMenuNavItem.AudioVideo) }
    val screenFocusSaver = rememberFocusSaver()
    val navFocusRequester = remember { FocusRequester() }

    screenFocusSaver.RestoreFocus()

    LaunchedEffect(Unit) {
        // 首次进入（没有历史焦点）时把焦点放到当前分类上。
        if (screenFocusSaver.savedKeyValue().isEmpty()) {
            runCatching { navFocusRequester.requestFocus() }
        }
    }

    BackHandler(onBack = onBack)

    // 焦点驱动的滚动策略对整页生效：左右两栏的列表都要给下缘留余量，
    // 否则最后一项贴着裁剪边界，圆角和高亮底边会看起来像丢了。
    val density = LocalDensity.current
    val bringIntoViewSpec =
        remember(density) {
            SettingsBringIntoViewSpec(
                edgeMarginPx = with(density) { SettingsOptionDefaults.ListEdgePadding.toPx() },
            )
        }

    CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
        BoxWithConstraints(
            modifier =
                modifier
                    .fillMaxSize()
                    // 与其它页面一致：各页面都用 colorScheme.background 铺底，
                    // 设置页若改用 surface 会与首页/轨道区出现色差。
                    .background(MaterialTheme.colorScheme.background),
        ) {
            val navWidth =
                minOf(
                    SettingsLayoutDefaults.NavMaxWidth,
                    maxWidth * SettingsLayoutDefaults.NavWidthFraction,
                )

            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier =
                        Modifier
                            .width(navWidth)
                            .fillMaxHeight()
                            .padding(
                                start = SettingsLayoutDefaults.HorizontalPadding,
                                end = SettingsLayoutDefaults.PaneGap,
                                top = SettingsLayoutDefaults.VerticalPadding,
                                bottom = SettingsLayoutDefaults.VerticalPadding,
                            ),
                    verticalArrangement = Arrangement.spacedBy(SettingsLayoutDefaults.TitleSpacing),
                ) {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                // 列表末尾留白：滚到尽头时最后一项也不贴裁剪边界。
                                .padding(vertical = SettingsOptionDefaults.ListEdgePadding),
                        verticalArrangement = Arrangement.spacedBy(SettingsOptionDefaults.ItemSpacing),
                    ) {
                        SettingsMenuNavItem.entries.forEach { item ->
                            SettingsOptionRow(
                                title = item.displayName,
                                modifier =
                                    Modifier
                                        .then(
                                            if (item == currentMenu) {
                                                Modifier.focusRequester(navFocusRequester)
                                            } else {
                                                Modifier
                                            },
                                        ).focusSaverItem(screenFocusSaver, "nav_${item.name}")
                                        .onFocusChanged {
                                            // 焦点经过即切换分类：与旧的设置导航行为一致，
                                            // 用户不必先按确认键才能看到右侧内容。
                                            if (it.isFocused && item != currentMenu) {
                                                currentMenu = item
                                            }
                                        },
                                icon = item.icon,
                                selected = item == currentMenu,
                                onClick = { currentMenu = item },
                            )
                        }
                    }
                }

                SettingsPane(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(
                                start = SettingsLayoutDefaults.HorizontalPadding,
                                end = SettingsLayoutDefaults.HorizontalPadding,
                                top = SettingsLayoutDefaults.VerticalPadding,
                                bottom = SettingsLayoutDefaults.VerticalPadding,
                            ),
                    currentMenu = currentMenu,
                    onBackNav = { runCatching { navFocusRequester.requestFocus() } },
                    onNavigateToMediaCodec = onNavigateToMediaCodec,
                    onNavigateToLogViewer = onNavigateToLogViewer,
                    screenFocusSaver = screenFocusSaver,
                )
            }
        }
    }
}

/**
 * 右栏：分类标题 + 说明 + 选项列表。
 *
 * D-Pad Left 在内容区一律回到左栏分类列表——选项行是满宽的，
 * 内容区内部没有"更左边"的目标（与旧实现一致）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsPane(
    modifier: Modifier = Modifier,
    currentMenu: SettingsMenuNavItem,
    onBackNav: () -> Unit,
    onNavigateToMediaCodec: () -> Unit = {},
    onNavigateToLogViewer: () -> Unit = {},
    screenFocusSaver: FocusSaver,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SettingsLayoutDefaults.PaneSpacing),
    ) {
        Text(
            text = currentMenu.displayName,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = currentMenu.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onPreviewKeyEvent {
                        if (it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown) {
                            onBackNav()
                            true
                        } else {
                            false
                        }
                    },
        ) {
            when (currentMenu) {
                SettingsMenuNavItem.AudioVideo -> AudioVideoSetting()
                SettingsMenuNavItem.UI -> UISetting()
                SettingsMenuNavItem.Other ->
                    OtherSetting(
                        onNavigateToLogViewer = onNavigateToLogViewer,
                        screenFocusSaver = screenFocusSaver,
                    )
                SettingsMenuNavItem.Storage -> StorageSetting()
                SettingsMenuNavItem.Info ->
                    InfoSetting(
                        onOpenMediaCodec = onNavigateToMediaCodec,
                        screenFocusSaver = screenFocusSaver,
                    )
                SettingsMenuNavItem.About -> AboutSetting()
            }
        }
    }
}

/**
 * 焦点驱动的滚动策略：让目标行停在距容器上下缘各 [edgeMarginPx] 的位置。
 *
 * 平台默认策略只保证"目标露出来"，首行/末行会紧贴视口边缘，12dp 圆角被
 * 滚动容器裁掉一角，看起来像"顶边或底边丢了"。这里上下都留出固定余量，
 * 与列表自带的 `contentPadding` 配合，行永远不贴边。
 */
@OptIn(ExperimentalFoundationApi::class)
private class SettingsBringIntoViewSpec(
    private val edgeMarginPx: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        val bottomLimit = containerSize - edgeMarginPx
        val overflowBottom = offset + size - bottomLimit
        return when {
            // 目标在视口上方：回滚到留出上缘余量的位置
            offset < edgeMarginPx -> offset - edgeMarginPx
            overflowBottom > 0f -> overflowBottom
            else -> 0f
        }
    }
}

/**
 * 设置导航分类。
 *
 * @param displayName 分类名（左栏与右栏标题共用）。
 * @param description 右栏说明文字。
 * @param icon 左栏图标。
 */
enum class SettingsMenuNavItem(
    val displayName: String,
    val description: String,
    val icon: ImageVector,
) {
    AudioVideo(
        displayName = "播放设置",
        description = "首选清晰度、音视频编码与默认倍速",
        icon = Icons.Outlined.SmartDisplay,
    ),
    UI(
        displayName = "界面设置",
        description = "启动页、置顶 Tab、主题与界面缩放",
        icon = Icons.Outlined.Palette,
    ),
    Other(
        displayName = "更多设置",
        description = "接口类型、日志查看与实验性选项",
        icon = Icons.Outlined.Tune,
    ),
    Storage(
        displayName = "存储管理",
        description = "缓存占用、自动清理与手动清除",
        icon = Icons.Outlined.Storage,
    ),
    Info(
        displayName = "设备信息",
        description = "设备能力、编解码支持与应用版本",
        icon = Icons.Outlined.Memory,
    ),
    About(
        displayName = "关于",
        description = "版本信息、开源许可与项目地址",
        icon = Icons.Outlined.Info,
    ),
}
