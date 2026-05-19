package com.tmuxes.ssh

import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.keepalive.KeepAliveRunner
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.DirectConnection
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.DisconnectListener
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Direct SSH connection implementation using SSHJ.
 *
 * Handles direct connections and jump-host tunneled connections (owns an SSHClient).
 *
 * @param serverId Unique identifier of the server this connection belongs to.
 * @param config Complete SSH configuration — the sole configuration source.
 * @param hostKeyVerifier SSHJ host key verifier for the connection.
 * @param tunnelProvider Optional factory for a direct-tcpip channel (jump-host tunneling).
 * @param scope Coroutine scope for structured concurrency (port forward accept loops, etc.).
 */
class DirectSshConnection(
    override val serverId: Long,
    private val config: SshConfig,
    private val hostKeyVerifier: HostKeyVerifier,
    private val tunnelProvider: (suspend () -> DirectConnection)? = null,
    parentScope: CoroutineScope
) : SshConnection {

    /**
     * Per-connection coroutine scope, child of the supervisor's scope. Cancelled
     * on [disconnect] so that any port-forward accept loops created during the
     * connection's lifetime are also torn down. Uses [SupervisorJob] so a single
     * port-forward failure does not cancel sibling forwards.
     */
    private val scope: CoroutineScope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())

    @Volatile
    private var client: SSHClient? = null

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    // Track port forward handles for cleanup on disconnect
    private val portForwardHandles = mutableListOf<DirectPortForwardHandle>()

    // -------------------------------------------------------------------------
    // connect()
    // -------------------------------------------------------------------------

    override suspend fun connect() = withContext(Dispatchers.IO) {
        if (_state.value == ConnectionState.AUTHENTICATED) {
            AppLogger.d(Category.SSH) { "connect() called but already authenticated to ${config.hostname}:${config.port}" }
            return@withContext
        }

        _state.value = ConnectionState.CONNECTING
        AppLogger.i(Category.SSH) { "Connecting to ${config.hostname}:${config.port} as ${config.username}" }

        var ssh: SSHClient? = null
        try {
            // Ensure SSHJ routes all crypto through Bouncy Castle.
            // This must happen before SSHClient instantiation because
            // DefaultConfig.init*() methods probe SecurityUtils during construction.
            SecurityUtils.setSecurityProvider("BC")

            val defaultConfig = DefaultConfig()

            // Use KeepAliveProvider.KEEP_ALIVE instead of the SSHJ default
            // (Heartbeater). The default only sends SSH_MSG_IGNORE packets
            // periodically and never inspects whether the peer is still
            // responding. KEEP_ALIVE sends SSH_MSG_GLOBAL_REQUEST and counts
            // consecutive unanswered requests; after [keepAliveMaxCount]
            // misses the transport is marked dead, the disconnect listener
            // fires, and the supervisor can reconcile a reconnect.
            //
            // Without this, a half-open TCP socket (network blackhole,
            // NAT idle-timeout, device suspend mid-transfer) stays in
            // ESTABLISHED forever and every subsequent tmux list-sessions
            // /exec hangs until the OS's ~2h TCP keepalive fires. That's
            // the exact symptom users report as "sessions page is empty
            // after reconnect, must force-quit".
            defaultConfig.keepAliveProvider = KeepAliveProvider.KEEP_ALIVE

            // Apply algorithm preferences from config
            if (config.preferredCiphers.isNotEmpty()) {
                defaultConfig.cipherFactories = defaultConfig.cipherFactories
                    .filter { it.name in config.preferredCiphers }
            }
            if (config.preferredKex.isNotEmpty()) {
                defaultConfig.keyExchangeFactories = defaultConfig.keyExchangeFactories
                    .filter { it.name in config.preferredKex }
            }
            if (config.preferredMacs.isNotEmpty()) {
                defaultConfig.macFactories = defaultConfig.macFactories
                    .filter { it.name in config.preferredMacs }
            }
            if (config.preferredHostKeyAlgs.isNotEmpty()) {
                defaultConfig.keyAlgorithms = defaultConfig.keyAlgorithms
                    .filter { it.name in config.preferredHostKeyAlgs }
            }

            ssh = SSHClient(defaultConfig)

            // Host key verification
            ssh.addHostKeyVerifier(hostKeyVerifier)

            // Timeouts
            ssh.connectTimeout = config.connectTimeout
            ssh.timeout = config.readTimeout
            // The transport timeout governs key exchange completion.  When host
            // key verification requires user interaction (accept/reject dialog),
            // the default 30 s is too short — the user may not respond in time.
            ssh.transport.timeoutMs = config.transportTimeout

            // Keep-alive must be configured BEFORE ssh.connect(): SSHClient.onConnect()
            // runs the key-exchange handshake and then inspects
            // `connection.keepAlive.isEnabled()`. If the interval is > 0 at that
            // moment, onConnect() calls `keepAlive.start()` which spins up the
            // daemon thread that pings the peer. If we set the interval AFTER
            // onConnect (i.e. after auth as we used to), the thread never starts,
            // the interval field is updated on an idle Thread, and no keepalives
            // are ever sent — which is exactly the pre-existing bug that let
            // half-open TCP sockets hang forever after a WAN blip.
            //
            // For KeepAliveRunner we also set maxAliveCount up-front. After
            // `keepAliveMaxCount` consecutive unanswered SSH_MSG_GLOBAL_REQUESTs
            // the runner calls `conn.notifyError`, which fires the transport
            // DisconnectListener, which flips our ConnectionState to ERROR, and
            // ConnectionSupervisor then schedules a reconnect.
            if (config.keepAliveInterval > 0) {
                val keepAlive = ssh.connection.keepAlive
                keepAlive.keepAliveInterval = config.keepAliveInterval
                if (keepAlive is KeepAliveRunner && config.keepAliveMaxCount > 0) {
                    keepAlive.maxAliveCount = config.keepAliveMaxCount
                }
            }

            // Connect — either through a tunnel or directly
            if (tunnelProvider != null) {
                AppLogger.d(Category.SSH) { "Tunnelling via jump host to ${config.hostname}:${config.port}" }
                val tunnel = tunnelProvider.invoke()
                ssh.connectVia(tunnel)
            } else {
                ssh.connect(config.hostname, config.port)
            }

            AppLogger.d(Category.SSH) { "TCP connection established to ${config.hostname}:${config.port}" }

            // Authenticate
            when (val auth = config.auth) {
                is AuthConfig.Password -> {
                    AppLogger.d(Category.SSH) { "Authenticating with password for ${config.username}@${config.hostname}" }
                    ssh.authPassword(config.username, auth.password)
                }
                is AuthConfig.Key -> {
                    AppLogger.d(Category.SSH) { "Authenticating with key for ${config.username}@${config.hostname}" }
                    val keyFile = SshKeyLoader.loadKeyFile(auth.privateKeyData, auth.passphrase)
                    ssh.authPublickey(config.username, keyFile)
                }
            }

            AppLogger.i(Category.SSH) { "Authenticated successfully to ${config.username}@${config.hostname}:${config.port}" }

            // Compression
            if (config.compression) {
                ssh.useCompression()
            }

            // Remote port forwarding
            config.remoteForwards.forEach { fwd ->
                try {
                    val params = net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder.Forward(fwd.remotePort)
                    val target = net.schmizz.sshj.connection.channel.direct.Parameters(
                        fwd.localHost, fwd.localPort, "127.0.0.1", fwd.remotePort
                    )
                    ssh.remotePortForwarder.bind(params) { target }
                    AppLogger.d(Category.SSH) { "Remote forward: remote:${fwd.remotePort} -> ${fwd.localHost}:${fwd.localPort}" }
                } catch (e: Exception) {
                    AppLogger.w(Category.PFWD) {
                        "rfwd.bind ✗ remote:${fwd.remotePort} → ${fwd.localHost}:${fwd.localPort} cause='${e.message}'"
                    }
                }
            }

            // Register disconnect listener to detect transport-level disconnections
            ssh.transport.disconnectListener = DisconnectListener { reason, msg ->
                AppLogger.w(Category.SSH) {
                    "ssh.transport.disconnect host=${config.hostname}:${config.port} " +
                    "reason=$reason msg='${msg ?: ""}' → state=ERROR"
                }
                _state.value = ConnectionState.ERROR
            }

            client = ssh
            _state.value = ConnectionState.AUTHENTICATED
        } catch (e: Exception) {
            // Clean up the SSHClient if it was created but auth failed
            try { ssh?.disconnect() } catch (_: Exception) {}

            // The Supervisor classifies the exception via isAuthError(); the
            // transport state is the same ERROR for both network and auth
            // failures (auth/network distinction lives at the supervisor layer).
            _state.value = ConnectionState.ERROR
            val errorMsg = buildErrorMessage(e)
            AppLogger.e(Category.SSH, e) { "Connection failed to ${config.hostname}:${config.port}: $errorMsg" }
            throw SshException(errorMsg, e)
        }
    }

    // -------------------------------------------------------------------------
    // executeCommand()
    // -------------------------------------------------------------------------

    override suspend fun executeCommand(command: String, timeoutMs: Long): String {
        val ssh = requireAuthenticatedClient()
        val session = withContext(Dispatchers.IO) { ssh.startSession() }
        // Capture the command into an immutable local once it is created.
        // Using `cmd!!` inside the read block would NPE if the exec() call
        // above somehow returned null; keep a local non-null reference that
        // both reads can share.
        var cmd: Session.Command? = null
        try {
            val c = withContext(Dispatchers.IO) { session.exec(command) }
            cmd = c
            val stdout = withTimeout(timeoutMs) {
                runInterruptible(Dispatchers.IO) {
                    IOUtils.readFully(c.inputStream).toString(Charsets.UTF_8.name())
                }
            }
            val stderr = runInterruptible(Dispatchers.IO) {
                IOUtils.readFully(c.errorStream).toString(Charsets.UTF_8.name())
            }
            runInterruptible(Dispatchers.IO) { c.join(5, TimeUnit.SECONDS) }
            val exit = c.exitStatus
            if (exit != null && exit != 0) {
                throw SshException("Command failed (exit $exit): $stderr")
            }
            return stdout
        } finally {
            // Always close the command channel before the parent session — defends
            // against leaks when the join() above times out or runInterruptible
            // throws CancellationException.
            try { cmd?.let { withContext(Dispatchers.IO) { it.close() } } } catch (_: Throwable) {}
            try { withContext(Dispatchers.IO) { session.close() } } catch (_: Throwable) {}
        }
    }

    // -------------------------------------------------------------------------
    // openSession()
    // -------------------------------------------------------------------------

    override suspend fun openSession(rows: Int, cols: Int, command: String): SshSession = withContext(Dispatchers.IO) {
        val ssh = requireAuthenticatedClient()
        try {
            val session: Session = ssh.startSession()

            // Request environment variables (most servers restrict this via AcceptEnv)
            config.envVars.forEach { (key, value) ->
                try { session.setEnvVar(key, value) } catch (_: Exception) {} // allow-bypass-D5: AcceptEnv-restricted is the common case; logging every rejected env var would flood
            }

            session.allocatePTY(config.termType, cols, rows, 0, 0, emptyMap())

            // Run [command] directly inside the PTY via SSH `exec` request.
            // This skips the user's login shell entirely — when the command
            // exits, the channel closes; the user is not dropped into a free
            // shell. The whole app uses tmux, so this is always a tmux
            // invocation supplied by [com.tmuxes.tmux.TmuxManager].
            val cmd = session.exec(command)

            AppLogger.d(Category.SSH) { "Opened session on ${config.hostname}:${config.port} (${cols}x${rows}, term=${config.termType}, cmd=$command)" }
            SshSession(session, cmd)
        } catch (e: Exception) {
            AppLogger.e(Category.SSH, e) { "Failed to open session on ${config.hostname}:${config.port}" }
            throw SshException("Failed to open session: ${e.message}", e)
        }
    }

    // -------------------------------------------------------------------------
    // setupLocalPortForward()
    // -------------------------------------------------------------------------

    override suspend fun setupLocalPortForward(
        localPort: Int,
        remoteHost: String,
        remotePort: Int
    ): PortForwardHandle = withContext(Dispatchers.IO) {
        val ssh = requireAuthenticatedClient()
        val params = net.schmizz.sshj.connection.channel.direct.Parameters(
            "127.0.0.1", localPort, remoteHost, remotePort
        )

        // Bind first; if bind fails, nothing to clean up.
        val serverSocket = ServerSocket()
        serverSocket.reuseAddress = true
        try {
            serverSocket.bind(InetSocketAddress("127.0.0.1", localPort))
        } catch (e: Exception) {
            try { serverSocket.close() } catch (_: IOException) {}
            AppLogger.e(Category.SSH, e) { "Port forward bind failed: $localPort -> $remoteHost:$remotePort" }
            throw SshException(
                "Failed to bind local port $localPort: ${e.message}",
                e
            )
        }

        // Bind succeeded — every error path from here MUST close serverSocket.
        try {
            val forwarder = ssh.newLocalPortForwarder(params, serverSocket)

            AppLogger.i(Category.SSH) { "Port forward: localhost:$localPort -> $remoteHost:$remotePort via ${config.hostname}" }

            // Run the accept loop in a background coroutine
            val job = scope.launch(Dispatchers.IO) {
                try {
                    forwarder.listen()
                } catch (_: IOException) {
                    // Forwarder was closed (ServerSocket closed to unblock accept())
                }
            }

            val handle = DirectPortForwardHandle(
                localPort = localPort,
                remoteHost = remoteHost,
                remotePort = remotePort,
                serverSocket = serverSocket,
                acceptJob = job
            )

            synchronized(portForwardHandles) {
                portForwardHandles.add(handle)
            }

            handle
        } catch (e: Exception) {
            try { serverSocket.close() } catch (_: IOException) {}
            AppLogger.e(Category.SSH, e) { "Port forward setup failed: $localPort -> $remoteHost:$remotePort" }
            throw SshException(
                "Failed to set up port forward $localPort -> $remoteHost:$remotePort: ${e.message}",
                e
            )
        }
    }

    // -------------------------------------------------------------------------
    // openChannel()
    // -------------------------------------------------------------------------

    override suspend fun openChannel(command: String): SshChannel = withContext(Dispatchers.IO) {
        val ssh = requireAuthenticatedClient()
        try {
            val session: Session = ssh.startSession()
            val cmd = session.exec(command)
            AppLogger.d(Category.SSH) { "Opened channel for command on ${config.hostname}: $command" }
            SshjChannel(session, cmd)
        } catch (e: Exception) {
            AppLogger.e(Category.SSH, e) { "Failed to open channel on ${config.hostname}" }
            throw SshException("Failed to open channel: ${e.message}", e)
        }
    }

    // -------------------------------------------------------------------------
    // createTunnel() — internal, not on interface
    // -------------------------------------------------------------------------

    /**
     * Create a direct-tcpip channel through this connection for jump-host tunneling.
     *
     * Used by the ConnectionSupervisor to provide a [tunnelProvider] to child
     * [DirectSshConnection] instances.
     */
    fun createTunnel(host: String, port: Int): DirectConnection {
        val ssh = requireAuthenticatedClient()
        return ssh.newDirectConnection(host, port)
    }

    // -------------------------------------------------------------------------
    // disconnect()
    // -------------------------------------------------------------------------

    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        AppLogger.i(Category.SSH) { "Disconnecting from ${config.hostname}:${config.port}" }

        // Close all tracked port forward handles
        synchronized(portForwardHandles) {
            for (handle in portForwardHandles) {
                try { handle.close() } catch (_: Exception) {}
            }
            portForwardHandles.clear()
        }

        // Cancel the per-connection scope so any orphaned coroutines (port
        // forward accept loops, etc.) are torn down.
        try { scope.cancel() } catch (_: Exception) {}

        try {
            client?.disconnect()
        } catch (_: IOException) {
            // Ignore errors during disconnect
        } finally {
            client = null
            _state.value = ConnectionState.DISCONNECTED
            AppLogger.d(Category.SSH) { "Disconnected from ${config.hostname}:${config.port}" }
        }
    }

    // -------------------------------------------------------------------------
    // isConnected()
    // -------------------------------------------------------------------------

    override fun isConnected(): Boolean {
        return _state.value == ConnectionState.AUTHENTICATED && client?.isConnected == true
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun requireAuthenticatedClient(): SSHClient {
        val ssh = client
        if (ssh == null || _state.value != ConnectionState.AUTHENTICATED) {
            throw SshException("Not connected or not authenticated (state: ${_state.value})")
        }
        if (!ssh.isConnected) {
            _state.value = ConnectionState.DISCONNECTED
            throw SshException("SSH connection has been lost")
        }
        return ssh
    }

    private fun buildErrorMessage(e: Exception): String {
        return when {
            e is net.schmizz.sshj.userauth.UserAuthException ->
                "Authentication failed for '${config.username}@${config.hostname}:${config.port}': ${e.message}"
            e is net.schmizz.sshj.transport.TransportException ->
                "Transport error connecting to '${config.hostname}:${config.port}': ${e.message}"
            e is java.net.ConnectException ->
                "Could not connect to '${config.hostname}:${config.port}': host unreachable or connection refused"
            e is java.net.UnknownHostException ->
                "Unknown host: '${config.hostname}'"
            e is java.net.SocketTimeoutException ->
                "Connection to '${config.hostname}:${config.port}' timed out"
            e is IOException ->
                "I/O error connecting to '${config.hostname}:${config.port}': ${e.message}"
            else ->
                "Connection to '${config.hostname}:${config.port}' failed: ${e.message}"
        }
    }

    // -------------------------------------------------------------------------
    // Inner classes
    // -------------------------------------------------------------------------

    /**
     * Port forward handle that owns a [ServerSocket] and accept-loop [Job].
     *
     * [close] closes the ServerSocket FIRST to unblock the blocking `accept()`
     * call, then cancels the coroutine job.
     */
    private class DirectPortForwardHandle(
        override val localPort: Int,
        override val remoteHost: String,
        override val remotePort: Int,
        private val serverSocket: ServerSocket,
        private val acceptJob: Job
    ) : PortForwardHandle {

        override val isActive: Boolean
            get() = !serverSocket.isClosed && acceptJob.isActive

        override fun close() {
            // Close ServerSocket FIRST to unblock accept(), then cancel the coroutine
            try { serverSocket.close() } catch (_: IOException) {}
            acceptJob.cancel()
        }
    }

    /**
     * Raw exec channel (no PTY) wrapping SSHJ command streams.
     *
     * Suitable for bidirectional byte-level tunneling.
     */
    private class SshjChannel(
        private val session: Session,
        private val cmd: Session.Command
    ) : SshChannel {

        override val inputStream: InputStream get() = cmd.inputStream
        override val outputStream: OutputStream get() = cmd.outputStream

        override fun close() {
            try { cmd.close() } catch (_: Exception) {}
            try { session.close() } catch (_: Exception) {}
        }
    }
}
