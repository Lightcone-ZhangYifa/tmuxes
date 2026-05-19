package com.tmuxes.ui.design

import androidx.compose.runtime.Immutable

/**
 * User-tunable opacity values for floating UI surfaces. Built once in
 * [AppTheme] from the user's `app.bubble_opacity` / `app.fab_opacity`
 * settings (Int 0..100 percent in storage) and provided via
 * `LocalAppTokens`.
 *
 * Read at use sites via `MaterialTheme.appTokens.opacity.bubble` /
 * `.fab`. Stored as Float in 0f..1f so call sites can pass directly to
 * `Color.copy(alpha=...)` without dividing by 100.
 */
@Immutable
data class OpacityTokens(
    val bubble: Float,
    val fab: Float
)
