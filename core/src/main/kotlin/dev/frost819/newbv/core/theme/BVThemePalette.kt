package dev.frost819.newbv.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * 强调色预设。
 *
 * `0` 表示"默认品牌色"（[BVColors.Primary] / [BVColors.PrimaryStrong]）；
 * 其余为 ARGB 颜色值，选中后统一驱动 `primary` 与 `secondary` 两个色槽——
 * 前者用于按钮/焦点容器，后者用于顶栏 Tab、UP 图标等强调元素。
 */
object BVAccentPresets {
    /** 默认品牌色（不使用自定义强调色）。 */
    const val Default = 0

    /**
     * 预设色板。
     *
     * 取值与 [BVColors] 的低饱和体系一致，避免自定义色与中性色打架。
     */
    val items: List<Pair<String, Int>> =
        listOf(
            "默认" to Default,
            "紫" to 0xFF7773AD.toInt(),
            "靛" to 0xFF6F7DC8.toInt(),
            "蓝" to 0xFF5B8FC9.toInt(),
            "青" to 0xFF5B9B94.toInt(),
            "绿" to 0xFF70A684.toInt(),
            "黄" to 0xFFC49B5C.toInt(),
            "橙" to 0xFFC97F5B.toInt(),
            "红" to 0xFFC87878.toInt(),
            "粉" to 0xFFC878A8.toInt(),
        )

    /** 预设值 → 颜色，`Default` 返回 null。 */
    fun colorOf(argb: Int): Color? = if (argb == Default) null else Color(argb)
}

/**
 * 主题调色板解析结果。
 *
 * 与 `ColorScheme` 解耦，便于在 tv-material3 与 material3 两套 scheme 之间复用同一份取值。
 */
internal data class BVPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val border: Color,
)

/**
 * 解析当前主题使用的调色板。
 *
 * 只用品牌默认色 + 可选的手动强调色，不依赖系统动态取色（Android 12+），
 * 以保证 minSdk 21 的老设备行为一致。
 *
 * @param isDark 是否深色主题。
 * @param accentSeed 手动强调色，null 表示使用默认品牌色。
 */
@Composable
internal fun rememberBVPalette(
    isDark: Boolean,
    accentSeed: Color?,
): BVPalette =
    remember(isDark, accentSeed) {
        buildBVPalette(isDark, accentSeed)
    }

private fun buildBVPalette(
    isDark: Boolean,
    accentSeed: Color?,
): BVPalette {
    val base =
        if (isDark) {
            BVPalette(
                primary = BVColors.Primary,
                onPrimary = Color.White,
                primaryContainer = BVColors.PrimaryStrong,
                onPrimaryContainer = Color.White,
                secondary = BVColors.Secondary,
                onSecondary = BVColors.DarkOnBackground,
                secondaryContainer = BVColors.DarkSurfaceVariant,
                onSecondaryContainer = BVColors.DarkOnSurface,
                background = BVColors.DarkBackground,
                onBackground = BVColors.DarkOnBackground,
                surface = BVColors.DarkSurface,
                onSurface = BVColors.DarkOnSurface,
                surfaceVariant = BVColors.DarkSurfaceVariant,
                onSurfaceVariant = BVColors.DarkOnSurfaceVariant,
                border = BVColors.DarkBorder,
            )
        } else {
            BVPalette(
                primary = BVColors.PrimaryStrong,
                onPrimary = Color.White,
                primaryContainer = BVColors.PrimaryLight,
                onPrimaryContainer = BVColors.LightOnBackground,
                secondary = BVColors.SecondaryStrong,
                onSecondary = Color.White,
                secondaryContainer = BVColors.LightSurfaceVariant,
                onSecondaryContainer = BVColors.LightOnBackground,
                background = BVColors.LightBackground,
                onBackground = BVColors.LightOnBackground,
                surface = BVColors.LightSurface,
                onSurface = BVColors.LightOnSurface,
                surfaceVariant = BVColors.LightSurfaceVariant,
                onSurfaceVariant = BVColors.LightOnSurfaceVariant,
                border = BVColors.LightBorder,
            )
        }

    if (accentSeed == null) return base

    // 手动强调色：只替换强调相关色槽，中性色保持品牌基线，避免整机跑色。
    // border（卡片/选项的焦点描边）也跟随强调色——否则自定义强调色后，
    // 信息流卡片聚焦边框仍是品牌默认色，视觉上"强调色不生效"。
    val onAccent = if (accentSeed.luminance() > .55f) Color.Black else Color.White
    return base.copy(
        primary = accentSeed,
        onPrimary = onAccent,
        primaryContainer = lerp(accentSeed, base.background, .72f),
        onPrimaryContainer = if (isDark) Color(0xFFF2F2F2) else base.onBackground,
        secondary = accentSeed,
        onSecondary = onAccent,
        secondaryContainer = lerp(accentSeed, base.background, .82f),
        onSecondaryContainer = if (isDark) Color(0xFFF2F2F2) else base.onBackground,
        border = lerp(accentSeed, base.border, .15f),
    )
}
