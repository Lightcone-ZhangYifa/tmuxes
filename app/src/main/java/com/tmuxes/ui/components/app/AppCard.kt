@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.tmuxes.ui.components.app

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmuxes.ui.design.appTokens

/**
 * Universal card. Surface-tinted, reads shape + spacing from tokens.
 * No `elevation` / `shape` / `padding` parameters — variants go through
 * the [variant] enum so callers can't make new visual languages.
 *
 * Click model: pass [onClick] and/or [onLongClick] AS PARAMETERS, NOT via
 * `modifier = Modifier.clickable {...}`. The Surface is non-clickable; the
 * click handlers are attached via `Modifier.combinedClickable` on the OUTER
 * caller-supplied modifier chain. This is the only model that lets
 * long-press through to the inner content — Material3 `Surface(onClick=)`
 * intercepts pointer-down events and any outer `modifier.clickable` is
 * shadowed.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    variant: AppCardVariant = AppCardVariant.Filled,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(MaterialTheme.appTokens.space.lg),
    content: @Composable () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val color = when (variant) {
        AppCardVariant.Filled -> tokens.colors.surfaceContainer
        AppCardVariant.Outlined -> tokens.colors.surface
        AppCardVariant.Elevated -> tokens.colors.surfaceContainerHigh
    }
    val borderColor = if (variant == AppCardVariant.Outlined)
        tokens.colors.outlineVariant else null
    val tonalElev = when (variant) {
        AppCardVariant.Elevated -> tokens.elevation.level1
        else -> tokens.elevation.level0
    }
    val shadowElev = when (variant) {
        AppCardVariant.Elevated -> tokens.elevation.level2
        else -> tokens.elevation.level0
    }
    val interactionSource = remember { MutableInteractionSource() }
    val clickModifier = if (onClick == null && onLongClick == null) {
        Modifier
    } else {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = androidx.compose.foundation.LocalIndication.current,
            onClick = onClick ?: {},
            onLongClick = onLongClick
        )
    }
    Surface(
        modifier = modifier
            .appPressFeedback(
                interactionSource = interactionSource,
                enabled = onClick != null || onLongClick != null
            )
            .then(clickModifier),
        shape = tokens.shape.lg,
        color = color,
        contentColor = tokens.colors.onSurface,
        tonalElevation = tonalElev,
        shadowElevation = shadowElev,
        border = borderColor?.let { androidx.compose.foundation.BorderStroke(1.dp, it) }
    ) {
        Box(Modifier.padding(contentPadding)) {
            content()
        }
    }
}

enum class AppCardVariant { Filled, Outlined, Elevated }

/**
 * Filled card hosting a vertically-stacked list of items (Settings rows,
 * grouped action lists, etc.). Default tight content padding (`space.xs`)
 * matches the visual density of the previous `SettingsCard` helper, which
 * this component replaces. Always fillMaxWidth.
 *
 * Children render inside a `Column` automatically — callers don't need to
 * wrap their items.
 */
@Composable
fun AppListCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(MaterialTheme.appTokens.space.xs),
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        variant = AppCardVariant.Filled,
        contentPadding = contentPadding
    ) {
        Column(content = content)
    }
}
