package com.tmuxes.ssh

import android.os.SystemClock
import com.tmuxes.data.db.KnownHostDao
import com.tmuxes.data.model.AuthMethod
import com.tmuxes.data.model.KnownHostEntity
import com.tmuxes.data.model.ServerEntity
import com.tmuxes.data.preferences.AppPreferences
import com.tmuxes.data.repository.ServerYamlRepository
import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import net.schmizz.sshj.userauth.UserAuthException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

// ---------------------------------------------------------------------------
// ReconcileSnapshot — all data needed for the pure decision function
// ---------------------------------------------------------------------------

/**
 * Immutable snapshot of the system state gathered during Phase 1 of reconciliation.
 * Passed to the pure [ConnectionSupervisor.computeActions] function.
 */
data class ReconcileSnapshot(
    val servers: List<ServerEntity>,
    val connectionStates: Map<Long, ConnectionState>,
    val intents: Map<Long, IntentSnapshot>,
    val isOnline: Boolean,
    val isForeground: Boolean,
    val knownHostsCache: Map<Long, List<KnownHostEntity>>,
    val now: Long,
    val reconnectIntervalSeconds: Int
)

/**
 * Immutable copy of a [ServerIntent] for the pure decision function.
 */
data class IntentSnapshot(
    val serverId: Long,
    val retryCount: Int = 0,
    val nextRetryAt: Long = 0L,
    val lastError: String? = null,
    val isPermanentlyFailed: Boolean = false,
    val hasActiveConnectJob: Boolean = false,
    val previousConnectionState: ConnectionState? = null,
    /**
     * Set by [ConnectionSupervisor.invalidateConnection] when the repository
     * tells us a server's connection-relevant fields just changed. Causes
     * [computeActions] to disconnect and reconnect even if the server is
     * already AUTHENTICATED. Cleared once a fresh connection succeeds.
     */
    val mustReconnect: Boolean = false
)

// ---------------------------------------------------------------------------
// ReconcileAction — sealed class of all possible side effects
// ---------------------------------------------------------------------------

sealed class ReconcileAction {
    data class Connect(val serverId: Long, val server: ServerEntity) : ReconcileAction()
    data class Disconnect(val serverId: Long) : ReconcileAction()
    data class UpdateState(val serverId: Long, val state: ServerConnectionState) : ReconcileAction()
    data class ScheduleRetry(val serverId: Long, val delayMs: Long) : ReconcileAction()
    data class ResetChildBackoffs(val parentId: Long) : ReconcileAction()
    data class MarkOrphanedChild(val serverId: Long) : ReconcileAction()
    data class CascadeDisconnect(val parentId: Long) : ReconcileAction()
    /**
     * Remove all tracked state for a server that has been deleted from the database.
     * Clears both [_serverStates] and the internal intent map. Emitted after any
     * required [Disconnect] to avoid leaking entries when users delete servers.
     */
    data class RemoveState(val serverId: Long) : ReconcileAction()
}

// ---------------------------------------------------------------------------
// ConnectionSupervisor — sole state authority for SSH connection management
// ---------------------------------------------------------------------------

/**
 * The ConnectionSupervisor continuously reconciles "desired state" (all enabled
 * servers in the database) against "actual state" (what's connected) and
 * auto-connects/reconnects everything.
 *
 * This is the central engine of the "always-connected" architecture and the
 * **sole state authority** for all external consumers (ViewModels, Widgets, Services).
 *
 * ## Three-Phase Reconciliation
 * 1. **Gather** — collect all data (servers, connection states, network, intents) outside locks
 * 2. **Compute** — pure function: given snapshot, produce a list of [ReconcileAction]s
 * 3. **Execute** — apply side effects (connect, disconnect, update state, schedule retry)
 *
 * ## Error handling policy
 * - **Network errors** (timeout, unreachable, I/O): infinite retry with capped
 *   exponential backoff (2s -> 4s -> 8s -> 16s -> 30s -> 60s cap)
 * - **Auth errors** (wrong password/key): stop retrying, mark as AUTH_FAILED,
 *   wait for user to edit credentials then call [resetRetryState]
 * - **Unknown host key**: only attempt in foreground (needs UI dialog)
 * - **Orphaned child** (parentId references deleted server): PARENT_FAILED
 * - **Parent auth failed**: child gets PARENT_FAILED with [ParentInfo]
 *
 * ## Lifecycle
 * - Initialized in [com.tmuxes.TmuxesApp.onCreate]
 * - Starts the foreground service when connect actions are present
 * - Reacts to database changes (server added/removed/edited)
 * - Reacts to network changes (offline -> online triggers immediate retry)
 * - Reacts to foreground transitions (reset backoffs, retry failed)
 * - Reacts to transport death (connection state -> ERROR/DISCONNECTED)
 */
class ConnectionSupervisor private constructor() {

    private lateinit var pool: SshConnectionPool
    private lateinit var serverRepository: ServerYamlRepository
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var preferences: AppPreferences
    private lateinit var serviceController: ServiceController
    private lateinit var knownHostDao: KnownHostDao
    private lateinit var scope: CoroutineScope

    /**
     * Callback invoked when a host key needs user verification.
     * Must be set before [start] is called.
     */
    var hostKeyPromptCallback: (suspend (HostKeyEvent) -> HostKeyPromptResult)? = null

    private val reconcileChannel = Channel<Unit>(Channel.CONFLATED)
    private val mutex = Mutex()
    private val perServerMutex = ConcurrentHashMap<Long, Mutex>()

    /** Per-server intent tracking (protected by [mutex]). */
    private val intents = mutableMapOf<Long, ServerIntent>()

    /** Per-server connection observer jobs (protected by [mutex]). */
    private val observerJobs = mutableMapOf<Long, Job>()

    private val _serverStates = MutableStateFlow<Map<Long, ServerConnectionState>>(emptyMap())

    /** The sole state source for all external consumers. */
    val serverStates: StateFlow<Map<Long, ServerConnectionState>> = _serverStates.asStateFlow()

    /**
     * Whether the app is currently in the foreground. Set by
     * [ConnectionTrigger]; read inside [gatherSnapshot] to gate the
     * background host-key check (we don't auto-prompt for unknown
     * host keys when the user can't answer the dialog).
     */
    @Volatile
    var isAppInForeground: Boolean = false

    /** Reconnect interval in seconds (configurable from preferences). */
    var reconnectIntervalSeconds: Int = com.tmuxes.data.settings.Settings.sshReconnectIntervalSeconds.default

    private var started = false
    private var mainJob: Job? = null

    /**
     * Shutdown flag — set by [disconnectAll] to prevent races with concurrent
     * [executeConnect] calls. Once true, new connect attempts are no-ops.
     */
    @Volatile
    private var shutdown = false

    // -----------------------------------------------------------------------
    // Internal intent tracking
    // -----------------------------------------------------------------------

    private data class ServerIntent(
        val serverId: Long,
        var retryCount: Int = 0,
        var nextRetryAt: Long = 0L,
        var lastError: String? = null,
        var isPermanentlyFailed: Boolean = false,
        var connectJob: Job? = null,
        var previousConnectionState: ConnectionState? = null,
        /**
         * Set by [ConnectionSupervisor.invalidateConnection] when the
         * repository tells us a server's connection-relevant fields just
         * changed. Causes [computeActions] to disconnect and reconnect
         * even if the server is already AUTHENTICATED. Cleared once a
         * fresh connection succeeds.
         */
        var mustReconnect: Boolean = false
    ) {
        fun toSnapshot() = IntentSnapshot(
            serverId = serverId,
            retryCount = retryCount,
            nextRetryAt = nextRetryAt,
            lastError = lastError,
            isPermanentlyFailed = isPermanentlyFailed,
            hasActiveConnectJob = connectJob?.isActive == true,
            previousConnectionState = previousConnectionState,
            mustReconnect = mustReconnect
        )
    }

    // -----------------------------------------------------------------------
    // Initialization
    // -----------------------------------------------------------------------

    fun initialize(
        pool: SshConnectionPool,
        serverRepository: ServerYamlRepository,
        networkMonitor: NetworkMonitor,
        preferences: AppPreferences,
        serviceController: ServiceController,
        knownHostDao: KnownHostDao,
        scope: CoroutineScope
    ) {
        this.pool = pool
        this.serverRepository = serverRepository
        this.networkMonitor = networkMonitor
        this.preferences = preferences
        this.serviceController = serviceController
        this.knownHostDao = knownHostDao
        this.scope = scope
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    /**
     * Start the supervisor. Spawns the reconcile-channel consumer and
     * subscribes to repository invalidations (which mutate intent state,
     * so they belong inside the supervisor).
     *
     * **External lifecycle / event triggers** — server-list changes,
     * network transitions, app foreground/background, screen on, user
     * refresh — are routed through [ConnectionTrigger]. The supervisor
     * intentionally does **not** subscribe to those: they belong to a
     * separate component so "what makes us reconnect?" has one
     * canonical answer instead of being scattered across this file.
     */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        AppLogger.i(Category.SSH) { "ConnectionSupervisor: starting" }

        // Main reconciliation consumer.
        mainJob = scope.launch {
            for (trigger in reconcileChannel) {
                try {
                    reconcile()
                } catch (e: Exception) {
                    AppLogger.e(Category.SSH, e) { "ConnectionSupervisor: reconciliation error" }
                }
            }
        }

        // Repository invalidations. The repository emits a serverId when
        // it commits a connection-relevant edit; the supervisor flips
        // mustReconnect=true on that intent and clears any accumulated
        // retry/permanent-fail state (those belong to the OLD config).
        // This subscription mutates intent state, so it stays inside the
        // supervisor. ConnectionTrigger separately observes the same
        // SharedFlow purely to nudge a reconcile + log a Reason.
        scope.launch {
            try {
                serverRepository.connectionInvalidations.collect { serverId ->
                    try {
                        mutex.withLock {
                            val intent = intents.getOrPut(serverId) { ServerIntent(serverId) }
                            intent.mustReconnect = true
                            intent.isPermanentlyFailed = false
                            intent.retryCount = 0
                            intent.nextRetryAt = 0L
                            intent.lastError = null
                        }
                        AppLogger.i(Category.SSH) { "ConnectionSupervisor: server $serverId connection-relevant fields changed, will reconnect" }
                        triggerReconciliation()
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        AppLogger.w(Category.SSH) { "invalidation handler failed: ${e.message}" }
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // normal shutdown
            } catch (e: Exception) {
                AppLogger.e(Category.SSH, e) { "invalidation collect loop died" }
            }
        }

        // Load saved reconnect interval (one-shot).
        scope.launch {
            try {
                reconnectIntervalSeconds =
                    preferences.get(com.tmuxes.data.settings.Settings.sshReconnectIntervalSeconds)
            } catch (_: kotlinx.coroutines.CancellationException) {
                // normal shutdown
            } catch (e: Exception) {
                AppLogger.w(Category.SSH) { "loading reconnect interval failed: ${e.message}" }
            }
        }
    }

    /**
     * Stop the supervisor. Cancels the main reconciliation job.
     */
    @Synchronized
    fun stop() {
        if (!started) return
        started = false
        mainJob?.cancel()
        mainJob = null
        AppLogger.i(Category.SSH) { "ConnectionSupervisor: stopped" }
    }

    /**
     * Reset retry state for a specific server.
     *
     * The user-initiated **Refresh** path on the Sessions tab calls this
     * to mean "I want you to try NOW, ignore your backoff timer." Without
     * this, a server in NETWORK_ERROR with `nextRetryAt > now` stays in
     * the backoff branch of [computeActions] and the user's tap appears
     * to do nothing until the scheduled retry fires on its own.
     *
     * Clearing `isPermanentlyFailed` also recovers AUTH_FAILED servers
     * after the user fixes credentials and re-issues a refresh.
     */
    fun resetRetryState(serverId: Long) {
        scope.launch {
            mutex.withLock {
                intents[serverId]?.let {
                    it.isPermanentlyFailed = false
                    it.retryCount = 0
                    it.nextRetryAt = 0L
                    it.lastError = null
                }
            }
            AppLogger.i(Category.SSH) { "ConnectionSupervisor: retry state reset for server $serverId" }
            triggerReconciliation()
        }
    }

    /**
     * Bulk variant of [resetRetryState] — called by the global refresh
     * button on the Sessions tab so that one user action releases every
     * server stuck in backoff.
     */
    fun resetAllRetryState() {
        scope.launch {
            mutex.withLock {
                for (intent in intents.values) {
                    intent.isPermanentlyFailed = false
                    intent.retryCount = 0
                    intent.nextRetryAt = 0L
                    intent.lastError = null
                }
            }
            triggerReconciliation()
        }
    }

    /**
     * Disconnect all servers and clean up.
     *
     * Sets [shutdown] to true BEFORE cancelling in-flight connect jobs to prevent
     * a concurrent reconciliation from creating new connections between the time
     * we cancel the existing ones and clear the pool.
     */
    suspend fun disconnectAll() {
        AppLogger.i(Category.SSH) { "ConnectionSupervisor: disconnecting all connections" }
        shutdown = true

        // Cancel all in-flight connect jobs first so they cannot publish new
        // connections into the pool after we drain it.
        val jobsToJoin = mutex.withLock {
            intents.values.mapNotNull { it.connectJob }.also {
                for (intent in intents.values) intent.connectJob = null
            }
        }
        for (job in jobsToJoin) {
            job.cancel()
            kotlinx.coroutines.withTimeoutOrNull(2000) { job.join() }
        }

        val allConnections = pool.getAll()
        for ((id, conn) in allConnections) {
            try {
                conn.disconnect()
            } catch (_: Exception) {}
            pool.remove(id)
        }
        mutex.withLock {
            observerJobs.values.forEach { it.cancel() }
            observerJobs.clear()
            intents.clear()
            // perServerMutex is only cleared here (shutdown) to avoid the
            // split-mutex hazard described in executeDisconnect.
            perServerMutex.clear()
        }
        _serverStates.update { emptyMap() }
        serviceController.stopService()
    }

    /**
     * Soft check: evaluate desired vs actual state and apply, **without**
     * touching backoff timers or permanent-fail flags. Use this when an
     * event indicates "something changed, reconsider what to do" — server
     * list emit, network capability tweak, child needing parent.
     *
     * The harder counterpart — release non-permanent backoffs first — is
     * exposed through [resetAllRetryState] / [resetRetryState], which the
     * [ConnectionTrigger] / refresh buttons call directly.
     */
    fun softCheck() {
        triggerReconciliation()
    }

    private fun triggerReconciliation() {
        reconcileChannel.trySend(Unit)
    }

    // -----------------------------------------------------------------------
    // Three-Phase Reconciliation
    // -----------------------------------------------------------------------

    private suspend fun reconcile() {
        val snapshot = gatherSnapshot()
        val actions = computeActions(snapshot)
        executeActions(actions, snapshot)
    }

    // --- Phase 1: Gather data (IO, no decisions) ---

    private suspend fun gatherSnapshot(): ReconcileSnapshot {
        val servers = try {
            serverRepository.getAllDecryptedOnce()
        } catch (e: Exception) {
            AppLogger.e(Category.SSH, e) { "ConnectionSupervisor: failed to load servers" }
            emptyList()
        }

        val connectionStates = buildConnectionStateMap()
        val online = networkMonitor.isOnline.value
        val now = SystemClock.elapsedRealtime()
        val foreground = isAppInForeground

        // Pre-fetch known hosts for background host-key checks
        val knownHostsCache: Map<Long, List<KnownHostEntity>> = if (!foreground) {
            servers.filter { server ->
                server.isEnabled &&
                    connectionStates[server.id] != ConnectionState.AUTHENTICATED &&
                    connectionStates[server.id] != ConnectionState.CONNECTING
            }.associate { server ->
                server.id to try {
                    knownHostDao.findByHostPort(server.hostname, server.port)
                } catch (_: Exception) { emptyList() }
            }
        } else {
            emptyMap()
        }

        // Snapshot intents and update previousConnectionState for next reconcile cycle
        val intentSnapshots = mutex.withLock {
            val snapshots = intents.mapValues { (_, v) -> v.toSnapshot() }
            // Update previousConnectionState for the NEXT reconcile cycle
            for ((id, intent) in intents) {
                intent.previousConnectionState = connectionStates[id]
            }
            snapshots
        }

        return ReconcileSnapshot(
            servers = servers,
            connectionStates = connectionStates,
            intents = intentSnapshots,
            isOnline = online,
            isForeground = foreground,
            knownHostsCache = knownHostsCache,
            now = now,
            reconnectIntervalSeconds = reconnectIntervalSeconds
        )
    }

    /**
     * Build a map of serverId -> ConnectionState from the pool's connections.
     */
    private fun buildConnectionStateMap(): Map<Long, ConnectionState> {
        val result = mutableMapOf<Long, ConnectionState>()
        for ((id, conn) in pool.getAll()) {
            result[id] = conn.state.value
        }
        return result
    }

    // --- Phase 2: Pure decision function (NO side effects, unit-testable) ---
    /**
     * Given a snapshot of the entire system state, compute the list of actions
     * to execute. This is a **pure function** with no side effects.
     *
     * @param snapshot The immutable system state snapshot.
     * @return Ordered list of [ReconcileAction]s to execute.
     */
    internal fun computeActions(snapshot: ReconcileSnapshot): List<ReconcileAction> {
        val actions = mutableListOf<ReconcileAction>()
        val serverMap = snapshot.servers.associateBy { it.id }
        val desiredServerIds = snapshot.servers.map { it.id }.toSet()

        // Clean up intents/connection state for deleted servers.
        // Include BOTH intent keys and pool keys — a deleted server may still
        // have a live connection even if its intent was already cleaned up.
        val strayIds = (snapshot.intents.keys + snapshot.connectionStates.keys) - desiredServerIds
        for (id in strayIds) {
            if (snapshot.connectionStates.containsKey(id)) {
                actions.add(ReconcileAction.Disconnect(id))
            }
            actions.add(ReconcileAction.UpdateState(id, ServerConnectionState(ServerStatus.DISCONNECTED)))
            actions.add(ReconcileAction.RemoveState(id))
        }

        // Sort servers: parents first (by depth), then children
        val sorted = sortByDepth(snapshot.servers)

        for (server in sorted) {
            val intent = snapshot.intents[server.id] ?: IntentSnapshot(server.id)
            val connState = snapshot.connectionStates[server.id]

            // Track parent-recovery: when parent just became AUTHENTICATED, reset children
            val prevConnState = intent.previousConnectionState
            if (connState == ConnectionState.AUTHENTICATED &&
                prevConnState != null && prevConnState != ConnectionState.AUTHENTICATED
            ) {
                actions.add(ReconcileAction.ResetChildBackoffs(server.id))
            }

            // Cascade disconnect: parent dropped from AUTHENTICATED
            if (prevConnState == ConnectionState.AUTHENTICATED &&
                connState != null && connState != ConnectionState.AUTHENTICATED
            ) {
                actions.add(ReconcileAction.CascadeDisconnect(server.id))
            }

            // --- Paused server ---
            if (!server.isEnabled) {
                if (connState != null && connState != ConnectionState.DISCONNECTED) {
                    actions.add(ReconcileAction.Disconnect(server.id))
                }
                actions.add(ReconcileAction.UpdateState(server.id,
                    ServerConnectionState(ServerStatus.PAUSED)))
                continue
            }

            // --- Already connected ---
            if (connState == ConnectionState.AUTHENTICATED) {
                // The repository tells us when a server's connection-relevant
                // fields just changed (write-path detection in
                // ServerYamlRepository.connectionInvalidations). Drop the
                // stale connection and reconnect with the new config.
                if (intent.mustReconnect) {
                    actions.add(ReconcileAction.Disconnect(server.id))
                    // Reflect the brief in-flight state in the UI so
                    // consumers (e.g., the Sessions tab chip row, the
                    // TerminalScreen status banner) see the transition
                    // instead of a stale CONNECTED → CONNECTED jump.
                    // Without this, the cached serverStates entry stays
                    // CONNECTED between Disconnect and Connect, which
                    // can cause SessionCoordinator's reattach observer
                    // to attempt sendInput against a pool entry that
                    // was just removed.
                    actions.add(ReconcileAction.UpdateState(server.id,
                        ServerConnectionState(ServerStatus.CONNECTING)))
                    actions.add(ReconcileAction.Connect(server.id, server))
                    continue
                }
                actions.add(ReconcileAction.UpdateState(server.id,
                    ServerConnectionState(ServerStatus.CONNECTED)))
                continue
            }

            // --- Currently connecting ---
            if (connState == ConnectionState.CONNECTING) {
                actions.add(ReconcileAction.UpdateState(server.id,
                    ServerConnectionState(ServerStatus.CONNECTING)))
                continue
            }

            // --- Auth failed permanently ---
            if (intent.isPermanentlyFailed) {
                actions.add(ReconcileAction.UpdateState(server.id,
                    ServerConnectionState(
                        status = ServerStatus.AUTH_FAILED,
                        errorMessage = intent.lastError ?: "Authentication failed"
                    )))
                continue
            }

            // --- No network ---
            if (!snapshot.isOnline) {
                actions.add(ReconcileAction.UpdateState(server.id,
                    ServerConnectionState(ServerStatus.NO_NETWORK)))
                continue
            }

            // --- Tunneled child: check parent status ---
            // A server with parentId always reaches its host through a
            // ProxyJump tunnel built from the parent's authenticated SSH
            // transport. The parent must be AUTHENTICATED before we can
            // open the direct-tcpip channel SSHJ wraps as our socket.
            if (server.parentId != null) {
                val parentServer = serverMap[server.parentId]

                // Orphaned child: parent has been deleted
                if (parentServer == null) {
                    actions.add(ReconcileAction.MarkOrphanedChild(server.id))
                    continue
                }

                val parentConnState = snapshot.connectionStates[server.parentId]
                val parentIntent = snapshot.intents[server.parentId]

                // Parent auth failed permanently — propagate to child
                if (parentIntent?.isPermanentlyFailed == true) {
                    actions.add(ReconcileAction.UpdateState(server.id,
                        ServerConnectionState(
                            status = ServerStatus.PARENT_FAILED,
                            errorMessage = "Parent server authentication failed",
                            parentInfo = ParentInfo(
                                parentId = parentServer.id,
                                parentName = parentServer.displayName,
                                parentStatus = ServerStatus.AUTH_FAILED
                            )
                        )))
                    continue
                }

                // Parent not connected yet — wait
                if (parentConnState != ConnectionState.AUTHENTICATED) {
                    val parentStatus = when {
                        parentIntent?.isPermanentlyFailed == true -> ServerStatus.AUTH_FAILED
                        !parentServer.isEnabled -> ServerStatus.PAUSED
                        parentConnState == ConnectionState.CONNECTING -> ServerStatus.CONNECTING
                        parentConnState == ConnectionState.ERROR -> ServerStatus.NETWORK_ERROR
                        else -> ServerStatus.CONNECTING
                    }
                    actions.add(ReconcileAction.UpdateState(server.id,
                        ServerConnectionState(
                            status = ServerStatus.WAITING_PARENT,
                            errorMessage = "Waiting for parent server",
                            parentInfo = ParentInfo(
                                parentId = parentServer.id,
                                parentName = parentServer.displayName,
                                parentStatus = parentStatus
                            )
                        )))
                    continue
                }
            }

            // --- Background host key check ---
            if (!snapshot.isForeground) {
                val knownHosts = snapshot.knownHostsCache[server.id] ?: emptyList()
                if (knownHosts.isEmpty()) {
                    actions.add(ReconcileAction.UpdateState(server.id,
                        ServerConnectionState(
                            status = ServerStatus.WAITING_HOST_KEY,
                            errorMessage = "Waiting for host key verification"
                        )))
                    continue
                }
            }

            // --- Backoff not elapsed ---
            if (intent.nextRetryAt > 0 && snapshot.now < intent.nextRetryAt) {
                actions.add(ReconcileAction.UpdateState(server.id,
                    ServerConnectionState(
                        status = ServerStatus.NETWORK_ERROR,
                        retryCount = intent.retryCount,
                        nextRetryAt = intent.nextRetryAt,
                        errorMessage = intent.lastError
                    )))
                continue
            }

            // --- Connect job already running ---
            if (intent.hasActiveConnectJob) {
                actions.add(ReconcileAction.UpdateState(server.id,
                    ServerConnectionState(ServerStatus.CONNECTING)))
                continue
            }

            // --- Attempt connection ---
            actions.add(ReconcileAction.UpdateState(server.id,
                ServerConnectionState(ServerStatus.CONNECTING)))
            actions.add(ReconcileAction.Connect(server.id, server))
        }

        return actions
    }

    // --- Phase 3: Execute side effects ---

    private suspend fun executeActions(
        actions: List<ReconcileAction>,
        snapshot: ReconcileSnapshot
    ) {
        val hasConnectActions = actions.any { it is ReconcileAction.Connect }
        if (hasConnectActions) {
            try { serviceController.ensureServiceRunning() } catch (e: Exception) {
                AppLogger.w(Category.SVC) {
                    "supervisor.ensureServiceRunning ✗ cause='${e.message}' — connect actions will proceed without FGS"
                }
            }
        }

        for (action in actions) {
            when (action) {
                is ReconcileAction.UpdateState -> {
                    updateServerState(action.serverId, action.state)
                }

                is ReconcileAction.Connect -> {
                    executeConnect(action.serverId, action.server)
                }

                is ReconcileAction.Disconnect -> {
                    executeDisconnect(action.serverId)
                }

                is ReconcileAction.ScheduleRetry -> {
                    scope.launch {
                        delay(action.delayMs + 500)
                        triggerReconciliation()
                    }
                }

                is ReconcileAction.ResetChildBackoffs -> {
                    mutex.withLock {
                        val serverMap = snapshot.servers.associateBy { it.id }
                        for ((childId, childIntent) in intents) {
                            val childServer = serverMap[childId]
                            if (childServer?.parentId == action.parentId) {
                                childIntent.retryCount = 0
                                childIntent.nextRetryAt = 0L
                                childIntent.isPermanentlyFailed = false
                                AppLogger.d(Category.SSH) { "ConnectionSupervisor: parent ${action.parentId} recovered, resetting child $childId" }
                            }
                        }
                    }
                }

                is ReconcileAction.MarkOrphanedChild -> {
                    executeDisconnect(action.serverId)
                    updateServerState(action.serverId, ServerConnectionState(
                        status = ServerStatus.PARENT_FAILED,
                        errorMessage = "Parent server has been deleted"
                    ))
                    mutex.withLock {
                        intents[action.serverId]?.isPermanentlyFailed = true
                    }
                }

                is ReconcileAction.RemoveState -> {
                    _serverStates.update { it - action.serverId }
                    mutex.withLock {
                        intents.remove(action.serverId)
                        observerJobs.remove(action.serverId)?.cancel()
                    }
                }

                is ReconcileAction.CascadeDisconnect -> {
                    val serverMap = snapshot.servers.associateBy { it.id }
                    // Walk the descendant tree transitively — for chains like
                    // A -> B -> C, dropping A must cascade to BOTH B and C, not
                    // just B. Direct-children-only would leave C in a zombie
                    // CONNECTED state because its tunnel was actually built
                    // through B which itself was built through A.
                    //
                    // Cycle protection: a malformed servers.yaml with a
                    // parent cycle (A parent=B, B parent=A) would send this
                    // BFS into an infinite loop inside the supervisor mutex,
                    // hanging every subsequent reconcile and growing
                    // `descendants` / `queue` until OOM. The YAML file is
                    // user-editable through Settings → Edit Config (YAML),
                    // so reachable in practice. Track visited ids.
                    val descendants = mutableListOf<Long>()
                    val visited = mutableSetOf(action.parentId)
                    val queue = ArrayDeque(listOf(action.parentId))
                    while (queue.isNotEmpty()) {
                        val pid = queue.removeFirst()
                        val kids = snapshot.servers
                            .filter { it.parentId == pid }
                            .map { it.id }
                            .filter { visited.add(it) } // skip already-seen → cycle break
                        descendants.addAll(kids)
                        queue.addAll(kids)
                    }
                    val parentServer = serverMap[action.parentId]
                    for (childId in descendants) {
                        val childConn = pool.get(childId)
                        if (childConn != null) {
                            try { childConn.disconnect() } catch (_: Exception) {}
                            pool.remove(childId)
                        }
                        mutex.withLock {
                            observerJobs.remove(childId)?.cancel()
                        }
                        updateServerState(childId, ServerConnectionState(
                            status = ServerStatus.WAITING_PARENT,
                            errorMessage = "Parent connection lost",
                            parentInfo = parentServer?.let {
                                ParentInfo(
                                    parentId = it.id,
                                    parentName = it.displayName,
                                    parentStatus = ServerStatus.NETWORK_ERROR
                                )
                            }
                        ))
                    }
                }
            }
        }

        // Check if all servers are disconnected/paused — stop service
        val allInactive = _serverStates.value.values.all {
            it.status == ServerStatus.PAUSED ||
                it.status == ServerStatus.DISCONNECTED ||
                it.status == ServerStatus.IDLE
        } && _serverStates.value.isNotEmpty()

        if (allInactive && !hasConnectActions) {
            try { serviceController.stopService() } catch (e: Exception) {
                AppLogger.w(Category.SVC) {
                    "supervisor.stopService ✗ cause='${e.message}' — FGS may linger until next reconcile"
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Connection execution
    // -----------------------------------------------------------------------

    private suspend fun executeConnect(serverId: Long, server: ServerEntity) {
        // Refuse to start new connections after shutdown was requested.
        if (shutdown) return

        mutex.withLock {
            val intent = intents.getOrPut(serverId) { ServerIntent(serverId) }

            // Don't start a new connect if one is already running
            if (intent.connectJob?.isActive == true) return

            intent.connectJob = scope.launch {
                var pendingConnection: SshConnection? = null
                try {
                    AppLogger.d(Category.SSH) { "ConnectionSupervisor: connecting server $serverId (${server.displayName})" }

                    val connection = createConnection(serverId, server)
                    pendingConnection = connection

                    // Acquire per-server mutex for connect/disconnect serialization.
                    // Everything from "disconnect existing" through "publish CONNECTED state"
                    // happens inside the lock so cancellation cannot interleave and leave
                    // the server with a live pool entry but a stale CONNECTING UI state.
                    val serverMutex = perServerMutex.getOrPut(serverId) { Mutex() }
                    serverMutex.withLock {
                        // Disconnect existing connection if any
                        pool.get(serverId)?.let { existing ->
                            try { existing.disconnect() } catch (_: Exception) {}
                        }

                        connection.connect()
                        pendingConnection = null  // pool now owns it

                        // Store in pool
                        pool.put(serverId, connection)

                        // Observe transport death (only ERROR/DISCONNECTED trigger reconciliation)
                        observeConnectionState(serverId, connection)

                        // Direct state update — does NOT trigger reconcile (breaks feedback loop).
                        // Done inside the same serverMutex.withLock as pool.put so it cannot
                        // be skipped by cancellation between the two lock blocks.
                        mutex.withLock {
                            val i = intents[serverId]
                            if (i != null) {
                                i.retryCount = 0
                                i.nextRetryAt = 0L
                                i.lastError = null
                                i.isPermanentlyFailed = false
                                i.previousConnectionState = ConnectionState.AUTHENTICATED
                                // The fresh connection was built from the
                                // server entity we just resolved, so any
                                // pending invalidation has been satisfied.
                                i.mustReconnect = false
                            }
                        }
                        updateServerState(serverId, ServerConnectionState(ServerStatus.CONNECTED))
                    }

                    AppLogger.i(Category.SSH) { "ConnectionSupervisor: connected server $serverId (${server.displayName})" }

                    // Trigger reconciliation so any child servers waiting on
                    // this parent can now attempt to connect. The observer
                    // path only fires on ERROR/DISCONNECTED, not on
                    // AUTHENTICATED, so without this nudge a child stays
                    // stuck in WAITING_PARENT forever after parent connects.
                    // No feedback loop: subsequent reconciliations see this
                    // server is already CONNECTED and skip the connect path.
                    triggerReconciliation()

                } catch (ce: kotlinx.coroutines.CancellationException) {
                    // Cancelled (likely by executeDisconnect). Clean up any partial
                    // connection we created but never published, then RETHROW so the
                    // coroutine machinery sees normal cancellation. Do NOT call
                    // handleConnectError — that would race with executeDisconnect's
                    // intent removal and clobber the just-set state.
                    pendingConnection?.let {
                        try { it.disconnect() } catch (_: Exception) {}
                    }
                    throw ce
                } catch (e: Exception) {
                    pendingConnection?.let {
                        try { it.disconnect() } catch (_: Exception) {}
                    }
                    handleConnectError(serverId, e)
                }
            }
        }
    }

    /**
     * Create the SSH connection for [server].
     *
     * Always builds a [DirectSshConnection]. If the server has a `parentId`,
     * the parent's authenticated SSHJ transport supplies a direct-tcpip
     * channel via [DirectSshConnection.createTunnel] — SSHJ wraps that
     * channel as the underlying socket through `connectVia(tunnel)`. This
     * is native ProxyJump: the child still owns its own SSHClient, picks
     * up keepalive, host-key verification, multiplexing, and channel-level
     * port forwarding without any of the OpenSSH-CLI shell-out fakery the
     * old `ForwardedSshConnection` used.
     */
    private suspend fun createConnection(
        serverId: Long,
        server: ServerEntity
    ): SshConnection {
        val authConfig = serverRepository.resolveAuthConfig(server)
        val globalDefaults = loadGlobalDefaults()
        val config = SshConfigBuilder.build(server, authConfig, globalDefaults)

        val tunnelProvider: (suspend () -> net.schmizz.sshj.connection.channel.direct.DirectConnection)? =
            if (server.parentId != null) {
                val parentConn = pool.get(server.parentId) as? DirectSshConnection
                    ?: throw SshException("Parent connection not available for server $serverId")
                suspend { parentConn.createTunnel(server.hostname, server.port) }
            } else {
                null
            }

        return DirectSshConnection(
            serverId = serverId,
            config = config,
            hostKeyVerifier = createHostKeyVerifier(server),
            tunnelProvider = tunnelProvider,
            parentScope = scope
        )
    }

    /**
     * Create an SSHJ HostKeyVerifier for the given server.
     */
    private fun createHostKeyVerifier(server: ServerEntity): net.schmizz.sshj.transport.verification.HostKeyVerifier {
        val promptCb = hostKeyPromptCallback
            ?: throw SshException("Host key prompt callback not set")
        return AppHostKeyVerifier(
            knownHostDao = knownHostDao,
            hostname = server.hostname,
            port = server.port,
            promptCallback = promptCb
        )
    }

    /**
     * Observe a connection's state flow. Only trigger reconciliation on
     * ERROR or DISCONNECTED transitions (NOT on AUTHENTICATED — that would
     * create a feedback loop).
     *
     * Must be called from a coroutine context (e.g., within a connect job).
     */
    private suspend fun observeConnectionState(serverId: Long, connection: SshConnection) {
        // Cancel any old observer, launch the new one, and register it ALL inside
        // the same mutex.withLock. This prevents a race where executeDisconnect
        // could run between launch and registration, causing the new observer to
        // be orphaned (never cancelled by future disconnects).
        mutex.withLock {
            observerJobs.remove(serverId)?.cancel()
            val job = scope.launch {
                try {
                    connection.state.collect { state ->
                        try {
                            if (state == ConnectionState.ERROR || state == ConnectionState.DISCONNECTED) {
                                AppLogger.d(Category.SSH) { "ConnectionSupervisor: transport death detected for server $serverId (state=$state)" }
                                triggerReconciliation()
                            }
                        } catch (ce: kotlinx.coroutines.CancellationException) {
                            throw ce
                        } catch (e: Exception) {
                            AppLogger.w(Category.SSH) { "state handler for server $serverId failed: ${e.message}" }
                        }
                    }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // normal cancellation — old observer replaced
                } catch (e: Exception) {
                    AppLogger.w(Category.SSH) { "state collect for server $serverId died: ${e.message}" }
                }
            }
            observerJobs[serverId] = job
        }
    }

    private suspend fun handleConnectError(serverId: Long, e: Exception) {
        mutex.withLock {
            val intent = intents[serverId] ?: return@withLock
            if (isAuthError(e) || isCircularChainError(e)) {
                intent.isPermanentlyFailed = true
                intent.lastError = e.message
                AppLogger.w(Category.SSH) {
                    "supervisor.permanent_fail server=$serverId state=AUTH_FAILED cause='${e.message}' (no more retries)"
                }
                updateServerState(serverId, ServerConnectionState(
                    status = ServerStatus.AUTH_FAILED,
                    errorMessage = e.message
                ))
            } else {
                intent.retryCount++
                val backoffMs = computeBackoff(intent.retryCount, reconnectIntervalSeconds)
                intent.nextRetryAt = SystemClock.elapsedRealtime() + backoffMs
                intent.lastError = e.message
                updateServerState(serverId, ServerConnectionState(
                    status = ServerStatus.NETWORK_ERROR,
                    retryCount = intent.retryCount,
                    nextRetryAt = intent.nextRetryAt,
                    errorMessage = e.message
                ))
                AppLogger.d(Category.SSH) { "ConnectionSupervisor: server $serverId failed (attempt ${intent.retryCount}), retry in ${backoffMs / 1000}s: ${e.message}" }
                // Schedule reconciliation after backoff expires
                scope.launch {
                    delay(backoffMs + 500)
                    triggerReconciliation()
                }
            }
        }
        // Trigger reconciliation after error handling
        triggerReconciliation()
    }

    private suspend fun executeDisconnect(serverId: Long) {
        // Cancel any in-flight connect job FIRST (outside per-server mutex to avoid
        // a deadlock if the job is currently waiting on the same mutex). Snapshot
        // the job under the global mutex, then await it with a timeout.
        val pendingConnect = mutex.withLock { intents[serverId]?.connectJob }
        if (pendingConnect != null && pendingConnect.isActive) {
            pendingConnect.cancel()
            withTimeoutOrNull(2_000) { pendingConnect.join() }
        }

        val serverMutex = perServerMutex.getOrPut(serverId) { Mutex() }
        serverMutex.withLock {
            val conn = pool.remove(serverId)
            if (conn != null) {
                try { conn.disconnect() } catch (_: Exception) {}
            }
            mutex.withLock {
                observerJobs.remove(serverId)?.cancel()
                intents.remove(serverId)
            }
            // Note: perServerMutex entries are NEVER removed here. Removing while
            // a concurrent connect on the same serverId held the OLD mutex would
            // let a subsequent getOrPut create a NEW Mutex, defeating mutual
            // exclusion. The memory cost of leaving stale entries is negligible.
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun updateServerState(serverId: Long, state: ServerConnectionState) {
        _serverStates.update { current ->
            current.toMutableMap().apply { this[serverId] = state }
        }
    }

    private fun loadGlobalDefaults(): SshGlobalDefaults = SshGlobalDefaults.from(preferences)

    /**
     * Sort servers by dependency depth: parents first, then children.
     * Uses iterative depth computation (NOT recursive).
     */
    private fun sortByDepth(servers: List<ServerEntity>): List<ServerEntity> {
        val serverMap = servers.associateBy { it.id }
        val depths = mutableMapOf<Long, Int>()

        fun computeDepth(server: ServerEntity): Int {
            depths[server.id]?.let { return it }
            val parentId = server.parentId
            val depth = if (parentId == null || serverMap[parentId] == null) {
                0
            } else {
                // Iterative walk to avoid stack overflow on deep chains.
                // Capture the parent into a local each iteration so
                // Kotlin can smart-cast it to non-null without relying
                // on `!!` — the previous `serverMap[current.parentId!!]!!`
                // was correct (the while condition checked both), but
                // brittle to future edits.
                var current = server
                var d = 0
                val visited = mutableSetOf<Long>()
                while (true) {
                    if (current.id in visited) break // circular protection
                    val pid = current.parentId ?: break
                    val next = serverMap[pid] ?: break
                    visited.add(current.id)
                    d++
                    current = next
                }
                d
            }
            depths[server.id] = depth
            return depth
        }

        servers.forEach { computeDepth(it) }
        return servers.sortedBy { depths[it.id] ?: 0 }
    }

    // -----------------------------------------------------------------------
    // Backoff and error classification (static/companion-accessible)
    // -----------------------------------------------------------------------

    companion object {
        @Volatile
        private var INSTANCE: ConnectionSupervisor? = null

        fun getInstance(): ConnectionSupervisor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConnectionSupervisor().also { INSTANCE = it }
            }
        }

        /**
         * Computes exponential backoff capped at reconnectIntervalSeconds.
         * Sequence: 2s, 4s, 8s, 16s, 30s, 60s, 60s, ...
         *
         * This is a pure function suitable for use in [computeActions] tests.
         */
        fun computeBackoff(retryCount: Int, reconnectIntervalSeconds: Int = 60): Long {
            val capMs = reconnectIntervalSeconds * 1000L
            return min(2000L * (1L shl min(retryCount, 10)), capMs)
        }

        fun isAuthError(e: Throwable): Boolean {
            if (e is UserAuthException) return true
            val cause = e.cause
            if (cause is UserAuthException) return true
            if (e is SshException && cause is UserAuthException) return true
            val msg = e.message ?: ""
            return msg.contains("Authentication failed", ignoreCase = true) ||
                msg.contains("Exhausted available authentication", ignoreCase = true)
        }

        fun isCircularChainError(e: Throwable): Boolean {
            val msg = e.message ?: ""
            return msg.contains("Circular jump host chain", ignoreCase = true) ||
                (msg.contains("Circular", ignoreCase = true) && msg.contains("chain", ignoreCase = true))
        }
    }
}

