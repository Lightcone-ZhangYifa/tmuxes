package com.tmuxes.ui.components.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tmuxes.i18n.t
import com.tmuxes.ui.design.appTokens

/**
 * Universal list item — leading icon + title + subtitle + trailing
 * slot. Padding / spacing / type all from tokens.
 *
 * Use inside [AppCard] / [AppSection] for the expected look. For a
 * "row of items separated by dividers", chain multiple inside a single
 * AppCard with [AppHorizontalDivider] between them.
 */
@Composable
fun AppListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    iconRole: AppIconRole = AppIconRole.OnSurfaceVariant,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val tokens = MaterialTheme.appTokens
    val titleText = t(title)
    val subtitleText = subtitle?.let { t(it) }
    val rowMod = modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.appPressable(onClick = onClick) else it }
        .padding(horizontal = tokens.space.lg, vertical = tokens.space.md)
    Row(
        modifier = rowMod,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            val tint = when (iconRole) {
                AppIconRole.OnSurface -> tokens.colors.onSurface
                AppIconRole.OnSurfaceVariant -> tokens.colors.onSurfaceVariant
                AppIconRole.Primary -> tokens.colors.primary
                AppIconRole.Accent -> tokens.colors.accent
                AppIconRole.Danger -> tokens.colors.danger
                AppIconRole.Warning -> tokens.colors.warning
                AppIconRole.Success -> tokens.colors.success
            }
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(tokens.space.lg))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(tokens.space.xxs)
        ) {
            Text(
                text = titleText,
                style = tokens.type.bodyLarge,
                color = tokens.colors.onSurface
            )
            if (subtitleText != null) {
                Text(
                    text = subtitleText,
                    style = tokens.type.bodyMedium,
                    color = tokens.colors.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(tokens.space.sm))
            Box { trailing() }
        }
    }
}
