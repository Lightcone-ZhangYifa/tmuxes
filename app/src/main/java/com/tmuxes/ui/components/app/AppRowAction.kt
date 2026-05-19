package com.tmuxes.ui.components.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One action revealed when a list row is left-swiped.
 *
 * `color` should come from `MaterialTheme.appTokens.status.*` (semantic
 * roles: connected/info/warning/error/...) or `tokens.colors.danger`,
 * never a raw hex literal. Background buttons use this color full-bleed.
 */
data class AppRowAction(
    val icon: ImageVector,
    val label: String,
    val color: Color,
    val onClick: () -> Unit
)
