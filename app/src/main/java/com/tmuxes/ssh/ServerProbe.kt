package com.tmuxes.ssh

import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Lightweight TCP reachability probe for servers.
 * Does NOT perform SSH handshake — only checks if the host:port is reachable.
 * Used by the Server page to show reachability status without coupling to SshConnectionManager.
 */
object ServerProbe {

    enum class ProbeResult(val label: String, val isError: Boolean) {
        REACHABLE("Reachable", false),
        UNREACHABLE("Connection refused", true),
        TIMEOUT("Connection timed out", true),
        DNS_FAILED("DNS resolution failed", true),
        NETWORK_UNREACHABLE("Network unreachable", true),
        UNKNOWN("Checking...", false)
    }

    /**
     * Attempt a TCP connection to [hostname]:[port].
     * Returns a detailed [ProbeResult] based on the outcome.
     */
    suspend fun probe(hostname: String, port: Int, timeoutMs: Int = 3000): ProbeResult =
        withContext(Dispatchers.IO) {
            AppLogger.timed(Category.NET, "probe.tcp $hostname:$port") {
                try {
                    withTimeout(timeoutMs.toLong() + 1000) {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(hostname, port), timeoutMs)
                            ProbeResult.REACHABLE
                        }
                    }
                } catch (_: UnknownHostException) {
                    AppLogger.d(Category.NET) { "probe.tcp ✗ DNS_FAILED $hostname:$port" }
                    ProbeResult.DNS_FAILED
                } catch (_: SocketTimeoutException) {
                    AppLogger.d(Category.NET) { "probe.tcp ✗ TIMEOUT $hostname:$port after ${timeoutMs}ms" }
                    ProbeResult.TIMEOUT
                } catch (e: ConnectException) {
                    val msg = e.message?.lowercase() ?: ""
                    val result = when {
                        msg.contains("refused") -> ProbeResult.UNREACHABLE
                        msg.contains("network") && msg.contains("unreachable") -> ProbeResult.NETWORK_UNREACHABLE
                        else -> ProbeResult.UNREACHABLE
                    }
                    AppLogger.d(Category.NET) { "probe.tcp ✗ ${result.name} $hostname:$port cause='${e.message}'" }
                    result
                } catch (_: NoRouteToHostException) {
                    AppLogger.d(Category.NET) { "probe.tcp ✗ NETWORK_UNREACHABLE $hostname:$port" }
                    ProbeResult.NETWORK_UNREACHABLE
                } catch (e: Exception) {
                    AppLogger.w(Category.NET) { "probe.tcp ✗ UNKNOWN $hostname:$port cause='${e.message}'" }
                    ProbeResult.UNREACHABLE
                }
            }
        }
}
