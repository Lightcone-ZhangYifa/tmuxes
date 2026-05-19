package com.tmuxes.ui.screens.hosts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.tmuxes.ui.components.app.AppLazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tmuxes.data.model.KnownHostEntity
import com.tmuxes.i18n.I18nRuntime
import com.tmuxes.i18n.t
import com.tmuxes.ui.components.app.AppRowAction
import com.tmuxes.ui.components.app.AppRowSwipe
import com.tmuxes.ui.components.app.AppCard
import com.tmuxes.ui.components.app.AppDeleteDialog
import com.tmuxes.ui.components.app.AppEmptyState
import com.tmuxes.ui.components.app.AppScaffold
import com.tmuxes.ui.components.app.AppTextField
import com.tmuxes.ui.components.app.rememberAppEntryLazyListState
import com.tmuxes.ui.design.appTokens
import com.tmuxes.ui.viewmodel.KnownHostsViewModel
import com.tmuxes.util.safeLaunch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownHostsScreen(
    onNavigateBack: () -> Unit,
    viewModel: KnownHostsViewModel = viewModel()
) {
    val tokens = MaterialTheme.appTokens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    val allHosts by viewModel.knownHosts.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberAppEntryLazyListState("known_hosts")

    val filteredHosts = if (searchQuery.isBlank()) {
        allHosts
    } else {
        val query = searchQuery.lowercase()
        allHosts.filter { host ->
            host.hostname.lowercase().contains(query) ||
                    host.keyType.lowercase().contains(query) ||
                    host.fingerprint.lowercase().contains(query)
        }
    }

    AppScaffold(
        title = "Known Hosts",
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState
    ) { padding ->
        if (allHosts.isEmpty()) {
            AppEmptyState(
                title = "No trusted hosts",
                subtitle = "Hosts are added when you first connect to a server",
                icon = Icons.Filled.Security,
                modifier = Modifier.padding(padding)
            )
        } else {
            AppLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = tokens.space.lg),
                verticalArrangement = Arrangement.spacedBy(tokens.space.sm)
            ) {
                // Search bar: only show when 5+ entries
                if (allHosts.size >= 5) {
                    item {
                        Spacer(modifier = Modifier.height(tokens.space.sm))
                        AppTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Search hosts...",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = t("Search"),
                                    tint = tokens.colors.onSurfaceVariant
                                )
                            }
                        )
                    }
                } else {
                    item { Spacer(modifier = Modifier.height(tokens.space.sm)) }
                }

                items(filteredHosts, key = { it.id }) { host ->
                    KnownHostListItem(
                        host = host,
                        onCopyFingerprint = {
                            try {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Fingerprint", host.fingerprint))
                            } catch (_: Throwable) {} // allow-bypass-D5: clipboard set best-effort; snackbar follows regardless
                            scope.safeLaunch(tag = "KnownHosts") {
                                snackbarHostState.showSnackbar(I18nRuntime.t("Fingerprint copied"))
                            }
                        },
                        onDelete = {
                            viewModel.deleteHost(host)
                            scope.safeLaunch(tag = "KnownHosts") {
                                snackbarHostState.showSnackbar(I18nRuntime.t("Host removed"))
                            }
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(tokens.space.lg)) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Known host list item with reveal-swipe actions
// ---------------------------------------------------------------------------

@Composable
private fun KnownHostListItem(
    host: KnownHostEntity,
    onCopyFingerprint: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFullFingerprint by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AppDeleteDialog(
            title = "Remove Host",
            message = "Remove \"${host.hostname}:${host.port}\" from trusted hosts? You will be prompted to verify it again on next connection.",
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    AppRowSwipe(
        actions = listOf(
            AppRowAction(
                icon = Icons.Filled.ContentCopy,
                color = tokens.colors.primary,
                onClick = onCopyFingerprint,
                label = "Copy"
            ),
            AppRowAction(
                icon = Icons.Filled.Delete,
                color = tokens.colors.danger,
                onClick = { showDeleteConfirm = true },
                label = "Delete"
            )
        )
    ) {
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showFullFingerprint = !showFullFingerprint },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(tokens.space.lg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Filled.Key,
                    contentDescription = null,
                    tint = tokens.colors.primary,
                    modifier = Modifier
                        .size(32.dpUnit())
                        .padding(top = tokens.space.xxs)
                )
                Spacer(modifier = Modifier.width(tokens.space.lg))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(tokens.space.sm)
                    ) {
                        Text(
                            text = "${host.hostname}:${host.port}",
                            style = tokens.type.titleSmall,
                            color = tokens.colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        // Key type badge
                        Surface(
                            shape = tokens.shape.xs,
                            color = tokens.colors.primaryContainer
                        ) {
                            Text(
                                text = host.keyType,
                                style = tokens.type.labelSmall,
                                color = tokens.colors.onPrimaryContainer,
                                modifier = Modifier.padding(
                                    horizontal = tokens.space.xs + tokens.space.xxs,
                                    vertical = tokens.space.xxs
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(tokens.space.xs))

                    // Fingerprint (truncated unless tapped)
                    Text(
                        text = host.fingerprint,
                        style = tokens.type.monoSmall,
                        color = tokens.colors.onSurfaceVariant,
                        maxLines = if (showFullFingerprint) Int.MAX_VALUE else 1,
                        overflow = if (showFullFingerprint) TextOverflow.Clip else TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(tokens.space.xxs))

                    // Relative timestamp
                    Text(
                        text = formatRelativeTime(host.addedAt),
                        style = tokens.type.labelSmall,
                        color = tokens.colors.outline
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Relative time formatting
// ---------------------------------------------------------------------------

private fun formatRelativeTime(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val diffMillis = now - epochMillis

    if (diffMillis < 0) return "added just now"

    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
    val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
    val days = TimeUnit.MILLISECONDS.toDays(diffMillis)

    return when {
        minutes < 1 -> "added just now"
        minutes < 60 -> "added ${minutes}m ago"
        hours < 24 -> "added ${hours}h ago"
        days < 30 -> "added ${days} day${if (days != 1L) "s" else ""} ago"
        else -> {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(Date(epochMillis))
            "added $date"
        }
    }
}

private fun Int.dpUnit() = androidx.compose.ui.unit.Dp(this.toFloat())
