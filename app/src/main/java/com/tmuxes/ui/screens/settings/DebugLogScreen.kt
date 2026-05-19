package com.tmuxes.ui.screens.settings

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.tmuxes.ui.components.app.AppLazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import com.tmuxes.i18n.t
import com.tmuxes.ui.components.app.AppButton
import com.tmuxes.ui.components.app.AppButtonStyle
import com.tmuxes.ui.components.app.AppCard
import com.tmuxes.ui.components.app.AppIconButton
import com.tmuxes.ui.components.app.AppListItem
import com.tmuxes.ui.components.app.AppScaffold
import com.tmuxes.ui.components.app.AppSectionHeader
import com.tmuxes.ui.components.app.AppSpacerSize
import com.tmuxes.ui.components.app.AppVerticalSpacer
import com.tmuxes.ui.components.app.rememberAppEntryLazyListState
import com.tmuxes.ui.design.appTokens
import com.tmuxes.util.AppLogger
import com.tmuxes.util.DebugBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Debug-build-only screen exposing every tool the developer needs to
 * triage a bug from inside the app:
 *
 * 1. **Per-category log level** — change a category to TRACE without
 *    rebuilding or `adb shell`-ing
 * 2. **Recent breadcrumbs viewer** — last 256 log lines from the in-memory
 *    ring, with Copy + Share buttons
 * 3. **Collect Debug Bundle** — zip every diagnostic artefact (manifest,
 *    breadcrumbs, crash log, sanitised configs) and share it
 *
 * Levels set here are NOT persisted — they reset to defaults on the next
 * cold start. Use the ADB broadcast (see [com.tmuxes.util.DebugLogReceiver])
 * for repeatable retuning.
 */
@Composable
fun DebugLogScreen(onNavigateBack: () -> Unit) {
    val tokens = MaterialTheme.appTokens
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // Snapshot of breadcrumbs. Refresh button forces re-pull from AppLogger.
    var snapshotTick by remember { mutableStateOf(0) }
    val breadcrumbs = remember(snapshotTick) { AppLogger.snapshotBreadcrumbs() }

    // Track per-category level state so dropdown re-reads after change.
    var levelTick by remember { mutableStateOf(0) }
    val listState = rememberAppEntryLazyListState("debug_log")

    AppScaffold(
        title = "Debug Logging",
        onBack = onNavigateBack,
        actions = {
            AppIconButton(
                icon = Icons.Filled.Refresh,
                contentDescription = "Refresh breadcrumbs",
                onClick = { snapshotTick++ }
            )
        }
    ) { padding ->
        AppLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding)
                .padding(horizontal = tokens.space.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.space.sm)
        ) {
            // -------- Bundle export + breadcrumb actions ----------
            item {
                AppSectionHeader(text = "Diagnostics")
            }
            item {
                AppCard {
                    Column(modifier = Modifier.padding(tokens.space.md)) {
                        Text(
                            text = "${breadcrumbs.size}/256 breadcrumbs in ring",
                            style = tokens.type.bodyMedium,
                            color = tokens.colors.onSurfaceVariant
                        )
                        AppVerticalSpacer(AppSpacerSize.Sm)
                        Row(horizontalArrangement = Arrangement.spacedBy(tokens.space.sm)) {
                            AppButton(
                                text = "Bundle",
                                style = AppButtonStyle.Primary,
                                leadingIcon = Icons.Filled.Archive,
                                onClick = {
                                    scope.launch {
                                        try {
                                            val intent = withContext(Dispatchers.IO) {
                                                DebugBundle.export(context)
                                            }
                                            context.startActivity(
                                                android.content.Intent.createChooser(intent, "Share debug bundle")
                                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        } catch (t: Throwable) {
                                            AppLogger.w(AppLogger.Category.LIFECYCLE) {
                                                "debug.bundle share ✗ cause='${t.message}'"
                                            }
                                        }
                                    }
                                }
                            )
                            AppButton(
                                text = "Share log",
                                style = AppButtonStyle.Secondary,
                                leadingIcon = Icons.Filled.Share,
                                onClick = {
                                    val text = breadcrumbs.joinToString("\n")
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "tmuxes log")
                                        putExtra(android.content.Intent.EXTRA_TEXT, text)
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try {
                                        context.startActivity(
                                            android.content.Intent.createChooser(send, "Share log")
                                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } catch (_: Throwable) {} // allow-bypass-D5: share-intent dispatch best-effort; user can retry from same button
                                }
                            )
                            AppButton(
                                text = "Clear",
                                style = AppButtonStyle.Text,
                                leadingIcon = Icons.Filled.DeleteSweep,
                                onClick = {
                                    AppLogger.clearBreadcrumbs()
                                    snapshotTick++
                                }
                            )
                        }
                    }
                }
            }

            // -------- Per-category level controls ----------
            item { AppSectionHeader(text = "Per-Category Levels") }
            item {
                AppCard {
                    Column {
                        Text(
                            text = t("Levels reset on cold start. Use the ADB broadcast for repeatable retuning."),
                            style = tokens.type.bodySmall,
                            color = tokens.colors.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = tokens.space.lg,
                                vertical = tokens.space.sm
                            )
                        )
                        for (cat in AppLogger.Category.values()) {
                            CategoryLevelRow(cat, levelTick) { newLevel ->
                                AppLogger.setLevel(cat, newLevel)
                                levelTick++
                            }
                        }
                    }
                }
            }

            // -------- Breadcrumb viewer ----------
            item { AppSectionHeader(text = "Recent Breadcrumbs") }
            item {
                AppCard {
                    Column(modifier = Modifier.padding(tokens.space.sm)) {
                        if (breadcrumbs.isEmpty()) {
                            Text(
                                t("No breadcrumbs yet - interact with the app and tap Refresh."),
                                style = tokens.type.bodyMedium,
                                color = tokens.colors.onSurfaceVariant,
                                modifier = Modifier.padding(tokens.space.lg)
                            )
                        } else {
                            for (line in breadcrumbs.takeLast(64)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = tokens.space.sm, vertical = tokens.space.xxs),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = line,
                                        style = tokens.type.monoSmall,
                                        color = tokens.colors.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    AppIconButton(
                                        icon = Icons.Filled.ContentCopy,
                                        contentDescription = "Copy line",
                                        onClick = {
                                            scope.launch {
                                                clipboard.setClipEntry(
                                                    ClipEntry(ClipData.newPlainText("tmuxes log line", line))
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { AppVerticalSpacer(AppSpacerSize.Lg) }
        }
    }
}

@Composable
private fun CategoryLevelRow(
    category: AppLogger.Category,
    levelTick: Int,
    onLevelChange: (AppLogger.Level) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    var expanded by remember { mutableStateOf(false) }
    val current = remember(levelTick) { AppLogger.levelOf(category) }
    Box {
        AppListItem(
            title = category.name,
            subtitle = "tag=${category.tag}  level=$current",
            onClick = { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (level in AppLogger.Level.values()) {
                DropdownMenuItem(
                    text = { Text(level.name, style = tokens.type.bodyMedium) },
                    onClick = { onLevelChange(level); expanded = false }
                )
            }
        }
    }
}
