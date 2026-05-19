package com.tmuxes.ssh

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Monitors network connectivity and exposes it as a [StateFlow].
 *
 * The [ConnectionSupervisor] uses this to:
 * - Suppress connection attempts when offline (avoids wasting resources)
 * - Trigger immediate reconnection when connectivity returns
 * - Detect WiFi↔cellular transitions that silently kill TCP connections
 */
class NetworkMonitor(context: Context) {

    // Nullable-cast so a stripped ROM / test harness returning a wrong
    // type can't crash the TmuxesApp lazy init. When null, the monitor
    // silently no-ops — the network-change events won't fire, but the
    // app still starts instead of dying on cold boot.
    private val connectivityManager: ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkCurrentConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    /** Incremented on every network change; observers can use this to detect transitions. */
    private val _networkGeneration = MutableStateFlow(0L)
    val networkGeneration: StateFlow<Long> = _networkGeneration.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            try {
                AppLogger.i(Category.NET) { "NetworkMonitor: network available" }
                _isOnline.value = true
                _networkGeneration.update { it + 1 }
            } catch (_: Throwable) {}
        }

        override fun onLost(network: Network) {
            try {
                // Check if ANY network is still available (there may be multiple)
                val online = checkCurrentConnectivity()
                _isOnline.value = online
                if (!online) {
                    AppLogger.i(Category.NET) { "NetworkMonitor: all networks lost" }
                }
                _networkGeneration.update { it + 1 }
            } catch (_: Throwable) {}
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            try {
                val online = checkCurrentConnectivity()
                if (online != _isOnline.value) {
                    _isOnline.value = online
                    _networkGeneration.update { it + 1 }
                    AppLogger.d(Category.NET) { "NetworkMonitor: capabilities changed, online=$online" }
                }
            } catch (_: Throwable) {}
        }
    }

    init {
        // registerNetworkCallback can throw SecurityException (missing
        // ACCESS_NETWORK_STATE on a stripped ROM) or IllegalArgumentException
        // (too many callbacks registered on Android < R). Swallow so the
        // TmuxesApp.networkMonitor lazy init can still return a usable
        // instance — the monitor will just report its current state once
        // and never update, which is degraded but not fatal.
        try {
            val cm = connectivityManager
            if (cm != null) {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(request, networkCallback)
            }
        } catch (e: Throwable) {
            AppLogger.w(Category.NET) { "NetworkMonitor: registerNetworkCallback failed: ${e.message}" }
        }
    }

    fun stop() {
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
            // Already unregistered
        }
    }

    private fun checkCurrentConnectivity(): Boolean {
        val cm = connectivityManager ?: return true
        return try {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            isUsableNetwork(caps)
        } catch (_: Throwable) {
            // Some devices throw when the ConnectivityManager is in a
            // transient "settling" state after a network switch. Assume
            // online so the supervisor doesn't flap into offline mode.
            true
        }
    }

    private fun isUsableNetwork(caps: NetworkCapabilities): Boolean {
        val hasSupportedTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)

        // SSH over a local LAN/VPN can be perfectly valid even when Android
        // has not marked the network as "validated" Internet connectivity.
        return hasSupportedTransport &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
