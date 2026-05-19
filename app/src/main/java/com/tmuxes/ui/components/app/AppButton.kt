package com.tmuxes.ui.components.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.tmuxes.i18n.t
import com.tmuxes.ui.design.appTokens

/**
 * Universal button. The [style] enum picks visual variant; size and
 * color come from tokens. No raw color or padding parameters.
 *
 * Variants:
 * - **Primary**: filled with `primary`/`onPrimary` (the default
 *   "main action" button)
 * - **Secondary**: tonal-filled secondary container
 * - **Tonal**: tonal-filled tertiary (use sparingly, for subdued
 *   secondary actions on a primary-heavy surface)
 * - **Text**: text-only button
 * - **Outlined**: outlined button (use when text alone reads ambiguous)
 * - **Danger**: filled with `danger`/`onDanger` for destructive actions
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppButtonStyle = AppButtonStyle.Primary,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null
) {
    val tokens = MaterialTheme.appTokens
    val label = t(text)
    val interactionSource = remember { MutableInteractionSource() }
    val pressModifier = modifier.appPressFeedback(interactionSource, enabled)
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                androidx.compose.material3.Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(tokens.space.sm))
            }
            Text(text = label, style = tokens.type.labelLarge)
            if (trailingIcon != null) {
                Spacer(Modifier.width(tokens.space.sm))
                androidx.compose.material3.Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    val pad = PaddingValues(horizontal = tokens.space.lg, vertical = tokens.space.sm)
    when (style) {
        AppButtonStyle.Primary -> Button(
            onClick = onClick, modifier = pressModifier, enabled = enabled,
            shape = tokens.shape.sm,
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.colors.primary,
                contentColor = tokens.colors.onPrimary
            ),
            contentPadding = pad,
            interactionSource = interactionSource
        ) { Box { content() } }
        AppButtonStyle.Secondary -> FilledTonalButton(
            onClick = onClick, modifier = pressModifier, enabled = enabled,
            shape = tokens.shape.sm,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = tokens.colors.secondaryContainer,
                contentColor = tokens.colors.onSecondaryContainer
            ),
            contentPadding = pad,
            interactionSource = interactionSource
        ) { Box { content() } }
        AppButtonStyle.Tonal -> FilledTonalButton(
            onClick = onClick, modifier = pressModifier, enabled = enabled,
            shape = tokens.shape.sm,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = tokens.colors.tertiaryContainer,
                contentColor = tokens.colors.onTertiaryContainer
            ),
            contentPadding = pad,
            interactionSource = interactionSource
        ) { Box { content() } }
        AppButtonStyle.Text -> TextButton(
            onClick = onClick, modifier = pressModifier, enabled = enabled,
            shape = tokens.shape.sm,
            colors = ButtonDefaults.textButtonColors(contentColor = tokens.colors.primary),
            contentPadding = pad,
            interactionSource = interactionSource
        ) { Box { content() } }
        AppButtonStyle.Outlined -> OutlinedButton(
            onClick = onClick, modifier = pressModifier, enabled = enabled,
            shape = tokens.shape.sm,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.colors.primary),
            contentPadding = pad,
            interactionSource = interactionSource
        ) { Box { content() } }
        AppButtonStyle.Danger -> Button(
            onClick = onClick, modifier = pressModifier, enabled = enabled,
            shape = tokens.shape.sm,
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.colors.danger,
                contentColor = tokens.colors.onDanger
            ),
            contentPadding = pad,
            interactionSource = interactionSource
        ) { Box { content() } }
    }
}

enum class AppButtonStyle { Primary, Secondary, Tonal, Text, Outlined, Danger }
