package com.tmuxes.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import com.tmuxes.ui.components.app.appElasticVerticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.tmuxes.BuildConfig
import com.tmuxes.ui.components.app.AppButton
import com.tmuxes.ui.components.app.AppButtonStyle
import com.tmuxes.ui.components.app.AppCard
import com.tmuxes.ui.components.app.AppCardVariant
import com.tmuxes.ui.components.app.AppDialog
import com.tmuxes.ui.components.app.AppScaffold
import com.tmuxes.ui.components.app.AppHorizontalDivider
import com.tmuxes.ui.components.app.AppListCard
import com.tmuxes.ui.components.app.AppSectionHeader
import com.tmuxes.ui.components.app.AppSpacerSize
import com.tmuxes.ui.components.app.AppVerticalSpacer
import com.tmuxes.ui.components.app.appPressable
import com.tmuxes.ui.design.appTokens
import com.tmuxes.i18n.t
import com.tmuxes.util.CrashLogWriter

private const val PROJECT_REPOSITORY_URL = "https://github.com/Lightcone-ZhangYifa/tmuxes"
private const val PRODUCT_NAME = "tmuxes"
private const val PROJECT_LICENSE_DESCRIPTION =
    "tmuxes is open source under the GNU General Public License v3.0 only (GPL-3.0-only)."

private data class AboutLicenseItem(
    val component: String,
    val licenseName: String
)

private val ABOUT_LICENSE_ITEMS = listOf(
    AboutLicenseItem("tmux", "ISC License"),
    AboutLicenseItem("AndroidX / Jetpack Compose", "Apache License 2.0"),
    AboutLicenseItem("Kotlin / kotlinx", "Apache License 2.0"),
    AboutLicenseItem("SSHJ", "Apache License 2.0"),
    AboutLicenseItem("Bouncy Castle", "Bouncy Castle Licence"),
    AboutLicenseItem("SnakeYAML", "Apache License 2.0"),
    AboutLicenseItem("Sora Editor", "GNU Lesser General Public License v2.1 only"),
    AboutLicenseItem("JetBrains Mono", "SIL Open Font License 1.1"),
    AboutLicenseItem("TextMate YAML / Catppuccin", "MIT License")
)

private fun releaseVersionLabel(versionName: String): String = "v$versionName"

@Composable
fun SettingsScreen(
    onNavigateToAppAppearance: () -> Unit = {},
    onNavigateToTerminalAppearance: () -> Unit = {},
    onNavigateToTerminalInput: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToSshConnection: () -> Unit = {},
    onNavigateToShellSession: () -> Unit = {},
    onNavigateToSecurityKeys: () -> Unit = {},
    onNavigateToSnippets: () -> Unit = {},
    onNavigateToYamlEditor: () -> Unit = {},
    onNavigateToDebugLog: () -> Unit = {}
) {
    val tokens = MaterialTheme.appTokens
    AppScaffold(
        title = "Settings",
        titleIcon = Icons.Filled.Settings
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .appElasticVerticalScroll(rememberScrollState())
                .padding(horizontal = tokens.space.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.space.xxs)
        ) {
            AppVerticalSpacer(AppSpacerSize.Sm)

            // =================================================================
            // MENU ITEMS
            // =================================================================

            AppListCard {
                SettingsMenuRow(
                    icon = Icons.Filled.Palette,
                    title = "App Appearance",
                    subtitle = "Language, theme, color, density",
                    onClick = onNavigateToAppAppearance
                )

                MenuDivider()

                SettingsMenuRow(
                    icon = Icons.Filled.Terminal,
                    title = "Terminal Appearance",
                    subtitle = "Typography, colors, cursor, rendering",
                    onClick = onNavigateToTerminalAppearance
                )

                MenuDivider()

                SettingsMenuRow(
                    icon = Icons.Filled.Keyboard,
                    title = "Terminal Input",
                    subtitle = "Extra keys, hardware keys, gestures, tmux",
                    onClick = onNavigateToTerminalInput
                )

                MenuDivider()

                SettingsMenuRow(
                    icon = Icons.Filled.Notifications,
                    title = "Notifications & Feedback",
                    subtitle = "Bell, vibration, screen wake",
                    onClick = onNavigateToNotifications
                )
            }

            AppVerticalSpacer(AppSpacerSize.Md)

            AppListCard {
                SettingsMenuRow(
                    icon = Icons.Filled.Wifi,
                    title = "SSH Defaults",
                    subtitle = "PTY, timeouts, keepalive, algorithms",
                    onClick = onNavigateToSshConnection
                )

                MenuDivider()

                SettingsMenuRow(
                    icon = Icons.Filled.Key,
                    title = "Security & Keys",
                    subtitle = "Known hosts, SSH keys, trust policy",
                    onClick = onNavigateToSecurityKeys
                )
            }

            AppVerticalSpacer(AppSpacerSize.Md)

            AppListCard {
                SettingsMenuRow(
                    icon = Icons.Filled.Code,
                    title = "Snippets",
                    subtitle = "Manage snippet libraries",
                    onClick = onNavigateToSnippets
                )

                MenuDivider()

                SettingsMenuRow(
                    icon = Icons.Filled.Edit,
                    title = "Edit Config (YAML)",
                    subtitle = "View and edit raw configuration",
                    onClick = onNavigateToYamlEditor
                )
            }

            // Debug logging shortcut — only present in debug builds. The
            // entire row plus its surrounding spacer compile out to nothing
            // when BuildConfig.DEBUG is false (constant-fold).
            if (com.tmuxes.BuildConfig.DEBUG) {
                AppVerticalSpacer(AppSpacerSize.Md)
                AppListCard {
                    SettingsMenuRow(
                        icon = Icons.Filled.BugReport,
                        title = "Debug Logging",
                        subtitle = "Per-category levels, log viewer, bundle export",
                        onClick = onNavigateToDebugLog
                    )
                }
            }

            AppVerticalSpacer(AppSpacerSize.Xl)

            // =================================================================
            // ABOUT (inline, no sub-page)
            // =================================================================

            AppSectionHeader("About")

            // State for the crash-log viewer dialog
            var showCrashLogDialog by remember { mutableStateOf(false) }
            var crashLogText by remember { mutableStateOf("") }
            val context = androidx.compose.ui.platform.LocalContext.current

            if (showCrashLogDialog) {
                val shareCrashLogTitle = t("Share crash log")
                val display = if (crashLogText.isBlank()) {
                    t("No crashes recorded. If the app has crashed for you, open this dialog again after the next crash to see the stack trace, then tap Copy to share it with the developer.")
                } else {
                    crashLogText
                }
                AppDialog(
                    title = "Crash Log",
                    onDismiss = { showCrashLogDialog = false },
                    confirmLabel = "Close",
                    onConfirm = { showCrashLogDialog = false },
                    confirmStyle = AppButtonStyle.Text,
                    dismissLabel = null,
                    contentScrollable = false,
                    customActions = {
                        if (crashLogText.isNotBlank()) {
                            AppButton(
                                text = "Clear",
                                style = AppButtonStyle.Danger,
                                onClick = {
                                    try { CrashLogWriter.clearCrashLog() } catch (_: Throwable) {} // allow-bypass-D5: clear is idempotent best-effort
                                    crashLogText = ""
                                    showCrashLogDialog = false
                                }
                            )
                            AppButton(
                                text = "Copy",
                                style = AppButtonStyle.Outlined,
                                onClick = {
                                    try {
                                        val clipboard = context.getSystemService(
                                            android.content.Context.CLIPBOARD_SERVICE
                                        ) as? android.content.ClipboardManager
                                        clipboard?.setPrimaryClip(
                                            android.content.ClipData.newPlainText(
                                                "tmuxes crash log", crashLogText
                                            )
                                        )
                                    } catch (_: Throwable) {} // allow-bypass-D5: clipboard set best-effort
                                }
                            )
                            AppButton(
                                text = "Share",
                                style = AppButtonStyle.Primary,
                                onClick = {
                                    try {
                                        val shareIntent = android.content.Intent(
                                            android.content.Intent.ACTION_SEND
                                        ).apply {
                                            type = "text/plain"
                                            putExtra(
                                                android.content.Intent.EXTRA_SUBJECT,
                                                "tmuxes crash log"
                                            )
                                            putExtra(
                                                android.content.Intent.EXTRA_TEXT,
                                                crashLogText
                                            )
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(
                                            android.content.Intent.createChooser(
                                                shareIntent, shareCrashLogTitle
                                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } catch (_: Throwable) {}
                                }
                            )
                        }
                        AppButton(
                            text = "Close",
                            style = AppButtonStyle.Text,
                            onClick = { showCrashLogDialog = false }
                        )
                    },
                    content = {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 400.dpUnit())
                                .appElasticVerticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = display,
                                style = tokens.type.monoSmall,
                                color = tokens.colors.onSurface
                            )
                        }
                    }
                )
            }

            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            AboutProductPanel(
                versionName = BuildConfig.VERSION_NAME,
                onOpenCrashLog = {
                    crashLogText = try {
                        CrashLogWriter.readCrashLog()
                    } catch (_: Throwable) { "" }
                    showCrashLogDialog = true
                },
                onOpenRepository = { uriHandler.openUri(PROJECT_REPOSITORY_URL) }
            )

            AppVerticalSpacer(AppSpacerSize.Lg)
        }
    }
}

// ---------------------------------------------------------------------------
// About panel
// ---------------------------------------------------------------------------

@Composable
private fun AboutProductPanel(
    versionName: String,
    onOpenCrashLog: () -> Unit,
    onOpenRepository: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val versionLabel = releaseVersionLabel(versionName)
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        variant = AppCardVariant.Elevated
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.space.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = tokens.shape.sm,
                    color = tokens.colors.primaryContainer
                ) {
                    Box(
                        modifier = Modifier.size(52.dpUnit()),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Terminal,
                            contentDescription = null,
                            tint = tokens.colors.onPrimaryContainer,
                            modifier = Modifier.size(28.dpUnit())
                        )
                    }
                }
                Spacer(Modifier.width(tokens.space.lg))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = PRODUCT_NAME,
                        style = tokens.type.titleLarge,
                        color = tokens.colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = t("SSH and tmux client for Android"),
                        style = tokens.type.bodySmall,
                        color = tokens.colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = tokens.shape.xs,
                    color = tokens.colors.surfaceContainerHighest
                ) {
                    Text(
                        text = versionLabel,
                        style = tokens.type.monoSmall,
                        color = tokens.colors.onSurface,
                        modifier = Modifier.padding(horizontal = tokens.space.sm, vertical = tokens.space.xs),
                        maxLines = 1
                    )
                }
            }

            Text(
                text = PROJECT_LICENSE_DESCRIPTION,
                style = tokens.type.bodyMedium,
                color = tokens.colors.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(tokens.space.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.space.sm)
                ) {
                    AboutCapabilityTag(
                        icon = Icons.Filled.Wifi,
                        label = "SSHJ",
                        modifier = Modifier.weight(1f)
                    )
                    AboutCapabilityTag(
                        icon = Icons.Filled.Terminal,
                        label = "tmux",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.space.sm)
                ) {
                    AboutCapabilityTag(
                        icon = Icons.Filled.Key,
                        label = "Bouncy Castle",
                        modifier = Modifier.weight(1f)
                    )
                    AboutCapabilityTag(
                        icon = Icons.Filled.Edit,
                        label = "Sora Editor",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            AppHorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(tokens.space.sm)) {
                ABOUT_LICENSE_ITEMS.forEach { item ->
                    AboutSignalRow(
                        label = item.component,
                        value = item.licenseName,
                        translateLabel = false,
                        translateValue = false,
                        valueMaxLines = 2
                    )
                }
            }

            AppHorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.space.sm)
            ) {
                AppButton(
                    text = "GitHub",
                    onClick = onOpenRepository,
                    modifier = Modifier.weight(1f),
                    style = AppButtonStyle.Tonal,
                    leadingIcon = Icons.Filled.Code,
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew
                )
                AppButton(
                    text = "Crash Log",
                    onClick = onOpenCrashLog,
                    modifier = Modifier.weight(1f),
                    style = AppButtonStyle.Outlined,
                    leadingIcon = Icons.Filled.BugReport
                )
            }
        }
    }
}

@Composable
private fun AboutCapabilityTag(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val tokens = MaterialTheme.appTokens
    Surface(
        modifier = modifier,
        shape = tokens.shape.xs,
        color = tokens.colors.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.space.sm, vertical = tokens.space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tokens.colors.primary,
                modifier = Modifier.size(18.dpUnit())
            )
            Spacer(Modifier.width(tokens.space.sm))
            Text(
                text = t(label),
                style = tokens.type.labelMedium,
                color = tokens.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AboutSignalRow(
    label: String,
    value: String,
    translateLabel: Boolean = true,
    translateValue: Boolean = true,
    valueMaxLines: Int = 1
) {
    val tokens = MaterialTheme.appTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (translateLabel) t(label) else label,
            style = tokens.type.monoSmall,
            color = tokens.colors.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (translateValue) t(value) else value,
            style = tokens.type.bodySmall,
            color = tokens.colors.onSurface,
            modifier = Modifier.weight(0.58f),
            maxLines = valueMaxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// Menu row composable
// ---------------------------------------------------------------------------

@Composable
private fun SettingsMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val titleText = t(title)
    val subtitleText = t(subtitle)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appPressable(onClick = onClick)
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tokens.colors.onSurfaceVariant,
            modifier = Modifier.size(24.dpUnit())
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = tokens.space.lg)
        ) {
            Text(
                text = titleText,
                style = tokens.type.bodyLarge,
                color = tokens.colors.onSurface
            )
            Text(
                text = subtitleText,
                style = tokens.type.bodySmall,
                color = tokens.colors.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = tokens.colors.onSurfaceVariant,
            modifier = Modifier.size(20.dpUnit())
        )
    }
}

@Composable
private fun MenuDivider() {
    AppHorizontalDivider(inset = true)
}

private fun Int.dpUnit() = androidx.compose.ui.unit.Dp(this.toFloat())
