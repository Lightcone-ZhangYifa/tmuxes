package com.tmuxes.ui.components.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmuxes.i18n.t
import com.tmuxes.ui.design.appTokens

/**
 * Centered empty-state placeholder: icon, title, optional subtitle,
 * optional primary action. Replaces the four screen-specific empty
 * states (`EmptyServerState`, `NoServersEmptyState`, the inline
 * if-empty in `KeyManagerScreen`, the inline message in `LibraryDetail`).
 */
@Composable
fun AppEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val tokens = MaterialTheme.appTokens
    val titleText = t(title)
    val subtitleText = subtitle?.let { t(it) }
    Box(
        modifier = modifier.fillMaxSize().padding(tokens.space.xl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tokens.space.md)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tokens.colors.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = titleText,
                style = tokens.type.titleMedium,
                color = tokens.colors.onSurface,
                textAlign = TextAlign.Center
            )
            if (subtitleText != null) {
                Text(
                    text = subtitleText,
                    style = tokens.type.bodyMedium,
                    color = tokens.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(tokens.space.sm))
                AppButton(
                    text = actionLabel,
                    onClick = onAction,
                    style = AppButtonStyle.Primary
                )
            }
        }
    }
}

/** Centered error placeholder — same layout as empty-state but with danger tint and retry action. */
@Composable
fun AppErrorState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onRetry: (() -> Unit)? = null,
    icon: ImageVector = Icons.Filled.ErrorOutline
) {
    val tokens = MaterialTheme.appTokens
    val titleText = t(title)
    val subtitleText = subtitle?.let { t(it) }
    Box(
        modifier = modifier.fillMaxSize().padding(tokens.space.xl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tokens.space.md)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tokens.colors.danger,
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = titleText,
                style = tokens.type.titleMedium,
                color = tokens.colors.onSurface,
                textAlign = TextAlign.Center
            )
            if (subtitleText != null) {
                Text(
                    text = subtitleText,
                    style = tokens.type.bodyMedium,
                    color = tokens.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (onRetry != null) {
                Spacer(Modifier.height(tokens.space.sm))
                AppButton(text = "Retry", onClick = onRetry, style = AppButtonStyle.Secondary)
            }
        }
    }
}

/** Centered loading spinner. */
@Composable
fun AppLoadingState(modifier: Modifier = Modifier, message: String? = null) {
    val tokens = MaterialTheme.appTokens
    Box(
        modifier = modifier.fillMaxSize().padding(tokens.space.xl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tokens.space.lg)
        ) {
            CircularProgressIndicator(color = tokens.colors.primary)
            if (message != null) {
                Text(
                    text = message,
                    style = tokens.type.bodyMedium,
                    color = tokens.colors.onSurfaceVariant
                )
            }
        }
    }
}
