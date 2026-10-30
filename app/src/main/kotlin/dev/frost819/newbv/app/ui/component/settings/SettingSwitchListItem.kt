package dev.frost819.newbv.app.ui.component.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * 带开关的设置列表项。
 *
 * 点击整行切换开关状态；开关用 [SettingsPillSwitch] 自绘且不可聚焦，
 * 焦点始终由整行承担——避免 D-Pad 经过时"行高亮 + 开关高亮"两层视觉打架。
 *
 * @param title 标题。
 * @param supportText 副文本。
 * @param checked 当前开关状态。
 * @param onCheckedChange 开关变化回调。
 */
@Composable
fun SettingSwitchListItem(
    modifier: Modifier = Modifier,
    title: String,
    supportText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    var switchChecked by remember(checked) { mutableStateOf(checked) }

    val toggle = {
        switchChecked = !switchChecked
        onCheckedChange(switchChecked)
    }

    SettingsOptionRow(
        title = title,
        modifier = modifier,
        supportingText = supportText.ifEmpty { null },
        trailingContent = { SettingsPillSwitch(checked = switchChecked) },
        onClick = toggle,
    )
}
