package com.tmuxes.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmuxes.i18n.t
import com.tmuxes.ui.components.app.AppLazyColumn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A single log entry shown in the Output bubble. */
data class OutputLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val message: String
)

private val timestampFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

/**
 * Body of the Output bubble — append-only log with auto-scroll-to-bottom.
 * No header / no tab strip.
 */
@Composable
fun OutputBubbleContent(
    outputLog: List<OutputLogEntry>,
    modifier: Modifier = Modifier
) {
    if (outputLog.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxWidth()
                .wrapContentSize(Alignment.Center)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                text = t("No output"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        val listState = rememberLazyListState()
        LaunchedEffect(outputLog.size) {
            if (outputLog.isNotEmpty()) {
                listState.animateScrollToItem(outputLog.lastIndex)
            }
        }
        AppLazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            items(outputLog) { entry ->
                val message = t(entry.message)
                Text(
                    text = "[${timestampFormat.format(Date(entry.timestamp))}] $message",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
