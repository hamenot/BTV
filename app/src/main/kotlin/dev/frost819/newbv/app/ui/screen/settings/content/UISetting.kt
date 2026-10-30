package dev.frost819.newbv.app.ui.screen.settings.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.frost819.newbv.app.ui.component.settings.OptionDialog
import dev.frost819.newbv.app.ui.component.settings.SettingListItem
import dev.frost819.newbv.app.ui.component.settings.SettingSwitchListItem
import dev.frost819.newbv.app.ui.component.settings.SettingsOptionDefaults
import dev.frost819.newbv.app.ui.component.settings.SettingsOptionRow
import dev.frost819.newbv.app.ui.component.settings.displayName
import dev.frost819.newbv.app.ui.screen.main.displayName
import dev.frost819.newbv.core.theme.BVAccentPresets
import dev.frost819.newbv.data.datastore.HomeTopNavItem
import dev.frost819.newbv.data.datastore.LeftNaviItem
import dev.frost819.newbv.data.datastore.PersonalTopNavItem
import dev.frost819.newbv.data.datastore.Prefs
import dev.frost819.newbv.data.datastore.ThemeMode
import kotlin.math.roundToInt

/**
 * 界面设置页。
 *
 * 启动页/首页 Tab/个人页 Tab/显示视频详情/常显进度条/Density/主题模式。
 */
@Composable
fun UISetting(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var showDensityDialog by remember { mutableStateOf(false) }
    var dialogDensity by remember { mutableFloatStateOf(Prefs.density) }
    var showStartupPageDialog by remember { mutableStateOf(false) }
    var showHomepageDialog by remember { mutableStateOf(false) }
    var showPersonalPageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAccentDialog by remember { mutableStateOf(false) }
    var accentArgb by remember { mutableStateOf(Prefs.accentColor) }

    var showVideoInfo by remember { mutableStateOf(Prefs.showVideoInfo) }
    // 常显进度条订阅偏好 Flow：播放器快捷键改了偏好后回到设置页，开关显示的是真实值。
    val showPersistentSeek by Prefs.showPersistentSeekFlow.collectAsState()
    var selectedLeftNavItem by remember { mutableStateOf(Prefs.homeLeftNavItem) }
    var selectedFirstHomeTopNavItem by remember { mutableStateOf(Prefs.firstHomeTopNavItem) }
    var selectedFirstPersonalTopNavItem by remember { mutableStateOf(Prefs.firstPersonalTopNavItem) }
    var selectedThemeMode by remember { mutableStateOf(Prefs.themeMode) }
    var density by remember { mutableFloatStateOf(Prefs.density) }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(SettingsOptionDefaults.ItemSpacing),
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(SettingsOptionDefaults.ItemSpacing),
                // 最后一行贴住视口下缘会把聚焦高亮的底边裁掉，需要内容内边距兜住。
                contentPadding = PaddingValues(vertical = SettingsOptionDefaults.ListEdgePadding),
            ) {
                item {
                    SettingListItem(
                        title = "启动页",
                        supportText = "当前：${selectedLeftNavItem.displayName}",
                        onClick = { showStartupPageDialog = true },
                    )
                }
                item {
                    SettingListItem(
                        title = "首页置顶 Tab",
                        supportText = "当前：${selectedFirstHomeTopNavItem.displayName}",
                        onClick = { showHomepageDialog = true },
                    )
                }
                item {
                    SettingListItem(
                        title = "个人页置顶 Tab",
                        supportText = "当前：${selectedFirstPersonalTopNavItem.displayName}",
                        onClick = { showPersonalPageDialog = true },
                    )
                }
                item {
                    SettingListItem(
                        title = "主题模式",
                        supportText = "当前：${selectedThemeMode.displayName}",
                        onClick = { showThemeDialog = true },
                    )
                }
                item {
                    SettingListItem(
                        title = "强调色",
                        supportText = "当前：${accentDisplayName(accentArgb)}",
                        onClick = { showAccentDialog = true },
                    )
                }
                item {
                    SettingSwitchListItem(
                        title = "显示视频详情",
                        supportText = "点击卡片时先进入详情页而非直接播放",
                        checked = showVideoInfo,
                        onCheckedChange = {
                            showVideoInfo = it
                            Prefs.showVideoInfo = it
                        },
                    )
                }
                item {
                    SettingSwitchListItem(
                        title = "常显进度条",
                        supportText = "播放器底部常驻迷你进度条",
                        checked = showPersistentSeek,
                        onCheckedChange = {
                            Prefs.showPersistentSeek = it
                        },
                    )
                }
                item {
                    SettingListItem(
                        title = "界面缩放",
                        supportText = "当前：$density",
                        onClick = {
                            dialogDensity = density
                            showDensityDialog = true
                        },
                    )
                }
            }
        }
    }

    if (showAccentDialog) {
        AccentColorDialog(
            accentArgb = accentArgb,
            onSelectPreset = { argb ->
                accentArgb = argb
                Prefs.accentColor = argb
                showAccentDialog = false
            },
            onDismiss = { showAccentDialog = false },
        )
    }

    UIDensityDialog(
        show = showDensityDialog,
        onHideDialog = {
            showDensityDialog = false
            if (dialogDensity != density) {
                density = dialogDensity
                Prefs.density = dialogDensity
            }
        },
        density = dialogDensity,
        onDensityChange = { dialogDensity = it },
    )

    if (showStartupPageDialog) {
        OptionDialog(
            options = LeftNaviItem.entries.toTypedArray(),
            selectedOption = selectedLeftNavItem,
            onDismiss = { showStartupPageDialog = false },
            onSelect = {
                Prefs.homeLeftNavItem = it
                selectedLeftNavItem = it
            },
            getDisplayName = { it.displayName },
        )
    }

    if (showHomepageDialog) {
        OptionDialog(
            options = HomeTopNavItem.entries.toTypedArray(),
            selectedOption = selectedFirstHomeTopNavItem,
            onDismiss = { showHomepageDialog = false },
            onSelect = {
                Prefs.firstHomeTopNavItem = it
                selectedFirstHomeTopNavItem = it
            },
            getDisplayName = { it.displayName },
        )
    }

    if (showPersonalPageDialog) {
        OptionDialog(
            options = PersonalTopNavItem.entries.toTypedArray(),
            selectedOption = selectedFirstPersonalTopNavItem,
            onDismiss = { showPersonalPageDialog = false },
            onSelect = {
                Prefs.firstPersonalTopNavItem = it
                selectedFirstPersonalTopNavItem = it
            },
            getDisplayName = { it.displayName },
        )
    }

    if (showThemeDialog) {
        OptionDialog(
            options = ThemeMode.entries.toTypedArray(),
            selectedOption = selectedThemeMode,
            onDismiss = { showThemeDialog = false },
            onSelect = {
                Prefs.themeMode = it
                selectedThemeMode = it
            },
            getDisplayName = { it.displayName },
        )
    }
}

/**
 * Density 调节弹窗。
 *
 * D-Pad Up/Down 调整 density 值，范围 0.5 ~ 5.0，步进 0.1。
 * 弹窗打开期间仅更新本地 [dialogDensity]，关闭时才写入 Prefs，
 * 避免全局 density 变化导致 AlertDialog 窗口重建闪烁。
 */
@Composable
private fun UIDensityDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    onHideDialog: () -> Unit,
    density: Float,
    onDensityChange: (Float) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(show) {
        if (show) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    if (show) {
        AlertDialog(
            modifier = modifier,
            onDismissRequest = { onHideDialog() },
            title = { Text(text = "界面缩放") },
            text = {
                Column(
                    modifier =
                        Modifier
                            .focusRequester(focusRequester)
                            .focusable()
                            .fillMaxWidth()
                            .onPreviewKeyEvent {
                                when {
                                    // Up/Down 调整数值（持续按住可连调）
                                    (it.key == Key.DirectionUp || it.key == Key.DirectionDown) &&
                                        it.type == KeyEventType.KeyDown -> {
                                        var newDensity =
                                            if (it.key == Key.DirectionUp) {
                                                density + 0.1f
                                            } else {
                                                density - 0.1f
                                            }
                                        newDensity = (newDensity * 10).roundToInt() / 10f
                                        onDensityChange(newDensity.coerceIn(0.5f, 5f))
                                        true
                                    }
                                    // 遥控器确认键（OK/Enter）：直接应用当前缩放并退出弹窗
                                    (
                                        it.key == Key.DirectionCenter ||
                                            it.key == Key.Enter ||
                                            it.key == Key.Spacebar
                                    ) &&
                                        it.type == KeyEventType.KeyUp -> {
                                        onHideDialog()
                                        true
                                    }
                                    else -> false
                                }
                            },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier =
                            Modifier.clickable {
                                var newDensity = density + 0.1f
                                newDensity = (newDensity * 10).roundToInt() / 10f
                                onDensityChange(newDensity.coerceIn(0.5f, 5f))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(imageVector = Icons.Rounded.ArrowDropUp, contentDescription = "增加")
                    }
                    Text(text = "$density")
                    Box(
                        modifier =
                            Modifier.clickable {
                                var newDensity = density - 0.1f
                                newDensity = (newDensity * 10).roundToInt() / 10f
                                onDensityChange(newDensity.coerceIn(0.5f, 5f))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(imageVector = Icons.Rounded.ArrowDropDown, contentDescription = "减少")
                    }
                }
            },
            // 不设确认按钮：调整完按遥控器 OK 键直接应用并退出弹窗（见上方按键处理）。
            confirmButton = {},
        )
    }
}

/**
 * 强调色选择弹窗。
 *
 * 手动色板与"跟随壁纸"互斥：选任一即关闭另一项，避免两个来源同时生效。
 * 动态取色仅在 Android 12（API 31）及以上出现对应选项。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccentColorDialog(
    accentArgb: Int,
    onSelectPreset: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstOptionRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { firstOptionRequester.requestFocus() }
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(420.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "强调色",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(
                    // 540dp 高的列表在 1080p / 2x 密度下会超出弹窗可用高度，末几项被裁掉。
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(BVAccentPresets.items) { index, (name, argb) ->
                        val isSelected = accentArgb == argb
                        SettingsOptionRow(
                            title = name,
                            modifier =
                                if (index == 0) {
                                    Modifier.focusRequester(firstOptionRequester)
                                } else {
                                    Modifier
                                },
                            selected = isSelected,
                            trailingContent = {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (argb == BVAccentPresets.Default) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    Color(argb)
                                                },
                                            ),
                                )
                            },
                            trailingIcon = if (isSelected) Icons.Rounded.Check else null,
                            onClick = { onSelectPreset(argb) },
                        )
                    }
                }
            }
        }
    }
}

/** 强调色的展示文案（用于设置项副标题）。 */
private fun accentDisplayName(argb: Int): String =
    BVAccentPresets.items.firstOrNull { it.second == argb }?.first ?: "默认"
