package com.tmuxes.ui.components.app

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.tmuxes.i18n.t
import com.tmuxes.ui.design.appTokens

/**
 * Universal icon button. Tap target = 48dp (M3 minimum). Tint comes
 * from token role; no raw colors.
 */
@Composable
fun AppIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
    role: AppIconRole = AppIconRole.OnSurface
) {
    val tokens = MaterialTheme.appTokens
    val localizedContentDescription = contentDescription?.let { t(it) }
    val interactionSource = remember { MutableInteractionSource() }
    val tint = when (role) {
        AppIconRole.OnSurface -> tokens.colors.onSurface
        AppIconRole.OnSurfaceVariant -> tokens.colors.onSurfaceVariant
        AppIconRole.Primary -> tokens.colors.primary
        AppIconRole.Accent -> tokens.colors.accent
        AppIconRole.Danger -> tokens.colors.danger
        AppIconRole.Warning -> tokens.colors.warning
        AppIconRole.Success -> tokens.colors.success
    }
    IconButton(
        onClick = onClick,
        modifier = modifier.appPressFeedback(interactionSource, enabled),
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint),
        interactionSource = interactionSource
    ) {
        Icon(imageVector = icon, contentDescription = localizedContentDescription)
    }
}

enum class AppIconRole { OnSurface, OnSurfaceVariant, Primary, Accent, Danger, Warning, Success }
