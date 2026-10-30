package dev.frost819.newbv.app.ui.component.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.frost819.newbv.core.focus.focusInvertedColors
import dev.frost819.newbv.core.focus.touchClickable

/**
 * 设置选项行的尺寸规格。
 *
 * 对齐 Animeko TV 的 `TvOptionRow` / `TvOptionDefaults`：12dp 圆角、48–56dp 行高、
 * 16/10 内边距、行距 6dp、聚焦整行反色且不缩放。
 */
object SettingsOptionDefaults {
    /** 行容器圆角：与 Animeko 的 `ItemShape` 一致。 */
    val Shape = RoundedCornerShape(12.dp)

    /** 单行最小高度：保证 10 英尺观看距离下的触达面积。 */
    val MinHeight = 56.dp

    /** 左侧图标尺寸。 */
    val IconSize = 20.dp

    /** 行左右内边距。 */
    val HorizontalPadding = 16.dp

    /** 行上下内边距。 */
    val VerticalPadding = 10.dp

    /** 行与行的间距。 */
    val ItemSpacing = 6.dp

    /**
     * 列表首尾留白（上下各一份）。
     *
     * 必须加在滚动内容内部：第一行/最后一行若贴着视口边缘，12dp 圆角会被滚动容器
     * 裁掉一角，看起来像"顶边或底边丢了"。与 [SettingsOptionRow] 的 `focusedScale = 1f`
     * 配套使用——只关缩放不留余量，行的圆角仍会紧贴边缘。
     */
    val ListEdgePadding = 12.dp

    /** 尾部箭头尺寸。 */
    val ChevronSize = 18.dp
}

/**
 * 设置选项行（Animeko TV 风格）。
 *
 * 结构为「图标 + 标题/副标题 + 右侧当前值 + 尾部箭头」，聚焦时整行反色
 * （[MaterialTheme.colorScheme.inverseSurface] / `inverseOnSurface`），
 * 不使用缩放与描边——TV 上缩放会让相邻行抖动，描边会与行的实心高亮叠加成双框。
 *
 * @param title 主标题。
 * @param modifier 外部修饰符。
 * @param supportingText 副标题（说明或当前值的补充描述）。
 * @param value 右侧当前值，如「1080P」「跟随系统」。
 * @param selected 是否为"当前生效项"（选中态用主色底，与聚焦反色区分）。
 * @param icon 左侧图标。
 * @param trailingIcon 尾部图标，通常为右箭头。
 * @param trailingContent 尾部自定义内容（如开关），位于箭头之前。
 * @param onClick 点击回调。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsOptionRow(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    value: String? = null,
    selected: Boolean = false,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().touchClickable(onClick = onClick),
        shape = ClickableSurfaceDefaults.shape(SettingsOptionDefaults.Shape),
        // 必须显式关闭聚焦放大：TV Material3 默认会放大 1.05 倍，
        // 撑出行边界后被滚动容器裁掉，列表首/末项就会出现"顶边或底边丢失"。
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = .98f),
        colors =
            focusInvertedColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = .16f)
                    } else {
                        Color.Transparent
                    },
                contentColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = SettingsOptionDefaults.MinHeight)
                    .padding(
                        horizontal = SettingsOptionDefaults.HorizontalPadding,
                        vertical = SettingsOptionDefaults.VerticalPadding,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(SettingsOptionDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!supportingText.isNullOrEmpty()) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = .7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (!value.isNullOrEmpty()) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalContentColor.current.copy(alpha = .85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(12.dp))
                trailingContent()
            }

            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(SettingsOptionDefaults.ChevronSize),
                )
            }
        }
    }
}

/**
 * 胶囊开关（Animeko 风格）。
 *
 * 用 Canvas 自绘 36×20 的胶囊：**开启态**使用全局强调色（`colorScheme.primary`，
 * 与 tv-material3 `Switch` 的开启态一致），**关闭态**取当前内容色的低透明度——
 * 因此在聚焦反色（深底变浅底）时关闭态仍然成立。不可聚焦——开关由所在行整体承担点击。
 *
 * @param checked 是否开启。
 * @param modifier 外部修饰符。
 */
@Composable
fun SettingsPillSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val contentColor = LocalContentColor.current
    val accentColor = MaterialTheme.colorScheme.primary
    val trackColor = if (checked) accentColor.copy(alpha = .45f) else contentColor.copy(alpha = .18f)
    val thumbColor = if (checked) accentColor else contentColor
    Canvas(
        modifier = Modifier.size(width = 36.dp, height = 20.dp),
    ) {
        val radius = size.height / 2
        drawRoundRect(
            color = trackColor,
            cornerRadius = CornerRadius(radius),
        )
        drawCircle(
            color = thumbColor,
            radius = radius - 3.dp.toPx(),
            center =
                Offset(
                    x = if (checked) size.width - radius else radius,
                    y = radius,
                ),
        )
    }
}
