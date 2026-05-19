package com.tmuxes.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing tokens on an 8 dp grid, scaled by user-selected density.
 *
 * Naming: `xxs / xs / sm / md / lg / xl / xxl / xxxl` — semantic shorthand
 * for "padding rhythm slots". Screens NEVER use raw `.dp` for layout
 * spacing; they call `tokens.space.lg` etc.
 *
 * The three density modes are real spacing changes (not LocalDensity tricks
 * which break Material control sizing). Compact = 75 % spacing, Spacious
 * = 125 %, Comfortable = 100 % baseline.
 */
@Immutable
data class SpaceTokens(
    val xxs: Dp,
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val xxl: Dp,
    val xxxl: Dp
) {
    companion object {
        private val BASE = SpaceTokens(
            xxs = 2.dp,
            xs = 4.dp,
            sm = 8.dp,
            md = 12.dp,
            lg = 16.dp,
            xl = 24.dp,
            xxl = 32.dp,
            xxxl = 48.dp
        )

        fun of(density: AppDensity): SpaceTokens = when (density) {
            AppDensity.Compact -> BASE.scale(0.75f)
            AppDensity.Comfortable -> BASE
            AppDensity.Spacious -> BASE.scale(1.25f)
        }
    }

    private fun scale(factor: Float) = SpaceTokens(
        xxs = (xxs.value * factor).dp,
        xs = (xs.value * factor).dp,
        sm = (sm.value * factor).dp,
        md = (md.value * factor).dp,
        lg = (lg.value * factor).dp,
        xl = (xl.value * factor).dp,
        xxl = (xxl.value * factor).dp,
        xxxl = (xxxl.value * factor).dp
    )
}

enum class AppDensity {
    Compact, Comfortable, Spacious;

    companion object {
        fun fromKey(key: String): AppDensity = when (key) {
            "compact" -> Compact
            "spacious" -> Spacious
            else -> Comfortable
        }
    }
}
