package com.tmuxes.ui.components.app

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tmuxes.i18n.t
import com.tmuxes.ui.design.appTokens

/** Filter chip with token colors. */
@Composable
fun AppFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    val tokens = MaterialTheme.appTokens
    val labelText = t(label)
    val interactionSource = remember { MutableInteractionSource() }
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.appPressFeedback(interactionSource, enabled),
        enabled = enabled,
        interactionSource = interactionSource,
        label = { Text(labelText, style = tokens.type.labelMedium) },
        leadingIcon = if (leadingIcon != null) ({
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
        }) else null,
        shape = tokens.shape.xs,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = tokens.colors.surfaceContainer,
            labelColor = tokens.colors.onSurface,
            selectedContainerColor = tokens.colors.primaryContainer,
            selectedLabelColor = tokens.colors.onPrimaryContainer,
            iconColor = tokens.colors.onSurfaceVariant,
            selectedLeadingIconColor = tokens.colors.onPrimaryContainer
        )
    )
}

@Composable
fun AppAssistChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    val tokens = MaterialTheme.appTokens
    val labelText = t(label)
    val interactionSource = remember { MutableInteractionSource() }
    AssistChip(
        onClick = onClick,
        modifier = modifier.appPressFeedback(interactionSource, enabled),
        enabled = enabled,
        interactionSource = interactionSource,
        label = { Text(labelText, style = tokens.type.labelMedium) },
        leadingIcon = if (leadingIcon != null) ({
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
        }) else null,
        shape = tokens.shape.xs,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = tokens.colors.surfaceContainer,
            labelColor = tokens.colors.onSurface,
            leadingIconContentColor = tokens.colors.onSurfaceVariant
        )
    )
}
