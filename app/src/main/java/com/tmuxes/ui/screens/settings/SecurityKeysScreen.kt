package com.tmuxes.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import com.tmuxes.ui.components.app.appElasticVerticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tmuxes.data.settings.Settings
import com.tmuxes.i18n.t
import com.tmuxes.ui.components.app.AppAlgorithmSelector
import com.tmuxes.ui.components.app.SshAlgorithms
import com.tmuxes.ui.components.app.AppScaffold
import com.tmuxes.ui.components.app.AppHorizontalDivider
import com.tmuxes.ui.components.app.AppListCard
import com.tmuxes.ui.components.app.AppSectionHeader
import com.tmuxes.ui.components.app.AppVerticalSpacer
import com.tmuxes.ui.components.app.AppSpacerSize
import com.tmuxes.ui.components.app.rememberAppEntryScrollState
import com.tmuxes.ui.design.appTokens
import com.tmuxes.ui.viewmodel.SettingsViewModel

@Composable
fun SecurityKeysScreen(
    onNavigateBack: () -> Unit,
    onNavigateToKeyManager: () -> Unit,
    onNavigateToKnownHosts: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val tokens = MaterialTheme.appTokens
    val scrollState = rememberAppEntryScrollState("security_keys")
    val prefs = viewModel.preferences
    val sshStrictHostKey by prefs.flow(Settings.sshStrictHostKey)
        .collectAsState(initial = Settings.sshStrictHostKey.default)
    val sshPreferredCiphers by prefs.flow(Settings.sshPreferredCiphers)
        .collectAsState(initial = Settings.sshPreferredCiphers.default)
    val sshPreferredKex by prefs.flow(Settings.sshPreferredKex)
        .collectAsState(initial = Settings.sshPreferredKex.default)
    val sshPreferredMacs by prefs.flow(Settings.sshPreferredMacs)
        .collectAsState(initial = Settings.sshPreferredMacs.default)
    val sshPreferredHostKeyAlgs by prefs.flow(Settings.sshPreferredHostKeyAlgs)
        .collectAsState(initial = Settings.sshPreferredHostKeyAlgs.default)

    AppScaffold(
        title = "Security & Keys",
        onBack = onNavigateBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .appElasticVerticalScroll(scrollState)
                .padding(padding)
                .padding(horizontal = tokens.space.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.space.sm)
        ) {
            // -----------------------------------------------------------------
            // HOST KEY POLICY
            // -----------------------------------------------------------------
            AppSectionHeader(text = "Host Key Policy")
            AppListCard {
                SettingsDropdownItem(
                    icon = Icons.Filled.Security,
                    title = "Strict Host Key",
                    description = "How to handle unknown host keys",
                    currentValue = sshStrictHostKey,
                    options = listOf(
                        "accept-new" to "Accept New",
                        "strict" to "Strict",
                        "accept-all" to "Accept All"
                    ),
                    onValueChange = { viewModel.set(Settings.sshStrictHostKey, it) }
                )
                AppHorizontalDivider(inset = true)
                SettingsNavigationItem(
                    icon = Icons.Filled.Security,
                    title = "Known Hosts",
                    description = "Manage trusted host keys",
                    onClick = onNavigateToKnownHosts
                )
            }

            // -----------------------------------------------------------------
            // SSH KEYS
            // -----------------------------------------------------------------
            AppSectionHeader(text = "SSH Keys")
            AppListCard {
                SettingsNavigationItem(
                    icon = Icons.Filled.Key,
                    title = "Manage SSH Keys",
                    description = "Generate, import, and manage key pairs",
                    onClick = onNavigateToKeyManager
                )
            }

            // -----------------------------------------------------------------
            // ALGORITHMS
            // -----------------------------------------------------------------
            AppSectionHeader(text = "Algorithms")
            AppListCard {
                Text(
                    text = t("Defaults work with modern servers. Only change for compliance requirements."),
                    style = tokens.type.bodySmall,
                    color = tokens.colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = tokens.space.lg, vertical = tokens.space.md)
                )
                AppHorizontalDivider(inset = true)
                AppAlgorithmSelector(
                    title = "Ciphers",
                    description = "Encryption algorithms for data transfer",
                    allAlgorithms = SshAlgorithms.ciphers,
                    selectedAlgorithms = sshPreferredCiphers
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() },
                    onSelectionChanged = { viewModel.set(Settings.sshPreferredCiphers, it.joinToString(",")) }
                )
                AppHorizontalDivider(inset = true)
                AppAlgorithmSelector(
                    title = "Key Exchange (KEX)",
                    description = "Algorithms for secure key negotiation",
                    allAlgorithms = SshAlgorithms.kex,
                    selectedAlgorithms = sshPreferredKex
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() },
                    onSelectionChanged = { viewModel.set(Settings.sshPreferredKex, it.joinToString(",")) }
                )
                AppHorizontalDivider(inset = true)
                AppAlgorithmSelector(
                    title = "MACs",
                    description = "Message authentication codes for integrity",
                    allAlgorithms = SshAlgorithms.macs,
                    selectedAlgorithms = sshPreferredMacs
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() },
                    onSelectionChanged = { viewModel.set(Settings.sshPreferredMacs, it.joinToString(",")) }
                )
                AppHorizontalDivider(inset = true)
                AppAlgorithmSelector(
                    title = "Host Key Algorithms",
                    description = "Algorithms for verifying server identity",
                    allAlgorithms = SshAlgorithms.hostKeyAlgs,
                    selectedAlgorithms = sshPreferredHostKeyAlgs
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() },
                    onSelectionChanged = { viewModel.set(Settings.sshPreferredHostKeyAlgs, it.joinToString(",")) }
                )
            }

            AppVerticalSpacer(AppSpacerSize.Lg)
        }
    }
}
