package com.tmuxes.ui.viewmodel

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tmuxes.TmuxesApp
import com.tmuxes.data.model.ServerEntity
import com.tmuxes.data.settings.Settings
import com.tmuxes.data.settings.tmuxPrefixByteFor
import com.tmuxes.i18n.I18nRuntime
import com.tmuxes.session.AttachRequest
import com.tmuxes.session.ConsumerId
import com.tmuxes.session.ConsumerType
import com.tmuxes.session.ManagedSession
import com.tmuxes.session.SessionCoordinator
import com.tmuxes.session.SessionKey
import com.tmuxes.session.SessionState
import com.tmuxes.ssh.ServerConnectionState
import com.tmuxes.ssh.ServerStatus
import com.tmuxes.ssh.withConnection
import com.tmuxes.ssh.withConnectionWaiting
import com.tmuxes.terminal.emulator.TerminalListener
import com.tmuxes.tmux.TmuxManager
import com.tmuxes.tmux.TmuxSession
import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import com.tmuxes.util.safeLaunch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TmuxesApp
    private val serverRepository = app.serverRepository
    private val connectionPool = app.connectionPool
    private val connectionSupervisor = app.connectionSupervisor
    private val connectionTrigger = app.connectionTrigger
    private val preferences = app.preferences
    private val coordinator: SessionCoordinator get() = app.sessionCoordinator

    /** ConsumerId used by the in-app terminal — single instance shared by all UI tabs. */
    private val appConsumer = ConsumerId(ConsumerType.APP_TERMINAL, "app")

    /** Encode a [SessionKey] as the canonical `"$serverId:$sessionName"` string used in favorite-session sets and other persisted maps. */
    private fun toStringKey(key: SessionKey): String = "${key.serverId}:${key.sessionName}"

    /** Decode a `"$serverId:$sessionName"` string into a [SessionKey] rooted at the in-app ("app") qualifier. */
    private fun toAppSessionKey(stringKey: String): SessionKey? {
        val parsed = parseSessionKey(stringKey) ?: return null
        return SessionKey(parsed.first, parsed.second, "app")
    }

    // -----------------------------------------------------------------------
    // Connection states (exposed for UI, direct from supervisor)
    // -----------------------------------------------------------------------

    /**
     * Per-server connection state — the supervisor's [ServerConnectionState]
     * with full status (CONNECTED / CONNECTING / AUTH_FAILED / NETWORK_ERROR /
     * NO_NETWORK / PAUSED / WAITING_PARENT / WAITING_HOST_KEY / PARENT_FAILED /
     * IDLE / DISCONNECTED), `errorMessage`, retry counts and parent info.
     *
     * UI consumers read this directly. There is no longer a collapsed-to-4-buckets
     * `Map<Long, ConnectionState>` projection — that lost information about
     * what kind of failure had occurred and forced UI screens to display
     * a context-free "Error" label.
     */
    val serverStates: StateFlow<Map<Long, ServerConnectionState>> =
        connectionSupervisor.serverStates

    // -----------------------------------------------------------------------
    // Server list (for connection status display)
    // -----------------------------------------------------------------------

    val servers: StateFlow<List<ServerEntity>> = serverRepository.allServers
        .catch { e ->
            AppLogger.w(Category.SSH) { "SessionVM servers flow error (emitting empty): ${e.message}" }
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // -----------------------------------------------------------------------
    // Per-server refresh state
    // -----------------------------------------------------------------------

    sealed class ServerRefreshState {
        data object Idle : ServerRefreshState()
        data object Loading : ServerRefreshState()
        data class Error(val message: String) : ServerRefreshState()
    }

    private val _serverRefreshStates = MutableStateFlow<Map<Long, ServerRefreshState>>(emptyMap())
    val serverRefreshStates: StateFlow<Map<Long, ServerRefreshState>> = _serverRefreshStates.asStateFlow()

    /**
     * `true` whenever ANY server is mid-refresh — drives the global spinner
     * on the Sessions tab top bar so the user sees that the refresh button
     * actually did something while the supervisor reconciles.
     */
    val isAnyRefreshing: StateFlow<Boolean> = _serverRefreshStates
        .map { states -> states.values.any { it is ServerRefreshState.Loading } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Per-server cached session results — refreshing one server doesn't clear others
    private val _serverSessions = MutableStateFlow<Map<Long, List<TmuxSession>>>(emptyMap())

    // Flat projection of all sessions across servers
    val sessions: StateFlow<List<TmuxSession>> = _serverSessions
        .map { serverMap -> serverMap.values.flatten() }
        .catch { e ->
            AppLogger.w(Category.SSH) { "SessionVM sessions flow error: ${e.message}" }
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // -----------------------------------------------------------------------
    // Active UI sessions (subset of coordinator, tracked by this ViewModel)
    // -----------------------------------------------------------------------

    private val _uiSessionKeys = MutableStateFlow<Set<String>>(emptySet())

    val activeSessions: StateFlow<Map<String, ManagedSession>> = combine(
        coordinator.sessions, _uiSessionKeys
    ) { all, uiKeys ->
        all.asSequence()
            .filter { (key, _) -> key.qualifier == "app" }
            .map { (key, session) -> toStringKey(key) to session }
            .filter { (stringKey, _) -> stringKey in uiKeys }
            .toMap()
    }
        .catch { e ->
            AppLogger.w(Category.SSH) { "SessionVM activeSessions flow error: ${e.message}" }
            emit(emptyMap())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _activeSessionKey = MutableStateFlow<String?>(null)
    val activeSessionKey: StateFlow<String?> = _activeSessionKey.asStateFlow()

    // tmux copy-mode (a.k.a. edit / view-history mode) is purely server-side
    // state — the SSH client cannot detect it. We track app-side per session
    // and accept that user-driven prefix-key entry/exit can drift the bool.
    // One extra FAB tap resyncs (ESC to a non-copy-mode session is a no-op;
    // prefix `[` to a copy-mode session is a no-op).
    private val _copyModeSessions = MutableStateFlow<Set<SessionKey>>(emptySet())
    val copyModeSessions: StateFlow<Set<SessionKey>> = _copyModeSessions.asStateFlow()

    // -----------------------------------------------------------------------
    // Session cache management: clear on disconnect, auto-refresh on connect
    // -----------------------------------------------------------------------

    init {
        viewModelScope.safeLaunch(tag = "SessionVM.stateCollector") {
            // Track the set of previously-CONNECTED server IDs AND the
            // SshConnection instance identity each was connected with,
            // so we can detect two kinds of transition:
            //
            //   1. non-CONNECTED → CONNECTED (first connect or revival
            //      after a visible drop)
            //   2. CONNECTED → CONNECTED with a DIFFERENT SshConnection
            //      instance, which drops and re-establishes the
            //      connection but may be squashed by StateFlow if the
            //      observer was suspended on a slow fetch.
            //
            // Without tracking the connection instance, reconnects that
            // fit inside a slow observer cycle would leave the sessions
            // list showing stale content from the old remote.
            var previouslyConnectedIds: Set<Long> = emptySet()
            var previousConnections: Map<Long, com.tmuxes.ssh.SshConnection> = emptyMap()
            try {
                serverStates.collect { states ->
                    try {
                        // 1) Purge cached session lists for servers that left CONNECTED.
                        _serverSessions.update { current ->
                            val disconnected = current.keys.filter { serverId ->
                                states[serverId]?.status != ServerStatus.CONNECTED
                            }
                            if (disconnected.isEmpty()) current
                            else current - disconnected.toSet()
                        }

                        // 2) Detect servers that just became CONNECTED and refresh.
                        //    refreshServerSuspend immediately flips the per-server
                        //    state to Loading, so any stale Error from a prior failed
                        //    refresh is cleared without extra bookkeeping.
                        val currentlyConnectedIds = states
                            .filterValues { it.status == ServerStatus.CONNECTED }
                            .keys
                        val currentConnections = currentlyConnectedIds
                            .mapNotNull { id -> connectionPool.get(id)?.let { id to it } }
                            .toMap()

                        // "Newly connected" now means either: never
                        // seen before, OR the pool's SshConnection
                        // instance is different from what we saw last
                        // time (a reconnect swapped the object).
                        val newlyConnected = currentlyConnectedIds.filter { id ->
                            id !in previouslyConnectedIds ||
                                previousConnections[id] !== currentConnections[id]
                        }
                        previouslyConnectedIds = currentlyConnectedIds
                        previousConnections = currentConnections

                        for (serverId in newlyConnected) {
                            // Fire-and-forget; failures are stored in _serverRefreshStates.
                            safeLaunch(tag = "SessionVM.autoRefresh") { refreshServerSuspend(serverId) }
                        }
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        AppLogger.w(Category.SSH) { "SessionVM state handler failed: ${e.message}" }
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // ViewModel cleared — normal shutdown
            } catch (e: Exception) {
                AppLogger.w(Category.SSH) { "SessionVM state collector failed: ${e.message}" }
            }
        }

        // Reactive cleanup of copy-mode flags: when a session disappears
        // from activeSessions (disconnect, kill, server removed), drop its
        // entry. The boolean is meaningless once the PTY is gone.
        viewModelScope.safeLaunch(tag = "SessionVM.copyModeCleanup") {
            activeSessions.collect { active ->
                val activeKeys = active.keys.mapNotNull { toAppSessionKey(it) }.toSet()
                _copyModeSessions.value = _copyModeSessions.value.intersect(activeKeys)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Favorites
    // -----------------------------------------------------------------------

    val favoriteSessions: StateFlow<Set<String>> =
        preferences.flow(com.tmuxes.data.settings.Settings.favoriteSessions)
            .catch { e ->
                AppLogger.w(Category.SSH) { "SessionVM favoriteSessions flow error: ${e.message}" }
                emit(emptySet())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val sessionColors: StateFlow<Map<String, Int>> =
        preferences.sessionColorsFlow()
            .catch { e ->
                AppLogger.w(Category.SSH) { "SessionVM sessionColors flow error: ${e.message}" }
                emit(emptyMap())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // -----------------------------------------------------------------------
    // UI state
    // -----------------------------------------------------------------------

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _terminalTitle = MutableStateFlow<String?>(null)
    val terminalTitle: StateFlow<String?> = _terminalTitle.asStateFlow()

    private val _bellRingCount = MutableStateFlow(0L)
    val bellRingCount: StateFlow<Long> = _bellRingCount.asStateFlow()

    private val _commandHistoryRefreshing = MutableStateFlow<Map<String, Int>>(emptyMap())
    val commandHistoryRefreshing: StateFlow<Map<String, Int>> = _commandHistoryRefreshing.asStateFlow()

    private val shellHistory = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val commandHistory: StateFlow<Map<String, List<String>>> = shellHistory.asStateFlow()

    // -----------------------------------------------------------------------
    // Terminal sizing
    // -----------------------------------------------------------------------

    private var initialRows: Int = 24
    private var initialCols: Int = 80

    fun setInitialTerminalSize(cols: Int, rows: Int) {
        initialCols = cols.coerceAtLeast(1)
        initialRows = rows.coerceAtLeast(1)
    }

    fun getCommandHistory(serverId: Long, sessionName: String): List<String> {
        return shellHistory.value[sessionKey(serverId, sessionName)].orEmpty()
    }

    suspend fun refreshCommandHistory(serverId: Long, sessionName: String): List<String> {
        val connection = connectionPool.get(serverId)
            ?: return emptyList()
        val key = sessionKey(serverId, sessionName)
        setCommandHistoryRefreshing(key, true)
        return try {
            val history = try {
                TmuxManager.readShellHistory(connection, sessionName)
                    .asReversed()
                    .distinct()
                    .asReversed()
            } catch (e: Exception) {
                AppLogger.w(Category.TMUX) { "refreshCommandHistory: read failed (${e.message})" }
                return emptyList()
            }
            val currentHistory = shellHistory.value[key]
            if (currentHistory != history) {
                shellHistory.value = shellHistory.value.toMutableMap().apply {
                    put(key, history)
                }
            }
            history
        } finally {
            setCommandHistoryRefreshing(key, false)
        }
    }

    companion object {
        private const val REFRESH_SETTLE_TIMEOUT_MS = 1_500L
        private const val REFRESH_CONNECT_TIMEOUT_MS = 10_000L

        fun sessionKey(serverId: Long, sessionName: String) = "$serverId:$sessionName"

        fun parseSessionKey(key: String): Pair<Long, String>? {
            val parts = key.split(":", limit = 2)
            val serverId = parts.firstOrNull()?.toLongOrNull() ?: return null
            val sessionName = parts.getOrNull(1) ?: return null
            return serverId to sessionName
        }
    }

    private fun setCommandHistoryRefreshing(key: String, refreshing: Boolean) {
        // Refcount instead of boolean: if two concurrent refreshes are
        // running for the same session (e.g., the user opens the quick
        // switcher twice in rapid succession), the boolean version
        // would have the first finalizer reset the indicator to "not
        // refreshing" while the second refresh was still in flight,
        // making the spinner stop early. Using a count keeps the
        // spinner visible until ALL in-flight refreshes finish.
        _commandHistoryRefreshing.value = _commandHistoryRefreshing.value.toMutableMap().apply {
            if (refreshing) {
                put(key, (get(key) ?: 0) + 1)
            } else {
                val next = (get(key) ?: 0) - 1
                if (next <= 0) remove(key) else put(key, next)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Refresh sessions — per-server, single-attempt, hierarchy-aware
    // -----------------------------------------------------------------------

    /**
     * User-initiated refresh for one server. Resets the supervisor's
     * retry/backoff state for this server (treats the tap as "I want you
     * to try NOW, ignore your backoff timer") and either lists tmux
     * sessions immediately if already connected, or waits briefly for the
     * supervisor to enter a real connection attempt before resolving to an
     * actionable result.
     */
    fun refreshServer(serverId: Long) {
        AppLogger.i(Category.SESSION) { "vm.refreshServer id=$serverId" }
        viewModelScope.safeLaunch(tag = "SessionVM") {
            try { connectionTrigger.userRefresh(serverId) } catch (t: Throwable) {
                AppLogger.w(Category.SESSION) {
                    "vm.refreshServer trigger ✗ id=$serverId cause='${t.message}' — falling through to refreshServerSuspend"
                }
            }
            refreshServerSuspend(serverId)
        }
    }

    /**
     * User-initiated refresh for every enabled server.
     *
     * Resets the supervisor's retry/backoff for ALL servers (so a top-bar
     * refresh tap during NETWORK_ERROR backoff means "try all of them
     * NOW") and dispatches a per-server refresh in parallel — each on
     * viewModelScope — so a slow or failing server doesn't block the
     * others.
     */
    fun refreshAllServers() {
        AppLogger.i(Category.SESSION) { "vm.refreshAllServers" }
        viewModelScope.safeLaunch(tag = "SessionVM.refreshAll") {
            try { connectionTrigger.userRefresh() } catch (t: Throwable) {
                AppLogger.w(Category.SESSION) {
                    "vm.refreshAllServers trigger ✗ cause='${t.message}' — proceeding with per-server fanout anyway"
                }
            }
            val enabledServers = try {
                serverRepository.allServers.first().filter { it.isEnabled }
            } catch (_: Throwable) {
                emptyList()
            }
            AppLogger.d(Category.SESSION) { "vm.refreshAllServers fanOut count=${enabledServers.size}" }
            for (server in enabledServers) {
                refreshServer(server.id)
            }
        }
    }

    /**
     * Suspend version: lists tmux sessions for [serverId], driving the
     * per-server [ServerRefreshState] machine.
     *
     * - **Fast path**: connection already live → list sessions and
     *   write Idle.
     * - **Slow path**: not yet connected → give the async supervisor
     *   trigger a short settle window, then wait only while the server is
     *   actively connecting. If it remains IDLE/DISCONNECTED, resolve
     *   quickly so the refresh spinner cannot look stuck.
     *
     * Atomically guards against duplicate concurrent refresh for the
     * same server via a Loading-state sentinel.
     */
    private suspend fun refreshServerSuspend(serverId: Long) {
        var alreadyLoading = false
        _serverRefreshStates.update { current ->
            if (current[serverId] is ServerRefreshState.Loading) {
                alreadyLoading = true
                current
            } else {
                current + (serverId to ServerRefreshState.Loading)
            }
        }
        if (alreadyLoading) return
        try {
            val server = serverRepository.getById(serverId)
                ?: throw IllegalStateException("Server $serverId not found")

            // Fast path: connection already live — list sessions now.
            val existing = connectionPool.get(serverId)
            if (existing != null && existing.isConnected()) {
                val sessions = TmuxManager.listSessions(existing, serverId, server.displayName, server.color)
                _serverSessions.update { it + (serverId to sessions) }
                _serverRefreshStates.update { it + (serverId to ServerRefreshState.Idle) }
                return
            }

            awaitRefreshOutcome(serverId)

            val finalConn = connectionPool.get(serverId)
            if (finalConn != null && finalConn.isConnected()) {
                val sessions = TmuxManager.listSessions(finalConn, serverId, server.displayName, server.color)
                _serverSessions.update { it + (serverId to sessions) }
                _serverRefreshStates.update { it + (serverId to ServerRefreshState.Idle) }
            } else {
                val state = serverStates.value[serverId]
                val message = state?.errorMessage ?: when (state?.status) {
                    ServerStatus.AUTH_FAILED -> "Authentication failed"
                    ServerStatus.NETWORK_ERROR -> "Network error"
                    ServerStatus.NO_NETWORK -> "Offline"
                    ServerStatus.PARENT_FAILED -> "Parent server failed"
                    ServerStatus.WAITING_PARENT -> "Waiting for parent server"
                    ServerStatus.WAITING_HOST_KEY -> "Waiting for host-key approval"
                    ServerStatus.PAUSED -> "Server is paused"
                    ServerStatus.CONNECTING -> "Connection still in progress"
                    else -> "Could not connect"
                }
                _serverRefreshStates.update {
                    it + (serverId to ServerRefreshState.Error(message))
                }
            }
        } catch (e: Exception) {
            _serverRefreshStates.update {
                it + (serverId to ServerRefreshState.Error(e.message ?: "Unknown error"))
            }
        }
    }

    private suspend fun awaitRefreshOutcome(serverId: Long): ServerStatus? {
        val firstProgress = kotlinx.coroutines.withTimeoutOrNull(REFRESH_SETTLE_TIMEOUT_MS) {
            serverStates
                .map { it[serverId]?.status }
                .first { status ->
                    status == ServerStatus.CONNECTED || status.isRefreshProgressStatus()
                }
        }
        if (firstProgress == ServerStatus.CONNECTED) return firstProgress

        val settledStatus = serverStates.value[serverId]?.status
        if (settledStatus == null ||
            settledStatus.isRefreshDisconnectedStatus() ||
            settledStatus.isRefreshResultStatus()
        ) {
            return settledStatus
        }

        return kotlinx.coroutines.withTimeoutOrNull(REFRESH_CONNECT_TIMEOUT_MS - REFRESH_SETTLE_TIMEOUT_MS) {
            serverStates
                .map { it[serverId]?.status }
                .first { status ->
                    status == ServerStatus.CONNECTED || status.isRefreshResultStatus()
                }
        } ?: serverStates.value[serverId]?.status
    }

    private fun ServerStatus?.isRefreshProgressStatus(): Boolean =
        this == ServerStatus.CONNECTING || this == ServerStatus.WAITING_PARENT

    private fun ServerStatus?.isRefreshDisconnectedStatus(): Boolean =
        this == ServerStatus.IDLE || this == ServerStatus.DISCONNECTED

    private fun ServerStatus?.isRefreshResultStatus(): Boolean =
        this == ServerStatus.AUTH_FAILED ||
            this == ServerStatus.NETWORK_ERROR ||
            this == ServerStatus.NO_NETWORK ||
            this == ServerStatus.PARENT_FAILED ||
            this == ServerStatus.PAUSED ||
            this == ServerStatus.WAITING_HOST_KEY

    fun clearServerRefreshError(serverId: Long) {
        _serverRefreshStates.update {
            if (it[serverId] is ServerRefreshState.Error) it + (serverId to ServerRefreshState.Idle)
            else it
        }
    }

    // -----------------------------------------------------------------------
    // Session management — delegates to SessionCoordinator
    // -----------------------------------------------------------------------

    fun attachSession(serverId: Long, sessionName: String) {
        AppLogger.i(Category.SESSION) { "vm.attachSession key=$serverId:$sessionName" }
        viewModelScope.safeLaunch(tag = "SessionVM") {
            try {
                attachSessionInternal(serverId, sessionName)
            } catch (e: Exception) {
                AppLogger.w(Category.SESSION) { "vm.attachSession ✗ key=$serverId:$sessionName cause='${e.message}'" }
                _errorMessage.value = I18nRuntime.t("Failed to attach session: {error}", "error" to e.message)
            }
        }
    }

    private suspend fun attachSessionInternal(serverId: Long, sessionName: String) {
        val stringKey = sessionKey(serverId, sessionName)
        val sessionKey = SessionKey(serverId, sessionName, "app")

        if (stringKey in _uiSessionKeys.value) {
            val existing = coordinator.sessions.value[sessionKey]
            if (existing != null && !existing.isEnded) {
                _activeSessionKey.value = stringKey
                return
            }
        }

        val bellEnabled = preferences.flow(com.tmuxes.data.settings.Settings.bellEnabled).first()
        val vibrationEnabled = preferences.flow(com.tmuxes.data.settings.Settings.vibrationEnabled).first()

        val listener = object : TerminalListener {
            override fun onBellRing() {
                _bellRingCount.value++
                if (bellEnabled) {
                    try {
                        val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 50)
                        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                        android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed({
                                // Runs on the main Looper — a throw from
                                // ToneGenerator.release() (rare but
                                // possible on some Android audio stacks)
                                // would otherwise crash the process.
                                try { toneGen.release() } catch (_: Throwable) {}
                            }, 150)
                    } catch (_: Exception) {}
                }
                if (vibrationEnabled) {
                    try {
                        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            app.getSystemService(VibratorManager::class.java)?.defaultVibrator
                        } else {
                            @Suppress("DEPRECATION")
                            app.getSystemService(Vibrator::class.java)
                        }
                        if (vibrator?.hasVibrator() == true) {
                            vibrator.vibrate(
                                VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                            )
                        }
                    } catch (_: Exception) {}
                }
            }

            override fun onTitleChanged(title: String) {
                _terminalTitle.value = title
            }

            override fun onDataOutput(data: ByteArray) {
                // Handled by the coordinator
            }
        }

        val server = serverRepository.getById(serverId)
            ?: throw IllegalStateException("Server $serverId not found")

        coordinator.attach(
            AttachRequest(
                serverId = serverId,
                sessionName = sessionName,
                serverName = server.displayName,
                serverColor = server.color,
                rows = initialRows,
                cols = initialCols,
                scrollbackLines = preferences.get(Settings.terminalScrollbackLines),
                consumer = appConsumer,
                terminalListener = listener
            )
        )

        _uiSessionKeys.value = _uiSessionKeys.value + stringKey
        _activeSessionKey.value = stringKey
        preferences.setLastSession(serverId, sessionName)
    }

    fun createSession(serverId: Long, sessionName: String) {
        AppLogger.i(Category.SESSION) { "vm.createSession key=$serverId:$sessionName (detached)" }
        viewModelScope.safeLaunch(tag = "SessionVM") {
            try {
                withConnectionWaiting(connectionPool, connectionTrigger, serverId) { connection ->
                    TmuxManager.createDetachedSession(connection, sessionName)
                }
            } catch (e: Exception) {
                AppLogger.w(Category.SESSION) { "vm.createSession ✗ key=$serverId:$sessionName cause='${e.message}'" }
                _errorMessage.value = I18nRuntime.t("Failed to create session: {error}", "error" to e.message)
                return@safeLaunch
            }
            // Post-commit: refresh the session list so the new entry
            // shows up. A failure here is a refresh error, not a
            // create error — the session was already created on the
            // remote tmux server.
            try { refreshServerSuspend(serverId) } catch (e: Exception) {
                AppLogger.w(Category.SSH) { "SessionVM: post-create refresh failed: ${e.message}" }
            }
        }
    }

    fun createAndAttachSession(serverId: Long, sessionName: String, onAttached: (String) -> Unit = {}) {
        viewModelScope.safeLaunch(tag = "SessionVM") {
            val resolvedName: String
            try {
                resolvedName = withConnectionWaiting(connectionPool, connectionTrigger, serverId) { connection ->
                    TmuxManager.createDetachedSession(connection, sessionName)
                }
                attachSessionInternal(serverId, resolvedName)
            } catch (e: Exception) {
                _errorMessage.value = I18nRuntime.t("Failed to create session: {error}", "error" to e.message)
                return@safeLaunch
            }
            // Post-commit: refresh + fire callback. Neither failure
            // should be reported as "Failed to create session" since
            // the create + attach already succeeded.
            try { refreshServerSuspend(serverId) } catch (e: Exception) {
                AppLogger.w(Category.SSH) { "SessionVM: post-create refresh failed: ${e.message}" }
            }
            try { onAttached(resolvedName) } catch (e: Exception) {
                AppLogger.w(Category.SSH) { "SessionVM: onAttached callback failed: ${e.message}" }
            }
        }
    }

    fun killSession(serverId: Long, sessionName: String) {
        AppLogger.i(Category.SESSION) { "vm.killSession key=$serverId:$sessionName" }
        viewModelScope.safeLaunch(tag = "SessionVM") {
            try {
                withConnectionWaiting(connectionPool, connectionTrigger, serverId) { connection ->
                    TmuxManager.killSession(connection, sessionName)
                }
                val key = sessionKey(serverId, sessionName)
                detachSession(key)
                // Close every coordinator entry that points at this
                // logical session — both the in-app "app" qualifier and
                // any "widget_*" qualifiers. Without this, widgets
                // attached to the killed session would keep stale
                // SessionCoordinator entries until the per-widget SSH
                // EOF reached us (~100ms later), during which a widget
                // refresh could try to write to a dead PTY.
                coordinator.closeAllForSession(serverId, sessionName)
                shellHistory.value = shellHistory.value.toMutableMap().apply { remove(key) }
            } catch (e: Exception) {
                AppLogger.w(Category.SESSION) { "vm.killSession ✗ key=$serverId:$sessionName cause='${e.message}'" }
                _errorMessage.value = I18nRuntime.t("Failed to kill session: {error}", "error" to e.message)
                return@safeLaunch
            }
            // Post-commit: refresh the list so the killed session
            // disappears. A failure here is a refresh error, not a
            // kill error — the session is already gone from tmux.
            try { refreshServerSuspend(serverId) } catch (e: Exception) {
                AppLogger.w(Category.SSH) { "SessionVM: post-kill refresh failed: ${e.message}" }
            }
        }
    }

    fun renameSession(serverId: Long, oldName: String, newName: String) {
        viewModelScope.safeLaunch(tag = "SessionVM") {
            val oldKey = sessionKey(serverId, oldName)
            val newKey = sessionKey(serverId, newName)
            // Track what we've committed so the catch block can roll
            // back correctly:
            //   - newKeyAddedSpeculatively: true once we've added
            //     newKey to _uiSessionKeys BEFORE the coordinator
            //     rename commits. If the coordinator rename then
            //     fails, we roll this back.
            //   - renameCommitted: true once coordinator.rename has
            //     successfully renamed the coordinator entry. After
            //     this point the rename is DONE — a later failure
            //     (history migration, favorite migration, refresh)
            //     must NOT roll back the _uiSessionKeys change, or
            //     we'd strand a session that actually exists under
            //     the new name.
            var newKeyAddedSpeculatively = false
            var renameCommitted = false
            try {
                withConnectionWaiting(connectionPool, connectionTrigger, serverId) { connection ->
                    TmuxManager.renameSession(connection, oldName, newName)
                }

                // To avoid a flicker where `activeSessions` briefly
                // returns empty for the renamed session — the combine()
                // of coordinator.sessions and _uiSessionKeys recomputes
                // on every upstream emission, so a strict swap would
                // emit one intermediate "no match" frame — we keep
                // BOTH the old and the new key in _uiSessionKeys
                // across the coordinator.rename call.
                if (oldKey in _uiSessionKeys.value && newKey !in _uiSessionKeys.value) {
                    _uiSessionKeys.value = _uiSessionKeys.value + newKey
                    newKeyAddedSpeculatively = true
                }
                // Rename the coordinator's session entry — without this,
                // an in-place rename of an attached session would orphan
                // the coordinator entry and subsequent input (looked up
                // by the NEW _activeSessionKey) would silently miss the
                // live SSH PTY.
                coordinator.rename(serverId, oldName, newName)
                renameCommitted = true
                if (oldKey in _uiSessionKeys.value) {
                    _uiSessionKeys.value = _uiSessionKeys.value - oldKey
                    if (_activeSessionKey.value == oldKey) _activeSessionKey.value = newKey
                }
                // Migrate command history to the new key so the user
                // doesn't lose their per-session shell history on rename.
                val oldHistory = shellHistory.value[oldKey]
                shellHistory.value = shellHistory.value.toMutableMap().apply {
                    remove(oldKey)
                    if (oldHistory != null) put(newKey, oldHistory)
                }
                // Also migrate the favorite-session mark — favorites are
                // keyed by `$serverId:$sessionName` so the rename would
                // otherwise silently un-favorite the session. A failure
                // here (disk full, YAML write error) must NOT bubble up
                // to the outer catch: the rename itself already
                // succeeded, and showing a "Failed to rename session"
                // error to the user would be misleading. The favorite
                // mark is a secondary UX polish, not a correctness
                // boundary.
                try {
                    preferences.renameFavoriteSession(oldKey, newKey)
                } catch (e: Exception) {
                    AppLogger.w(Category.SSH) { "SessionVM: rename favorite migration failed for $oldKey → $newKey: ${e.message}" }
                }
                try {
                    preferences.renameSessionColor(oldKey, newKey)
                } catch (e: Exception) {
                    AppLogger.w(Category.SSH) { "SessionVM: rename color migration failed for $oldKey → $newKey: ${e.message}" }
                }
                // Same for refreshServerSuspend — a network blip here
                // shouldn't undo a successful rename.
                try {
                    refreshServerSuspend(serverId)
                } catch (e: Exception) {
                    AppLogger.w(Category.SSH) { "SessionVM: post-rename refresh failed for server $serverId: ${e.message}" }
                }
            } catch (e: Exception) {
                // Only roll back the speculative _uiSessionKeys
                // insertion if the coordinator rename DIDN'T actually
                // commit. Once coordinator.rename succeeds, the
                // session exists under `newKey` and must stay in
                // _uiSessionKeys — a later failure (history / favorite
                // / refresh) is a secondary error that doesn't undo
                // the rename itself.
                if (newKeyAddedSpeculatively && !renameCommitted) {
                    _uiSessionKeys.value = _uiSessionKeys.value - newKey
                }
                _errorMessage.value = I18nRuntime.t("Failed to rename session: {error}", "error" to e.message)
            }
        }
    }

    fun toggleFavorite(serverId: Long, sessionName: String) {
        viewModelScope.safeLaunch(tag = "SessionVM") {
            preferences.toggleFavoriteSession(sessionKey(serverId, sessionName))
        }
    }

    fun setSessionColor(serverId: Long, sessionName: String, color: Int) {
        viewModelScope.safeLaunch(tag = "SessionVM") {
            preferences.setSessionColor(sessionKey(serverId, sessionName), color)
        }
    }

    // -----------------------------------------------------------------------
    // Active session management
    // -----------------------------------------------------------------------

    fun switchSession(key: String) {
        if (key in _uiSessionKeys.value) {
            _activeSessionKey.value = key
            // TerminalView will resize the emulator via setTerminalEmulator/onSizeChanged

            val parsedKey = parseSessionKey(key) ?: return
            viewModelScope.safeLaunch(tag = "SessionVM") { preferences.setLastSession(parsedKey.first, parsedKey.second) }
        }
    }

    fun sendInput(data: ByteArray) {
        viewModelScope.safeLaunch(tag = "SessionVM") {
            sendInputInternal(data)
        }
    }

    /**
     * Toggle tmux copy-mode for the active session. Sends `[prefix, '[']`
     * to enter or `ESC` to exit, then flips app-side state. The prefix
     * byte comes from [Settings.tmuxPrefixKey] so users with a non-default
     * tmux config see the FAB work without source edits.
     *
     * State drift (user manually types `q` to exit, or types `prefix [`
     * to enter, bypassing the FAB) is acceptable: one extra FAB tap
     * resyncs because both ESC and `prefix [` are idempotent in their
     * respective absent states.
     */
    fun toggleCopyMode() {
        viewModelScope.safeLaunch(tag = "SessionVM.copyMode") {
            val stringKey = _activeSessionKey.value ?: return@safeLaunch
            val sessionKey = toAppSessionKey(stringKey) ?: return@safeLaunch
            val currentlyActive = sessionKey in _copyModeSessions.value
            val prefixByte = tmuxPrefixByteFor(preferences.get(Settings.tmuxPrefixKey))
            val bytes = if (currentlyActive) {
                byteArrayOf(0x1B) // ESC — works in vi and emacs mode-keys
            } else {
                byteArrayOf(prefixByte, 0x5B) // prefix `[` — enter copy-mode
            }
            try {
                coordinator.sendInput(sessionKey, bytes)
                _copyModeSessions.value = if (currentlyActive) {
                    _copyModeSessions.value - sessionKey
                } else {
                    _copyModeSessions.value + sessionKey
                }
                AppLogger.i(Category.SSH) {
                    "copy-mode ${if (currentlyActive) "exit" else "enter"} session=$sessionKey"
                }
            } catch (e: Exception) {
                _errorMessage.value = I18nRuntime.t("Failed to toggle copy-mode: {error}", "error" to e.message)
            }
        }
    }

    fun sendCommand(command: String, executeImmediately: Boolean) {
        val payload = command.toByteArray(Charsets.UTF_8)
        viewModelScope.safeLaunch(tag = "SessionVM") {
            sendInputInternal(payload)
            if (executeImmediately) {
                sendInputInternal(byteArrayOf('\r'.code.toByte()))
            }
        }
    }

    private suspend fun sendInputInternal(data: ByteArray) {
        val stringKey = _activeSessionKey.value ?: return
        val sessionKey = toAppSessionKey(stringKey) ?: return
        try {
            coordinator.sendInput(sessionKey, data)
        } catch (e: Exception) {
            _errorMessage.value = I18nRuntime.t("Failed to send input: {error}", "error" to e.message)
        }
    }

    /**
     * Get (or lazily create) the [com.tmuxes.session.SessionResizeBus] for
     * a session key string. The TerminalView wires this onto its
     * [com.tmuxes.terminal.view.TerminalView.resizeBus] property; from
     * then on, every resize trigger flows through the bus (which dedupes
     * + coordinates with reattach replays).
     *
     * Callers MUST route terminal resizing through the bus.
     */
    suspend fun getResizeBus(stringKey: String): com.tmuxes.session.SessionResizeBus? {
        val sessionKey = toAppSessionKey(stringKey) ?: return null
        val consumer = com.tmuxes.session.ConsumerId(
            com.tmuxes.session.ConsumerType.APP_TERMINAL,
            "vm:$stringKey"
        )
        return coordinator.attachResizeBus(sessionKey, consumer)
    }

    fun detachSession(key: String) {
        val sessionKey = toAppSessionKey(key)
        _uiSessionKeys.value = _uiSessionKeys.value - key
        if (_activeSessionKey.value == key) {
            _activeSessionKey.value = _uiSessionKeys.value.firstOrNull()
        }
        shellHistory.value = shellHistory.value.toMutableMap().apply {
            remove(key)
        }
        // Detach the in-app consumer; coordinator keeps the session alive
        // (so a new ViewModel instance can re-attach during config change)
        if (sessionKey != null) {
            coordinator.detach(sessionKey, appConsumer)
            val resizeConsumer = com.tmuxes.session.ConsumerId(
                com.tmuxes.session.ConsumerType.APP_TERMINAL,
                "vm:$key"
            )
            viewModelScope.safeLaunch(tag = "SessionVM") {
                coordinator.detachResizeBus(sessionKey, resizeConsumer)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Remote clipboard
    // -----------------------------------------------------------------------

    private val _remoteClipboard = MutableStateFlow<String?>(null)
    val remoteClipboard: StateFlow<String?> = _remoteClipboard.asStateFlow()

    fun fetchRemoteClipboard() {
        val key = _activeSessionKey.value ?: return
        val parsed = parseSessionKey(key) ?: return
        viewModelScope.safeLaunch(tag = "SessionVM") {
            try {
                val result = withConnectionWaiting(connectionPool, connectionTrigger, parsed.first) { conn ->
                    conn.executeCommand(
                        "tmux show-buffer 2>/dev/null || xclip -selection clipboard -o 2>/dev/null || echo ''"
                    ).trim()
                }
                _remoteClipboard.value = result.ifEmpty { null }
            } catch (e: Exception) {
                _remoteClipboard.value = null
            }
        }
    }

    fun setRemoteClipboard(text: String) {
        val key = _activeSessionKey.value ?: return
        val parsed = parseSessionKey(key) ?: return
        viewModelScope.safeLaunch(tag = "SessionVM") {
            try {
                val quoted = com.tmuxes.ssh.shellEscape(text)
                withConnectionWaiting(connectionPool, connectionTrigger, parsed.first) { conn ->
                    conn.executeCommand(
                        "tmux set-buffer -- $quoted 2>/dev/null; printf '%s' $quoted | xclip -selection clipboard 2>/dev/null || true"
                    )
                }
                _remoteClipboard.value = text
            } catch (e: Exception) {
                _errorMessage.value = I18nRuntime.t("Failed to set remote clipboard: {error}", "error" to e.message)
            }
        }
    }

    fun clearError() { _errorMessage.value = null }

    override fun onCleared() {
        super.onCleared()
        // Detach the in-app consumer for each tracked session. The coordinator
        // keeps the ManagedSession alive in its map (different qualifier than
        // widget consumers, so they don't interfere) — a new ViewModel can
        // attach again and reuse the existing emulator/scrollback.
        for (stringKey in _uiSessionKeys.value) {
            val sessionKey = toAppSessionKey(stringKey) ?: continue
            coordinator.detach(sessionKey, appConsumer)
        }
    }

}
