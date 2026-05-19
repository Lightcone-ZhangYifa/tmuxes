package com.tmuxes.session

import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Logical (server, tmux-session-name) identity, independent of which SSH
 * session a particular consumer holds. Used as the cross-PTY link key in
 * [SessionCoordinator.linkedWidgetSize] / [SessionCoordinator.publishWidgetSize]
 * so an in-app resize bus can yield to the widget's last-known size for
 * the same logical tmux session.
 */
data class TmuxKey(val serverId: Long, val sessionName: String)

/**
 * Per-PTY resize orchestrator. Two subtypes:
 *
 * - [InAppResizeBus] — drives the in-app SSH PTY. When the in-app view
 *   is visible, PTY size = the view's dimensions. When the in-app view
 *   is hidden, PTY size yields to the linked widget's size (so tmux
 *   doesn't downsize all clients to the in-app's stale full-size).
 *
 * - [WidgetResizeBus] — drives a widget's SSH PTY. Always tracks the
 *   widget's UI dimensions. Visibility is a no-op (widgets don't yield
 *   to anyone).
 *
 * Public API is intentionally tiny — three methods, no trigger enums.
 * `replayAfterReconnect` is internal: only [SessionCoordinator] calls it
 * after the SSH session is reattached.
 */
sealed class SessionResizeBus {

    abstract val sessionKey: SessionKey

    /**
     * The consumer's view dimensions changed (or were just learned). The
     * bus records the size and emits to the PTY if the bus's current
     * state means this size is "live" (in-app: visible; widget: always).
     */
    abstract fun setSize(cols: Int, rows: Int)

    /**
     * The consumer's visibility changed. For [InAppResizeBus]:
     *   - true → emit in-app size (yield to in-app)
     *   - false → emit linked widget size (yield to widget)
     * For [WidgetResizeBus] this is a no-op.
     */
    abstract fun setVisible(visible: Boolean)

    /** Re-emit the appropriate size after the underlying SSH PTY was recreated. */
    internal abstract fun replayAfterReconnect()
}

/**
 * In-app PTY resize bus. Implements the "yield to widget on hidden"
 * rule from the user's spec.
 */
class InAppResizeBus internal constructor(
    override val sessionKey: SessionKey,
    val tmuxKey: TmuxKey,
    private val coordinator: SessionCoordinator,
    private val scope: CoroutineScope
) : SessionResizeBus() {

    private val mutex = Mutex()
    private var visible = false
    private var inAppSize: Pair<Int, Int>? = null
    private var lastSent: Pair<Int, Int>? = null

    override fun setSize(cols: Int, rows: Int) {
        if (cols <= 0 || rows <= 0) return
        scope.launch {
            mutex.withLock {
                inAppSize = cols to rows
                if (visible) emitLocked(cols to rows, "size")
            }
        }
    }

    override fun setVisible(visible: Boolean) {
        scope.launch {
            mutex.withLock {
                this@InAppResizeBus.visible = visible
                if (visible) {
                    inAppSize?.let { emitLocked(it, "visible→inApp") }
                } else {
                    val widgetSize = coordinator.linkedWidgetSize(tmuxKey)
                    if (widgetSize != null) {
                        emitLocked(widgetSize, "hidden→widget")
                    } else {
                        AppLogger.d(Category.SESSION) {
                            "bus[in-app] $sessionKey hidden, no linked widget — no emit"
                        }
                    }
                }
            }
        }
    }

    /**
     * Called by [SessionCoordinator.publishWidgetSize] when a widget bus
     * for the same [tmuxKey] emitted a new size. If we're currently
     * hidden, yield to it.
     */
    internal fun onLinkedWidgetSizeChanged(size: Pair<Int, Int>) {
        scope.launch {
            mutex.withLock {
                if (!visible) emitLocked(size, "linkedWidget→hidden")
            }
        }
    }

    override fun replayAfterReconnect() {
        scope.launch {
            mutex.withLock {
                val target = if (visible) inAppSize else coordinator.linkedWidgetSize(tmuxKey)
                if (target != null) {
                    lastSent = null  // bypass dedup — PTY is fresh
                    emitLocked(target, "replayAfterReconnect")
                }
            }
        }
    }

    private suspend fun emitLocked(size: Pair<Int, Int>, reason: String) {
        if (size == lastSent) {
            AppLogger.d(Category.SESSION) {
                "bus[in-app] $sessionKey $reason ${size.first}x${size.second} → dedup"
            }
            return
        }
        AppLogger.i(Category.SESSION) {
            "bus[in-app] $sessionKey $reason ${size.first}x${size.second} → emit"
        }
        try {
            coordinator.resize(sessionKey, size.first, size.second)
            lastSent = size
        } catch (e: Exception) {
            AppLogger.w(Category.SESSION) {
                "bus[in-app] $sessionKey emit ✗ cause='${e.message}'"
            }
        }
    }
}

/**
 * Widget PTY resize bus. Always tracks the widget's UI dimensions.
 * Visibility / lifecycle / connection events do not affect this bus —
 * the widget renders on the home screen regardless of the app process
 * state, so its PTY should always match what the widget shows.
 */
class WidgetResizeBus internal constructor(
    override val sessionKey: SessionKey,
    val tmuxKey: TmuxKey,
    private val coordinator: SessionCoordinator,
    private val scope: CoroutineScope
) : SessionResizeBus() {

    private val mutex = Mutex()
    private var widgetSize: Pair<Int, Int>? = null
    private var lastSent: Pair<Int, Int>? = null

    override fun setSize(cols: Int, rows: Int) {
        if (cols <= 0 || rows <= 0) return
        scope.launch {
            mutex.withLock {
                widgetSize = cols to rows
                emitLocked(cols to rows, "size")
            }
        }
    }

    override fun setVisible(visible: Boolean) {
        // Widget bus does not yield to anything; visibility is irrelevant.
        AppLogger.t(Category.SESSION) {
            "bus[widget] $sessionKey setVisible($visible) — no-op"
        }
    }

    override fun replayAfterReconnect() {
        scope.launch {
            mutex.withLock {
                widgetSize?.let {
                    lastSent = null  // bypass dedup — PTY is fresh
                    emitLocked(it, "replayAfterReconnect")
                }
            }
        }
    }

    private suspend fun emitLocked(size: Pair<Int, Int>, reason: String) {
        if (size == lastSent) {
            AppLogger.d(Category.SESSION) {
                "bus[widget] $sessionKey $reason ${size.first}x${size.second} → dedup"
            }
            return
        }
        AppLogger.i(Category.SESSION) {
            "bus[widget] $sessionKey $reason ${size.first}x${size.second} → emit"
        }
        try {
            coordinator.resize(sessionKey, size.first, size.second)
            lastSent = size
            // Publish so any in-app bus on the same logical tmux session
            // can yield to this size when it goes hidden.
            coordinator.publishWidgetSize(tmuxKey, size)
        } catch (e: Exception) {
            AppLogger.w(Category.SESSION) {
                "bus[widget] $sessionKey emit ✗ cause='${e.message}'"
            }
        }
    }
}
