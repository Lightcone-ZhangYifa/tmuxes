package com.tmuxes.ui.components.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tmuxes.i18n.t
import com.tmuxes.ssh.ServerStatus
import com.tmuxes.ui.design.StatusTokens
import com.tmuxes.ui.design.appTokens

/**
 * A 8dp status dot followed by an optional label, color picked from
 * [StatusTokens.forServerStatus] or [StatusTokens.forSeverity].
 *
 * Replaces the scattered `Box(Modifier.size(8.dp).background(<color>, CircleShape))`
 * pattern duplicated across ServerList / ServerDetail / SessionPicker.
 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    sizeDp: Int = 8
) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .background(color, CircleShape)
    )
}

@Composable
fun StatusIndicator(
    status: ServerStatus,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val tokens = MaterialTheme.appTokens
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(color = tokens.status.forServerStatus(status))
        if (label != null) {
            Spacer(Modifier.width(tokens.space.sm))
            Text(text = t(label), style = tokens.type.labelMedium, color = tokens.colors.onSurfaceVariant)
        }
    }
}

@Composable
fun SeverityBadge(
    severity: StatusTokens.Severity,
    label: String,
    modifier: Modifier = Modifier
) {
    val tokens = MaterialTheme.appTokens
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(color = tokens.status.forSeverity(severity))
        Spacer(Modifier.width(tokens.space.sm))
    Text(text = t(label), style = tokens.type.labelMedium, color = tokens.colors.onSurfaceVariant)
    }
}
