package com.tmuxes.ssh

import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import net.schmizz.sshj.connection.channel.direct.Session
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Wraps a PTY-attached SSHJ exec channel for interactive terminal use.
 *
 * Provides convenient read/write methods, PTY resize support, and a
 * coroutine-based reader that delivers shell output via a callback.
 *
 * The underlying [session] is always a `SessionChannel` (SSHJ's concrete
 * impl) which implements both `Session.Shell` and `Session.Command`. We
 * read/write via the [command] streams, and dispatch the SSH `window-change`
 * channel request via the same channel reflected through `Session.Shell`.
 * Casting is safe and does not depend on internal SSHJ classes — both
 * interfaces are part of the public API and the runtime object satisfies both.
 */
class SshSession(
    private val session: Session,
    private val command: Session.Command
) {

    val inputStream: InputStream get() = command.inputStream
    val outputStream: OutputStream get() = command.outputStream

    val isOpen: Boolean
        get() = command.isOpen

    @Volatile
    private var readJob: Job? = null

    @Volatile
    private var closed = false

    /**
     * Send raw bytes to the remote shell.
     */
    suspend fun write(data: ByteArray) {
        try {
            runInterruptible(Dispatchers.IO) {
                outputStream.write(data)
                outputStream.flush()
            }
        } catch (e: IOException) {
            AppLogger.w(Category.SESSION) { "session.write ✗ bytes=${data.size} cause='${e.message}'" }
            throw SshException("Failed to write to shell: ${e.message}", e)
        }
    }

    /**
     * Send a text string to the remote shell encoded as UTF-8.
     */
    suspend fun write(text: String) = write(text.toByteArray(Charsets.UTF_8))

    /**
     * Request a window size change on the remote PTY.
     */
    suspend fun resize(cols: Int, rows: Int) {
        try {
            runInterruptible(Dispatchers.IO) {
                // SessionChannel implements Session.Shell, so the same
                // channel object that holds our exec command can also
                // dispatch the SSH `window-change` request. Cast on call
                // (cheap, no allocations) instead of holding a redundant
                // typed reference.
                (session as Session.Shell).changeWindowDimensions(cols, rows, 0, 0)
            }
        } catch (e: Exception) {
            AppLogger.d(Category.SESSION) { "session.resize ✗ ${cols}x${rows} cause='${e.message}'" }
        }
    }

    /**
     * Launch a coroutine that continuously reads from the shell output
     * and invokes [onData] with each chunk of data received.
     *
     * [onEnd] is called exactly once when the read loop exits for any reason
     * (EOF, IOException, cancellation). It runs in the `finally` block so it
     * is guaranteed to fire.
     *
     * @param scope  The coroutine scope in which the reader runs.
     * @param onData Invoked on every chunk of bytes read from the shell.
     *               Called on [Dispatchers.IO].
     * @param onEnd  Called once when reading stops, regardless of the cause.
     */
    fun startReading(
        scope: CoroutineScope,
        onData: (ByteArray) -> Unit,
        onEnd: () -> Unit = {}
    ) {
        if (closed) {
            AppLogger.d(Category.SESSION) { "session.startReading skip (already closed)" }
            return
        }
        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(8192)
            var totalBytes = 0L
            var eofReason: String? = null
            try {
                while (isActive && command.isOpen && !closed) {
                    // runInterruptible bridges blocking read() with coroutine
                    // cancellation. When the transport dies (network drop,
                    // remote close), read() raises IOException — we MUST
                    // catch it here or it propagates as an uncaught exception
                    // in the appScope and Android's default handler kills
                    // the process. CancellationException is rethrown so that
                    // structured concurrency cancellation still works.
                    val n = try {
                        runInterruptible { command.inputStream.read(buffer) }
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (e: Throwable) {
                        // Transport death, shell closed, or any other IO
                        // failure — exit the loop cleanly so onEnd() runs.
                        eofReason = "io ${e.javaClass.simpleName}: ${e.message}"
                        break
                    }
                    if (n <= 0) {
                        eofReason = "EOF n=$n"
                        break
                    }
                    totalBytes += n
                    // Protect against onData() throwing (e.g., emulator
                    // buffer ops racing with shutdown). A throw here would
                    // escape the launch block and crash the app.
                    try {
                        onData(buffer.copyOf(n))
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        AppLogger.w(Category.SESSION) {
                            "session.onData callback threw, dropping ${n}B chunk: ${t.javaClass.simpleName}"
                        }
                    }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                // Final safety net. Any unexpected exception from the loop
                // body is swallowed so the launch coroutine cannot crash
                // the app. onEnd() in finally will signal EOF to callers.
                AppLogger.e(Category.SESSION, t) { "session.read crashed (final safety net)" }
            } finally {
                AppLogger.i(Category.SESSION) {
                    "session.read ← ended cause='${eofReason ?: "cancelled/closed"}' totalBytes=$totalBytes"
                }
                try { onEnd() } catch (t: Throwable) {
                    AppLogger.w(Category.SESSION) {
                        "session.onEnd listener threw cause='${t.message}' — listener bug, EOF still propagated"
                    }
                }
            }
        }
    }

    /**
     * Stop the reading coroutine and close the channel and session.
     */
    suspend fun close() = withContext(Dispatchers.IO) {
        AppLogger.d(Category.SESSION) { "session.close" }
        closed = true
        val job = readJob
        readJob = null
        job?.cancel()
        try { job?.join() } catch (_: Exception) {}
        try { command.close() } catch (_: IOException) {}
        try { session.close() } catch (_: IOException) {}
    }
}
