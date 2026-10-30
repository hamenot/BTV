package dev.frost819.newbv.app.ui.screen.settings.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import dev.frost819.newbv.BuildConfig
import dev.frost819.newbv.app.entity.player.SponsorBlockCategory
import dev.frost819.newbv.app.entity.player.parseSponsorBlockCategories
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.settings.OptionDialog
import dev.frost819.newbv.app.ui.component.settings.SettingListItem
import dev.frost819.newbv.app.ui.component.settings.SettingsOptionDefaults
import dev.frost819.newbv.app.ui.component.settings.displayName
import dev.frost819.newbv.data.datastore.ApiType
import dev.frost819.newbv.data.datastore.Prefs

/**
 * 其他设置页。
 *
 * 接口选择/空降助手/崩溃上报/查看日志。
 *
 * @param onNavigateToLogViewer 跳转日志查看页回调。
 */
@Composable
fun OtherSetting(
    modifier: Modifier = Modifier,
    onNavigateToLogViewer: () -> Unit = {},
    screenFocusSaver: FocusSaver? = null,
) {
    val scrollState = rememberScrollState()

    var showPreferedApiDialog by remember { mutableStateOf(false) }
    var selectedApi by remember { mutableStateOf(Prefs.apiType) }
    var crashReportEnabled by remember { mutableStateOf(Prefs.crashReportEnabled) }

    var sponsorBlockEnabled by remember { mutableStateOf(Prefs.sponsorBlockEnabled) }
    var sponsorBlockShowTip by remember { mutableStateOf(Prefs.sponsorBlockShowTip) }
    var sponsorBlockShowMarkers by remember { mutableStateOf(Prefs.sponsorBlockShowMarkers) }
    var sponsorBlockCategories by remember {
        mutableStateOf(parseSponsorBlockCategories(Prefs.sponsorBlockCategories))
    }
    var showSponsorCategoryDialog by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                // 底部留白放在滚动内容内侧，避免最后一行贴住视口下缘被裁掉圆角。
                .padding(vertical = SettingsOptionDefaults.ListEdgePadding),
        verticalArrangement = Arrangement.spacedBy(SettingsOptionDefaults.ItemSpacing),
    ) {
        SettingListItem(
            title = "接口选择",
            supportText = "当前：${selectedApi.displayName}",
            onClick = { showPreferedApiDialog = true },
        )

        SettingListItem(
            title = "空降助手",
            supportText =
                if (sponsorBlockEnabled) {
                    "自动跳过视频中的赞助广告、三连提示等片段（数据来自 bsbsb.top 社区标注）"
                } else {
                    "已关闭"
                },
            trailingContent = {
                Switch(
                    checked = sponsorBlockEnabled,
                    onCheckedChange = {
                        sponsorBlockEnabled = it
                        Prefs.sponsorBlockEnabled = it
                    },
                )
            },
            onClick = {
                sponsorBlockEnabled = !sponsorBlockEnabled
                Prefs.sponsorBlockEnabled = sponsorBlockEnabled
            },
        )

        if (sponsorBlockEnabled) {
            SettingListItem(
                modifier =
                    screenFocusSaver?.let { Modifier.focusSaverItem(it, "content_sponsor_categories") } ?: Modifier,
                title = "跳过类别",
                supportText = sponsorBlockCategories.joinToString("、") { it.displayName },
                onClick = { showSponsorCategoryDialog = true },
            )

            SettingListItem(
                title = "跳过提示",
                supportText = "跳过片段后短暂显示提示文字",
                trailingContent = {
                    Switch(
                        checked = sponsorBlockShowTip,
                        onCheckedChange = {
                            sponsorBlockShowTip = it
                            Prefs.sponsorBlockShowTip = it
                        },
                    )
                },
                onClick = {
                    sponsorBlockShowTip = !sponsorBlockShowTip
                    Prefs.sponsorBlockShowTip = sponsorBlockShowTip
                },
            )

            SettingListItem(
                title = "进度条标记",
                supportText = "在进度条上用类别颜色标出片段位置",
                trailingContent = {
                    Switch(
                        checked = sponsorBlockShowMarkers,
                        onCheckedChange = {
                            sponsorBlockShowMarkers = it
                            Prefs.sponsorBlockShowMarkers = it
                        },
                    )
                },
                onClick = {
                    sponsorBlockShowMarkers = !sponsorBlockShowMarkers
                    Prefs.sponsorBlockShowMarkers = sponsorBlockShowMarkers
                },
            )
        }

        SettingListItem(
            title = "上传崩溃日志",
            supportText = "崩溃时自动上传日志到开发者服务器",
            trailingContent = {
                Switch(
                    checked = crashReportEnabled,
                    onCheckedChange = {
                        crashReportEnabled = it
                        Prefs.crashReportEnabled = it
                    },
                )
            },
            onClick = {
                crashReportEnabled = !crashReportEnabled
                Prefs.crashReportEnabled = crashReportEnabled
            },
        )

        if (BuildConfig.DEBUG) {
            SettingListItem(
                title = "触发测试崩溃",
                supportText = "仅用于测试崩溃上传流程",
                onClick = { throw RuntimeException("Test crash for upload verification") },
            )
        }

        SettingListItem(
            modifier = screenFocusSaver?.let { Modifier.focusSaverItem(it, "content_log_viewer") } ?: Modifier,
            title = "查看日志",
            supportText = "查看崩溃日志和应用日志，支持扫码下载",
            onClick = onNavigateToLogViewer,
        )
    }

    if (showPreferedApiDialog) {
        OptionDialog(
            options = ApiType.entries.toTypedArray(),
            selectedOption = selectedApi,
            onDismiss = { showPreferedApiDialog = false },
            onSelect = {
                Prefs.apiType = it
                selectedApi = it
            },
            getDisplayName = { it.displayName },
        )
    }

    if (showSponsorCategoryDialog) {
        SponsorBlockCategoryDialog(
            selected = sponsorBlockCategories,
            onDismiss = { showSponsorCategoryDialog = false },
            onToggle = { category, checked ->
                val next =
                    if (checked) {
                        sponsorBlockCategories + category
                    } else {
                        sponsorBlockCategories - category
                    }
                sponsorBlockCategories = next
                Prefs.sponsorBlockCategories = next.joinToString(",") { it.code }
            },
        )
    }
}

/**
 * 空降助手跳过类别多选弹窗。
 *
 * 每行一个类别（复选框），勾选即时保存；「精彩时刻」类别只做进度条标记、不参与跳过。
 *
 * @param selected 当前启用的类别集合。
 * @param onDismiss 关闭回调。
 * @param onToggle 切换某类别的启用状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SponsorBlockCategoryDialog(
    selected: Set<SponsorBlockCategory>,
    onDismiss: () -> Unit,
    onToggle: (SponsorBlockCategory, Boolean) -> Unit,
) {
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val maxHeightDp = with(density) { (windowInfo.containerSize.height * 0.6f).toDp() }

    BasicAlertDialog(
        modifier = Modifier.padding(vertical = 24.dp),
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth(0.7f)
                    .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    text = "跳过类别",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxHeightDp),
                    // 首/末项贴住视口边缘时，聚焦高亮的圆角会被裁掉。
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(SettingsOptionDefaults.ItemSpacing),
                ) {
                    items(SponsorBlockCategory.entries.toList()) { category ->
                        val checked = category in selected
                        SettingListItem(
                            title = category.displayName,
                            supportText =
                                if (category == SponsorBlockCategory.POI_HIGHLIGHT) {
                                    "仅显示进度条标记，不自动跳过"
                                } else {
                                    ""
                                },
                            // 与外部设置页开关同一形态（tv-material3 Switch）；
                            // onCheckedChange 传 null 让开关本身不可聚焦，焦点由整行承担。
                            trailingContent = {
                                Switch(checked = checked, onCheckedChange = null)
                            },
                            onClick = { onToggle(category, !checked) },
                        )
                    }
                }
                Text(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    text = "开启后播放到对应片段时自动跳过",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
