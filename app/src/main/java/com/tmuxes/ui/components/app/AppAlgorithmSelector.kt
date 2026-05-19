package com.tmuxes.ui.components.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmuxes.i18n.t
import com.tmuxes.ui.design.appTokens

/**
 * Catalog of every SSH algorithm name we let the user toggle.
 * Lives with [AppAlgorithmSelector] because no other consumer needs
 * the lists.
 */
object SshAlgorithms {
    val ciphers = listOf(
        "chacha20-poly1305@openssh.com", "aes256-gcm@openssh.com", "aes128-gcm@openssh.com",
        "aes256-ctr", "aes192-ctr", "aes128-ctr",
        "aes256-cbc", "aes192-cbc", "aes128-cbc"
    )
    val kex = listOf(
        "curve25519-sha256", "curve25519-sha256@libssh.org",
        "ecdh-sha2-nistp256", "ecdh-sha2-nistp384", "ecdh-sha2-nistp521",
        "diffie-hellman-group-exchange-sha256",
        "diffie-hellman-group18-sha512", "diffie-hellman-group16-sha512",
        "diffie-hellman-group14-sha256", "diffie-hellman-group14-sha1"
    )
    val macs = listOf(
        "hmac-sha2-256-etm@openssh.com", "hmac-sha2-512-etm@openssh.com",
        "hmac-sha2-256", "hmac-sha2-512",
        "hmac-sha1-etm@openssh.com", "hmac-sha1"
    )
    val hostKeyAlgs = listOf(
        "ssh-ed25519", "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521",
        "rsa-sha2-512", "rsa-sha2-256", "ssh-rsa"
    )
}

/**
 * Multi-select chip group for SSH algorithm preferences. An empty
 * `selectedAlgorithms` list means "all selected" (default state).
 *
 * Internal chips go through [AppFilterChip] so colors / shape follow the
 * App* design tokens — the previous raw `FilterChip` inside this component
 * was the last raw-M3-chip site outside the App* family.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppAlgorithmSelector(
    title: String,
    description: String,
    allAlgorithms: List<String>,
    selectedAlgorithms: List<String>,
    onSelectionChanged: (List<String>) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    // Empty list means all selected
    val effectiveSelected = if (selectedAlgorithms.isEmpty()) allAlgorithms else selectedAlgorithms

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.sm + tokens.space.xxs)
    ) {
        Text(
            text = t(title),
            style = tokens.type.bodyLarge,
            color = tokens.colors.onSurface
        )
        Text(
            text = t(description),
            style = tokens.type.bodySmall,
            color = tokens.colors.onSurfaceVariant,
            modifier = Modifier.padding(bottom = tokens.space.sm)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            allAlgorithms.forEach { algorithm ->
                val isSelected = algorithm in effectiveSelected
                AppFilterChip(
                    selected = isSelected,
                    label = algorithm,
                    onClick = {
                        val newSelection = if (isSelected) {
                            // Don't allow deselecting the last one
                            if (effectiveSelected.size > 1) {
                                effectiveSelected - algorithm
                            } else {
                                effectiveSelected
                            }
                        } else {
                            effectiveSelected + algorithm
                        }
                        // If all are selected, store empty list (= default all)
                        if (newSelection.size == allAlgorithms.size &&
                            newSelection.containsAll(allAlgorithms)
                        ) {
                            onSelectionChanged(emptyList())
                        } else {
                            onSelectionChanged(newSelection)
                        }
                    }
                )
            }
        }

        AppButton(
            text = "Reset to defaults",
            onClick = { onSelectionChanged(emptyList()) },
            style = AppButtonStyle.Text,
            modifier = Modifier.align(Alignment.End)
        )
    }
}
