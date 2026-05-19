package com.tmuxes.service

import com.tmuxes.data.model.ServerEntity
import com.tmuxes.ssh.ServerConnectionState
import com.tmuxes.ssh.ServerStatus
import java.util.Locale

internal data class ConnectionNotificationCopy(
    val title: String,
    val text: String,
    val subText: String,
    val detailLines: List<String>
)

internal object ConnectionNotificationFormatter {
    private const val MAX_DETAIL_LINES = 6
    private const val MAX_ERROR_CHARS = 72

    fun starting(): ConnectionNotificationCopy = ConnectionNotificationCopy(
        title = "tmuxes: starting SSH monitor",
        text = "Preparing background connection monitoring.",
        subText = "Starting",
        detailLines = listOf("Starting SSH monitor and loading configured servers.")
    )

    fun format(
        servers: List<ServerEntity>,
        states: Map<Long, ServerConnectionState>,
        nowElapsedMs: Long
    ): ConnectionNotificationCopy {
        val rows = buildRows(servers, states)
        val monitoredRows = rows.filter { it.isMonitored }
        if (monitoredRows.isEmpty()) {
            return ConnectionNotificationCopy(
                title = "tmuxes: no servers monitored",
                text = "Enable a server to start background SSH monitoring.",
                subText = "No enabled servers",
                detailLines = listOf("No enabled servers are currently being monitored.")
            )
        }

        val counts = StatusCounts.from(monitoredRows)
        val actionRows = monitoredRows.filter { it.needsAction }
        val allConnected = counts.connected == monitoredRows.size
        val allOffline = counts.noNetwork == monitoredRows.size

        val title = when {
            actionRows.isNotEmpty() -> "tmuxes: action needed"
            allOffline -> "tmuxes: waiting for network"
            counts.retrying > 0 -> "tmuxes: reconnecting SSH"
            counts.connecting + counts.waitingParent > 0 -> "tmuxes: connecting SSH"
            allConnected -> "tmuxes: SSH connections active"
            counts.connected > 0 -> "tmuxes: partially connected"
            else -> "tmuxes: monitoring SSH"
        }

        val text = when {
            actionRows.isNotEmpty() -> {
                "${actionRows.size} ${plural(actionRows.size, "server needs", "servers need")} attention: ${names(actionRows)}"
            }
            allOffline -> {
                "No network; ${monitoredRows.size} ${plural(monitoredRows.size, "server", "servers")} will reconnect when Android is online."
            }
            counts.retrying > 0 -> {
                "${counts.connected}/${monitoredRows.size} connected; ${counts.retrying} ${plural(counts.retrying, "server is", "servers are")} retrying after network errors."
            }
            counts.connecting + counts.waitingParent > 0 -> {
                "${counts.connected}/${monitoredRows.size} connected; ${counts.connecting + counts.waitingParent} ${plural(counts.connecting + counts.waitingParent, "server is", "servers are")} still connecting."
            }
            allConnected -> {
                "${monitoredRows.size}/${monitoredRows.size} connected. Background sessions stay reachable."
            }
            else -> "${counts.connected}/${monitoredRows.size} connected; ${counts.idle} waiting for reconnect."
        }

        val detailRows = monitoredRows.sortedWith(compareBy<ServerRow> { it.priority }.thenBy { it.name.lowercase(Locale.US) })
        val detailLines = buildList {
            add("Summary: ${counts.toSummary()}")
            detailRows.take(MAX_DETAIL_LINES - 1).forEach { row ->
                add("${row.name}: ${rowDetail(row, nowElapsedMs)}")
            }
            val remaining = detailRows.size - (MAX_DETAIL_LINES - 1)
            if (remaining > 0) add("+$remaining more ${plural(remaining, "server", "servers")}")
        }

        return ConnectionNotificationCopy(
            title = title,
            text = text,
            subText = "${counts.connected}/${monitoredRows.size} connected",
            detailLines = detailLines
        )
    }

    private fun buildRows(
        servers: List<ServerEntity>,
        states: Map<Long, ServerConnectionState>
    ): List<ServerRow> {
        val byId = servers.associateBy { it.id }
        val configuredRows = servers.map { server ->
            val status = states[server.id] ?: ServerConnectionState(
                status = if (server.isEnabled) ServerStatus.IDLE else ServerStatus.PAUSED
            )
            ServerRow(
                name = server.displayName,
                isEnabled = server.isEnabled,
                state = status
            )
        }
        val strayRows = states
            .filterKeys { it !in byId }
            .map { (id, state) ->
                ServerRow(
                    name = "Server #$id",
                    isEnabled = true,
                    state = state
                )
            }
        return configuredRows + strayRows
    }

    private fun rowDetail(row: ServerRow, nowElapsedMs: Long): String = when (row.state.status) {
        ServerStatus.CONNECTED -> "connected"
        ServerStatus.CONNECTING -> "connecting now"
        ServerStatus.NETWORK_ERROR -> retryText(row.state, nowElapsedMs)
        ServerStatus.AUTH_FAILED -> actionText("authentication failed", row.state.errorMessage)
        ServerStatus.WAITING_HOST_KEY -> "action needed - verify host key in tmuxes"
        ServerStatus.PARENT_FAILED -> {
            val parentName = row.state.parentInfo?.parentName ?: "parent server"
            actionText("parent $parentName failed", row.state.errorMessage)
        }
        ServerStatus.WAITING_PARENT -> {
            val parent = row.state.parentInfo
            if (parent == null) {
                "waiting for parent server"
            } else {
                "waiting for parent ${parent.parentName} (${parent.parentStatus.label.lowercase(Locale.US)})"
            }
        }
        ServerStatus.NO_NETWORK -> "paused - no network; will reconnect automatically"
        ServerStatus.PAUSED -> "disabled"
        ServerStatus.DISCONNECTED -> "disconnected; waiting for reconnect"
        ServerStatus.IDLE -> "idle; waiting for connection check"
    }

    private fun retryText(state: ServerConnectionState, nowElapsedMs: Long): String {
        val retry = if (state.retryCount > 0) "retry ${state.retryCount}" else "retry scheduled"
        val retryAt = state.nextRetryAt
        val eta = if (retryAt != null && retryAt > nowElapsedMs) {
            val seconds = ((retryAt - nowElapsedMs) / 1000L).coerceAtLeast(1L)
            " in about ${seconds}s"
        } else {
            " soon"
        }
        val reason = cleanError(state.errorMessage)
        return if (reason == null) "$retry$eta after network error" else "$retry$eta - $reason"
    }

    private fun actionText(reason: String, errorMessage: String?): String {
        val error = cleanError(errorMessage)
        return if (error == null || error.equals(reason, ignoreCase = true)) {
            "action needed - $reason"
        } else {
            "action needed - $reason: $error"
        }
    }

    private fun cleanError(errorMessage: String?): String? {
        val firstLine = errorMessage
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() }
            ?: return null
        return if (firstLine.length > MAX_ERROR_CHARS) {
            firstLine.take(MAX_ERROR_CHARS - 3).trimEnd() + "..."
        } else {
            firstLine
        }
    }

    private fun names(rows: List<ServerRow>): String =
        rows.take(3).joinToString(", ") { it.name }.let { shown ->
            val remaining = rows.size - 3
            if (remaining > 0) "$shown +$remaining more" else shown
        }

    private fun plural(count: Int, singular: String, plural: String): String =
        if (count == 1) singular else plural

    private data class ServerRow(
        val name: String,
        val isEnabled: Boolean,
        val state: ServerConnectionState
    ) {
        val isMonitored: Boolean
            get() = isEnabled || state.status !in inactiveStatuses

        val needsAction: Boolean
            get() = state.status in actionStatuses

        val priority: Int
            get() = when (state.status) {
                ServerStatus.AUTH_FAILED,
                ServerStatus.PARENT_FAILED,
                ServerStatus.WAITING_HOST_KEY -> 0
                ServerStatus.NO_NETWORK -> 1
                ServerStatus.NETWORK_ERROR -> 2
                ServerStatus.WAITING_PARENT -> 3
                ServerStatus.CONNECTING -> 4
                ServerStatus.DISCONNECTED,
                ServerStatus.IDLE -> 5
                ServerStatus.CONNECTED -> 6
                ServerStatus.PAUSED -> 7
            }
    }

    private data class StatusCounts(
        val connected: Int,
        val connecting: Int,
        val retrying: Int,
        val noNetwork: Int,
        val waitingParent: Int,
        val action: Int,
        val idle: Int
    ) {
        fun toSummary(): String = buildList {
            if (connected > 0) add("$connected connected")
            if (connecting > 0) add("$connecting connecting")
            if (retrying > 0) add("$retrying retrying")
            if (noNetwork > 0) add("$noNetwork without network")
            if (waitingParent > 0) add("$waitingParent waiting for parent")
            if (action > 0) add("$action ${plural(action, "needs attention", "need attention")}")
            if (idle > 0) add("$idle idle")
        }.joinToString(", ").ifBlank { "no active connection state" }

        companion object {
            fun from(rows: List<ServerRow>): StatusCounts {
                var connected = 0
                var connecting = 0
                var retrying = 0
                var noNetwork = 0
                var waitingParent = 0
                var action = 0
                var idle = 0

                for (row in rows) {
                    when (row.state.status) {
                        ServerStatus.CONNECTED -> connected++
                        ServerStatus.CONNECTING -> connecting++
                        ServerStatus.NETWORK_ERROR -> retrying++
                        ServerStatus.NO_NETWORK -> noNetwork++
                        ServerStatus.WAITING_PARENT -> waitingParent++
                        ServerStatus.AUTH_FAILED,
                        ServerStatus.PARENT_FAILED,
                        ServerStatus.WAITING_HOST_KEY -> action++
                        ServerStatus.IDLE,
                        ServerStatus.DISCONNECTED,
                        ServerStatus.PAUSED -> idle++
                    }
                }

                return StatusCounts(
                    connected = connected,
                    connecting = connecting,
                    retrying = retrying,
                    noNetwork = noNetwork,
                    waitingParent = waitingParent,
                    action = action,
                    idle = idle
                )
            }
        }
    }

    private val actionStatuses = setOf(
        ServerStatus.AUTH_FAILED,
        ServerStatus.PARENT_FAILED,
        ServerStatus.WAITING_HOST_KEY
    )

    private val inactiveStatuses = setOf(
        ServerStatus.IDLE,
        ServerStatus.DISCONNECTED,
        ServerStatus.PAUSED
    )
}
