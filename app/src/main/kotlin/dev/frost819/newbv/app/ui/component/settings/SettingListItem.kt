package dev.frost819.newbv.app.ui.component.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 设置列表项（可进入下一级的选项）。
 *
 * 视觉规格见 [SettingsOptionRow]：12dp 圆角、聚焦整行反色、右侧当前值 + 箭头。
 * 与旧实现相比去掉了 `ListItem` 的双行高亮与缩放，改为 Animeko TV 的选项行形态。
 *
 * @param title 标题。
 * @param supportText 副文本（通常是"当前：xxx"或说明）。
 * @param onClick 点击回调。
 * @param value 右侧当前值（可选）。
 * @param trailingContent 尾部自定义内容，提供时不再显示箭头。
 */
@Composable
fun SettingListItem(
    modifier: Modifier = Modifier,
    title: String,
    supportText: String,
    onClick: () -> Unit,
    value: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    SettingsOptionRow(
        title = title,
        modifier = modifier,
        supportingText = supportText.ifEmpty { null },
        value = value,
        trailingContent = trailingContent,
        trailingIcon = if (trailingContent == null) Icons.AutoMirrored.Rounded.KeyboardArrowRight else null,
        onClick = onClick,
    )
}
