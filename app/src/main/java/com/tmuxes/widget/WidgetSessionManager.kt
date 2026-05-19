package com.tmuxes.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.PowerManager
import android.widget.RemoteViews
import com.tmuxes.R
import com.tmuxes.TmuxesApp
import com.tmuxes.session.AttachRequest
import com.tmuxes.session.ConsumerId
import com.tmuxes.session.ConsumerType
import com.tmuxes.session.ManagedSession
import com.tmuxes.session.SessionCoordinator
import com.tmuxes.session.SessionKey
import com.tmuxes.session.SessionState
import com.tmuxes.ssh.ServerStatus
import com.tmuxes.terminal.emulator.TerminalColors
import com.tmuxes.util.AppLogger
import com.tmuxes.util.AppLogger.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages widget-specific tmux attachments.
 *
 * Delegates all session lifecycle (attach, detach, reattach on reconnect) to
 * [SessionCoordinator]. This class only manages:
 * - Widget ID -> SessionKey mapping
 * - Bitmap rendering and coalescing
 * - Per-widget config
 * - Screen on/off awareness
 * - RemoteViews construction
 *
 * The SessionCoordinator automatically reattaches sessions when servers
 * reconnect, so no health-check polling is needed here.
 */
class WidgetSessionManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val renderer = TerminalBitmapRenderer(context)
    private val bindings = ConcurrentHashMap<Int, WidgetBinding>()

    /** Set lazily — the coordinator may not be available at construction time. */
    private var sessionCoordinator: SessionCoordinator? = null

    @Volatile
    private var isScreenOn = true

    private var observerJob: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            // onReceive runs on the main thread — any uncaught
            // exception here kills the host process. Screen on/off
            // fires many times a day, so an occasional throw from
            // renderAllWidgets (e.g. a ConcurrentModificationException
            // racing with a bindings mutation, or an AppWidgetManager
            // IPC failure mid-screen-on) would look exactly like
            // "the app crashes everywhere" because it correlates
            // with screen events rather than user actions. Wrap
            // matches the pattern of BootReceiver and all
            // AppWidgetProvider callbacks.
            try {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        isScreenOn = true
                        // Widget bus does not yield on visibility changes —
                        // it always tracks the widget's UI dimensions. Just
                        // re-render so the freshest tmux content is shown.
                        renderAllWidgets()
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOn = false
                    }
                }
            } catch (_: Throwable) {
                // Swallow — the next screen event or scheduled
                // render will retry with fresh state.
            }
        }
    }

    fun start() {
        // Each step in start() is wrapped individually so one failure
        // (e.g., registerReceiver throwing on a duplicate registration,
        // or PowerManager cast failing on an exotic device) cannot leave
        // the widget manager half-initialized and crash the foreground
        // service start path.
        try {
            val app = context.applicationContext as? TmuxesApp
            sessionCoordinator = app?.sessionCoordinator
        } catch (_: Throwable) {} // allow-bypass-D5: defensive applicationContext cast at WidgetSessionManager.start; failure leaves sessionCoordinator null and widget renders empty

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(screenReceiver, filter)
            }
        } catch (e: Throwable) {
            AppLogger.w(Category.RENDER) { "WidgetSessionManager: registerReceiver failed: ${e.message}" }
        }

        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            isScreenOn = pm?.isInteractive ?: true
        } catch (_: Throwable) {
            isScreenOn = true
        }

        try {
            val allBindings = TerminalWidget.getAllBindings(context)
            for ((widgetId, binding) in allBindings) {
                try {
                    attachWidget(widgetId, binding.first, binding.second)
                } catch (e: Throwable) {
                    AppLogger.w(Category.RENDER) { "WidgetSessionManager: attachWidget $widgetId failed: ${e.message}" }
                }
            }
            AppLogger.i(Category.RENDER) { "WidgetSessionManager: started with ${allBindings.size} bindings" }
        } catch (e: Throwable) {
            AppLogger.e(Category.RENDER, e) { "WidgetSessionManager: getAllBindings failed" }
        }

        // Observe SessionCoordinator's sessions flow for render triggers.
        // Wrap collect in try/catch with CancellationException rethrow so a
        // transient flow error doesn't permanently silence widget updates.
        val coordinator = sessionCoordinator
        if (coordinator != null) {
            observerJob = scope.launch {
                try {
                    coordinator.sessions.collect { sessions ->
                        try {
                            if (isScreenOn) {
                                for (binding in bindings.values) {
                                    val session = sessions[binding.sessionKey]
                                    if (session != null) {
                                        binding.scheduleRender()
                                        continue
                                    }
                                    // Snapshot the binding's attached
                                    // session reference ONCE — reading
                                    // it twice (hasAttachedSession +
                                    // attachedSessionRef) would race
                                    // with a concurrent reattachIfNeeded
                                    // that could set attachedSession
                                    // to null between the two calls.
                                    val attached = binding.attachedSessionRef() ?: continue
                                    // Three cases when the binding's
                                    // sessionKey isn't in the map:
                                    //   (a) iter 10 renamed the
                                    //       coordinator entry — the
                                    //       same ManagedSession is
                                    //       still in `sessions` under
                                    //       a different key. Detect
                                    //       this by ManagedSession
                                    //       identity and rename the
                                    //       binding to follow.
                                    //   (b) iter 15 closeAllForSession
                                    //       removed the entry entirely
                                    //       (kill). If the user later
                                    //       created a new tmux session
                                    //       with the same name, the
                                    //       widget should reattach to
                                    //       the new one instead of
                                    //       showing the dead ghost of
                                    //       the killed one.
                                    //   (c) kill without recreate.
                                    //       Just rerender to show the
                                    //       dead state.
                                    val renamedTo = sessions.entries
                                        .firstOrNull { (_, s) -> s === attached }?.key
                                    if (renamedTo != null && renamedTo.sessionName != binding.sessionName) {
                                        // (a) Renamed in place.
                                        binding.renameTo(renamedTo.sessionName)
                                        binding.scheduleRender()
                                        continue
                                    }
                                    // Case (b) vs (c): did a new app /
                                    // widget session with the binding's
                                    // sessionName appear in the map?
                                    // Match by (serverId, sessionName)
                                    // rather than key, since a new
                                    // ManagedSession has a different
                                    // identity.
                                    val recreatedNewId = sessions.keys.firstOrNull {
                                        it.serverId == binding.sessionKey.serverId &&
                                        it.sessionName == binding.sessionName
                                    }
                                    val attachedEnded = attached.state.value == com.tmuxes.session.SessionState.ENDED
                                    if (recreatedNewId != null && attachedEnded) {
                                        // (b) New session exists with
                                        // the same logical name —
                                        // reattach the widget to it.
                                        // reattachIfNeeded will see
                                        // ENDED state and issue a full
                                        // re-attach.
                                        binding.reattachIfNeeded()
                                    } else {
                                        binding.scheduleRender()
                                    }
                                }
                            }
                        } catch (ce: kotlinx.coroutines.CancellationException) {
                            throw ce
                        } catch (e: Throwable) {
                            AppLogger.w(Category.RENDER) { "WidgetSessionManager: render trigger failed: ${e.message}" }
                        }
                    }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // normal scope cancellation on stop()
                } catch (e: Throwable) {
                    AppLogger.e(Category.RENDER, e) { "WidgetSessionManager: sessions collect died" }
                }
            }
        }
    }

    fun stop() {
        observerJob?.cancel()
        observerJob = null
        val coordinator = sessionCoordinator
        for (binding in bindings.values) {
            if (coordinator != null) {
                coordinator.detach(binding.sessionKey, binding.consumerId)
            }
        }
        bindings.clear()
        try { context.unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        AppLogger.i(Category.RENDER) { "WidgetSessionManager: stopped" }
    }

    fun onWidgetAdded(widgetId: Int, serverId: Long, sessionName: String) {
        AppLogger.i(Category.RENDER) { "wsm.add id=$widgetId server=$serverId session='$sessionName'" }
        attachWidget(widgetId, serverId, sessionName)
    }

    fun onWidgetRemoved(widgetId: Int) {
        val binding = bindings.remove(widgetId)
        AppLogger.i(Category.RENDER) { "wsm.remove id=$widgetId hadBinding=${binding != null}" }
        if (binding == null) return
        val coordinator = sessionCoordinator ?: return
        coordinator.detach(binding.sessionKey, binding.consumerId)
        scope.launch {
            try { coordinator.detachResizeBus(binding.sessionKey, binding.consumerId) } catch (_: Throwable) {} // allow-bypass-D5: best-effort bus cleanup
        }
    }

    fun onWidgetOptionsChanged(widgetId: Int) {
        val binding = bindings[widgetId]
        if (binding != null) {
            // Size changed — detach old session and reattach with new PTY size
            binding.reattachForCurrentSize()
            return
        }

        val config = TerminalWidget.getConfig(context, widgetId)
        if (config.serverId > 0 && config.sessionName.isNotBlank()) {
            attachWidget(widgetId, config.serverId, config.sessionName)
        }
    }

    /** Called when connection state changes — trigger render for all widgets. */
    fun onConnectionChanged() {
        for (binding in bindings.values) {
            binding.reattachIfNeeded()
        }
        // SSH-side reconnects are handled by SessionCoordinator.reattach,
        // which calls each bus's internal replayAfterReconnect. Widget
        // bus does not have a visibility-driven path — it always tracks
        // the widget UI dimensions, so nothing to propagate here.
    }

    fun renderAllWidgets() {
        for (binding in bindings.values) {
            binding.scheduleRender()
        }
    }

    private fun attachWidget(widgetId: Int, serverId: Long, sessionName: String) {
        // Detach old binding if exists
        val old = bindings.remove(widgetId)
        if (old != null) {
            sessionCoordinator?.detach(old.sessionKey, old.consumerId)
        }

        val binding = WidgetBinding(widgetId, serverId, sessionName)
        bindings[widgetId] = binding
        binding.reattachForCurrentSize()
    }

    private inner class WidgetBinding(
        val widgetId: Int,
        val serverId: Long,
        sessionName: String
    ) {
        // Mutable so the coordinator-sessions observer can update the
        // binding's logical session name without recreating it. The
        // persisted widget config is updated in lock-step.
        @Volatile var sessionName: String = sessionName
            private set
        val consumerId = ConsumerId(ConsumerType.WIDGET, widgetId.toString())
        @Volatile var sessionKey: SessionKey = SessionKey(serverId, sessionName, "widget_$widgetId")
            private set

        fun renameTo(newName: String) {
            if (newName == sessionName) return
            // PERSIST FIRST, then update in-memory state. The
            // persistent widget config is the source of truth — the
            // in-memory binding fields are a cache. If saveConfig
            // throws (disk full, file system glitch), we MUST keep
            // the old in-memory name so a future reattach is
            // consistent with what the next app launch will read
            // from disk. Otherwise we'd have an in-memory rename
            // that gets reverted across an app restart, which is
            // confusing.
            try {
                val config = TerminalWidget.getConfig(context, widgetId)
                if (config.sessionName != newName) {
                    TerminalWidget.saveConfig(context, widgetId, config.copy(sessionName = newName))
                }
            } catch (_: Throwable) {
                // Persist failed — bail out, don't update memory.
                // The next coordinator.sessions emission will retry
                // through the same idempotent rename detection path.
                return
            }
            val oldKey = sessionKey
            sessionName = newName
            sessionKey = SessionKey(serverId, newName, "widget_$widgetId")
            // The cached resizeBus is keyed by oldKey in the coordinator
            // registry; force re-creation against the new key on the next
            // request. Drop the stale bus from the registry as well so it
            // doesn't linger (small but real cleanup).
            val coordinator = sessionCoordinator
            val cid = consumerId
            resizeBus = null
            if (coordinator != null) {
                scope.launch {
                    try { coordinator.detachResizeBus(oldKey, cid) } catch (_: Throwable) {} // allow-bypass-D5: best-effort cleanup of stale rename bus
                }
            }
        }

        @Volatile private var serverName: String = ""
        @Volatile private var lastRows: Int = 0
        @Volatile private var lastCols: Int = 0
        @Volatile private var attachedSession: ManagedSession? = null
        @Volatile private var resizeBus: com.tmuxes.session.SessionResizeBus? = null

        /**
         * Lazy-init the resize bus for the binding's current sessionKey.
         * Recreated if the key changes (e.g., tmux rename moved us to a
         * new key). All resize signals from the widget go through this.
         */
        private suspend fun ensureBus(): com.tmuxes.session.SessionResizeBus? {
            val coordinator = sessionCoordinator ?: return null
            val current = resizeBus
            // Bus stays valid as long as sessionKey hasn't changed since
            // creation. Rename → new bus (the old one is GC'd).
            if (current == null) {
                val fresh = coordinator.attachResizeBus(sessionKey, consumerId)
                resizeBus = fresh
                return fresh
            }
            return current
        }

        /** Push a size demand through the bus + sync the local emulator. */
        suspend fun requestResize(cols: Int, rows: Int) {
            val emulator = attachedSession?.emulator
            if (emulator != null && (cols != emulator.buffer.columns || rows != emulator.buffer.rows)) {
                try { emulator.resize(rows, cols) } catch (_: Throwable) {} // allow-bypass-D5: emulator buffer resize is best-effort; bus retries via SSH path
            }
            ensureBus()?.setSize(cols, rows)
            lastCols = cols
            lastRows = rows
        }

        /**
         * Whether this binding currently holds a reference to a
         * ManagedSession (alive, ENDED, or otherwise). Used by the
         * outer manager's coordinator-sessions observer to detect a
         * binding whose session was just removed from the coordinator
         * map (e.g., by closeAllForSession after an in-app kill) so
         * the widget can redraw to reflect the new state.
         */
        fun hasAttachedSession(): Boolean = attachedSession != null

        /**
         * Identity-only accessor for rename detection in the
         * coordinator-sessions observer. Returns the ManagedSession
         * reference (or null) so the observer can compare by identity
         * to find the binding's session under a new key after a tmux
         * rename.
         */
        fun attachedSessionRef(): ManagedSession? = attachedSession
        // Held so we can remove the listener on detach/reattach to prevent
        // accumulation across binding lifecycles.
        @Volatile private var contentListener: (() -> Unit)? = null

        private val renderLock = Any()
        private var renderScheduled = false
        private var renderPending = false

        fun scheduleRender() {
            synchronized(renderLock) {
                if (!isScreenOn) return
                if (renderScheduled) {
                    renderPending = true
                    return
                }
                renderScheduled = true
            }
            scope.launch {
                try {
                    renderWidget()
                } finally {
                    val shouldReschedule = synchronized(renderLock) {
                        renderScheduled = false
                        val pending = renderPending
                        renderPending = false
                        pending
                    }
                    if (shouldReschedule) {
                        scheduleRender()
                    }
                }
            }
        }

        /**
         * Attempt to attach via SessionCoordinator if not already attached or
         * if the PTY size changed.
         */
        fun reattachForCurrentSize() {
            scope.launch {
                try {
                    val coordinator = sessionCoordinator ?: run {
                        scheduleRender()
                        return@launch
                    }

                    val (cols, rows) = computeWidgetTerminalSize()

                    // Already attached with the same size — nothing to do.
                    val existing = attachedSession
                    if (existing != null &&
                        existing.state.value != SessionState.ENDED &&
                        cols == lastCols && rows == lastRows
                    ) {
                        existing.emulator.setMaxScrollback(
                            TerminalWidget.getConfig(context, widgetId).scrollbackLines
                        )
                        scheduleRender()
                        return@launch
                    }

                    // Existing session, only the size changed → push the
                    // demand through the resize bus (which sends the SSH
                    // window-change after dedup, and falls back to a full
                    // reattach via SessionCoordinator if the resize fails).
                    if (existing != null && existing.state.value != SessionState.ENDED) {
                        requestResize(cols, rows)
                        scheduleRender()
                        return@launch
                    }

                    // No live session — perform a full attach.
                    // Drop any previous emulator listener so we don't accumulate
                    // duplicates if attach is called multiple times for the
                    // same binding.
                    if (existing != null) {
                        contentListener?.let { existing.emulator.removeContentListener(it) }
                        contentListener = null
                        coordinator.detach(sessionKey, consumerId)
                        attachedSession = null
                    }

                    val app = context.applicationContext as? TmuxesApp
                    val server = try {
                        app?.serverRepository?.getById(serverId)
                    } catch (_: Exception) { null }
                    serverName = server?.displayName ?: ""

                    val session = coordinator.attach(
                        AttachRequest(
                            serverId = serverId,
                            sessionName = sessionName,
                            serverName = serverName,
                            serverColor = server?.color ?: 0,
                            rows = rows,
                            cols = cols,
                            scrollbackLines = TerminalWidget.getConfig(context, widgetId).scrollbackLines,
                            consumer = consumerId
                        )
                    )

                    // Register a content listener on the emulator for render triggers,
                    // saving the reference so we can remove it on the next reattach.
                    val listener: () -> Unit = { scheduleRender() }
                    session.emulator.addContentListener(listener)
                    contentListener = listener

                    attachedSession = session
                    // Initial size is recorded by the bus; widget bus does
                    // not respond to visibility (it always tracks widget UI
                    // dimensions), so no setVisible call here.
                    requestResize(cols, rows)

                    AppLogger.i(Category.RENDER) { "WidgetSessionManager: attached widget=$widgetId session=$serverId:$sessionName (${cols}x${rows})" }
                    scheduleRender()
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (e: Throwable) {
                    AppLogger.d(Category.RENDER) { "WidgetSessionManager: attach failed for widget $widgetId: ${e.message}" }
                    attachedSession = null
                    contentListener = null
                    scheduleRender()
                }
            }
        }

        /**
         * Re-attach only if the current session is dead or disconnected.
         * Called when connection state changes.
         */
        fun reattachIfNeeded() {
            val session = attachedSession
            if (session == null || session.state.value == SessionState.ENDED) {
                reattachForCurrentSize()
            } else {
                scheduleRender()
            }
        }

        suspend fun renderWidget() {
            if (!isScreenOn) return

            val mgr = AppWidgetManager.getInstance(context)
            val (widthPx, heightPx, _, _) = computeWidgetDimensions(mgr)

            val config = TerminalWidget.getConfig(context, widgetId)

            val app = context.applicationContext as? TmuxesApp
            val schemeName = try {
                config.colorScheme.ifEmpty {
                    app?.preferences?.flow(com.tmuxes.data.settings.Settings.terminalColorScheme)?.first()
                        ?: TerminalColors.DEFAULT_SCHEME_NAME
                }
            } catch (_: Exception) {
                TerminalColors.DEFAULT_SCHEME_NAME
            }
            val customSchemesJson = try {
                app?.preferences?.flow(com.tmuxes.data.settings.Settings.terminalCustomSchemes)?.first().orEmpty()
            } catch (_: Exception) {
                ""
            }
            val scheme = TerminalColors.resolveScheme(schemeName, customSchemesJson)

            val fontSize = config.fontSize
            val (renderW, renderH) = renderer.adjustDimensionsForOrientation(widthPx, heightPx, config.orientation)

            val session = attachedSession
            val sessionState = session?.state?.value
            val isConnected = session != null &&
                sessionState == SessionState.ACTIVE

            val bitmap = if (session != null) {
                val parsedCursorStyle = when (config.cursorStyle.lowercase()) {
                    "underline" -> com.tmuxes.terminal.view.CursorStyle.UNDERLINE
                    "bar" -> com.tmuxes.terminal.view.CursorStyle.BAR
                    else -> com.tmuxes.terminal.view.CursorStyle.BLOCK
                }

                val connectionStateStr = when (sessionState) {
                    SessionState.ACTIVE -> ServerStatus.CONNECTED.name
                    SessionState.RECONNECTING -> ServerStatus.CONNECTING.name
                    SessionState.DISCONNECTED -> ServerStatus.DISCONNECTED.name
                    SessionState.ENDED, null -> ServerStatus.DISCONNECTED.name
                }

                renderer.renderFromEmulator(
                    emulator = session.emulator,
                    widthPx = renderW,
                    heightPx = renderH,
                    scheme = scheme,
                    sessionName = sessionName,
                    serverName = serverName,
                    isConnected = isConnected,
                    connectionState = connectionStateStr,
                    fontSizeOverrideSp = if (fontSize > 0f) fontSize else null,
                    opacity = config.backgroundOpacity,
                    showTitleBar = config.showTitleBar,
                    fontFamily = config.fontFamily,
                    fontWeight = config.fontWeight,
                    cursorStyle = parsedCursorStyle,
                    showCursor = config.cursorBlink,
                    cursorColor = config.cursorColor,
                    titleAccentColor = config.titleAccentColor,
                    boldIsBright = config.boldIsBright,
                    underlineStyle = config.underlineStyle,
                    lineSpacing = config.lineSpacing,
                    terminalPadding = config.terminalPadding
                )
            } else {
                renderer.render(
                    capturedOutput = "",
                    widthPx = renderW,
                    heightPx = renderH,
                    scheme = scheme,
                    sessionName = sessionName,
                    serverName = serverName,
                    isConnected = false,
                    connectionState = ServerStatus.CONNECTING.name,
                    opacity = config.backgroundOpacity,
                    showTitleBar = config.showTitleBar,
                    fontFamily = config.fontFamily,
                    fontWeight = config.fontWeight,
                    showCursor = config.cursorBlink,
                    cursorColor = config.cursorColor,
                    titleAccentColor = config.titleAccentColor,
                    boldIsBright = config.boldIsBright,
                    underlineStyle = config.underlineStyle,
                    lineSpacing = config.lineSpacing,
                    terminalPadding = config.terminalPadding
                )
            }

            val finalBitmap = renderer.rotateBitmap(bitmap, config.orientation)

            val views = RemoteViews(context.packageName, R.layout.widget_terminal)
            views.setImageViewBitmap(R.id.widget_terminal_image, finalBitmap)
            views.setFloat(
                R.id.widget_terminal_image,
                "setAlpha",
                (config.opacity / 100f).coerceIn(0f, 1f)
            )

            val intent = Intent(context, com.tmuxes.MainActivity::class.java).apply {
                putExtra("server_id", serverId)
                putExtra("session_name", sessionName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                widgetId,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_terminal_image, pendingIntent)

            try {
                mgr.updateAppWidget(widgetId, views)
            } catch (_: Exception) {
                // Best effort.
            }

            // Bitmap was parceled by RemoteViews; recycle the local copy
            finalBitmap.recycle()
        }

        private fun computeWidgetTerminalSize(): Pair<Int, Int> {
            val mgr = AppWidgetManager.getInstance(context)
            val (widthPx, heightPx, _, _) = computeWidgetDimensions(mgr)
            val config = TerminalWidget.getConfig(context, widgetId)
            val (renderW, renderH) = renderer.adjustDimensionsForOrientation(widthPx, heightPx, config.orientation)
            // Cap to the same dimensions the bitmap renderer will use,
            // so PTY cols/rows match the rendered visible area exactly.
            val (capW, capH) = renderer.capDimensions(renderW, renderH)
            val effectiveFontSize = if (config.fontSize > 0f) config.fontSize
            else renderer.computeOptimalFontSize(capW, capH)
            return renderer.computeTerminalSize(
                widthPx = capW,
                heightPx = capH,
                fontSizeSp = effectiveFontSize,
                showTitleBar = config.showTitleBar,
                fontFamily = config.fontFamily,
                fontWeight = config.fontWeight,
                lineSpacing = config.lineSpacing,
                terminalPadding = config.terminalPadding
            )
        }

        private fun computeWidgetDimensions(mgr: AppWidgetManager): WidgetDimensions {
            val options = mgr.getAppWidgetOptions(widgetId)
            val density = context.resources.displayMetrics.density
            val minWidthDp = options.positiveInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
            val minHeightDp = options.positiveInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120)
            val maxWidthDp = options.positiveInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidthDp)
                .coerceAtLeast(minWidthDp)
            val maxHeightDp = options.positiveInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeightDp)
                .coerceAtLeast(minHeightDp)

            /*
             * Android reports widget bounds as an orientation range, not a
             * single exact rectangle. The real launcher frame is one of the
             * two orientation pairs:
             * - portrait:  minWidth × maxHeight
             * - landscape: maxWidth × minHeight
             *
             * Rendering at maxWidth × maxHeight creates a synthetic size that
             * is larger than the actual host frame on both axes. The ImageView
             * then scales the bitmap, while PTY cols/rows were computed from
             * the larger synthetic area, so terminal content no longer lines
             * up with the launcher resize border.
             */
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val widthDp = if (landscape) maxWidthDp else minWidthDp
            val heightDp = if (landscape) minHeightDp else maxHeightDp
            val widthPx = (widthDp * density).roundToInt().coerceAtLeast(100)
            val heightPx = (heightDp * density).roundToInt().coerceAtLeast(60)
            return WidgetDimensions(widthPx, heightPx, widthDp, heightDp)
        }
    }

    private data class WidgetDimensions(
        val widthPx: Int,
        val heightPx: Int,
        val widthDp: Int,
        val heightDp: Int
    )
}

private fun android.os.Bundle.positiveInt(key: String, fallback: Int): Int =
    getInt(key, fallback).takeIf { it > 0 } ?: fallback
