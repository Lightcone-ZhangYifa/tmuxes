package com.tmuxes.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Top-level design-token container. The single source of truth read by
 * every App component and every screen.
 *
 * Access pattern:
 * ```
 *   val tokens = LocalAppTokens.current
 *   Modifier.padding(tokens.space.lg)
 *   Text(text, style = tokens.type.titleMedium)
 *   color = tokens.colors.accent
 * ```
 *
 * Or via the convenience read on `MaterialTheme`:
 * ```
 *   Modifier.padding(MaterialTheme.appTokens.space.lg)
 * ```
 */
@Immutable
data class AppTokens(
    val colors: ColorTokens,
    val type: TypeTokens,
    val shape: ShapeTokens,
    val space: SpaceTokens,
    val elevation: ElevationTokens,
    val motion: MotionTokens,
    val status: StatusTokens,
    val opacity: OpacityTokens
)

/**
 * The CompositionLocal injected by [AppTheme]. Reading this outside an
 * AppTheme tree throws — that's intentional: forgetting to wrap the
 * activity in AppTheme should fail loudly, not fall back to silent
 * defaults.
 */
val LocalAppTokens = staticCompositionLocalOf<AppTokens> {
    error("LocalAppTokens not provided. Wrap your composition in AppTheme { ... }.")
}

/** Sugar so `MaterialTheme.appTokens.colors.accent` reads naturally. */
val androidx.compose.material3.MaterialTheme.appTokens: AppTokens
    @Composable @ReadOnlyComposable
    get() = LocalAppTokens.current
