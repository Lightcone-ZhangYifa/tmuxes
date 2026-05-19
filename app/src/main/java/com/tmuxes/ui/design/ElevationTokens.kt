package com.tmuxes.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elevation tokens — shadow / tonal layering levels.
 *
 * The app's aesthetic is largely flat (level0 / level1) but has clear
 * conventions for raised elements (FAB at level3, dragged item at level4).
 */
@Immutable
data class ElevationTokens(
    val level0: Dp = 0.dp,
    val level1: Dp = 1.dp,
    val level2: Dp = 3.dp,
    val level3: Dp = 6.dp,
    val level4: Dp = 8.dp,
    val level5: Dp = 12.dp
) {
    companion object {
        val Default = ElevationTokens()
    }
}
