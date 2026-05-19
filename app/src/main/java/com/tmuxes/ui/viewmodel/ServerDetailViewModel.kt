package com.tmuxes.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tmuxes.TmuxesApp
import com.tmuxes.data.model.ServerEntity
import com.tmuxes.i18n.I18nRuntime
import com.tmuxes.portforward.PortForwardCoordinator
import com.tmuxes.portforward.PortForwardRule
import com.tmuxes.ssh.ServerConnectionState
import com.tmuxes.ssh.ServerStatus
import com.tmuxes.ssh.SshConfigHost
import com.tmuxes.ssh.SshConfigParser
import com.tmuxes.ssh.SshConnectionPool
import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import com.tmuxes.util.safeLaunch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * ViewModel for the ServerDetailScreen.
 * Handles SSH config auto-fetching, reachability probing, and child server display.
 * Does NOT create servers — import navigates to AddEditServerScreen with pre-filled data.
 */
class ServerDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TmuxesApp
    private val repository = app.serverRepository
    private val connectionPool: SshConnectionPool = app.connectionPool
    private val portForwardCoordinator: PortForwardCoordinator = app.portForwardCoordinator

    // -----------------------------------------------------------------------
    // Server data
    // -----------------------------------------------------------------------

    private val _server = MutableStateFlow<ServerEntity?>(null)
    val server: StateFlow<ServerEntity?> = _server.asStateFlow()

    private val _parentServer = MutableStateFlow<ServerEntity?>(null)
    val parentServer: StateFlow<ServerEntity?> = _parentServer.asStateFlow()

    private val _children = MutableStateFlow<List<ServerEntity>>(emptyList())
    val children: StateFlow<List<ServerEntity>> = _children.asStateFlow()

    // -----------------------------------------------------------------------
    // Loading state
    // -----------------------------------------------------------------------

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    // -----------------------------------------------------------------------
    // SSH Config
    // -----------------------------------------------------------------------

    private val _configHosts = MutableStateFlow<List<SshConfigHost>>(emptyList())
    val configHosts: StateFlow<List<SshConfigHost>> = _configHosts.asStateFlow()

    private val _isFetchingConfig = MutableStateFlow(false)
    val isFetchingConfig: StateFlow<Boolean> = _isFetchingConfig.asStateFlow()

    private val _configStatus = MutableStateFlow<ConfigStatus>(ConfigStatus.Idle)
    val configStatus: StateFlow<ConfigStatus> = _configStatus.asStateFlow()

    sealed class ConfigStatus {
        data object Idle : ConfigStatus()
        data object WaitingForConnection : ConfigStatus()
        data object Fetching : ConfigStatus()
        data class Loaded(val count: Int) : ConfigStatus()
        data class Error(val message: String) : ConfigStatus()
    }

    // -----------------------------------------------------------------------
    // SSH status
    // -----------------------------------------------------------------------

    private val connectionSupervisor = app.connectionSupervisor
    private val _serverStatus = MutableStateFlow(ServerConnectionState(ServerStatus.IDLE))
    val serverStatus: StateFlow<ServerConnectionState> = _serverStatus.asStateFlow()

    // -----------------------------------------------------------------------
    // Known host fingerprints
    // -----------------------------------------------------------------------

    private val knownHostDao = app.database.knownHostDao()

    private val _hostFingerprints = MutableStateFlow<List<com.tmuxes.data.model.KnownHostEntity>>(emptyList())
    val hostFingerprints: StateFlow<List<com.tmuxes.data.model.KnownHostEntity>> = _hostFingerprints.asStateFlow()

    fun refreshHostFingerprints() {
        val s = _server.value ?: return
        viewModelScope.safeLaunch(tag = "ServerDetailVM.refreshHostFingerprints") {
            try {
                _hostFingerprints.value = knownHostDao.findByHostPort(s.hostname, s.port)
            } catch (e: Exception) {
                AppLogger.w(Category.DB) { "refreshHostFingerprints failed: ${e.message}" }
            }
        }
    }

    // -----------------------------------------------------------------------
    // System info (fetched via SSH when connected)
    // -----------------------------------------------------------------------

    data class ServerSystemInfo(
        val hostname: String? = null,
        val os: String? = null,
        val kernel: String? = null,
        val arch: String? = null,
        val uptime: String? = null,
        val loadAvg: String? = null,
        val cpuCount: String? = null,
        val cpuModel: String? = null,
        val memoryTotal: String? = null,
        val memoryUsed: String? = null,
        val memoryFree: String? = null,
        val diskTotal: String? = null,
        val diskUsed: String? = null,
        val diskAvail: String? = null,
        val diskUsage: String? = null,
        val ipAddresses: String? = null,
        val sshVersion: String? = null,
        val shell: String? = null,
        val loggedInUsers: String? = null,
        val tmuxSessions: String? = null
    )

    private val _systemInfo = MutableStateFlow<ServerSystemInfo?>(null)
    val systemInfo: StateFlow<ServerSystemInfo?> = _systemInfo.asStateFlow()

    private val _isFetchingSystemInfo = MutableStateFlow(false)
    val isFetchingSystemInfo: StateFlow<Boolean> = _isFetchingSystemInfo.asStateFlow()

    private val _systemInfoError = MutableStateFlow<String?>(null)
    val systemInfoError: StateFlow<String?> = _systemInfoError.asStateFlow()

    private var systemInfoFetched = false

    // -----------------------------------------------------------------------
    // Error
    // -----------------------------------------------------------------------

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private var configObserverJob: Job? = null
    private var statusObserverJob: Job? = null
    private var configFetched = false
    private var currentServerId: Long = -1

    // -----------------------------------------------------------------------
    // Load
    // -----------------------------------------------------------------------

    fun loadServer(serverId: Long) {
        // The cheap parts (disk read of the server entity, children,
        // host-fingerprints) ALWAYS run so an edit to the server is
        // visible the next time this screen enters composition. The
        // expensive parts (status observer + remote SSH-config fetch)
        // only run on the FIRST load for this serverId. Re-running
        // them on every screen re-entry would re-cancel and re-fetch
        // remote data on every drill-down pop.
        val isFirstLoad = currentServerId != serverId
        currentServerId = serverId

        viewModelScope.safeLaunch(tag = "ServerDetailVM.loadServer") {
            if (isFirstLoad) _isLoading.value = true
            _loadError.value = null
            try {
                val server = repository.getById(serverId)
                if (server == null) {
                    _loadError.value = I18nRuntime.t("Server not found")
                    _isLoading.value = false
                    return@safeLaunch
                }
                _server.value = server

                // Load parent info
                if (server.parentId != null) {
                    _parentServer.value = repository.getById(server.parentId)
                }

                // Load children (cheap)
                refreshChildren(serverId)

                _isLoading.value = false

                // Load known host fingerprints (cheap DB query)
                _hostFingerprints.value = knownHostDao.findByHostPort(server.hostname, server.port)

                if (isFirstLoad) {
                    observeServerStatus(serverId)
                    // Start auto-fetch SSH config — expensive network call,
                    // only run once per detail-screen lifetime.
                    startAutoFetchConfig(serverId)
                }
            } catch (e: Exception) {
                _loadError.value = I18nRuntime.t("Failed to load server: {error}", "error" to e.message)
                _isLoading.value = false
                AppLogger.e(Category.SSH, e) { "Failed to load server $serverId" }
            }
        }
    }

    private suspend fun refreshChildren(serverId: Long) {
        _children.value = repository.getChildrenOfOnce(serverId)
    }

    // -----------------------------------------------------------------------
    // SSH status
    // -----------------------------------------------------------------------

    private fun observeServerStatus(serverId: Long) {
        statusObserverJob?.cancel()
        statusObserverJob = viewModelScope.safeLaunch(tag = "ServerDetailVM.observeServerStatus") {
            try {
                connectionSupervisor.serverStates.collect { states ->
                    _serverStatus.value = states[serverId] ?: ServerConnectionState(ServerStatus.IDLE)
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // normal cancellation on loadServer() or onCleared()
            } catch (e: Exception) {
                AppLogger.w(Category.SSH) { "observeServerStatus collect failed: ${e.message}" }
            }
        }
    }

    fun refreshProbe() {
        val s = _server.value ?: return
        _serverStatus.value = connectionSupervisor.serverStates.value[s.id] ?: ServerConnectionState(ServerStatus.IDLE)
    }

    // -----------------------------------------------------------------------
    // SSH Config auto-fetch
    // -----------------------------------------------------------------------

    private fun startAutoFetchConfig(serverId: Long) {
        configObserverJob?.cancel()
        configFetched = false
        systemInfoFetched = false

        // Observe supervisor server states — auto-fetch when connected, keep observing for reconnects
        _configStatus.value = ConfigStatus.WaitingForConnection
        configObserverJob = viewModelScope.safeLaunch(tag = "ServerDetailVM.startAutoFetchConfig") {
            try {
                // First check if already connected
                val connection = connectionPool.get(serverId)
                if (connection != null && connection.isConnected()) {
                    fetchSshConfigInternal(serverId)
                    fetchSystemInfoInternal(serverId)
                }

                // Track previous state AND the SshConnection identity
                // so we can detect a *transition* back to CONNECTED —
                // that's our signal that a reconnect just
                // re-established the connection,
                // possibly to a DIFFERENT remote (the user might have
                // edited the hostname). We must re-fetch the SSH
                // config and system info because the cached values
                // came from the OLD connection.
                //
                // Tracking just the ServerStatus isn't enough: if the
                // fetch is slow (15s timeout) and a
                // CONNECTED → CONNECTING → CONNECTED sequence fits
                // entirely inside that window, StateFlow squashes
                // the intermediate CONNECTING emission and the
                // observer sees two consecutive CONNECTED values —
                // no transition detected → no re-fetch → stale SSH
                // config pointing at the OLD remote.
                //
                // The pool's SshConnection instance changes identity
                // on every reconnect (executeConnect calls
                // `pool.put(serverId, connection)` with a brand new
                // object). Comparing identity catches the swap even
                // when the StateFlow view looks unchanged.
                var prevStatus: ServerStatus? = null
                var prevConnection: com.tmuxes.ssh.SshConnection? = null
                connectionSupervisor.serverStates.collect { states ->
                    val state = states[serverId]
                    val now = state?.status
                    if (now == ServerStatus.CONNECTED) {
                        val currentConnection = connectionPool.get(serverId)
                        val reconnectDetected =
                            (prevStatus != null && prevStatus != ServerStatus.CONNECTED) ||
                            (prevConnection != null && prevConnection !== currentConnection)
                        if (reconnectDetected) {
                            configFetched = false
                            systemInfoFetched = false
                        }
                        if (!configFetched) fetchSshConfigInternal(serverId)
                        if (!systemInfoFetched) fetchSystemInfoInternal(serverId)
                        prevConnection = currentConnection
                    }
                    prevStatus = now
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // normal cancellation on navigation away
            } catch (e: Exception) {
                AppLogger.w(Category.SSH) { "startAutoFetchConfig failed: ${e.message}" }
            }
        }
    }

    /**
     * Manual refresh — re-fetch SSH config regardless of previous state.
     */
    fun refreshSshConfig() {
        val serverId = currentServerId
        if (serverId < 0) return
        configFetched = false
        viewModelScope.safeLaunch(tag = "ServerDetailVM.refreshSshConfig") {
            fetchSshConfigInternal(serverId)
        }
    }

    /**
     * Also refresh children list (called after navigating back from AddEditServerScreen).
     */
    fun refreshData() {
        val serverId = currentServerId
        if (serverId < 0) return
        viewModelScope.safeLaunch(tag = "ServerDetailVM.refreshData") {
            try {
                refreshChildren(serverId)
                // Re-check status (supervisor auto-reconciles, just read latest)
                val s = _server.value
                if (s != null) {
                    _serverStatus.value = connectionSupervisor.serverStates.value[s.id] ?: ServerConnectionState(ServerStatus.IDLE)
                }
            } catch (e: Exception) {
                AppLogger.w(Category.DB) { "refreshData failed: ${e.message}" }
            }
        }
    }

    private suspend fun fetchSshConfigInternal(serverId: Long) {
        if (_isFetchingConfig.value) return
        _isFetchingConfig.value = true
        _configStatus.value = ConfigStatus.Fetching

        try {
            val connection = connectionPool.get(serverId)
            if (connection == null || !connection.isConnected()) {
                _configStatus.value = ConfigStatus.WaitingForConnection
                _isFetchingConfig.value = false
                return
            }

            val configText = withTimeout(15_000) {
                connection.executeCommand("cat ~/.ssh/config 2>/dev/null || echo ''")
            }

            val hosts = SshConfigParser.parse(configText)
            _configHosts.value = hosts
            configFetched = true

            _configStatus.value = if (hosts.isEmpty()) {
                ConfigStatus.Error(I18nRuntime.t("No hosts found in ~/.ssh/config"))
            } else {
                ConfigStatus.Loaded(hosts.size)
            }

            AppLogger.i(Category.SSH) { "Fetched SSH config from server $serverId: ${hosts.size} hosts" }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            _configStatus.value = ConfigStatus.Error(I18nRuntime.t("Timed out reading SSH config"))
            AppLogger.w(Category.SSH) { "Timeout fetching SSH config from server $serverId: ${e.message}" }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // Normal cancellation when navigating away — not a real error.
            // Restore WaitingForConnection so the next visit re-triggers.
            _configStatus.value = ConfigStatus.WaitingForConnection
            throw ce
        } catch (e: Exception) {
            _configStatus.value = ConfigStatus.Error(I18nRuntime.t("Failed: {error}", "error" to e.message))
            AppLogger.w(Category.SSH) { "Failed to fetch SSH config from server $serverId: ${e.message}" }
        } finally {
            _isFetchingConfig.value = false
        }
    }

    // -----------------------------------------------------------------------
    // Check if config host is already imported
    // -----------------------------------------------------------------------

    fun isAlreadyImported(parentServerId: Long, configHost: SshConfigHost): Boolean {
        val children = _children.value
        val targetHostname = configHost.hostName ?: configHost.host
        return children.any { child ->
            child.parentId == parentServerId &&
                child.hostname == targetHostname &&
                (configHost.port == null || child.port == (configHost.port ?: 22))
        }
    }

    // -----------------------------------------------------------------------
    // System info fetch
    // -----------------------------------------------------------------------

    private suspend fun fetchSystemInfoInternal(serverId: Long) {
        if (_isFetchingSystemInfo.value) return
        _isFetchingSystemInfo.value = true
        _systemInfoError.value = null

        try {
            val connection = connectionPool.get(serverId)
            if (connection == null || !connection.isConnected()) {
                _systemInfoError.value = I18nRuntime.t("Not connected")
                _isFetchingSystemInfo.value = false
                return
            }

            val output = withTimeout(20_000) {
                connection.executeCommand(SYSTEM_INFO_COMMAND)
            }

            _systemInfo.value = parseSystemInfo(output)
            systemInfoFetched = true
            AppLogger.i(Category.SSH) { "Fetched system info from server $serverId" }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            _systemInfoError.value = I18nRuntime.t("Timed out fetching system info")
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // Normal cancellation on navigation — rethrow and clear state
            _systemInfoError.value = null
            throw ce
        } catch (e: Exception) {
            _systemInfoError.value = I18nRuntime.t("Failed: {error}", "error" to e.message)
        } finally {
            _isFetchingSystemInfo.value = false
        }
    }

    fun refreshSystemInfo() {
        val serverId = currentServerId
        if (serverId < 0) return
        systemInfoFetched = false
        viewModelScope.safeLaunch(tag = "ServerDetailVM.refreshSystemInfo") { fetchSystemInfoInternal(serverId) }
    }

    private fun parseSystemInfo(output: String): ServerSystemInfo {
        val sections = mutableMapOf<String, String>()
        var currentKey: String? = null
        val currentValue = StringBuilder()

        for (line in output.lines()) {
            if (line.startsWith("===") && line.endsWith("===")) {
                if (currentKey != null) {
                    sections[currentKey] = currentValue.toString().trim()
                }
                currentKey = line.removePrefix("===").removeSuffix("===")
                currentValue.clear()
            } else if (currentKey != null) {
                if (currentValue.isNotEmpty()) currentValue.append("\n")
                currentValue.append(line)
            }
        }
        if (currentKey != null) {
            sections[currentKey] = currentValue.toString().trim()
        }

        // Parse memory from free -h output
        val memLines = sections["MEM"]?.lines() ?: emptyList()
        val memValues = if (memLines.size >= 2) {
            val parts = memLines[1].trim().split(Regex("\\s+"))
            Triple(parts.getOrNull(1), parts.getOrNull(2), parts.getOrNull(3))
        } else Triple(null, null, null)

        // Parse disk from df -h output
        val diskParts = sections["DISK"]?.trim()?.split(Regex("\\s+"))
        val diskTotal = diskParts?.getOrNull(1)
        val diskUsed = diskParts?.getOrNull(2)
        val diskAvail = diskParts?.getOrNull(3)
        val diskUsage = diskParts?.getOrNull(4)

        // Parse CPU
        val cpuLines = sections["CPU"]?.lines() ?: emptyList()
        val cpuCount = cpuLines.getOrNull(0)?.trim()
        val cpuModel = cpuLines.getOrNull(1)?.trim()

        return ServerSystemInfo(
            hostname = sections["HOSTNAME"]?.takeIf { it.isNotBlank() },
            os = sections["OS"]?.takeIf { it.isNotBlank() },
            kernel = sections["KERNEL"]?.takeIf { it.isNotBlank() },
            arch = sections["ARCH"]?.takeIf { it.isNotBlank() },
            uptime = sections["UPTIME"]?.takeIf { it.isNotBlank() },
            loadAvg = sections["LOAD"]?.takeIf { it.isNotBlank() },
            cpuCount = cpuCount?.takeIf { it.isNotBlank() },
            cpuModel = cpuModel?.takeIf { it.isNotBlank() },
            memoryTotal = memValues.first,
            memoryUsed = memValues.second,
            memoryFree = memValues.third,
            diskTotal = diskTotal,
            diskUsed = diskUsed,
            diskAvail = diskAvail,
            diskUsage = diskUsage,
            ipAddresses = sections["IP"]?.takeIf { it.isNotBlank() },
            sshVersion = sections["SSH"]?.takeIf { it.isNotBlank() },
            shell = sections["SHELL"]?.takeIf { it.isNotBlank() },
            loggedInUsers = sections["USERS"]?.takeIf { it.isNotBlank() },
            tmuxSessions = sections["TMUX"]?.takeIf { it.isNotBlank() }
        )
    }

    companion object {
        private const val SYSTEM_INFO_COMMAND = """echo "===HOSTNAME===" && hostname 2>/dev/null && echo "===OS===" && (cat /etc/os-release 2>/dev/null | grep PRETTY_NAME | cut -d'"' -f2 || echo "") && echo "===KERNEL===" && uname -r 2>/dev/null && echo "===ARCH===" && uname -m 2>/dev/null && echo "===UPTIME===" && (uptime -p 2>/dev/null || uptime 2>/dev/null) && echo "===LOAD===" && cat /proc/loadavg 2>/dev/null && echo "===CPU===" && (nproc 2>/dev/null || echo "") && (lscpu 2>/dev/null | grep "Model name" | sed 's/.*: *//' || echo "") && echo "===MEM===" && (free -h 2>/dev/null | head -2 || echo "") && echo "===DISK===" && (df -h / 2>/dev/null | tail -1 || echo "") && echo "===IP===" && (hostname -I 2>/dev/null || echo "") && echo "===SSH===" && (ssh -V 2>&1 || echo "") && echo "===SHELL===" && echo ${"$"}SHELL && echo "===USERS===" && (who 2>/dev/null | wc -l || echo "") && echo "===TMUX===" && (tmux list-sessions 2>/dev/null | wc -l || echo "0")"""
    }

    // -----------------------------------------------------------------------
    // Delete server
    // -----------------------------------------------------------------------

    fun deleteServer(onDeleted: () -> Unit) {
        val server = _server.value ?: return
        viewModelScope.safeLaunch(tag = "ServerDetailVM.deleteServer") {
            try {
                repository.delete(server)
                onDeleted()
            } catch (e: Exception) {
                _errorMessage.value = I18nRuntime.t("Failed to delete server: {error}", "error" to e.message)
            }
        }
    }

    fun setError(message: String) {
        _errorMessage.value = message
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // -----------------------------------------------------------------------
    // Port forwarding (delegated to PortForwardCoordinator)
    // -----------------------------------------------------------------------

    /** UI projection of an active local port forward. */
    data class PortForward(
        val localPort: Int,
        val remoteHost: String,
        val remotePort: Int,
        val isActive: Boolean = false,
        val rule: PortForwardRule? = null
    )

    /** Port forwards for the current server, derived from PortForwardCoordinator. */
    val portForwards: StateFlow<List<PortForward>> = portForwardCoordinator.activeForwards
        .map { allForwards ->
            val serverId = currentServerId
            (allForwards[serverId] ?: emptyList()).map { apf ->
                PortForward(
                    localPort = apf.rule.localPort,
                    remoteHost = apf.rule.remoteHost,
                    remotePort = apf.rule.remotePort,
                    isActive = apf.state == com.tmuxes.portforward.PortForwardState.ACTIVE,
                    rule = apf.rule
                )
            }
        }
        .catch { e ->
            AppLogger.w(Category.SSH) { "ServerDetailVM portForwards flow error: ${e.message}" }
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setupPortForward(serverId: Long, localPort: Int, remoteHost: String, remotePort: Int) {
        viewModelScope.safeLaunch(tag = "ServerDetailVM.setupPortForward") {
            try {
                val rule = PortForwardRule(localPort = localPort, remoteHost = remoteHost, remotePort = remotePort)
                val result = portForwardCoordinator.addLocalForward(serverId, rule)
                if (result.state == com.tmuxes.portforward.PortForwardState.FAILED) {
                    _errorMessage.value = I18nRuntime.t(
                        "Failed to set up forward: {error}",
                        "error" to (result.errorMessage ?: I18nRuntime.t("Unknown error"))
                    )
                }
            } catch (e: Exception) {
                _errorMessage.value = I18nRuntime.t("Failed to set up forward: {error}", "error" to e.message)
            }
        }
    }

    fun removePortForward(entry: PortForward) {
        val rule = entry.rule ?: PortForwardRule(entry.localPort, entry.remoteHost, entry.remotePort)
        viewModelScope.safeLaunch(tag = "ServerDetailVM.removePortForward") {
            try {
                portForwardCoordinator.removeLocalForward(currentServerId.toLong(), rule)
            } catch (e: Exception) {
                _errorMessage.value = I18nRuntime.t("Failed to remove forward: {error}", "error" to e.message)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        configObserverJob?.cancel()
        statusObserverJob?.cancel()
        // Port forwards are managed by PortForwardCoordinator — no cleanup needed here
    }
}
