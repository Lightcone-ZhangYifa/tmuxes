package com.tmuxes.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Semantic status tokens — colors carrying meaning ("connected", "error",
 * "warning") rather than design intent (primary, secondary). Decoupled
 * from [ColorTokens] because they should NOT respond to user accent
 * customization (a user-picked purple "error" colour would be unsafe).
 *
 * The [forStatus] / [forSeverity] helpers were the old `StatusColors` /
 * `RevealColors` objects, now re-homed here so all design-token reads
 * go through `LocalAppTokens.status`.
 */
@Immutable
data class StatusTokens(
    val connected: Color = Color(0xFF4CAF50),
    val connecting: Color = Color(0xFFFF9800),
    val error: Color = Color(0xFFE53935),
    val warning: Color = Color(0xFFF9A825),
    val info: Color = Color(0xFF1E88E5),
    val idle: Color = Color(0xFF9E9E9E),
    val paused: Color = Color(0xFF78909C),
    /** White text on saturated badges. Matches all 5 status colors at AA. */
    val onStatus: Color = Color.White
) {
    /**
     * Map a [com.tmuxes.ssh.ServerStatus] to its visual indicator color.
     */
    fun forServerStatus(status: com.tmuxes.ssh.ServerStatus): Color = when (status) {
        com.tmuxes.ssh.ServerStatus.CONNECTED -> connected
        com.tmuxes.ssh.ServerStatus.CONNECTING,
        com.tmuxes.ssh.ServerStatus.WAITING_PARENT,
        com.tmuxes.ssh.ServerStatus.NO_NETWORK,
        com.tmuxes.ssh.ServerStatus.WAITING_HOST_KEY -> connecting
        com.tmuxes.ssh.ServerStatus.IDLE,
        com.tmuxes.ssh.ServerStatus.DISCONNECTED -> idle
        com.tmuxes.ssh.ServerStatus.PAUSED -> paused
        com.tmuxes.ssh.ServerStatus.NETWORK_ERROR,
        com.tmuxes.ssh.ServerStatus.AUTH_FAILED,
        com.tmuxes.ssh.ServerStatus.PARENT_FAILED -> error
    }

    enum class Severity { Info, Success, Warning, Danger }

    fun forSeverity(severity: Severity): Color = when (severity) {
        Severity.Info -> info
        Severity.Success -> connected
        Severity.Warning -> warning
        Severity.Danger -> error
    }

    companion object {
        val Default = StatusTokens()
    }
}
