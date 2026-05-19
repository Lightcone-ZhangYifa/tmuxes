package com.tmuxes.ui.design

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography tokens. Eleven M3 styles + two app-semantic styles
 * (`sectionHeader`, `mono`). Three user-selectable type scales (small /
 * default / large = 82 / 90 / 105 percent of base font sizes).
 *
 * Screens never use `.copy(fontSize = …)` — if a new style is needed it
 * is added here once.
 */
@Immutable
data class TypeTokens(
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val displaySmall: TextStyle,
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val titleSmall: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle,
    /** Uppercase section header — replaces 7 `letterSpacing = 1.5.sp` repeats. */
    val sectionHeader: TextStyle,
    /** Monospace body — terminal status, key fingerprints, hex codes. */
    val mono: TextStyle,
    val monoSmall: TextStyle
) {
    fun toMaterialTypography(): Typography = Typography(
        displayLarge = displayLarge,
        displayMedium = displayMedium,
        displaySmall = displaySmall,
        headlineLarge = headlineLarge,
        headlineMedium = headlineMedium,
        headlineSmall = headlineSmall,
        titleLarge = titleLarge,
        titleMedium = titleMedium,
        titleSmall = titleSmall,
        bodyLarge = bodyLarge,
        bodyMedium = bodyMedium,
        bodySmall = bodySmall,
        labelLarge = labelLarge,
        labelMedium = labelMedium,
        labelSmall = labelSmall
    )

    companion object {
        fun of(scale: AppTypeScale): TypeTokens {
            val factor = when (scale) {
                AppTypeScale.Small -> 0.82f
                AppTypeScale.Default -> 0.9f
                AppTypeScale.Large -> 1.05f
            }
            fun s(base: Int) = (base * factor).sp
            val mono = FontFamily.Monospace
            val sans = FontFamily.Default

            return TypeTokens(
                displayLarge = TextStyle(
                    fontFamily = sans, fontWeight = FontWeight.Normal,
                    fontSize = s(57), lineHeight = s(64), letterSpacing = (-0.25).sp
                ),
                displayMedium = TextStyle(
                    fontFamily = sans, fontWeight = FontWeight.Normal,
                    fontSize = s(45), lineHeight = s(52)
                ),
                displaySmall = TextStyle(
                    fontFamily = sans, fontWeight = FontWeight.Normal,
                    fontSize = s(36), lineHeight = s(44)
                ),
                headlineLarge = TextStyle(
                    fontFamily = sans, fontWeight = FontWeight.SemiBold,
                    fontSize = s(32), lineHeight = s(40)
                ),
                headlineMedium = TextStyle(
                    fontFamily = sans, fontWeight = FontWeight.SemiBold,
                    fontSize = s(28), lineHeight = s(36)
                ),
                headlineSmall = TextStyle(
                    fontFamily = sans, fontWeight = FontWeight.SemiBold,
                    fontSize = s(24), lineHeight = s(32)
                ),
                titleLarge = TextStyle(
                    fontFamily = sans, fontWeight = FontWeight.Medium,
                    fontSize = s(22), lineHeight = s(28)
                ),
                titleMedium = TextStyle(
                    fontFamily = sans, fontWeight = FontWeight.Medium,
                    fontSize = s(16), lineHeight = s(24), letterSpacing = 0.15.sp
                ),
                titleSmall = TextStyle(
                    fontFamily = sans, fontWeight = FontWeight.Medium,
                    fontSize = s(14), lineHeight = s(20), letterSpacing = 0.1.sp
                ),
                bodyLarge = TextStyle(
                    fontFamily = sans, fontWeight = FontWeight.Normal,
                    fontSize = s(16), lineHeight = s(24), letterSpacing = 0.5.sp
                ),
                bodyMedium = TextStyle(
                    fontFamily = sans, fontWeight = FontWeight.Normal,
                    fontSize = s(14), lineHeight = s(20), letterSpacing = 0.25.sp
                ),
                bodySmall = TextStyle(
                    fontFamily = sans, fontWeight = FontWeight.Normal,
                    fontSize = s(12), lineHeight = s(16), letterSpacing = 0.4.sp
                ),
                labelLarge = TextStyle(
                    fontFamily = sans, fontWeight = FontWeight.Medium,
                    fontSize = s(14), lineHeight = s(20), letterSpacing = 0.1.sp
                ),
                labelMedium = TextStyle(
                    fontFamily = sans, fontWeight = FontWeight.Medium,
                    fontSize = s(12), lineHeight = s(16), letterSpacing = 0.5.sp
                ),
                labelSmall = TextStyle(
                    fontFamily = sans, fontWeight = FontWeight.Medium,
                    fontSize = s(11), lineHeight = s(16), letterSpacing = 0.5.sp
                ),
                sectionHeader = TextStyle(
                    fontFamily = sans, fontWeight = FontWeight.SemiBold,
                    fontSize = s(12), lineHeight = s(16), letterSpacing = 1.5.sp
                ),
                mono = TextStyle(
                    fontFamily = mono, fontWeight = FontWeight.Normal,
                    fontSize = s(14), lineHeight = s(20)
                ),
                monoSmall = TextStyle(
                    fontFamily = mono, fontWeight = FontWeight.Normal,
                    fontSize = s(12), lineHeight = s(16)
                )
            )
        }
    }
}

enum class AppTypeScale {
    Small, Default, Large;

    companion object {
        fun fromKey(key: String): AppTypeScale = when (key) {
            "small" -> Small
            "large" -> Large
            else -> Default
        }
    }
}
