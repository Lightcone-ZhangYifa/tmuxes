package com.tmuxes.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tmuxes.TmuxesApp
import com.tmuxes.data.model.KnownHostEntity
import com.tmuxes.util.AppLogger
import com.tmuxes.util.safeLaunch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the KnownHostsScreen.
 * Provides a live list of all known host entries and supports deletion.
 */
class KnownHostsViewModel(application: Application) : AndroidViewModel(application) {

    private val knownHostDao = (application as TmuxesApp).database.knownHostDao()

    val knownHosts: StateFlow<List<KnownHostEntity>> = knownHostDao.getAll()
        // Room DAO Flow can throw on DB corruption or migration race.
        // Catch to prevent viewModelScope from crashing.
        .catch { e ->
            AppLogger.w(AppLogger.Category.DB) { "KnownHostsVM getAll flow error: ${e.message}" }
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteHost(host: KnownHostEntity) {
        viewModelScope.safeLaunch(tag = "KnownHostsVM.deleteHost") { knownHostDao.delete(host) }
    }
}
