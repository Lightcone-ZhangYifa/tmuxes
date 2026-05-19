package com.tmuxes.portforward

import com.tmuxes.data.repository.ServerYamlRepository
import com.tmuxes.ssh.ConnectionSupervisor
import com.tmuxes.ssh.PortForwardHandle
import com.tmuxes.ssh.ServerStatus
import com.tmuxes.ssh.SshConnectionPool
import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

// ---------------------------------------------------------------------------
// Data types
// ---------------------------------------------------------------------------

/**
 * A local port forward rule: binds [localPort] on the Android device and
 * forwards traffic through the SSH tunnel to [remoteHost]:[remotePort].
 */
data class PortForwardRule(
    val localPort: Int,
    val remoteHost: String,
    val remotePort: Int
) {
    /**
     * Serialize to the `localPort:remoteHost:remotePort` format used in servers.yaml.
     */
    fun serialize(): String = "$localPort:$remoteHost:$remotePort"

    companion object {
        /**
         * Parse a single `localPort:remoteHost:remotePort` or `localPort:remotePort`
         * string. Returns null if the format is invalid.
         */
        fun parse(text: String): PortForwardRule? {
            val parts = text.trim().split(':')
            return when (parts.size) {
                3 -> {
                    val local = parts[0].trim().toIntOrNull() ?: return null
                    val host = parts[1].trim()
                    val remote = parts[2].trim().toIntOrNull() ?: return null
                    PortForwardRule(localPort = local, remoteHost = host, remotePort = remote)
                }
                2 -> {
                    val local = parts[0].trim().toIntOrNull() ?: return null
                    val remote = parts[1].trim().toIntOrNull() ?: return null
                    PortForwardRule(localPort = local, remoteHost = "127.0.0.1", remotePort = remote)
                }
                else -> null
            }
        }

        /**
         * Parse a multi-line local-forwards string into a list of [PortForwardRule].
         * Invalid lines are silently skipped.
         */
        fun parseAll(text: String?): List<PortForwardRule> {
            if (text.isNullOrBlank()) return emptyList()
            return text.lines().mapNotNull { parse(it) }
        }

        /**
         * Serialize a list of rules to a multi-line string for storage.
         * Returns null if the list is empty.
         */
        fun serializeAll(rules: List<PortForwardRule>): String? {
            if (rules.isEmpty()) return null
            return rules.joinToString("\n") { it.serialize() }
        }
    }
}

/**
 * Runtime state of a single port forward.
 */
enum class PortForwardState {
    /** Forward is active and passing traffic. */
    ACTIVE,
    /** SSH connection lost; forward will be restored when reconnected. */
    DISCONNECTED,
    /** Currently re-establishing the forward after reconnect. */
    RECONNECTING,
    /** Forward failed to establish (e.g., port conflict). */
    FAILED
}

/**
 * Combines a [PortForwardRule] with its current runtime [state] and
 * optional [handle] for an active forward.
 */
data class ActivePortForward(
    val rule: PortForwardRule,
    val state: PortForwardState,
    val handle: PortForwardHandle? = null,
    val errorMessage: String? = null
)

// ---------------------------------------------------------------------------
// PortForwardCoordinator
// ---------------------------------------------------------------------------

/**
 * Manages local port forward lifecycle with automatic re-establishment after
 * SSH reconnect.
 *
 * Observes [ConnectionSupervisor.serverStates] to detect connection loss and
 * recovery, then automatically restores all configured local port forwards.
 * Remote forwards are handled by [DirectSshConnection.connect] from
 * [SshConfig.remoteForwards] and do not need coordinator management.
 *
 * Local forward rules are persisted in `servers.yaml` via
 * [ServerYamlRepository.updateLocalForwards].
 */
class PortForwardCoordinator(
    private val connectionPool: SshConnectionPool,
    private val supervisor: ConnectionSupervisor,
    private val serverRepository: ServerYamlRepository,
    private val scope: CoroutineScope
) {
    private val _activeForwards = MutableStateFlow<Map<Long, List<ActivePortForward>>>(emptyMap())

    /** Current state of all port forwards, keyed by server ID. */
    val activeForwards: StateFlow<Map<Long, List<ActivePortForward>>> = _activeForwards.asStateFlow()

    /** Per-server mutex to prevent onConnectionLost racing with onConnectionRestored. */
    private val perServerMutex = ConcurrentHashMap<Long, Mutex>()

    init {
        // Self-driven: observe supervisor.serverStates for CONNECTED/non-CONNECTED transitions.
        // Important: events are awaited (NOT launched separately) so consecutive
        // transitions for the same server are processed in strict emission order.
        // On a multi-threaded scope, separate `launch` blocks would be serialized
        // by the per-server mutex but their execution order would not match the
        // emission order, potentially leaving handles orphaned after rapid flaps.
        //
        // try/catch at every layer so a transient error in one transition
        // handler cannot tear the whole collector down — port forwards
        // would silently stop restoring on reconnect.
        scope.launch {
            try {
                var previousStates = emptyMap<Long, ServerStatus>()
                supervisor.serverStates.collect { states ->
                    try {
                        // Detect transitions
                        for ((serverId, state) in states) {
                            try {
                                val prev = previousStates[serverId]
                                val curr = state.status
                                if (prev == ServerStatus.CONNECTED && curr != ServerStatus.CONNECTED) {
                                    onConnectionLost(serverId)
                                } else if (prev != ServerStatus.CONNECTED && curr == ServerStatus.CONNECTED) {
                                    onConnectionRestored(serverId)
                                }
                            } catch (ce: kotlinx.coroutines.CancellationException) {
                                throw ce
                            } catch (e: Exception) {
                                AppLogger.w(Category.PFWD) { "PortForwardCoordinator: transition handler failed: ${e.message}" }
                            }
                        }
                        // Handle removed servers (deleted from supervisor)
                        for (serverId in previousStates.keys - states.keys) {
                            try { onConnectionLost(serverId) } catch (ce: kotlinx.coroutines.CancellationException) {
                                throw ce
                            } catch (e: Exception) {
                                AppLogger.w(Category.PFWD) { "PortForwardCoordinator: onConnectionLost for removed server failed: ${e.message}" }
                            }
                        }
                        previousStates = states.mapValues { it.value.status }
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        AppLogger.w(Category.PFWD) { "PortForwardCoordinator: state handler failed: ${e.message}" }
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // normal shutdown
            } catch (e: Exception) {
                AppLogger.e(Category.PFWD, e) { "PortForwardCoordinator: serverStates collector died" }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Add a local port forward for [serverId]. If the server is currently
     * connected, the forward is established immediately. The rule is persisted
     * to `servers.yaml` so it will be auto-restored on future reconnects.
     */
    suspend fun addLocalForward(serverId: Long, rule: PortForwardRule): ActivePortForward {
        val mutex = perServerMutex.getOrPut(serverId) { Mutex() }
        return mutex.withLock {
            // Persist rule
            val server = serverRepository.getById(serverId)
            val existingRules = PortForwardRule.parseAll(server?.localForwards)
            if (existingRules.none { it == rule }) {
                val updatedRules = existingRules + rule
                serverRepository.updateLocalForwards(serverId, PortForwardRule.serializeAll(updatedRules))
            }

            // Try to establish immediately if connected
            val connection = connectionPool.get(serverId)
            val forward = if (connection != null && connection.isConnected()) {
                try {
                    val handle = connection.setupLocalPortForward(
                        rule.localPort, rule.remoteHost, rule.remotePort
                    )
                    AppLogger.i(Category.PFWD) { "PortForwardCoordinator: established forward " +
                            "${rule.localPort}->${rule.remoteHost}:${rule.remotePort} for server $serverId" }
                    ActivePortForward(rule = rule, state = PortForwardState.ACTIVE, handle = handle)
                } catch (e: Exception) {
                    AppLogger.w(Category.PFWD) { "PortForwardCoordinator: failed to establish forward " +
                            "${rule.localPort}->${rule.remoteHost}:${rule.remotePort} for server $serverId: ${e.message}" }
                    ActivePortForward(rule = rule, state = PortForwardState.FAILED, errorMessage = e.message)
                }
            } else {
                ActivePortForward(rule = rule, state = PortForwardState.DISCONNECTED)
            }

            // Update state
            updateForward(serverId, forward)
            forward
        }
    }

    /**
     * Remove a local port forward for [serverId]. Closes the active handle
     * (if any) and removes the rule from `servers.yaml`.
     */
    suspend fun removeLocalForward(serverId: Long, rule: PortForwardRule) {
        val mutex = perServerMutex.getOrPut(serverId) { Mutex() }
        mutex.withLock {
            // Close handle if active
            val forwards = _activeForwards.value[serverId] ?: emptyList()
            val existing = forwards.find { it.rule == rule }
            existing?.handle?.let { handle ->
                try {
                    handle.close()
                } catch (e: Exception) {
                    AppLogger.w(Category.PFWD) { "PortForwardCoordinator: error closing handle for " +
                            "${rule.localPort}->${rule.remoteHost}:${rule.remotePort}: ${e.message}" }
                }
            }

            // Remove from state
            val updatedList = forwards.filter { it.rule != rule }
            if (updatedList.isEmpty()) {
                _activeForwards.value = _activeForwards.value - serverId
            } else {
                _activeForwards.value = _activeForwards.value + (serverId to updatedList)
            }

            // Remove from persisted rules
            val server = serverRepository.getById(serverId)
            val existingRules = PortForwardRule.parseAll(server?.localForwards)
            val updatedRules = existingRules.filter { it != rule }
            serverRepository.updateLocalForwards(serverId, PortForwardRule.serializeAll(updatedRules))

            AppLogger.i(Category.PFWD) { "PortForwardCoordinator: removed forward " +
                    "${rule.localPort}->${rule.remoteHost}:${rule.remotePort} for server $serverId" }
        }
    }

    // -------------------------------------------------------------------------
    // Internal: connection lifecycle handlers
    // -------------------------------------------------------------------------

    /**
     * Called when a server transitions from CONNECTED to non-CONNECTED.
     * Closes all active handles and sets states to DISCONNECTED.
     */
    private suspend fun onConnectionLost(serverId: Long) {
        val mutex = perServerMutex.getOrPut(serverId) { Mutex() }
        mutex.withLock {
            val forwards = _activeForwards.value[serverId] ?: return
            if (forwards.isEmpty()) return

            AppLogger.d(Category.PFWD) { "PortForwardCoordinator: connection lost for server $serverId, " +
                    "marking ${forwards.size} forward(s) as DISCONNECTED" }

            val updated = forwards.map { forward ->
                // Close handle safely
                forward.handle?.let { handle ->
                    try {
                        handle.close()
                    } catch (e: Exception) {
                        AppLogger.w(Category.PFWD) { "PortForwardCoordinator: error closing handle for " +
                                "${forward.rule.localPort}: ${e.message}" }
                    }
                }
                forward.copy(state = PortForwardState.DISCONNECTED, handle = null, errorMessage = null)
            }

            _activeForwards.value = _activeForwards.value + (serverId to updated)
        }
    }

    /**
     * Called when a server transitions to CONNECTED.
     * Reads local forward rules from [ServerYamlRepository] and re-establishes
     * each one. Remote forwards are handled automatically by
     * [DirectSshConnection.connect] from [SshConfig.remoteForwards].
     */
    private suspend fun onConnectionRestored(serverId: Long) {
        val mutex = perServerMutex.getOrPut(serverId) { Mutex() }
        mutex.withLock {
            // Read persisted rules from server config
            val server = serverRepository.getById(serverId) ?: return
            val rules = PortForwardRule.parseAll(server.localForwards)
            if (rules.isEmpty()) {
                // No rules to restore; clean up any stale state
                _activeForwards.value = _activeForwards.value - serverId
                return
            }

            AppLogger.d(Category.PFWD) { "PortForwardCoordinator: connection restored for server $serverId, " +
                    "restoring ${rules.size} forward(s)" }

            // Mark all as RECONNECTING
            val reconnecting = rules.map { rule ->
                ActivePortForward(rule = rule, state = PortForwardState.RECONNECTING)
            }
            _activeForwards.value = _activeForwards.value + (serverId to reconnecting)

            // Attempt to establish each forward
            val connection = connectionPool.get(serverId)
            val restored = rules.map { rule ->
                if (connection != null && connection.isConnected()) {
                    try {
                        val handle = connection.setupLocalPortForward(
                            rule.localPort, rule.remoteHost, rule.remotePort
                        )
                        AppLogger.i(Category.PFWD) { "PortForwardCoordinator: restored forward " +
                                "${rule.localPort}->${rule.remoteHost}:${rule.remotePort} for server $serverId" }
                        ActivePortForward(rule = rule, state = PortForwardState.ACTIVE, handle = handle)
                    } catch (e: Exception) {
                        AppLogger.w(Category.PFWD) { "PortForwardCoordinator: failed to restore forward " +
                                "${rule.localPort}->${rule.remoteHost}:${rule.remotePort} for server $serverId: ${e.message}" }
                        ActivePortForward(rule = rule, state = PortForwardState.FAILED, errorMessage = e.message)
                    }
                } else {
                    ActivePortForward(rule = rule, state = PortForwardState.DISCONNECTED)
                }
            }

            _activeForwards.value = _activeForwards.value + (serverId to restored)
        }
    }

    // -------------------------------------------------------------------------
    // Internal: state management
    // -------------------------------------------------------------------------

    /**
     * Add or update a single [ActivePortForward] in the state map.
     * Must be called under the per-server mutex.
     */
    private fun updateForward(serverId: Long, forward: ActivePortForward) {
        val current = _activeForwards.value[serverId]?.toMutableList() ?: mutableListOf()
        val existingIdx = current.indexOfFirst { it.rule == forward.rule }
        if (existingIdx >= 0) {
            current[existingIdx] = forward
        } else {
            current.add(forward)
        }
        _activeForwards.value = _activeForwards.value + (serverId to current.toList())
    }
}
