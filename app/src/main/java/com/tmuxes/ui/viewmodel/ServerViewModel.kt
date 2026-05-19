package com.tmuxes.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tmuxes.TmuxesApp
import com.tmuxes.data.model.ServerEntity
import com.tmuxes.i18n.I18nRuntime
import com.tmuxes.ssh.ServerConnectionState
import com.tmuxes.util.AppLogger
import com.tmuxes.util.safeLaunch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

/**
 * Pure CRUD ViewModel for server management.
 * No connection logic — that belongs to SessionViewModel / ConnectionSupervisor.
 */
class ServerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TmuxesApp
    private val repository = app.serverRepository
    private val connectionSupervisor = app.connectionSupervisor
    private val connectionTrigger = app.connectionTrigger

    // -----------------------------------------------------------------------
    // Servers
    // -----------------------------------------------------------------------

    val servers: StateFlow<List<ServerEntity>> = repository.allServers
        .catch { e ->
            AppLogger.w(AppLogger.Category.SSH) { "ServerVM allServers flow error: ${e.message}" }
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val serverStates: StateFlow<Map<Long, ServerConnectionState>> = connectionSupervisor.serverStates

    /** Trigger immediate reconciliation (e.g., pull-to-refresh). */
    fun refreshServerStates() {
        // Pull-to-refresh = user wants connections retried NOW. Goes through
        // ConnectionTrigger so the action is logged with Reason=USER_REFRESH
        // and non-permanent backoffs are released — same semantic as the
        // top-bar refresh button on the Sessions tab.
        connectionTrigger.userRefresh()
    }

    // -----------------------------------------------------------------------
    // Loading / error state
    // -----------------------------------------------------------------------

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _lastDeletedServer = MutableStateFlow<ServerEntity?>(null)
    val lastDeletedServer: StateFlow<ServerEntity?> = _lastDeletedServer.asStateFlow()

    // -----------------------------------------------------------------------
    // CRUD
    // -----------------------------------------------------------------------

    suspend fun getServerById(id: Long): ServerEntity? {
        return repository.getById(id)
    }

    fun addServer(server: ServerEntity) {
        AppLogger.i(AppLogger.Category.UI) { "vm.addServer hostname=${server.hostname}:${server.port} username=${server.username}" }
        viewModelScope.safeLaunch(tag = "ServerVM") {
            try {
                repository.insert(server)
                // Supervisor auto-reconciles via allServers Flow observation
            } catch (e: Exception) {
                AppLogger.w(AppLogger.Category.UI) { "vm.addServer ✗ cause='${e.message}'" }
                _errorMessage.value = I18nRuntime.t("Failed to add server: {error}", "error" to e.message)
            }
        }
    }

    fun updateServer(server: ServerEntity) {
        AppLogger.i(AppLogger.Category.UI) { "vm.updateServer id=${server.id}" }
        viewModelScope.safeLaunch(tag = "ServerVM") {
            try {
                // The repository emits a connectionInvalidations event for
                // any connection-relevant change; the supervisor consumes
                // that and clears retry/permanent-fail state synchronously.
                // No explicit resetRetryState call is needed here.
                repository.update(server)
            } catch (e: Exception) {
                AppLogger.w(AppLogger.Category.UI) { "vm.updateServer ✗ id=${server.id} cause='${e.message}'" }
                _errorMessage.value = I18nRuntime.t("Failed to update server: {error}", "error" to e.message)
            }
        }
    }

    fun setServerEnabled(server: ServerEntity, enabled: Boolean) {
        if (server.isEnabled == enabled) return
        AppLogger.i(AppLogger.Category.UI) { "vm.setServerEnabled id=${server.id} enabled=$enabled" }
        viewModelScope.safeLaunch(tag = "ServerVM") {
            try {
                repository.update(server.copy(isEnabled = enabled))
                if (enabled) connectionTrigger.userRefresh(server.id)
            } catch (e: Exception) {
                AppLogger.w(AppLogger.Category.UI) {
                    "vm.setServerEnabled ✗ id=${server.id} enabled=$enabled cause='${e.message}'"
                }
                _errorMessage.value = I18nRuntime.t("Failed to update server: {error}", "error" to e.message)
            }
        }
    }

    fun deleteServer(server: ServerEntity) {
        AppLogger.i(AppLogger.Category.UI) { "vm.deleteServer id=${server.id} ('${server.displayName}')" }
        viewModelScope.safeLaunch(tag = "ServerVM") {
            try {
                // Repository handles orphaning children
                repository.delete(server)
                // Supervisor auto-reconciles via allServers Flow observation
                _lastDeletedServer.value = server
            } catch (e: Exception) {
                AppLogger.w(AppLogger.Category.UI) { "vm.deleteServer ✗ id=${server.id} cause='${e.message}'" }
                _errorMessage.value = I18nRuntime.t("Failed to delete server: {error}", "error" to e.message)
            }
        }
    }

    fun undoDelete() {
        val server = _lastDeletedServer.value ?: return
        AppLogger.i(AppLogger.Category.UI) { "vm.undoDelete id=${server.id} ('${server.displayName}')" }
        viewModelScope.safeLaunch(tag = "ServerVM") {
            try {
                repository.insert(server)
                // Supervisor auto-reconciles via allServers Flow observation
                _lastDeletedServer.value = null
            } catch (e: Exception) {
                AppLogger.w(AppLogger.Category.UI) { "vm.undoDelete ✗ id=${server.id} cause='${e.message}'" }
                _errorMessage.value = I18nRuntime.t("Failed to restore server: {error}", "error" to e.message)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Hierarchy
    // -----------------------------------------------------------------------

    fun updateParent(serverId: Long, newParentId: Long?) {
        viewModelScope.safeLaunch(tag = "ServerVM") {
            try {
                // Validate no circular chain
                if (newParentId != null) {
                    val allServers = servers.value
                    if (wouldCreateCircle(allServers, serverId, newParentId)) {
                        _errorMessage.value = I18nRuntime.t("Cannot nest a server under its own descendant")
                        return@safeLaunch
                    }
                }
                repository.updateParent(serverId, newParentId)
                // Supervisor auto-reconciles via allServers Flow observation
            } catch (e: Exception) {
                _errorMessage.value = I18nRuntime.t("Failed to update parent: {error}", "error" to e.message)
            }
        }
    }

    /**
     * Check if setting [serverId]'s parent to [newParentId] would create a circular chain.
     * Walks up from [newParentId] to see if it eventually reaches [serverId].
     */
    private fun wouldCreateCircle(allServers: List<ServerEntity>, serverId: Long, newParentId: Long): Boolean {
        val serverMap = allServers.associateBy { it.id }
        var current: Long? = newParentId
        val visited = mutableSetOf<Long>()
        while (current != null) {
            if (current == serverId) return true
            if (!visited.add(current)) return true
            current = serverMap[current]?.parentId
        }
        return false
    }

    // -----------------------------------------------------------------------
    // Util
    // -----------------------------------------------------------------------

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearLastDeleted() {
        _lastDeletedServer.value = null
    }
}
