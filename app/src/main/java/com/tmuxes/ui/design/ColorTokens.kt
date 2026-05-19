package com.tmuxes.ui.design

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Color tokens — the single source of truth for every color the app
 * renders. Every screen reads `LocalAppTokens.current.colors.*` (or
 * indirectly via `MaterialTheme.colorScheme.*`, since [toMaterialScheme]
 * mirrors the same values).
 *
 * Three palettes are supported:
 * - **Default** — the app's curated dark / light palette.
 * - **Material You** — Android 12+ wallpaper-derived palette via
 *   `dynamic{Dark,Light}ColorScheme(context)`.
 * - **Custom** — user picks one accent family; it resolves to paired
 *   dark / light seed colors before roles are retinted; on-colors are
 *   recomputed for WCAG AA contrast.
 *
 * Beyond the M3 roles, app-specific tokens [accent], [danger],
 * [warning], [success], [info], [divider], [dimmer] solve the actual
 * pain points discovered in the audit (38 `outline.copy(alpha=0.5f)`
 * sites all collapse into [divider]; 8 hardcoded `tertiary` callers
 * that wanted "accent-ish" become [accent]).
 */
@Immutable
data class ColorTokens(
    // M3 roles
    val primary: Color, val onPrimary: Color,
    val primaryContainer: Color, val onPrimaryContainer: Color,
    val secondary: Color, val onSecondary: Color,
    val secondaryContainer: Color, val onSecondaryContainer: Color,
    val tertiary: Color, val onTertiary: Color,
    val tertiaryContainer: Color, val onTertiaryContainer: Color,
    val error: Color, val onError: Color,
    val errorContainer: Color, val onErrorContainer: Color,
    val background: Color, val onBackground: Color,
    val surface: Color, val onSurface: Color,
    val surfaceVariant: Color, val onSurfaceVariant: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val outline: Color, val outlineVariant: Color,
    val scrim: Color,
    val inverseSurface: Color, val inverseOnSurface: Color, val inversePrimary: Color,
    val surfaceTint: Color,
    // App semantic tokens (extend M3)
    val accent: Color, val onAccent: Color,
    val danger: Color, val onDanger: Color,
    val warning: Color, val onWarning: Color,
    val success: Color, val onSuccess: Color,
    val info: Color, val onInfo: Color,
    val divider: Color,
    val dimmer: Color,
    val isDark: Boolean
) {
    fun toMaterialScheme(): ColorScheme {
        val base = if (isDark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            secondary = secondary, onSecondary = onSecondary,
            secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary, onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
            error = error, onError = onError,
            errorContainer = errorContainer, onErrorContainer = onErrorContainer,
            background = background, onBackground = onBackground,
            surface = surface, onSurface = onSurface,
            surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            outline = outline, outlineVariant = outlineVariant,
            scrim = scrim,
            inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface,
            inversePrimary = inversePrimary,
            surfaceTint = surfaceTint
        )
    }

    companion object {
        /**
         * Build tokens from user settings.
         *
         * @param palette which palette family
         * @param accentArgb non-zero seed color when [palette] = Custom
         * @param isDark dark mode (already resolved from theme + system)
         * @param context needed only for Material You (dynamic palette)
         */
        fun build(
            palette: AppColorPalette,
            accentArgb: Int,
            isDark: Boolean,
            context: Context
        ): ColorTokens = when (palette) {
            AppColorPalette.Default -> if (isDark) defaultDark() else defaultLight()
            AppColorPalette.MaterialYou ->
                if (Build.VERSION.SDK_INT >= 31) fromMaterialScheme(
                    if (isDark) dynamicDarkColorScheme(context)
                    else dynamicLightColorScheme(context),
                    isDark = isDark
                ) else if (isDark) defaultDark() else defaultLight()
            AppColorPalette.Custom -> customFromSeed(
                Color(ThemeAccents.resolve(accentArgb, isDark)),
                isDark
            )
        }
    }
}

enum class AppColorPalette {
    Default, MaterialYou, Custom;

    companion object {
        fun fromKey(key: String): AppColorPalette = when (key) {
            "default", "mocha", "catppuccin" -> Default
            "material_you" -> MaterialYou
            "custom" -> Custom
            else -> Default
        }
    }
}

// =============================================================================
// Curated default dark + light palettes
// =============================================================================

private object Catppuccin {
    // Mocha (dark)
    val Rosewater = Color(0xFFF5E0DC)
    val Mauve     = Color(0xFFCBA6F7)
    val Pink      = Color(0xFFF5C2E7)
    val Red       = Color(0xFFF38BA8)
    val Peach     = Color(0xFFFAB387)
    val Yellow    = Color(0xFFF9E2AF)
    val Green     = Color(0xFFA6E3A1)
    val Teal      = Color(0xFF94E2D5)
    val Sky       = Color(0xFF89DCEB)
    val Blue      = Color(0xFF89B4FA)
    val Lavender  = Color(0xFFB4BEFE)
    val Text      = Color(0xFFCDD6F4)
    val Subtext1  = Color(0xFFBAC2DE)
    val Subtext0  = Color(0xFFA6ADC8)
    val Overlay2  = Color(0xFF9399B2)
    val Overlay1  = Color(0xFF7F849C)
    val Overlay0  = Color(0xFF6C7086)
    val Surface2  = Color(0xFF585B70)
    val Surface1  = Color(0xFF45475A)
    val Surface0  = Color(0xFF313244)
    val Base      = Color(0xFF1E1E2E)
    val Mantle    = Color(0xFF181825)
    val Crust     = Color(0xFF11111B)

    // Latte (light)
    val LatteRosewater = Color(0xFFDC8A78)
    val LatteLavender  = Color(0xFF7287FD)
    val LatteMauve     = Color(0xFF8839EF)
    val LattePink      = Color(0xFFEA76CB)
    val LatteRed       = Color(0xFFD20F39)
    val LattePeach     = Color(0xFFFE640B)
    val LatteYellow    = Color(0xFFDF8E1D)
    val LatteGreen     = Color(0xFF40A02B)
    val LatteTeal      = Color(0xFF179299)
    val LatteSky       = Color(0xFF04A5E5)
    val LatteBlue      = Color(0xFF1E66F5)
    val LatteText      = Color(0xFF4C4F69)
    val LatteSubtext1  = Color(0xFF5C5F77)
    val LatteSubtext0  = Color(0xFF6C6F85)
    val LatteOverlay2  = Color(0xFF7C7F93)
    val LatteOverlay1  = Color(0xFF8C8FA1)
    val LatteOverlay0  = Color(0xFF9CA0B0)
    val LatteSurface2  = Color(0xFFACB0BE)
    val LatteSurface1  = Color(0xFFBCC0CC)
    val LatteSurface0  = Color(0xFFCCD0DA)
    val LatteBase      = Color(0xFFEFF1F5)
    val LatteMantle    = Color(0xFFE6E9EF)
    val LatteCrust     = Color(0xFFDCE0E8)
}

private fun defaultDark(): ColorTokens = ColorTokens(
    primary = Catppuccin.Blue, onPrimary = Catppuccin.Crust,
    primaryContainer = Color(0xFF17365F), onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Catppuccin.Lavender, onSecondary = Catppuccin.Crust,
    secondaryContainer = Color(0xFF30314E), onSecondaryContainer = Color(0xFFE0E5FF),
    tertiary = Catppuccin.Teal, onTertiary = Catppuccin.Crust,
    tertiaryContainer = Color(0xFF1E3B36), onTertiaryContainer = Catppuccin.Teal,
    error = Catppuccin.Red, onError = Catppuccin.Crust,
    errorContainer = Color(0xFF3E1A22), onErrorContainer = Catppuccin.Red,
    background = Catppuccin.Crust, onBackground = Catppuccin.Text,
    surface = Catppuccin.Base, onSurface = Catppuccin.Text,
    surfaceVariant = Catppuccin.Surface0, onSurfaceVariant = Catppuccin.Subtext1,
    surfaceContainerLowest = Color(0xFF0D0D17),
    surfaceContainerLow = Catppuccin.Crust,
    surfaceContainer = Catppuccin.Mantle,
    surfaceContainerHigh = Catppuccin.Surface0,
    surfaceContainerHighest = Catppuccin.Surface1,
    outline = Catppuccin.Overlay0, outlineVariant = Catppuccin.Surface1,
    scrim = Color(0xFF000000),
    inverseSurface = Catppuccin.Text, inverseOnSurface = Catppuccin.Crust,
    inversePrimary = Catppuccin.LatteBlue,
    surfaceTint = Catppuccin.Blue,
    accent = Catppuccin.Blue, onAccent = Catppuccin.Crust,
    danger = Catppuccin.Red, onDanger = Catppuccin.Crust,
    warning = Catppuccin.Peach, onWarning = Catppuccin.Crust,
    success = Catppuccin.Green, onSuccess = Catppuccin.Crust,
    info = Catppuccin.Sky, onInfo = Catppuccin.Crust,
    divider = Catppuccin.Surface1,
    dimmer = Color(0x99000000),
    isDark = true
)

private fun defaultLight(): ColorTokens = ColorTokens(
    primary = Catppuccin.LatteBlue, onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E2FF), onPrimaryContainer = Color(0xFF0B1B3F),
    secondary = Color(0xFF475569), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0), onSecondaryContainer = Color(0xFF172033),
    tertiary = Color(0xFF0F766E), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCFBF1), onTertiaryContainer = Color(0xFF042F2E),
    error = Catppuccin.LatteRed, onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Catppuccin.LatteBase, onBackground = Catppuccin.LatteText,
    surface = Catppuccin.LatteBase, onSurface = Catppuccin.LatteText,
    surfaceVariant = Catppuccin.LatteMantle, onSurfaceVariant = Catppuccin.LatteSubtext0,
    surfaceContainerLowest = Color(0xFFF8F9FC),
    surfaceContainerLow = Catppuccin.LatteBase,
    surfaceContainer = Catppuccin.LatteMantle,
    surfaceContainerHigh = Catppuccin.LatteCrust,
    surfaceContainerHighest = Catppuccin.LatteSurface0,
    outline = Catppuccin.LatteOverlay0, outlineVariant = Catppuccin.LatteSurface0,
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF313244), inverseOnSurface = Catppuccin.LatteBase,
    inversePrimary = Catppuccin.Blue,
    surfaceTint = Catppuccin.LatteBlue,
    accent = Catppuccin.LatteBlue, onAccent = Color.White,
    danger = Catppuccin.LatteRed, onDanger = Color.White,
    warning = Color(0xFFB45309), onWarning = Color.White,
    success = Color(0xFF15803D), onSuccess = Color.White,
    info = Catppuccin.LatteBlue, onInfo = Color.White,
    divider = Catppuccin.LatteSurface1,
    dimmer = Color(0x66000000),
    isDark = false
)

/**
 * Build tokens from a Material You [ColorScheme]. The dynamic palette
 * already gives us most M3 roles; we synthesize the app-semantic
 * extensions (accent / danger / warning / success / info / divider /
 * dimmer) from the dynamic roles + safe constants.
 */
private fun fromMaterialScheme(scheme: ColorScheme, isDark: Boolean): ColorTokens =
    ColorTokens(
        primary = scheme.primary, onPrimary = scheme.onPrimary,
        primaryContainer = scheme.primaryContainer, onPrimaryContainer = scheme.onPrimaryContainer,
        secondary = scheme.secondary, onSecondary = scheme.onSecondary,
        secondaryContainer = scheme.secondaryContainer, onSecondaryContainer = scheme.onSecondaryContainer,
        tertiary = scheme.tertiary, onTertiary = scheme.onTertiary,
        tertiaryContainer = scheme.tertiaryContainer, onTertiaryContainer = scheme.onTertiaryContainer,
        error = scheme.error, onError = scheme.onError,
        errorContainer = scheme.errorContainer, onErrorContainer = scheme.onErrorContainer,
        background = scheme.background, onBackground = scheme.onBackground,
        surface = scheme.surface, onSurface = scheme.onSurface,
        surfaceVariant = scheme.surfaceVariant, onSurfaceVariant = scheme.onSurfaceVariant,
        surfaceContainerLowest = scheme.surfaceContainerLowest,
        surfaceContainerLow = scheme.surfaceContainerLow,
        surfaceContainer = scheme.surfaceContainer,
        surfaceContainerHigh = scheme.surfaceContainerHigh,
        surfaceContainerHighest = scheme.surfaceContainerHighest,
        outline = scheme.outline, outlineVariant = scheme.outlineVariant,
        scrim = scheme.scrim,
        inverseSurface = scheme.inverseSurface, inverseOnSurface = scheme.inverseOnSurface,
        inversePrimary = scheme.inversePrimary,
        surfaceTint = scheme.surfaceTint,
        accent = scheme.tertiary, onAccent = scheme.onTertiary,
        danger = scheme.error, onDanger = scheme.onError,
        warning = if (isDark) Catppuccin.Peach else Catppuccin.LattePeach,
        onWarning = (if (isDark) Catppuccin.Peach else Catppuccin.LattePeach).contrastingFg(),
        success = if (isDark) Catppuccin.Green else Catppuccin.LatteGreen,
        onSuccess = (if (isDark) Catppuccin.Green else Catppuccin.LatteGreen).contrastingFg(),
        info = if (isDark) Catppuccin.Sky else Catppuccin.LatteBlue,
        onInfo = (if (isDark) Catppuccin.Sky else Catppuccin.LatteBlue).contrastingFg(),
        divider = scheme.outlineVariant,
        dimmer = if (isDark) Color(0x99000000) else Color(0x66000000),
        isDark = isDark
    )

/**
 * Build a custom palette from a single seed color. Primary / secondary
 * / tertiary / accent all derive from rotating hue offsets of the seed;
 * containers are alpha-blended toward background; on-colors auto-pick
 * white or black based on luminance for WCAG AA contrast.
 *
 * This is a pragmatic minimum-viable replacement for Material's HCT
 * palette generator — enough to give the user a coherent theme without
 * adding an HCT/CAM16 library.
 */
private fun customFromSeed(seed: Color, isDark: Boolean): ColorTokens {
    val hue = seed.toHsl()[0]
    val saturation = seed.toHsl()[1].coerceIn(0.42f, 0.74f)
    val neutralSaturation = (saturation * if (isDark) 0.22f else 0.16f).coerceIn(0.06f, 0.14f)

    fun hueColor(offset: Float, sat: Float, light: Float): Color =
        hslToColor((hue + offset + 360f) % 360f, sat.coerceIn(0f, 1f), light.coerceIn(0f, 1f))

    fun neutral(light: Float): Color = hueColor(0f, neutralSaturation, light)

    val primary = if (isDark) {
        hueColor(0f, saturation, 0.74f)
    } else {
        hueColor(0f, saturation * 0.86f, 0.42f)
    }
    val secondary = if (isDark) {
        hueColor(32f, saturation * 0.62f, 0.72f)
    } else {
        hueColor(32f, saturation * 0.58f, 0.38f)
    }
    val tertiary = if (isDark) {
        hueColor(-58f, saturation * 0.68f, 0.70f)
    } else {
        hueColor(-58f, saturation * 0.62f, 0.38f)
    }

    val background = if (isDark) neutral(0.075f) else neutral(0.985f)
    val surface = if (isDark) neutral(0.105f) else neutral(0.992f)
    val surfaceVariant = if (isDark) neutral(0.18f) else neutral(0.92f)
    val surfaceContainerLowest = if (isDark) neutral(0.055f) else Color.White
    val surfaceContainerLow = if (isDark) neutral(0.085f) else neutral(0.972f)
    val surfaceContainer = if (isDark) neutral(0.12f) else neutral(0.955f)
    val surfaceContainerHigh = if (isDark) neutral(0.16f) else neutral(0.935f)
    val surfaceContainerHighest = if (isDark) neutral(0.205f) else neutral(0.905f)
    val onSurface = if (isDark) neutral(0.90f) else neutral(0.20f)
    val onSurfaceVariant = if (isDark) neutral(0.74f) else neutral(0.35f)
    val outline = if (isDark) neutral(0.45f) else neutral(0.50f)
    val outlineVariant = if (isDark) neutral(0.28f) else neutral(0.82f)

    val primaryContainer = primary.blend(surfaceContainer, if (isDark) 0.64f else 0.82f)
    val secondaryContainer = secondary.blend(surfaceContainer, if (isDark) 0.68f else 0.84f)
    val tertiaryContainer = tertiary.blend(surfaceContainer, if (isDark) 0.68f else 0.84f)
    val danger = (if (isDark) Catppuccin.Red else Catppuccin.LatteRed).blend(primary, 0.08f)
    val warning = (if (isDark) Catppuccin.Peach else Catppuccin.LattePeach).blend(primary, 0.10f)
    val success = (if (isDark) Catppuccin.Green else Catppuccin.LatteGreen).blend(primary, 0.08f)
    val info = (if (isDark) Catppuccin.Sky else Catppuccin.LatteBlue).blend(primary, 0.12f)
    val errorContainer = danger.blend(surfaceContainer, if (isDark) 0.72f else 0.82f)

    return ColorTokens(
        primary = primary, onPrimary = primary.contrastingFg(),
        primaryContainer = primaryContainer, onPrimaryContainer = primaryContainer.contrastingFg(),
        secondary = secondary, onSecondary = secondary.contrastingFg(),
        secondaryContainer = secondaryContainer, onSecondaryContainer = secondaryContainer.contrastingFg(),
        tertiary = tertiary, onTertiary = tertiary.contrastingFg(),
        tertiaryContainer = tertiaryContainer, onTertiaryContainer = tertiaryContainer.contrastingFg(),
        error = danger, onError = danger.contrastingFg(),
        errorContainer = errorContainer, onErrorContainer = errorContainer.contrastingFg(),
        background = background, onBackground = onSurface,
        surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        outline = outline, outlineVariant = outlineVariant,
        scrim = Color(0xFF000000),
        inverseSurface = if (isDark) onSurface else Color(0xFF25262F),
        inverseOnSurface = if (isDark) background else Color(0xFFF6F7FB),
        inversePrimary = if (isDark) primary.darken(0.34f) else primary.lighten(0.36f),
        surfaceTint = primary,
        accent = primary, onAccent = primary.contrastingFg(),
        danger = danger, onDanger = danger.contrastingFg(),
        warning = warning, onWarning = warning.contrastingFg(),
        success = success, onSuccess = success.contrastingFg(),
        info = info, onInfo = info.contrastingFg(),
        divider = outlineVariant,
        dimmer = if (isDark) Color(0x99000000) else Color(0x66000000),
        isDark = isDark
    )
}

// =============================================================================
// HSL utilities for custom palette derivation (no external deps)
// =============================================================================

private fun Color.contrastingFg(): Color =
    if (contrastRatio(Color.Black) >= contrastRatio(Color.White)) Color.Black else Color.White

private fun Color.contrastRatio(other: Color): Float {
    val l1 = luminance() + 0.05f
    val l2 = other.luminance() + 0.05f
    return maxOf(l1, l2) / minOf(l1, l2)
}

private fun Color.lighten(amount: Float): Color = blend(Color.White, amount)
private fun Color.darken(amount: Float): Color = blend(Color.Black, amount)

private fun Color.blend(other: Color, ratio: Float): Color {
    val r = ratio.coerceIn(0f, 1f)
    return Color(
        red = red * (1 - r) + other.red * r,
        green = green * (1 - r) + other.green * r,
        blue = blue * (1 - r) + other.blue * r,
        alpha = alpha
    )
}

private fun Color.shiftHue(degrees: Float): Color {
    val hsl = toHsl()
    val newH = (hsl[0] + degrees + 360f) % 360f
    return hslToColor(newH, hsl[1], hsl[2])
}

private fun Color.shiftLightness(delta: Float): Color {
    val hsl = toHsl()
    val newL = (hsl[2] + delta).coerceIn(0f, 1f)
    return hslToColor(hsl[0], hsl[1], newL)
}

private fun Color.toHsl(): FloatArray {
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (max == min) return floatArrayOf(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> ((g - b) / d + (if (g < b) 6f else 0f)) * 60f
        g -> ((b - r) / d + 2f) * 60f
        else -> ((r - g) / d + 4f) * 60f
    }
    return floatArrayOf(h, s, l)
}

private fun hslToColor(h: Float, s: Float, l: Float): Color {
    fun hueToRgb(p: Float, q: Float, t0: Float): Float {
        var t = t0
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
    if (s == 0f) return Color(l, l, l)
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    val hh = h / 360f
    return Color(hueToRgb(p, q, hh + 1f / 3f), hueToRgb(p, q, hh), hueToRgb(p, q, hh - 1f / 3f))
}
