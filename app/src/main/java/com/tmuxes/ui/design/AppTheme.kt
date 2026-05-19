package com.tmuxes.ui.design

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Navigation style provided by [AppTheme] from the user's
 * `app.navigation_style` setting. Currently only [com.tmuxes.ui.screens.home.HomeScreen]
 * reads this — but having it as a CompositionLocal keeps the read-site
 * decoupled from how the value is fetched.
 */
val LocalNavigationStyle = compositionLocalOf { "bottom_bar" }

/**
 * Root theme composable. Builds [AppTokens] from the user's appearance
 * settings, provides them via [LocalAppTokens], and feeds an equivalent
 * Material 3 [androidx.compose.material3.ColorScheme] / [Typography] to
 * [MaterialTheme] so default M3 controls (which we still use under
 * App-component wrappers) render with the right colors and font sizes.
 *
 * The seven appearance axes:
 *
 * @param themeMode "dark" / "light" / "system"
 * @param palette "mocha" / "material_you" / "custom"
 * @param accentArgb non-zero ARGB int when palette = custom; otherwise
 *                   a palette-default accent is used
 * @param density "compact" / "comfortable" / "spacious" — drives
 *                [SpaceTokens]
 * @param typeScale "small" / "default" / "large" — drives [TypeTokens]
 * @param cornerStyle "sharp" / "rounded" / "pill" — drives [ShapeTokens]
 * @param statusBarStyle "auto" / "surface" / "transparent" — drives
 *                       the activity window status / nav bar tint
 */
@Composable
fun AppTheme(
    themeMode: String,
    palette: String,
    accentArgb: Int,
    density: String,
    typeScale: String,
    cornerStyle: String,
    statusBarStyle: String,
    bubbleOpacityPercent: Int,
    fabOpacityPercent: Int,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    val tokens = remember(
        themeMode, palette, accentArgb, density, typeScale, cornerStyle, isDark,
        bubbleOpacityPercent, fabOpacityPercent
    ) {
        AppTokens(
            colors = ColorTokens.build(
                palette = AppColorPalette.fromKey(palette),
                accentArgb = accentArgb,
                isDark = isDark,
                context = context
            ),
            type = TypeTokens.of(AppTypeScale.fromKey(typeScale)),
            shape = ShapeTokens.of(AppCornerStyle.fromKey(cornerStyle)),
            space = SpaceTokens.of(AppDensity.fromKey(density)),
            elevation = ElevationTokens.Default,
            motion = MotionTokens.Default,
            status = StatusTokens.Default,
            opacity = OpacityTokens(
                bubble = (bubbleOpacityPercent / 100f).coerceIn(0.30f, 1f),
                fab = (fabOpacityPercent / 100f).coerceIn(0.50f, 0.95f)
            )
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            try {
                (view.context as? Activity)?.let { activity ->
                    val window = activity.window
                    @Suppress("DEPRECATION")
                    when (statusBarStyle) {
                        "surface" -> {
                            window.statusBarColor = tokens.colors.surface.toArgb()
                            window.navigationBarColor = tokens.colors.surface.toArgb()
                        }
                        "transparent" -> {
                            window.statusBarColor = android.graphics.Color.TRANSPARENT
                            window.navigationBarColor = android.graphics.Color.TRANSPARENT
                        }
                        else -> { // "auto" — surface in light mode, transparent in dark
                            if (isDark) {
                                window.statusBarColor = android.graphics.Color.TRANSPARENT
                                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                            } else {
                                window.statusBarColor = tokens.colors.surface.toArgb()
                                window.navigationBarColor = tokens.colors.surface.toArgb()
                            }
                        }
                    }
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !isDark
                        isAppearanceLightNavigationBars = !isDark
                    }
                }
            } catch (_: Throwable) {
                // Theme application is cosmetic — never crash composition.
            }
        }
    }

    CompositionLocalProvider(LocalAppTokens provides tokens) {
        MaterialTheme(
            colorScheme = tokens.colors.toMaterialScheme(),
            typography = tokens.type.toMaterialTypography(),
            content = content
        )
    }
}
