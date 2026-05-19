package com.tmuxes.ui.components.app

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.tmuxes.i18n.t
import com.tmuxes.ui.design.AppTokens
import com.tmuxes.ui.design.appTokens

/**
 * Universal FAB. Single component for every floating-action site in the
 * app. Always semi-transparent (containerAlpha capped at 0.95) — caller
 * cannot make an opaque FAB. Default size = Small.
 *
 * - `selected = true` → highlighted (primary fill instead of
 *   primaryContainer); used to indicate "this FAB's bubble is open"
 *   when paired with [AppFabBubbleController].
 * - `badge` → small dot/text overlay top-right (Material 3 BadgedBox).
 *   Pass `null` for no badge.
 * - `containerAlpha` → 0.5..0.95; clamped. Default 0.85.
 *
 * Pair with [AppFabBubbleController] / [AppFabBubble] for popover-on-tap
 * behavior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFab(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: AppFabSize = AppFabSize.Small,
    extendedLabel: String? = null,
    selected: Boolean = false,
    badge: AppFabBadge? = null,
    containerAlpha: Float? = null
) {
    val tokens = MaterialTheme.appTokens
    val effectiveAlpha = (containerAlpha ?: tokens.opacity.fab).coerceIn(0.5f, 0.95f) // allow-bypass-B3: FAB translucency contract (configurable via app.fab_opacity)
    val container = if (selected) {
        tokens.colors.primary.copy(alpha = effectiveAlpha) // allow-bypass-B3: FAB translucency contract
    } else {
        tokens.colors.primaryContainer.copy(alpha = effectiveAlpha) // allow-bypass-B3: FAB translucency contract
    }
    val onContainer = if (selected) tokens.colors.onPrimary else tokens.colors.onPrimaryContainer
    val localizedContentDescription = contentDescription?.let { t(it) }
    val localizedExtendedLabel = extendedLabel?.let { t(it) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressModifier = modifier.appPressFeedback(interactionSource)

    val fab: @Composable () -> Unit = {
        when {
            localizedExtendedLabel != null -> ExtendedFloatingActionButton(
                onClick = onClick,
                modifier = pressModifier,
                shape = tokens.shape.lg,
                containerColor = container,
                contentColor = onContainer,
                interactionSource = interactionSource,
                icon = { Icon(imageVector = icon, contentDescription = localizedContentDescription) },
                text = { Text(localizedExtendedLabel, style = tokens.type.labelLarge) }
            )
            size == AppFabSize.Small -> SmallFloatingActionButton(
                onClick = onClick,
                modifier = pressModifier,
                shape = tokens.shape.md,
                containerColor = container,
                contentColor = onContainer,
                interactionSource = interactionSource
            ) {
                Icon(imageVector = icon, contentDescription = localizedContentDescription)
            }
            else -> FloatingActionButton(
                onClick = onClick,
                modifier = pressModifier,
                shape = tokens.shape.lg,
                containerColor = container,
                contentColor = onContainer,
                interactionSource = interactionSource
            ) {
                Icon(imageVector = icon, contentDescription = localizedContentDescription)
            }
        }
    }

    if (badge == null) {
        fab()
    } else {
        BadgedBox(badge = { AppFabBadgeView(badge, tokens) }) {
            fab()
        }
    }
}

@Composable
private fun AppFabBadgeView(badge: AppFabBadge, tokens: AppTokens) {
    val color = when (badge.tone) {
        AppFabBadgeTone.Danger -> tokens.colors.danger
        AppFabBadgeTone.Warning -> tokens.colors.warning
        AppFabBadgeTone.Info -> tokens.colors.info
        AppFabBadgeTone.Success -> tokens.colors.success
    }
    if (badge.text.isNotEmpty()) {
        Badge(containerColor = color, contentColor = Color.White) {
            Text(badge.text, style = tokens.type.labelSmall)
        }
    } else {
        Badge(containerColor = color)
    }
}

enum class AppFabSize { Default, Small }
