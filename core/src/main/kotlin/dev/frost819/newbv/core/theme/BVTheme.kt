package dev.frost819.newbv.core.theme

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.tv.material3.SurfaceDefaults
import androidx.compose.material3.MaterialTheme as CommonMaterialTheme
import androidx.compose.material3.Surface as CommonSurface
import androidx.compose.material3.darkColorScheme as commonDark
import androidx.compose.material3.lightColorScheme as commonLight
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.darkColorScheme as tvDark
import androidx.tv.material3.lightColorScheme as tvLight

/**
 * new BV 根主题。
 *
 * 根据 [ThemeMode] 与系统状态选择深/浅色板，并提供自定义密度与字体缩放。
 * 可嵌套调用：播放器需要在浅色应用下固定使用深色主题时，用
 * `BVTheme(themeMode = ThemeMode.Dark, density = LocalDensity.current.density)` 包裹即可。
 *
 * 系统状态栏的同步由 [SystemBarsEffect] 负责，仅需在根主题处调用一次。
 *
 * @param themeMode 主题模式（跟随系统 / 深色 / 浅色）。
 * @param density 屏幕密度（TV 场景通常为 2.0）。
 * @param surfaceColor 主题 Surface 的底色，null 表示使用 `colorScheme.surface`。
 *                     嵌套调用时（如播放器强制深色）可传 [Color.Black] 覆盖默认深灰底色。
 * @param accentSeed 手动强调色，null 表示使用默认品牌色。
 * @param content 主题包裹的内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BVTheme(
    themeMode: ThemeMode = ThemeMode.FollowSystem,
    density: Float = 1f,
    surfaceColor: Color? = null,
    accentSeed: Color? = null,
    content: @Composable () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val systemIsDark = isSystemInDarkTheme()
    val isDark = themeMode.isDark(systemIsDark)

    val palette = rememberBVPalette(isDark = isDark, accentSeed = accentSeed)

    val tvColorScheme =
        if (isDark) {
            tvDark(
                primary = palette.primary,
                onPrimary = palette.onPrimary,
                primaryContainer = palette.primaryContainer,
                onPrimaryContainer = palette.onPrimaryContainer,
                secondary = palette.secondary,
                onSecondary = palette.onSecondary,
                secondaryContainer = palette.secondaryContainer,
                onSecondaryContainer = palette.onSecondaryContainer,
                background = palette.background,
                onBackground = palette.onBackground,
                surface = palette.surface,
                onSurface = palette.onSurface,
                surfaceVariant = palette.surfaceVariant,
                onSurfaceVariant = palette.onSurfaceVariant,
                border = palette.border,
            )
        } else {
            tvLight(
                primary = palette.primary,
                onPrimary = palette.onPrimary,
                primaryContainer = palette.primaryContainer,
                onPrimaryContainer = palette.onPrimaryContainer,
                // secondary 作为强调色（选中态文字/图标）使用时需要足够对比度，
                // 浅色背景下使用加深变体，避免低对比导致文字发虚
                secondary = palette.secondary,
                onSecondary = palette.onSecondary,
                secondaryContainer = palette.secondaryContainer,
                onSecondaryContainer = palette.onSecondaryContainer,
                background = palette.background,
                onBackground = palette.onBackground,
                surface = palette.surface,
                onSurface = palette.onSurface,
                surfaceVariant = palette.surfaceVariant,
                onSurfaceVariant = palette.onSurfaceVariant,
                border = palette.border,
            )
        }

    val commonColorScheme =
        if (isDark) {
            commonDark(
                primary = palette.primary,
                onPrimary = palette.onPrimary,
                primaryContainer = palette.primaryContainer,
                onPrimaryContainer = palette.onPrimaryContainer,
                secondary = palette.secondary,
                onSecondary = palette.onSecondary,
                secondaryContainer = palette.secondaryContainer,
                onSecondaryContainer = palette.onSecondaryContainer,
                background = palette.background,
                onBackground = palette.onBackground,
                surface = palette.surface,
                onSurface = palette.onSurface,
                surfaceVariant = palette.surfaceVariant,
                onSurfaceVariant = palette.onSurfaceVariant,
            )
        } else {
            commonLight(
                primary = palette.primary,
                onPrimary = palette.onPrimary,
                primaryContainer = palette.primaryContainer,
                onPrimaryContainer = palette.onPrimaryContainer,
                secondary = palette.secondary,
                onSecondary = palette.onSecondary,
                secondaryContainer = palette.secondaryContainer,
                onSecondaryContainer = palette.onSecondaryContainer,
                background = palette.background,
                onBackground = palette.onBackground,
                surface = palette.surface,
                onSurface = palette.onSurface,
                surfaceVariant = palette.surfaceVariant,
                onSurfaceVariant = palette.onSurfaceVariant,
            )
        }

    TvMaterialTheme(
        colorScheme = tvColorScheme,
        typography = BVTypography.tv,
    ) {
        CommonMaterialTheme(
            colorScheme = commonColorScheme,
            typography = BVTypography.common,
        ) {
            CompositionLocalProvider(
                LocalRippleConfiguration provides null,
                LocalDensity provides Density(density = density, fontScale = fontScale),
            ) {
                CommonSurface(color = Color.Transparent) {
                    TvSurface(
                        shape = RoundedCornerShape(0.dp),
                        // 显式给 contentColor：SurfaceDefaults.colors 默认按
                        // contentColorFor(containerColor) 自动配对，但 Black 不在配色方案中，
                        // 会回退到外层继承的 LocalContentColor（浅色 App 主题下是深色文字），
                        // 叠在黑底上不可见。旧值 colorScheme.surface 恰好命中 surface 才自动得到 onSurface。
                        colors =
                            SurfaceDefaults.colors(
                                containerColor = surfaceColor ?: tvColorScheme.surface,
                                contentColor = tvColorScheme.onSurface,
                            ),
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * 将当前主题色同步到系统状态栏（背景色 + 图标明暗）。
 *
 * 必须在根主题 [BVTheme] 内调用一次。嵌套主题（如播放器强制深色）不需要、
 * 也不应重复调用，否则退出后状态栏会停留在内层主题的颜色上。
 */
@Composable
fun SystemBarsEffect() {
    val view = LocalView.current
    if (view.isInEditMode) return
    val statusBarColor = TvMaterialTheme.colorScheme.primary
    // 逐层剥离 ContextWrapper 找 Activity。
    // view.context 不保证是 Activity（可能是 ContextThemeWrapper / Dialog 包装层），
    // 直接 `as Activity` 会抛 ClassCastException，而这里在 SideEffect 内，崩了就是整棵树 apply 失败。
    val activity =
        runCatching {
            generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>()
                .firstOrNull()
        }.getOrNull()

    SideEffect {
        val window = activity?.window ?: return@SideEffect
        window.statusBarColor = statusBarColor.toArgb()
        // 状态栏图标外观由背景亮度决定：暗色状态栏配浅色图标，反之配深色图标
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
            statusBarColor.luminance() > 0.5f
    }
}
