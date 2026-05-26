// allow-bypass-D5: hot Compose-UI path; defensive setX/bind wraps must not throw and self-logging would flood breadcrumbs
package com.tmuxes.ui.screens.terminal

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import com.tmuxes.ui.components.app.AppLazyColumn
import com.tmuxes.ui.components.app.AppLazyRow
import androidx.compose.foundation.lazy.items
import android.content.ClipData
import com.tmuxes.data.model.CommandSnippet
import com.tmuxes.data.model.SnippetLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tmuxes.util.safeLaunch
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tmuxes.i18n.I18nRuntime
import com.tmuxes.i18n.t
import com.tmuxes.util.findComponentActivity
import com.tmuxes.ui.components.app.AppRowAction
import com.tmuxes.ui.components.app.AppRowSwipe
import com.tmuxes.ui.components.app.CommandPanelSectionHeader
import com.tmuxes.ui.components.app.AppButton
import com.tmuxes.ui.components.app.AppButtonStyle
import com.tmuxes.ui.components.app.AppCard
import com.tmuxes.ui.components.app.AppCardVariant
import com.tmuxes.ui.components.app.AppDialog
import com.tmuxes.ui.components.app.AppFab
import com.tmuxes.ui.components.app.AppFabSize
import com.tmuxes.ui.components.app.AppIconButton
import com.tmuxes.ui.components.app.AppIconRole
import com.tmuxes.ui.components.app.AppTextField
import com.tmuxes.ui.components.keybar.ModifierSnapshot
import com.tmuxes.ui.design.IdentityColors
import com.tmuxes.ui.design.appTokens
import com.tmuxes.terminal.emulator.TerminalColors
import com.tmuxes.terminal.view.CursorStyle
import com.tmuxes.terminal.view.ExtraKeysBar
import com.tmuxes.terminal.view.TerminalGestureRouter
import com.tmuxes.terminal.view.TerminalView
import com.tmuxes.terminal.view.TerminalViewCallback
import com.tmuxes.terminal.view.rememberTerminalModifierLatch
import com.tmuxes.data.settings.Settings
import com.tmuxes.ui.viewmodel.SessionViewModel
import com.tmuxes.ui.viewmodel.SettingsViewModel
import com.tmuxes.ui.viewmodel.SnippetViewModel
import com.tmuxes.session.ManagedSession

private fun parseSwipeMode(key: String): TerminalGestureRouter.SwipeMode = when (key) {
    "scroll" -> TerminalGestureRouter.SwipeMode.SCROLL_LOCAL
    "arrow_keys" -> TerminalGestureRouter.SwipeMode.ARROW_KEYS
    else -> TerminalGestureRouter.SwipeMode.AUTO
}

private val BRACKETED_PASTE_PREFIX = "\u001b[200~".toByteArray(Charsets.UTF_8)
private val BRACKETED_PASTE_SUFFIX = "\u001b[201~".toByteArray(Charsets.UTF_8)

private fun SessionViewModel.sendTerminalPaste(text: String) {
    try {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val session = activeSessions.value[activeSessionKey.value]
        if (session?.emulator?.bracketedPasteMode == true) {
            sendInput(BRACKETED_PASTE_PREFIX + bytes + BRACKETED_PASTE_SUFFIX)
        } else {
            sendInput(bytes)
        }
    } catch (_: Throwable) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    serverId: Long,
    sessionName: String,
    onNavigateBack: () -> Unit,
    // Nullable-cast + fallback: `LocalContext.current as ComponentActivity`
    // throws ClassCastException during composition on Preview / test /
    // wrapped contexts (e.g. a ContextThemeWrapper), which crashes the
    // main thread. findComponentActivity() walks ContextWrappers to find
    // the real activity, and falls back to the Compose-supplied
    // ViewModelStoreOwner so lookup always succeeds in production.
    sessionViewModel: SessionViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current.findComponentActivity()
            ?: LocalViewModelStoreOwner.current!!
    ),
    settingsViewModel: SettingsViewModel = viewModel(),
    snippetViewModel: SnippetViewModel = viewModel()
) {
    val tokens = MaterialTheme.appTokens
    val activeSessions by sessionViewModel.activeSessions.collectAsState()
    val activeSessionKey by sessionViewModel.activeSessionKey.collectAsState()
    val sessionColors by sessionViewModel.sessionColors.collectAsState()
    val serverStates by sessionViewModel.serverStates.collectAsState()
    val commandHistory by sessionViewModel.commandHistory.collectAsState()
    val errorMessage by sessionViewModel.errorMessage.collectAsState()
    val enabledSnippets by snippetViewModel.enabledSnippets.collectAsState()
    val snippetLibraries by snippetViewModel.libraries.collectAsState()
    val remoteClipboard by sessionViewModel.remoteClipboard.collectAsState()
    val prefs = settingsViewModel.preferences
    val extraKeysEnabled by prefs.flow(Settings.extraKeysEnabled).collectAsState(initial = Settings.extraKeysEnabled.default)
    val fontSize by prefs.flow(Settings.terminalFontSize).collectAsState(initial = Settings.terminalFontSize.default)
    val fontFamily by prefs.flow(Settings.terminalFontFamily).collectAsState(initial = Settings.terminalFontFamily.default)
    val colorSchemeName by prefs.flow(Settings.terminalColorScheme).collectAsState(initial = Settings.terminalColorScheme.default)
    val customSchemesJson by prefs.flow(Settings.terminalCustomSchemes).collectAsState(initial = Settings.terminalCustomSchemes.default)
    val cursorStyle by prefs.flow(Settings.terminalCursorStyle).collectAsState(initial = Settings.terminalCursorStyle.default)
    val cursorBlink by prefs.flow(Settings.terminalCursorBlink).collectAsState(initial = Settings.terminalCursorBlink.default)
    val cursorBlinkSpeed by prefs.flow(Settings.terminalCursorBlinkSpeed).collectAsState(initial = Settings.terminalCursorBlinkSpeed.default)
    val cursorColor by prefs.flow(Settings.terminalCursorColor).collectAsState(initial = Settings.terminalCursorColor.default)
    val selectionColor by prefs.flow(Settings.terminalSelectionColor).collectAsState(initial = Settings.terminalSelectionColor.default)
    val scrollbackLines by prefs.flow(Settings.terminalScrollbackLines).collectAsState(initial = Settings.terminalScrollbackLines.default)
    val terminalPadding by prefs.flow(Settings.terminalPadding).collectAsState(initial = Settings.terminalPadding.default)
    val lineSpacing by prefs.flow(Settings.terminalLineSpacing).collectAsState(initial = Settings.terminalLineSpacing.default)
    val bgOpacity by prefs.flow(Settings.terminalBackgroundOpacity).collectAsState(initial = Settings.terminalBackgroundOpacity.default)
    val visualBellEnabled by prefs.flow(Settings.visualBell).collectAsState(initial = Settings.visualBell.default)
    val fontWeight by prefs.flow(Settings.terminalFontWeight).collectAsState(initial = Settings.terminalFontWeight.default)
    val boldIsBright by prefs.flow(Settings.terminalBoldIsBright).collectAsState(initial = Settings.terminalBoldIsBright.default)
    val underlineStyle by prefs.flow(Settings.terminalUnderlineStyle).collectAsState(initial = Settings.terminalUnderlineStyle.default)
    val doubleTapWordSelect by prefs.flow(Settings.doubleTapWordSelect).collectAsState(initial = Settings.doubleTapWordSelect.default)
    val verticalSwipeMode by prefs.flow(Settings.verticalSwipeMode).collectAsState(initial = Settings.verticalSwipeMode.default)
    val swipeLinesPerArrow by prefs.flow(Settings.swipeLinesPerArrow).collectAsState(initial = Settings.swipeLinesPerArrow.default)
    val terminalPinchZoomEnabled by prefs.flow(Settings.terminalPinchZoomEnabled).collectAsState(initial = Settings.terminalPinchZoomEnabled.default)
    val extraKeysHeight by prefs.flow(Settings.terminalExtraKeysHeight).collectAsState(initial = Settings.terminalExtraKeysHeight.default)
    val autoScrollOutput by prefs.flow(Settings.autoScrollOutput).collectAsState(initial = Settings.autoScrollOutput.default)
    val scrollOnKeystroke by prefs.flow(Settings.scrollOnKeystroke).collectAsState(initial = Settings.scrollOnKeystroke.default)
    val volumeKeysAction by prefs.flow(Settings.volumeKeysAction).collectAsState(initial = Settings.volumeKeysAction.default)
    val bellRingCount by sessionViewModel.bellRingCount.collectAsState()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Error display
    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        // Toast.show() can throw BadTokenException on a dying activity
        // and crashes the LaunchedEffect coroutine (which propagates to
        // the composition scope). Wrap defensively — losing an error
        // toast is strictly better than crashing the terminal screen.
        try {
            android.widget.Toast.makeText(context, I18nRuntime.t(msg), android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {}
        try { sessionViewModel.clearError() } catch (_: Throwable) {}
    }

    var showDetachDialog by remember { mutableStateOf(false) }
    var detachTargetKey by remember { mutableStateOf<String?>(null) }

    // Command panel bottom sheet
    val commandPanelController = com.tmuxes.ui.components.app.rememberAppFabBubbleController()
    val isCommandPanelOpen = commandPanelController.isOpen(TERMINAL_BUBBLE_COMMAND_PANEL)
    var commandSearch by remember { mutableStateOf("") }
    var saveToSnippetCommand by remember { mutableStateOf<String?>(null) }
    var expandedLibraryId by remember { mutableStateOf<Long?>(null) }

    // Create new session dialog
    var showCreateDialog by remember { mutableStateOf(false) }
    var newSessionName by remember { mutableStateOf("") }

    // Extra keys modifier state (hoisted so TerminalView + ExtraKeysBar share it).
    val modifierLatch = rememberTerminalModifierLatch()

    val requestedKey = remember(serverId, sessionName) {
        SessionViewModel.sessionKey(serverId, sessionName)
    }
    val requestedSession = activeSessions[requestedKey]
    val requestedSessionReady = requestedSession != null && requestedSession.isActive
    val currentKey = activeSessionKey?.takeIf { it in activeSessions } ?: requestedKey
    val currentSession = activeSessions[currentKey]
    val copyModeSessions by sessionViewModel.copyModeSessions.collectAsState()
    val isCopyModeActive = copyModeSessions.any { "${it.serverId}:${it.sessionName}" == currentKey }
    val currentHistory = commandHistory[currentKey].orEmpty()
    val filteredHistory = remember(commandSearch, currentHistory) {
        currentHistory.filter { it.contains(commandSearch, ignoreCase = true) }
    }
    val filteredSnippets = remember(commandSearch, enabledSnippets) {
        enabledSnippets.filter { es ->
            commandSearch.isBlank() ||
                es.snippet.name.contains(commandSearch, ignoreCase = true) ||
                es.snippet.command.contains(commandSearch, ignoreCase = true) ||
                es.library.name.contains(commandSearch, ignoreCase = true)
        }
    }

    val parsedCurrentKey = remember(currentKey) { SessionViewModel.parseSessionKey(currentKey) }
    val currentServerId = parsedCurrentKey?.first ?: serverId
    val currentSessionName = parsedCurrentKey?.second ?: sessionName
    val currentHistoryRefreshing by sessionViewModel.commandHistoryRefreshing.collectAsState()
    val isHistoryRefreshing = (currentHistoryRefreshing[currentKey] ?: 0) > 0

    val refreshCurrentHistory = remember(currentServerId, currentSessionName) {
        {
            scope.safeLaunch(tag = "TerminalScreen.refreshHistory") {
                sessionViewModel.refreshCommandHistory(currentServerId, currentSessionName)
            }
        }
    }

    fun openCommandPanel() {
        commandPanelController.open(TERMINAL_BUBBLE_COMMAND_PANEL)
    }

    LaunchedEffect(isCommandPanelOpen, currentKey) {
        if (isCommandPanelOpen) {
            refreshCurrentHistory()
        }
    }
    val serverStatus = serverStates[serverId]?.status

    // Attach when the server is connected; re-triggers on reconnection.
    LaunchedEffect(serverId, sessionName, serverStatus) {
        if (serverStatus == com.tmuxes.ssh.ServerStatus.CONNECTED) {
            sessionViewModel.attachSession(serverId, sessionName)
        }
    }

    LaunchedEffect(requestedKey, requestedSessionReady) {
        if (requestedSessionReady) {
            sessionViewModel.switchSession(requestedKey)
        }
    }

    // Detach when the screen leaves composition.
    DisposableEffect(serverId, sessionName) {
        onDispose {
            try {
                sessionViewModel.detachSession(
                    SessionViewModel.sessionKey(serverId, sessionName)
                )
            } catch (_: Throwable) {}
        }
    }

    // Keep screen on (respects user setting)
    val keepScreenOn by prefs.flow(Settings.keepScreenOn).collectAsState(initial = Settings.keepScreenOn.default)
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        val window = (view.context as? android.app.Activity)?.window
        try {
            if (keepScreenOn) {
                window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } catch (_: Throwable) {}
        onDispose {
            try { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } catch (_: Throwable) {}
        }
    }

    // Back = go back without detaching (session stays attached in background)
    BackHandler {
        onNavigateBack()
    }

    // Detach confirmation dialog (only when user explicitly taps disconnect)
    if (showDetachDialog) {
        val targetKey = detachTargetKey ?: currentKey
        val targetSession = activeSessions[targetKey]
        val detachMessage = if (targetSession != null) {
            t(
                "Close the SSH channel to \"{session}\" on {server}? The tmux session will keep running on the server.",
                "session" to targetSession.sessionName,
                "server" to targetSession.serverName
            )
        } else {
            t("Detach this session?")
        }
        AppDialog(
            title = "Detach Session?",
            text = detachMessage,
            onDismiss = { showDetachDialog = false; detachTargetKey = null },
            confirmLabel = "Detach",
            confirmStyle = AppButtonStyle.Danger,
            onConfirm = {
                showDetachDialog = false
                sessionViewModel.detachSession(targetKey)
                detachTargetKey = null
                // If no more sessions, go back
                if (sessionViewModel.activeSessions.value.isEmpty()) {
                    onNavigateBack()
                }
            }
        )
    }

    // Terminal grid background color — driven by the terminal color scheme
    // (NOT the app design tokens). This is a GRID concern.
    val termBgColor = remember(colorSchemeName, customSchemesJson) {
        val scheme = TerminalColors.resolveScheme(colorSchemeName, customSchemesJson)
        Color(scheme.background)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(termBgColor)
            .imePadding()
    ) {
        // Minimal tab strip — only shown when multiple sessions are active
        if (activeSessions.size > 1) {
            MinimalTabStrip(
                activeSessions = activeSessions,
                activeSessionKey = currentKey,
                sessionColors = sessionColors,
                onSwitchSession = { key -> sessionViewModel.switchSession(key) },
                onDetachSession = { key ->
                    detachTargetKey = key
                    showDetachDialog = true
                },
                onAddSession = {
                    sessionViewModel.refreshServer(serverId)
                    openCommandPanel()
                }
            )
        }

        // Terminal view area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (currentSession != null) {
                RealTerminalView(
                    sessionState = currentSession,
                    sessionViewModel = sessionViewModel,
                    fontSize = fontSize,
                    fontFamily = fontFamily,
                    colorSchemeName = colorSchemeName,
                    customSchemesJson = customSchemesJson,
                    fontWeight = fontWeight,
                    cursorStyle = cursorStyle,
                    cursorBlink = cursorBlink,
                    cursorBlinkSpeed = cursorBlinkSpeed,
                    cursorColor = cursorColor,
                    selectionColor = selectionColor,
                    scrollbackLines = scrollbackLines,
                    terminalPadding = terminalPadding,
                    lineSpacing = lineSpacing,
                    backgroundOpacity = bgOpacity,
                    visualBellEnabled = visualBellEnabled,
                    bellRingCount = bellRingCount,
                    boldIsBright = boldIsBright,
                    underlineStyle = underlineStyle,
                    autoScrollOnOutput = autoScrollOutput,
                    scrollOnKeystroke = scrollOnKeystroke,
                    doubleTapWordSelect = doubleTapWordSelect,
                    verticalSwipeMode = verticalSwipeMode,
                    swipeLinesPerArrow = swipeLinesPerArrow,
                    pinchZoomEnabled = terminalPinchZoomEnabled,
                    isCopyModeActive = isCopyModeActive,
                    volumeKeysAction = volumeKeysAction,
                    extraKeyModifiers = modifierLatch.snapshot,
                    onModifiersConsumed = { modifierLatch.consume() },
                    onFontSizeChanged = { newSize -> settingsViewModel.set(Settings.terminalFontSize, newSize) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = t("Connecting..."),
                            style = tokens.type.bodyLarge,
                            color = tokens.colors.onSurface
                        )
                        Spacer(modifier = Modifier.height(tokens.space.sm))
                        Text(
                            text = t("Attaching to {name}", "name" to sessionName),
                            style = tokens.type.bodySmall,
                            color = tokens.colors.onSurfaceVariant
                        )
                    }
                }
            }

            // Connection-lost banner
            if (currentSession != null &&
                !currentSession.isActive &&
                !currentSession.isEnded
            ) {
                val supervisorState by sessionViewModel.serverStates.collectAsState()
                val curState = supervisorState[currentSession.serverId]
                ConnectionLostBanner(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                    sessionState = currentSession.state.collectAsState().value,
                    retryCount = curState?.retryCount ?: 0,
                    nextRetryAt = curState?.nextRetryAt
                )
            }

            // Session-ended overlay
            if (currentSession?.isEnded == true) {
                SessionEndedOverlay(
                    onCloseTab = { sessionViewModel.detachSession(currentKey) },
                    onCreateNew = { showCreateDialog = true },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Floating action buttons (bottom-end) + Command Panel bubble.
            // Configure-Widget FAB only when this session is bound to a widget.
            val widgetId = remember(serverId, sessionName) {
                com.tmuxes.widget.TerminalWidget.getAllBindings(context)
                    .entries.find { it.value.first == serverId && it.value.second == sessionName }
                    ?.key
            }
            // Phone clipboard read — refreshes whenever the bubble opens.
            // Wrapped in try/catch because primaryClip can throw SecurityException
            // when the app is briefly background during composition transitions.
            val clipboardManager = remember {
                try { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }
                catch (_: Throwable) { null }
            }
            val phoneClipboard = remember(isCommandPanelOpen) {
                if (!isCommandPanelOpen) null
                else try {
                    clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString()
                } catch (_: Throwable) { null }
            }
            // Fetch remote clipboard whenever the bubble opens (not on every
            // recomposition).
            LaunchedEffect(isCommandPanelOpen) {
                if (isCommandPanelOpen) {
                    try { sessionViewModel.fetchRemoteClipboard() } catch (_: Throwable) {}
                }
            }

            TerminalFabCluster(
                controller = commandPanelController,
                widgetId = widgetId,
                onConfigureWidget = { id ->
                    try {
                        val intent = android.content.Intent(context, com.tmuxes.widget.WidgetConfigActivity::class.java).apply {
                            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        }
                        context.startActivity(intent)
                    } catch (_: Throwable) {
                        // Never crash the terminal screen on a failed config-launch.
                    }
                },
                isCopyModeActive = isCopyModeActive,
                onToggleCopyMode = { sessionViewModel.toggleCopyMode() }
            ) {
                CommandPanelBubbleContent(
                    commandSearch = commandSearch,
                    onSearchChange = { commandSearch = it },
                    isHistoryRefreshing = isHistoryRefreshing,
                    onRefreshHistory = { refreshCurrentHistory() },
                    remoteClipboard = remoteClipboard,
                    phoneClipboard = phoneClipboard,
                    filteredSnippets = filteredSnippets,
                    filteredHistory = filteredHistory,
                    expandedLibraryId = expandedLibraryId,
                    onExpandLibrary = { expandedLibraryId = it },
                    onSendCommand = { cmd, exec ->
                        sessionViewModel.sendCommand(cmd, executeImmediately = exec)
                    },
                    onSetRemoteClipboard = { sessionViewModel.setRemoteClipboard(it) },
                    onCopyToPhoneClipboard = { text ->
                        try { clipboardManager?.setPrimaryClip(ClipData.newPlainText("Remote", text)) }
                        catch (_: Throwable) {}
                    },
                    onSaveToSnippet = { saveToSnippetCommand = it },
                    onToggleFavorite = { libId, snipId -> snippetViewModel.toggleSnippetFavorited(libId, snipId) }
                )
            }
        }

        if (extraKeysEnabled) {
            ExtraKeysBar(
                onKeyPress = { sessionViewModel.sendInput(it) },
                latch = modifierLatch,
                applicationCursorKeys = currentSession?.emulator?.applicationCursorKeys ?: false,
                keyHeight = extraKeysHeight,
                containerColor = tokens.colors.surfaceContainer,
                contentColor = tokens.colors.onSurface,
            )
        }
    }

    // SaveToSnippetDialog (inline, doesn't leave terminal)
    saveToSnippetCommand?.let { cmd ->
        SaveToSnippetDialog(
            command = cmd,
            libraries = snippetLibraries,
            onDismiss = { saveToSnippetCommand = null },
            onSave = { name, command, libraryId ->
                try {
                    snippetViewModel.addSnippet(
                        libraryId,
                        CommandSnippet(
                            id = 0L,
                            name = name,
                            command = command
                        )
                    )
                    val libName = snippetLibraries.find { it.id == libraryId }?.name ?: "library"
                    saveToSnippetCommand = null
                    try {
                        android.widget.Toast.makeText(
                            context,
                            I18nRuntime.t("Saved to \"{name}\"", "name" to libName),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } catch (_: Throwable) {}
                } catch (_: Throwable) {
                    saveToSnippetCommand = null
                }
            },
            onCreateLibrary = { name ->
                snippetViewModel.addLibrary(
                    SnippetLibrary(id = 0L, name = name)
                )
            }
        )
    }

    // Create new session dialog
    if (showCreateDialog) {
        AppDialog(
            title = "New Session",
            onDismiss = {
                showCreateDialog = false
                newSessionName = ""
            },
            confirmLabel = "Create & Open",
            onConfirm = {
                showCreateDialog = false
                sessionViewModel.createAndAttachSession(serverId, newSessionName)
                newSessionName = ""
            },
            neutralLabel = "Create",
            onNeutral = {
                showCreateDialog = false
                sessionViewModel.createSession(serverId, newSessionName)
                newSessionName = ""
            },
            neutralStyle = AppButtonStyle.Outlined,
            content = {
                Column {
                    Text(
                        text = t("Enter a name for the new tmux session, or leave blank for the default."),
                        style = tokens.type.bodySmall,
                        color = tokens.colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(tokens.space.md))
                    AppTextField(
                        value = newSessionName,
                        onValueChange = { newSessionName = it },
                        label = "Session Name",
                        placeholder = "my-session",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }
}


/**
 * Body of the Terminal command-panel bubble. Hosts search input, both
 * clipboards (remote + phone), favorites, snippets (grouped by library),
 * and shell history.
 *
 * Tap contract: rows insert on tap. Swipe actions are reserved for secondary
 * item metadata, such as favorite or saving a history entry.
 */
@Composable
private fun CommandPanelBubbleContent(
    commandSearch: String,
    onSearchChange: (String) -> Unit,
    isHistoryRefreshing: Boolean,
    onRefreshHistory: () -> Unit,
    remoteClipboard: String?,
    phoneClipboard: String?,
    filteredSnippets: List<com.tmuxes.data.model.EnabledSnippet>,
    filteredHistory: List<String>,
    expandedLibraryId: Long?,
    onExpandLibrary: (Long?) -> Unit,
    onSendCommand: (String, Boolean) -> Unit,
    onSetRemoteClipboard: (String) -> Unit,
    onCopyToPhoneClipboard: (String) -> Unit,
    onSaveToSnippet: (String) -> Unit,
    onToggleFavorite: (libraryId: Long, snippetId: Long) -> Unit
) {
    val tokens = MaterialTheme.appTokens

    AppLazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = tokens.space.md,
            vertical = tokens.space.sm
        ),
        verticalArrangement = Arrangement.spacedBy(tokens.space.sm)
    ) {
        item {
            CommandPanelHeader(
                isHistoryRefreshing = isHistoryRefreshing,
                onRefreshHistory = onRefreshHistory
            )
        }

        item {
            CommandPanelSearchField(
                value = commandSearch,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ---- Clipboard ----
        item { CommandPanelSectionHeader("Clipboard") }
        item {
            CommandPanelClipboardPair(
                remoteClipboard = remoteClipboard,
                phoneClipboard = phoneClipboard,
                onInsertRemote = {
                    val text = remoteClipboard
                    if (!text.isNullOrBlank()) onSendCommand(text, false)
                },
                onInsertPhone = {
                    val text = phoneClipboard
                    if (!text.isNullOrBlank()) onSendCommand(text, false)
                },
                onSwap = { remoteBefore, phoneBefore ->
                    onSetRemoteClipboard(phoneBefore)
                    onCopyToPhoneClipboard(remoteBefore)
                }
            )
        }

        // ---- Favorites ----
        item { CommandPanelSectionHeader("Favorites") }
        val favoriteSnippets = filteredSnippets.filter { it.snippet.isFavorited }
        if (favoriteSnippets.isEmpty()) {
            item {
                CommandPanelEmptyText("No favorites yet")
            }
        } else {
            items(favoriteSnippets, key = { "fav_${it.snippet.id}" }) { es ->
                CommandPanelSnippetRow(
                    name = es.snippet.name,
                    command = es.snippet.command,
                    isFavorited = true,
                    onInsert = { onSendCommand(es.snippet.command, false) },
                    onToggleFavorite = { onToggleFavorite(es.library.id, es.snippet.id) }
                )
            }
        }

        // ---- Snippets ----
        item { CommandPanelSectionHeader("Snippets") }
        if (filteredSnippets.isEmpty()) {
            item {
                CommandPanelEmptyText(if (commandSearch.isBlank()) "No snippets" else "No matching snippets")
            }
        } else {
            val grouped = filteredSnippets.groupBy { it.library.id to it.library.name }
            grouped.forEach { (libKey, libSnippets) ->
                val (libId, libName) = libKey
                val isExpanded = expandedLibraryId == libId

                item(key = "lib_header_$libId") {
                    CommandPanelLibraryHeader(
                        name = libName,
                        count = libSnippets.size,
                        isExpanded = isExpanded,
                        onClick = { onExpandLibrary(if (isExpanded) null else libId) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (isExpanded) {
                    items(libSnippets, key = { "snippet_${it.snippet.id}" }) { es ->
                        CommandPanelSnippetRow(
                            name = es.snippet.name,
                            command = es.snippet.command,
                            isFavorited = es.snippet.isFavorited,
                            onInsert = { onSendCommand(es.snippet.command, false) },
                            onToggleFavorite = { onToggleFavorite(es.library.id, es.snippet.id) }
                        )
                    }
                }
            }
        }

        // ---- History ----
        item { CommandPanelSectionHeader("History") }
        if (filteredHistory.isEmpty()) {
            item {
                CommandPanelEmptyText(if (commandSearch.isBlank()) "No shell history yet" else "No matching history")
            }
        } else {
            items(filteredHistory.reversed(), key = { it }) { command ->
                CommandPanelHistoryRow(
                    command = command,
                    onInsert = { onSendCommand(command, false) },
                    onSaveToSnippets = { onSaveToSnippet(command) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(tokens.space.sm)) }
    }
}

@Composable
private fun CommandPanelHeader(
    isHistoryRefreshing: Boolean,
    onRefreshHistory: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(tokens.colors.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Code,
                contentDescription = null,
                tint = tokens.colors.onPrimaryContainer,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(modifier = Modifier.width(tokens.space.sm))
        Text(
            text = t("Command Panel"),
            style = tokens.type.titleMedium,
            color = tokens.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isHistoryRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = tokens.colors.primary
            )
        } else {
            AppIconButton(
                icon = Icons.Filled.Refresh,
                onClick = onRefreshHistory,
                contentDescription = "Refresh history",
                role = AppIconRole.Primary
            )
        }
    }
}

@Composable
private fun CommandPanelSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = MaterialTheme.appTokens
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(tokens.shape.sm)
            .background(tokens.colors.surface)
            .border(1.dp, tokens.colors.outlineVariant, tokens.shape.sm)
            .padding(horizontal = tokens.space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = tokens.colors.outline,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(tokens.space.sm))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = tokens.type.bodySmall.copy(color = tokens.colors.onSurface),
            cursorBrush = SolidColor(tokens.colors.primary),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = t("Search snippets or history"),
                            color = tokens.colors.outline,
                            style = tokens.type.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            }
        )
        if (value.isNotEmpty()) {
            Spacer(modifier = Modifier.width(tokens.space.xs))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = t("Clear search"),
                tint = tokens.colors.outline,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onValueChange("") }
            )
        }
    }
}

@Composable
private fun CommandPanelEmptyText(text: String) {
    val tokens = MaterialTheme.appTokens
    Text(
        text = t(text),
        color = tokens.colors.outline,
        style = tokens.type.bodySmall,
        modifier = Modifier.padding(horizontal = tokens.space.xs)
    )
}

@Composable
private fun CommandPanelLibraryHeader(
    name: String,
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = MaterialTheme.appTokens
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(tokens.shape.sm)
            .background(tokens.colors.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = tokens.type.labelMedium,
            color = tokens.colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = count.toString(),
            style = tokens.type.labelSmall,
            color = tokens.colors.outline
        )
        Spacer(modifier = Modifier.width(tokens.space.xs))
        Icon(
            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = t(if (isExpanded) "Collapse" else "Expand"),
            tint = tokens.colors.outline,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun CommandPanelSnippetRow(
    name: String,
    command: String,
    isFavorited: Boolean = false,
    onInsert: () -> Unit,
    onToggleFavorite: () -> Unit = {}
) {
    val tokens = MaterialTheme.appTokens
    AppRowSwipe(
        modifier = Modifier.fillMaxWidth(),
        actions = listOf(
            AppRowAction(
                icon = if (isFavorited) Icons.Filled.Star else Icons.Filled.StarBorder,
                color = tokens.status.warning,
                onClick = onToggleFavorite,
                label = if (isFavorited) "Unfav" else "Fav"
            )
        )
    ) {
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onInsert,
            variant = AppCardVariant.Outlined,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = tokens.space.md,
                vertical = tokens.space.sm
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFavorited) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = t("Favorited"),
                        tint = tokens.status.warning,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(tokens.space.xs))
                }
                if (name == command) {
                    // Short command: show only one line in monospace
                    Text(
                        text = command,
                        color = tokens.colors.onSurface,
                        style = tokens.type.monoSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    // Friendly name + command subtitle
                    Column {
                        Text(
                            text = name,
                            color = tokens.colors.onSurface,
                            style = tokens.type.bodyMedium
                        )
                        Text(
                            text = command,
                            color = tokens.colors.outline,
                            style = tokens.type.monoSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandPanelClipboardPair(
    remoteClipboard: String?,
    phoneClipboard: String?,
    onInsertRemote: () -> Unit,
    onInsertPhone: () -> Unit,
    onSwap: (remoteBefore: String, phoneBefore: String) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val canSwap = !remoteClipboard.isNullOrBlank() && !phoneClipboard.isNullOrBlank()
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        variant = AppCardVariant.Outlined,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(tokens.space.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CommandPanelClipboardPane(
                label = "Remote",
                content = remoteClipboard,
                emptyText = "Remote clipboard empty",
                onInsert = onInsertRemote,
                modifier = Modifier.weight(1f)
            )
            AppIconButton(
                icon = Icons.Filled.SwapHoriz,
                onClick = {
                    val remoteBefore = remoteClipboard
                    val phoneBefore = phoneClipboard
                    if (!remoteBefore.isNullOrBlank() && !phoneBefore.isNullOrBlank()) {
                        onSwap(remoteBefore, phoneBefore)
                    }
                },
                contentDescription = "Swap clipboards",
                enabled = canSwap,
                role = AppIconRole.Primary,
                modifier = Modifier.padding(horizontal = tokens.space.xs)
            )
            CommandPanelClipboardPane(
                label = "Phone",
                content = phoneClipboard,
                emptyText = "Clipboard empty",
                onInsert = onInsertPhone,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CommandPanelClipboardPane(
    label: String,
    content: String?,
    emptyText: String,
    onInsert: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = MaterialTheme.appTokens
    val hasContent = !content.isNullOrBlank()
    Column(
        modifier = modifier
            .height(88.dp)
            .clip(tokens.shape.sm)
            .background(tokens.colors.surfaceContainer)
            .clickable(enabled = hasContent, onClick = onInsert)
            .padding(tokens.space.sm),
        verticalArrangement = Arrangement.spacedBy(tokens.space.xs)
    ) {
        Text(
            text = t(label),
            color = tokens.colors.primary,
            style = tokens.type.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (hasContent) content.orEmpty() else t(emptyText),
            color = if (hasContent) tokens.colors.onSurface else tokens.colors.outline,
            style = tokens.type.monoSmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SaveToSnippetDialog(
    command: String,
    libraries: List<SnippetLibrary>,
    onDismiss: () -> Unit,
    onSave: (name: String, command: String, libraryId: Long) -> Unit,
    onCreateLibrary: (name: String) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    // Auto-suggest name: skip common prefixes (sudo, env, nohup, etc.)
    val suggestedName = remember(command) {
        val skipPrefixes = setOf("sudo", "env", "nohup", "time", "nice", "strace", "ltrace")
        command.trim().split("\\s+".toRegex())
            .dropWhile { it in skipPrefixes }
            .firstOrNull()?.take(30) ?: "snippet"
    }
    var name by remember { mutableStateOf(suggestedName) }
    var editedCommand by remember { mutableStateOf(command) }
    var selectedLibraryId by remember(libraries) {
        mutableStateOf(libraries.firstOrNull()?.id ?: 0L)
    }

    // Auto-select newly created library
    LaunchedEffect(libraries) {
        val newest = libraries.maxByOrNull { it.id }
        if (newest != null && selectedLibraryId == 0L) {
            selectedLibraryId = newest.id
        }
    }
    var showNewLibraryField by remember { mutableStateOf(false) }
    var newLibraryName by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val canSave = name.isNotBlank() && editedCommand.isNotBlank() && selectedLibraryId > 0L

    AppDialog(
        title = "Save to Snippet",
        onDismiss = onDismiss,
        confirmLabel = if (selectedLibraryId > 0L) "Save" else "Create a library first",
        confirmEnabled = canSave,
        onConfirm = {
            if (canSave) {
                onSave(name.trim(), editedCommand.trim(), selectedLibraryId)
            }
        },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.space.md)) {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Name",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = editedCommand,
                    onValueChange = { editedCommand = it },
                    label = "Command",
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                // Library dropdown
                Box {
                    AppTextField(
                        value = libraries.find { it.id == selectedLibraryId }?.name ?: t("Select library"),
                        onValueChange = {},
                        label = "Library",
                        singleLine = true,
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true }
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        libraries.forEach { lib ->
                            DropdownMenuItem(
                                text = { Text(lib.name) },
                                onClick = {
                                    selectedLibraryId = lib.id
                                    expanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(t("+ New Library"), color = tokens.colors.primary) },
                            onClick = {
                                expanded = false
                                showNewLibraryField = true
                            }
                        )
                    }
                }
                if (showNewLibraryField) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppTextField(
                            value = newLibraryName,
                            onValueChange = { newLibraryName = it },
                            label = "New library name",
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        com.tmuxes.ui.components.app.AppButton(
                            text = "Create",
                            style = com.tmuxes.ui.components.app.AppButtonStyle.Text,
                            onClick = {
                                if (newLibraryName.isNotBlank()) {
                                    onCreateLibrary(newLibraryName.trim())
                                    showNewLibraryField = false
                                    newLibraryName = ""
                                }
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun CommandPanelHistoryRow(
    command: String,
    onInsert: () -> Unit,
    onSaveToSnippets: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    AppRowSwipe(
        modifier = Modifier.fillMaxWidth(),
        actions = listOf(
            AppRowAction(
                icon = Icons.Filled.Bookmark,
                color = tokens.status.info,
                onClick = onSaveToSnippets,
                label = "Save"
            )
        )
    ) {
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onInsert,
            variant = AppCardVariant.Outlined,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = tokens.space.md,
                vertical = tokens.space.sm
            )
        ) {
            Text(
                text = command,
                color = tokens.colors.onSurface,
                style = tokens.type.monoSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Connection lost banner with pulsing orange indicator
// ---------------------------------------------------------------------------

@Composable
private fun ConnectionLostBanner(
    modifier: Modifier = Modifier,
    sessionState: com.tmuxes.session.SessionState = com.tmuxes.session.SessionState.DISCONNECTED,
    retryCount: Int = 0,
    nextRetryAt: Long? = null
) {
    val tokens = MaterialTheme.appTokens
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = tokens.motion.pulseAlphaMin,
        targetValue = tokens.motion.pulseAlphaMax,
        animationSpec = infiniteRepeatable(
            animation = tween(tokens.motion.durationPulse),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Live-ticking countdown (only meaningful while DISCONNECTED). When
    // sessionState != DISCONNECTED, ignore the countdown and show a plain
    // "Re-attaching…" copy.
    val countdownState = com.tmuxes.ui.components.app.rememberRetryCountdown(
        if (sessionState == com.tmuxes.session.SessionState.DISCONNECTED) nextRetryAt else null
    )
    val text = when (sessionState) {
        com.tmuxes.session.SessionState.RECONNECTING ->
            t("Re-attaching session…")
        com.tmuxes.session.SessionState.DISCONNECTED ->
            com.tmuxes.ui.components.app.formatCountdownText(
                seconds = countdownState.seconds,
                retryCount = retryCount,
                verbose = true
            )
        else -> t("Connection lost - reconnecting…")
    }
    val showProgress = sessionState == com.tmuxes.session.SessionState.DISCONNECTED &&
        countdownState.seconds != null && countdownState.seconds > 0 &&
        countdownState.peakSeconds > 0
    val progressFraction = if (showProgress) {
        countdownState.seconds!!.toFloat() / countdownState.peakSeconds.toFloat()
    } else 0f

    Box(
        modifier = modifier
            .background(tokens.colors.surface.copy(alpha = 0.8f)) // allow-bypass-B3: translucent-overlay (dim layer above terminal grid)
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.sm)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(tokens.space.sm)
                        .alpha(pulseAlpha)
                        .clip(CircleShape)
                        .background(tokens.colors.warning)
                )
                Spacer(modifier = Modifier.width(tokens.space.sm))
                Text(
                    text = text,
                    style = tokens.type.labelMedium,
                    color = tokens.colors.warning
                )
            }
            if (showProgress) {
                Spacer(modifier = Modifier.height(tokens.space.xxs))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp),
                    color = tokens.colors.warning.copy(alpha = 0.6f), // allow-bypass-B3: derived alpha for subtle countdown line
                    trackColor = androidx.compose.ui.graphics.Color.Transparent
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Session ended overlay
// ---------------------------------------------------------------------------

@Composable
private fun SessionEndedOverlay(
    onCloseTab: () -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = MaterialTheme.appTokens
    Box(
        modifier = modifier.background(tokens.colors.surface.copy(alpha = 0.85f)), // allow-bypass-B3: translucent-overlay ("session terminated" dim-out)
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tokens.space.lg)
        ) {
            Text(
                text = t("Session ended"),
                style = tokens.type.headlineSmall,
                color = tokens.colors.onSurface
            )
            Text(
                text = t("The tmux session has been terminated."),
                style = tokens.type.bodyMedium,
                color = tokens.colors.outline
            )
            Spacer(modifier = Modifier.height(tokens.space.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.space.md)) {
                AppButton(
                    text = "Close Tab",
                    onClick = onCloseTab,
                    style = AppButtonStyle.Outlined
                )
                AppButton(
                    text = "Create New",
                    onClick = onCreateNew,
                    style = AppButtonStyle.Primary
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Minimal tab strip (only shown when multiple sessions active)
// ---------------------------------------------------------------------------

@Composable
private fun MinimalTabStrip(
    activeSessions: Map<String, ManagedSession>,
    activeSessionKey: String?,
    sessionColors: Map<String, Int>,
    onSwitchSession: (String) -> Unit,
    onDetachSession: (String) -> Unit,
    onAddSession: () -> Unit = {}
) {
    val tokens = MaterialTheme.appTokens
    AppLazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.colors.surfaceContainer)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = tokens.space.sm, vertical = tokens.space.xs),
        horizontalArrangement = Arrangement.spacedBy(tokens.space.xxs)
    ) {
        items(activeSessions.entries.toList(), key = { it.key }) { (key, state) ->
            val isActive = key == activeSessionKey
            val identityColor = sessionColors[key]?.takeIf { it != 0 } ?: state.serverColor
            val backgroundColor = when {
                identityColor != 0 -> IdentityColors.containerColor(identityColor, tokens.colors)
                isActive -> tokens.colors.surfaceContainerHigh
                else -> Color.Transparent
            }
            val outlineColor = when {
                isActive -> tokens.colors.primary
                identityColor != 0 -> IdentityColors.outlineColor(identityColor, tokens.colors)
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .clip(tokens.shape.xs)
                    .background(backgroundColor)
                    .border(1.dp, outlineColor, tokens.shape.xs)
                    .clickable { onSwitchSession(key) }
                    .padding(horizontal = tokens.space.sm, vertical = tokens.space.xs)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) tokens.status.connected
                                else tokens.colors.surfaceContainerHighest
                            )
                    )
                    Spacer(modifier = Modifier.width(tokens.space.xs))
                    Text(
                        text = state.sessionName,
                        style = tokens.type.monoSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isActive) tokens.colors.onSurface else tokens.colors.outline
                    )
                    Spacer(modifier = Modifier.width(tokens.space.xs))
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = t("Detach {name}", "name" to state.sessionName),
                        tint = if (isActive) tokens.colors.outline else tokens.colors.outlineVariant,
                        modifier = Modifier
                            .size(12.dp)
                            .clickable { onDetachSession(key) }
                    )
                }
            }
        }

        item(key = "__add_session__") {
            Box(
                modifier = Modifier
                    .clip(tokens.shape.xs)
                    .clickable { onAddSession() }
                    .padding(horizontal = 6.dp, vertical = tokens.space.xs)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = t("Add session"),
                    tint = tokens.colors.outline,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Real TerminalView wrapper (AndroidView bridge)
// ---------------------------------------------------------------------------

private data class TerminalViewBridgeConfig(
    val fontSizeSp: Float,
    val typeface: Typeface,
    val terminalColorScheme: TerminalColors.ColorScheme,
    val cursorStyle: CursorStyle,
    val cursorBlink: Boolean,
    val cursorBlinkSpeed: Long,
    val cursorColor: Int,
    val selectionColor: Int,
    val boldIsBright: Boolean,
    val underlineStyle: String,
    val autoScrollOnOutput: Boolean,
    val scrollOnKeystroke: Boolean,
    val doubleTapWordSelect: Boolean,
    val swipeMode: TerminalGestureRouter.SwipeMode,
    val swipeLinesPerArrow: Int,
    val pinchZoomEnabled: Boolean,
    val isCopyModeActive: Boolean,
    val volumeKeysAction: String,
    val terminalPadding: Int,
    val lineSpacingMultiplier: Float,
    val backgroundOpacity: Int,
    val scrollbackLines: Int,
    val callback: TerminalViewCallback,
    val extraKeyModifiers: ModifierSnapshot,
    val onModifiersConsumed: () -> Unit,
)

private fun TerminalView.applyInputBinding(
    config: TerminalViewBridgeConfig,
    emulator: com.tmuxes.terminal.emulator.TerminalEmulator,
) {
    try { callback = config.callback } catch (_: Throwable) {}
    try { extraKeyModifiers = config.extraKeyModifiers } catch (_: Throwable) {}
    try { onModifiersConsumed = config.onModifiersConsumed } catch (_: Throwable) {}
    try { bindTerminalEmulator(emulator) } catch (_: Throwable) {}
}

private fun TerminalView.applyVisualConfig(
    config: TerminalViewBridgeConfig,
    clearDefaultCursorColor: Boolean,
    emulator: com.tmuxes.terminal.emulator.TerminalEmulator,
) {
    try { fontSizeSp = config.fontSizeSp } catch (_: Throwable) {}
    try { fontFamily = config.typeface } catch (_: Throwable) {}
    try { colorScheme = config.terminalColorScheme } catch (_: Throwable) {}
    try { cursorStyle = config.cursorStyle } catch (_: Throwable) {}
    try { cursorBlinkEnabled = config.cursorBlink } catch (_: Throwable) {}
    try { cursorBlinkSpeed = config.cursorBlinkSpeed } catch (_: Throwable) {}
    try {
        if (config.cursorColor != 0) {
            cursorColor = config.cursorColor
        } else if (clearDefaultCursorColor) {
            cursorColor = null
        }
    } catch (_: Throwable) {}
    try { if (config.selectionColor != 0) selectionHighlightColor = config.selectionColor } catch (_: Throwable) {}
    try { boldIsBright = config.boldIsBright } catch (_: Throwable) {}
    try { underlineStyle = config.underlineStyle } catch (_: Throwable) {}
    try { autoScrollOnOutput = config.autoScrollOnOutput } catch (_: Throwable) {}
    try { scrollOnKeystroke = config.scrollOnKeystroke } catch (_: Throwable) {}
    try { doubleTapWordSelect = config.doubleTapWordSelect } catch (_: Throwable) {}
    try { swipeMode = config.swipeMode } catch (_: Throwable) {}
    try { linesPerArrow = config.swipeLinesPerArrow } catch (_: Throwable) {}
    try { pinchZoomEnabled = config.pinchZoomEnabled } catch (_: Throwable) {}
    try { copyModeActive = config.isCopyModeActive } catch (_: Throwable) {}
    try { volumeKeysAction = config.volumeKeysAction } catch (_: Throwable) {}
    try { terminalPadding = config.terminalPadding } catch (_: Throwable) {}
    try { lineSpacing = config.lineSpacingMultiplier } catch (_: Throwable) {}
    try { backgroundOpacity = config.backgroundOpacity } catch (_: Throwable) {}
    try { emulator.setMaxScrollback(config.scrollbackLines) } catch (_: Throwable) {}
}

private fun TerminalView.requestInitialTerminalFocus() {
    try { isFocusable = true } catch (_: Throwable) {}
    try { isFocusableInTouchMode = true } catch (_: Throwable) {}
    try { requestFocus() } catch (_: Throwable) {}
}

@Composable
private fun RealTerminalView(
    sessionState: ManagedSession,
    sessionViewModel: SessionViewModel,
    fontSize: Int,
    fontFamily: String,
    colorSchemeName: String,
    customSchemesJson: String,
    fontWeight: String = "normal",
    cursorStyle: String = "block",
    cursorBlink: Boolean = true,
    cursorBlinkSpeed: Int = 530,
    cursorColor: Int = 0,
    selectionColor: Int = 0,
    scrollbackLines: Int = 10000,
    terminalPadding: Int = 0,
    lineSpacing: Int = 100,
    backgroundOpacity: Int = 100,
    visualBellEnabled: Boolean = false,
    bellRingCount: Long = 0L,
    boldIsBright: Boolean = false,
    underlineStyle: String = "solid",
    autoScrollOnOutput: Boolean = true,
    scrollOnKeystroke: Boolean = true,
    doubleTapWordSelect: Boolean = true,
    verticalSwipeMode: String = "auto",
    swipeLinesPerArrow: Int = 1,
    pinchZoomEnabled: Boolean = true,
    isCopyModeActive: Boolean = false,
    volumeKeysAction: String = "volume",
    extraKeyModifiers: ModifierSnapshot = ModifierSnapshot.NONE,
    onModifiersConsumed: () -> Unit = {},
    onFontSizeChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val emulator = sessionState.emulator
    val context = LocalContext.current

    val terminalColorScheme = remember(colorSchemeName, customSchemesJson) {
        TerminalColors.resolveScheme(colorSchemeName, customSchemesJson)
    }

    val typeface = remember(fontFamily, fontWeight) {
        val baseTypeface = when (fontFamily.lowercase()) {
            "jetbrains_mono", "jetbrains mono", "jetbrainsmono" ->
                TerminalView.loadJetBrainsMono(context) ?: Typeface.MONOSPACE
            "monospace" ->
                TerminalView.loadJetBrainsMono(context) ?: Typeface.MONOSPACE
            "sans_serif", "sans-serif" -> Typeface.SANS_SERIF
            "serif" -> Typeface.SERIF
            else ->
                TerminalView.loadJetBrainsMono(context) ?: Typeface.MONOSPACE
        }
        val weight = when (fontWeight.lowercase()) {
            "thin" -> 100
            "light" -> 300
            "normal" -> 400
            "medium" -> 500
            "bold" -> 700
            else -> 400
        }
        if (Build.VERSION.SDK_INT >= 28) {
            Typeface.create(baseTypeface, weight, false)
        } else if (weight >= 700) {
            Typeface.create(baseTypeface, Typeface.BOLD)
        } else {
            baseTypeface
        }
    }

    val parsedCursorStyle = remember(cursorStyle) {
        when (cursorStyle.lowercase()) {
            "underline" -> CursorStyle.UNDERLINE
            "bar" -> CursorStyle.BAR
            else -> CursorStyle.BLOCK
        }
    }

    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }

    // Visual bell: flash the terminal view when a bell event fires
    LaunchedEffect(bellRingCount) {
        if (bellRingCount > 0 && visualBellEnabled) {
            terminalViewRef?.flashVisualBell()
        }
    }

    val callback = remember(
        context,
        sessionViewModel,
        onFontSizeChanged
    ) {
        object : TerminalViewCallback {
            override fun onKeyEvent(data: ByteArray) {
                sessionViewModel.sendInput(data)
            }

            override fun onTextInput(text: String) {
                sessionViewModel.sendInput(text.toByteArray(Charsets.UTF_8))
            }

            override fun onPasteText(text: String) {
                // IME-initiated paste — wrap with bracketed-paste markers
                // when the running program has enabled bracketed paste
                // mode (DECSET 2004), so editors like vim/zsh/fish can
                // distinguish a paste from typed input. Mirrors the
                // long-press paste flow in [onPasteRequested].
                sessionViewModel.sendTerminalPaste(text)
            }

            override fun onFontSizeChanged(newSize: Float) {
                // Font size now triggers a resize via TerminalView's bus
                // automatically (the fontSizeSp setter calls requestResize).
                // We only forward the user-pref persistence here.
                onFontSizeChanged(newSize.toInt())
            }

            override fun onTextSelected(text: String) {
                // Clipboard writes can throw on some Android builds when the app
                // is briefly backgrounded. Never crash on a copy.
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as? ClipboardManager
                    val clip = android.content.ClipData.newPlainText("terminal", text)
                    clipboard?.setPrimaryClip(clip)
                } catch (_: Throwable) {}
            }

            override fun onPasteRequested() {
                // Clipboard reads throw SecurityException on Android 10+
                // when the caller is not in the foreground; never crash
                // on a paste action.
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as? ClipboardManager
                    val clipText = clipboard?.primaryClip
                        ?.getItemAt(0)?.text?.toString()
                    if (!clipText.isNullOrEmpty()) {
                        sessionViewModel.sendTerminalPaste(clipText)
                    }
                } catch (_: Throwable) {}
            }

            override fun onSendBytes(data: ByteArray, reason: String) {
                // Gesture-driven byte injection (e.g. swipe → arrow keys
                // in tmux copy-mode). Logged at TERMINAL category for
                // post-hoc tracing; routed through the same sendInput
                // path as keyboard/IME input.
                com.tmuxes.util.AppLogger.d(com.tmuxes.util.AppLogger.Category.TERMINAL) {
                    "tv.gesture.bytes($reason) ${data.size}B"
                }
                sessionViewModel.sendInput(data)
            }

        }
    }

    val bridgeConfig = TerminalViewBridgeConfig(
        fontSizeSp = fontSize.toFloat(),
        typeface = typeface,
        terminalColorScheme = terminalColorScheme,
        cursorStyle = parsedCursorStyle,
        cursorBlink = cursorBlink,
        cursorBlinkSpeed = cursorBlinkSpeed.toLong(),
        cursorColor = cursorColor,
        selectionColor = selectionColor,
        boldIsBright = boldIsBright,
        underlineStyle = underlineStyle,
        autoScrollOnOutput = autoScrollOnOutput,
        scrollOnKeystroke = scrollOnKeystroke,
        doubleTapWordSelect = doubleTapWordSelect,
        swipeMode = parseSwipeMode(verticalSwipeMode),
        swipeLinesPerArrow = swipeLinesPerArrow,
        pinchZoomEnabled = pinchZoomEnabled,
        isCopyModeActive = isCopyModeActive,
        volumeKeysAction = volumeKeysAction,
        terminalPadding = terminalPadding,
        lineSpacingMultiplier = lineSpacing / 100f,
        backgroundOpacity = backgroundOpacity,
        scrollbackLines = scrollbackLines,
        callback = callback,
        extraKeyModifiers = extraKeyModifiers,
        onModifiersConsumed = onModifiersConsumed,
    )

    // Wire the resize bus into TerminalView whenever the active session changes.
    val activeSessionKey by sessionViewModel.activeSessionKey.collectAsState()
    LaunchedEffect(activeSessionKey, terminalViewRef) {
        val tv = terminalViewRef ?: return@LaunchedEffect
        val key = activeSessionKey
        tv.resizeBus = if (key != null) sessionViewModel.getResizeBus(key) else null
    }

    DisposableEffect(Unit) {
        onDispose {
            try { terminalViewRef?.releaseInputFocus() } catch (_: Throwable) {}
        }
    }

    AndroidView(
        // Defensive wrappers: both factory and update run on the main thread
        // during composition. An uncaught exception here terminates the
        // process (TmuxesApp.installUncaughtExceptionHandler deliberately
        // does NOT swallow main-thread exceptions). TerminalView's setters
        // call updateFontMetrics / renderer.updatePaints / invalidate,
        // which allocate Paint objects and measure text; under resource
        // pressure (font subsystem hiccup, bitmap OOM, renderer race
        // with onDraw) any of them can throw and would crash the whole
        // app. The Terminal screen is the most-used screen, so an
        // unguarded setter here reads exactly like "crashes everywhere".
        factory = { ctx ->
            try {
                TerminalView(ctx).apply {
                    applyVisualConfig(
                        config = bridgeConfig,
                        clearDefaultCursorColor = false,
                        emulator = emulator,
                    )
                    applyInputBinding(bridgeConfig, emulator)
                    terminalViewRef = this

                    requestInitialTerminalFocus()
                }
            } catch (_: Throwable) {
                // Last-resort fallback: return an empty TerminalView so
                // Compose doesn't see a null factory result (which would
                // itself crash). A subsequent recomposition may succeed.
                TerminalView(ctx)
            }
        },
        update = { tv ->
            try {
                if (tv !== terminalViewRef) {
                    terminalViewRef = tv
                }
                tv.applyInputBinding(bridgeConfig, emulator)
                tv.applyVisualConfig(
                    config = bridgeConfig,
                    clearDefaultCursorColor = true,
                    emulator = emulator,
                )
            } catch (_: Throwable) {
                // Any unforeseen failure in the update path — swallow so
                // a subsequent composition can try again with fresh state.
            }
        },
        modifier = modifier
    )
}
