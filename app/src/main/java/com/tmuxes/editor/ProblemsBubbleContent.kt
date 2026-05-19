package com.tmuxes.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmuxes.i18n.t
import com.tmuxes.ui.components.app.AppLazyColumn
import com.tmuxes.ui.components.app.appPressable

/**
 * Body of the Problems bubble — list of YAML diagnostics with
 * click-to-jump-to-line. When [diagnostics] is empty, shows a
 * "no problems detected" empty state.
 *
 * No header / no tab strip — this is content rendered inside an
 * [com.tmuxes.ui.components.app.AppFabBubble].
 */
@Composable
fun ProblemsBubbleContent(
    diagnostics: List<YamlDiagnosticAnalyzer.Diagnostic>,
    onDiagnosticClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (diagnostics.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxWidth()
                .wrapContentSize(Alignment.Center)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = t("No problems detected"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        val listState = rememberLazyListState()
        AppLazyColumn(
            state = listState,
            modifier = modifier.fillMaxWidth()
        ) {
            items(diagnostics) { diag ->
                DiagnosticRow(diag, onClick = { onDiagnosticClick(diag.line) })
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    diagnostic: YamlDiagnosticAnalyzer.Diagnostic,
    onClick: () -> Unit
) {
    val isError = diagnostic.severity == YamlDiagnosticAnalyzer.Severity.ERROR
    val iconTint = if (isError) MaterialTheme.colorScheme.error
                   else MaterialTheme.colorScheme.tertiary
    val icon = if (isError) Icons.Filled.Error else Icons.Filled.Warning
    val iconDescription = if (isError) "Error" else "Warning"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .appPressable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            icon,
            contentDescription = t(iconDescription),
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = t("Ln {line}", "line" to diagnostic.line + 1),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp)
        )
        Text(
            text = t(diagnostic.message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
