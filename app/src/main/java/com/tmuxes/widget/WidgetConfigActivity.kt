package com.tmuxes.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.tmuxes.util.findComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.tmuxes.ui.components.app.AppLazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Padding
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.tmuxes.MainActivity
import com.tmuxes.TmuxesApp
import com.tmuxes.data.model.ServerEntity
import com.tmuxes.ssh.ServerConnectionState
import com.tmuxes.ssh.ServerStatus
import com.tmuxes.ssh.HostKeyEvent
import com.tmuxes.ssh.HostKeyPromptResult
import com.tmuxes.terminal.emulator.TerminalColors
import com.tmuxes.tmux.TmuxSession
import com.tmuxes.ui.components.app.AppButton
import com.tmuxes.ui.components.app.AppButtonStyle
import com.tmuxes.ui.components.app.AppCard
import com.tmuxes.ui.components.app.AppDialog
import com.tmuxes.ui.components.app.AppFilterChip
import com.tmuxes.ui.components.app.AppIconButton
import com.tmuxes.ui.components.app.AppIconRole
import com.tmuxes.ui.components.app.AppScaffold
import com.tmuxes.ui.components.app.AppTextField
import com.tmuxes.ui.components.app.rememberAppEntryLazyListState
import com.tmuxes.ui.design.appTokens
import com.tmuxes.ui.screens.settings.ColorChipSetting
import com.tmuxes.ui.screens.settings.ColorSchemeSelector
import com.tmuxes.ui.screens.settings.CursorStyleSelector
import com.tmuxes.ui.screens.settings.FontFamilySelector
import com.tmuxes.ui.screens.settings.FontSizeSetting
import com.tmuxes.ui.screens.settings.ScrollbackSelector
import com.tmuxes.ui.components.app.AppListCard
import com.tmuxes.ui.components.app.AppHorizontalDivider
import com.tmuxes.ui.screens.settings.SettingSwitchItem
import com.tmuxes.ui.screens.settings.SettingsDropdownItem
import com.tmuxes.ui.components.app.AppSectionHeader
import com.tmuxes.ui.screens.settings.SettingsSliderItem
import com.tmuxes.data.settings.Settings
import com.tmuxes.i18n.I18nRuntime
import com.tmuxes.i18n.t
import com.tmuxes.ui.design.AppTheme
import com.tmuxes.ui.settings.SettingValueRenderer
import com.tmuxes.ui.viewmodel.SessionViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

/**
 * Configuration activity shown when the user adds a TerminalWidget to the
 * home screen, or reconfigures an existing one via long-press.
 *
 * Two sections:
 *  1. Session selection (server -> tmux session picker)
 *  2. Widget settings (font size, color scheme, orientation, font family)
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        // Everything below is defensive: this activity is launched by the
        // system launcher (not tmuxes code) with an Intent we don't fully
        // control. A corrupted config file, a missing `TmuxesApp` context,
        // or a Compose composition error must cancel the widget add flow
        // cleanly instead of crashing the launcher's add-widget trampoline.
        try {
            appWidgetId = intent?.extras?.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                finish()
                return
            }

            // Load existing config for reconfigure pre-population.
            val existingConfig = try {
                TerminalWidget.getConfig(this, appWidgetId)
            } catch (_: Throwable) {
                TerminalWidget.Companion.WidgetConfig()
            }

            setContent {
                val app = (applicationContext as TmuxesApp)
                val themeMode by app.preferences.flow(Settings.theme)
                    .collectAsState(initial = Settings.theme.default)
                val palette by app.preferences.flow(Settings.appColorPalette)
                    .collectAsState(initial = Settings.appColorPalette.default)
                val accent by app.preferences.flow(Settings.appAccentColor)
                    .collectAsState(initial = Settings.appAccentColor.default)
                val density by app.preferences.flow(Settings.appDensity)
                    .collectAsState(initial = Settings.appDensity.default)
                val typeScale by app.preferences.flow(Settings.appTypeScale)
                    .collectAsState(initial = Settings.appTypeScale.default)
                val cornerStyle by app.preferences.flow(Settings.appCornerStyle)
                    .collectAsState(initial = Settings.appCornerStyle.default)
                val statusBarStyle by app.preferences.flow(Settings.appStatusBarStyle)
                    .collectAsState(initial = Settings.appStatusBarStyle.default)
                val appLanguage by app.preferences.flow(Settings.appLanguage)
                    .collectAsState(initial = Settings.appLanguage.default)
                val bubbleOpacityPercent by app.preferences.flow(Settings.bubbleOpacity)
                    .collectAsState(initial = Settings.bubbleOpacity.default)
                val fabOpacityPercent by app.preferences.flow(Settings.fabOpacity)
                    .collectAsState(initial = Settings.fabOpacity.default)
                AppTheme(
                    themeMode = themeMode,
                    palette = palette,
                    accentArgb = accent,
                    density = density,
                    typeScale = typeScale,
                    cornerStyle = cornerStyle,
                    statusBarStyle = statusBarStyle,
                    bubbleOpacityPercent = bubbleOpacityPercent,
                    fabOpacityPercent = fabOpacityPercent
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.tmuxes.i18n.LocalI18n provides com.tmuxes.i18n.I18n(
                            com.tmuxes.i18n.AppLanguage.resolve(appLanguage)
                        )
                    ) {
                        androidx.compose.runtime.LaunchedEffect(appLanguage) {
                            com.tmuxes.i18n.I18nRuntime.setLanguage(appLanguage)
                        }
                        WidgetConfigScreen(
                            appWidgetId = appWidgetId,
                            existingConfig = existingConfig,
                            onConfirm = { config ->
                                confirmWidget(config)
                            }
                        )
                    }
                }
            }
        } catch (_: Throwable) {
            // Last-resort: cancel the add flow cleanly so the launcher
            // isn't left waiting on a dead activity.
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun confirmWidget(config: TerminalWidget.Companion.WidgetConfig) {
        try {
            TerminalWidget.saveConfig(this, appWidgetId, config)
        } catch (_: Throwable) {
            // saveGeneratedKey / saveConfig is already wrapped, but defend
            // in depth so a surprise throw can't leak out of confirmWidget
            // and crash the activity mid-finish.
        }

        try {
            // Attach this widget to a live session via WidgetSessionManager.
            (applicationContext as? TmuxesApp)?.widgetSessionManager
                ?.onWidgetAdded(appWidgetId, config.serverId, config.sessionName)
        } catch (_: Throwable) {} // allow-bypass-D5: widget attach is best-effort; widget will reconnect on next AppWidgetProvider tick

        try {
            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, result)
        } catch (_: Throwable) {
            setResult(RESULT_CANCELED)
        }
        finish()
    }
}

// ---------------------------------------------------------------------------
// Two-step selection: Server -> Session, then Widget Settings
// ---------------------------------------------------------------------------

private data class ServerWithSessions(
    val server: ServerEntity,
    val status: ServerConnectionState,
    val sessions: List<TmuxSession>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(
    appWidgetId: Int,
    existingConfig: TerminalWidget.Companion.WidgetConfig,
    onConfirm: (TerminalWidget.Companion.WidgetConfig) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as? TmuxesApp ?: return
    // findComponentActivity() handles wrapped contexts (preview,
    // ContextThemeWrapper). Fall back to the Compose-supplied
    // ViewModelStoreOwner if the activity can't be found.
    val storeOwner = context.findComponentActivity()
        ?: androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current
        ?: return
    val sessionViewModel: SessionViewModel = viewModel(
        viewModelStoreOwner = storeOwner
    )
    val servers by sessionViewModel.servers.collectAsState()
    val serverStates by sessionViewModel.serverStates.collectAsState()
    val sessions by sessionViewModel.sessions.collectAsState()
    val serverRefreshStates by sessionViewModel.serverRefreshStates.collectAsState()
    val hostKeyPrompt = app.hostKeyPrompt.collectAsState(initial = null)
    val customSchemesJson by app.preferences.flow(Settings.terminalCustomSchemes)
        .collectAsState(initial = Settings.terminalCustomSchemes.default)
    val customSchemes = remember(customSchemesJson) {
        TerminalColors.getCustomSchemes(customSchemesJson)
    }
    val contentListState = rememberAppEntryLazyListState(appWidgetId)

    val snackbarHostState = remember { SnackbarHostState() }

    // --- Session selection state ---
    val hasExistingSession = existingConfig.serverId > 0 && existingConfig.sessionName.isNotBlank()
    var selectedServerId by remember { mutableLongStateOf(if (hasExistingSession) existingConfig.serverId else 0L) }
    var selectedSessionName by remember { mutableStateOf(if (hasExistingSession) existingConfig.sessionName else "") }
    var isChangingSession by remember { mutableStateOf(false) }
    var createDialogServerId by remember { mutableStateOf<Long?>(null) }

    // --- Widget-specific settings state ---
    var selectedOpacity by remember { mutableIntStateOf(existingConfig.opacity) }
    var selectedShowTitleBar by remember { mutableStateOf(existingConfig.showTitleBar) }
    var selectedOrientation by remember { mutableIntStateOf(existingConfig.orientation) }

    // --- Terminal appearance settings state ---
    var selectedFontFamily by remember { mutableStateOf(existingConfig.fontFamily) }
    var selectedFontSize by remember { mutableFloatStateOf(existingConfig.fontSize) }
    var selectedFontWeight by remember { mutableStateOf(existingConfig.fontWeight) }
    var selectedColorScheme by remember { mutableStateOf(existingConfig.colorScheme) }
    var selectedCursorStyle by remember { mutableStateOf(existingConfig.cursorStyle) }
    var selectedCursorBlink by remember { mutableStateOf(existingConfig.cursorBlink) }
    var selectedCursorColor by remember { mutableIntStateOf(existingConfig.cursorColor) }
    var selectedTitleAccentColor by remember { mutableIntStateOf(existingConfig.titleAccentColor) }
    var selectedBackgroundOpacity by remember { mutableIntStateOf(existingConfig.backgroundOpacity) }
    var selectedBoldIsBright by remember { mutableStateOf(existingConfig.boldIsBright) }
    var selectedUnderlineStyle by remember { mutableStateOf(existingConfig.underlineStyle) }
    var selectedLineSpacing by remember { mutableIntStateOf(existingConfig.lineSpacing) }
    var selectedTerminalPadding by remember { mutableIntStateOf(existingConfig.terminalPadding) }
    var selectedScrollbackLines by remember { mutableIntStateOf(existingConfig.scrollbackLines) }

    val sessionIsSelected = selectedServerId > 0 && selectedSessionName.isNotBlank()
    // On reconfigure with existing session, show the selected card unless user taps "Change"
    val showSessionPicker = !sessionIsSelected || isChangingSession

    // Find display name for the selected server
    val selectedServerName = remember(selectedServerId, servers) {
        servers.find { it.id == selectedServerId }?.displayName ?: "Server #$selectedServerId"
    }

    // Refresh on entry
    LaunchedEffect(Unit) {
        sessionViewModel.refreshAllServers()
    }

    // Auto-refresh when servers become authenticated
    var previousAuthenticatedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(serverStates) {
        val authenticatedIds = serverStates
            .filterValues { it.status == ServerStatus.CONNECTED }
            .keys
        val newlyAuthenticated = authenticatedIds - previousAuthenticatedIds
        previousAuthenticatedIds = authenticatedIds
        for (id in newlyAuthenticated) {
            sessionViewModel.refreshServer(id)
        }
    }

    // Error display (consistent with SessionPickerScreen pattern)
    val errorMessage by sessionViewModel.errorMessage.collectAsState()
    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(I18nRuntime.t(msg))
        sessionViewModel.clearError()
    }

    val serversWithSessions = remember(servers, serverStates, sessions) {
        val sessionsByServerId = sessions.groupBy { it.serverId }
        servers.map { server ->
            ServerWithSessions(
                server = server,
                status = serverStates[server.id] ?: ServerConnectionState(ServerStatus.IDLE),
                sessions = sessionsByServerId[server.id].orEmpty()
            )
        }
    }

    WidgetHostKeyPromptDialog(app = app, promptState = hostKeyPrompt.value)

    createDialogServerId?.let { targetServerId ->
        var newSessionName by remember { mutableStateOf("") }
        AppDialog(
            title = "New Session",
            onDismiss = { createDialogServerId = null },
            confirmLabel = "Create",
            onConfirm = {
                sessionViewModel.createSession(targetServerId, newSessionName)
                createDialogServerId = null
            },
            content = {
                Column {
                    Text(
                        t("Enter a name for the new tmux session, or leave blank for the default."),
                        style = tokens.type.bodyMedium,
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

    AppScaffold(
        title = "Configure Widget",
        snackbarHostState = snackbarHostState,
        actions = {
            AppIconButton(
                icon = Icons.Default.Code,
                onClick = {
                    try {
                        val activity = context as? ComponentActivity ?: return@AppIconButton
                        val intent = Intent(activity, MainActivity::class.java).apply {
                            putExtra("navigate_to", "yaml_editor")
                            putExtra("widgetId", appWidgetId)
                        }
                        activity.startActivity(intent)
                    } catch (_: Throwable) {
                        // startActivity can throw ActivityNotFoundException
                        // or SecurityException on exotic states — never
                        // crash the widget config screen on a nav tap.
                    }
                },
                contentDescription = "Edit YAML",
                role = AppIconRole.OnSurface
            )
        },
        bottomBar = {
            // Bottom confirm button — top-rounded surface anchored bottom of screen
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(
                    topStart = tokens.space.lg,
                    topEnd = tokens.space.lg
                ),
                color = tokens.colors.surface,
                shadowElevation = tokens.elevation.level3
            ) {
                AppButton(
                    text = "Confirm",
                    onClick = {
                        onConfirm(
                            TerminalWidget.Companion.WidgetConfig(
                                serverId = selectedServerId,
                                sessionName = selectedSessionName,
                                opacity = selectedOpacity,
                                showTitleBar = selectedShowTitleBar,
                                orientation = selectedOrientation,
                                titleAccentColor = selectedTitleAccentColor,
                                fontFamily = selectedFontFamily,
                                fontSize = selectedFontSize,
                                fontWeight = selectedFontWeight,
                                colorScheme = selectedColorScheme,
                                cursorStyle = selectedCursorStyle,
                                cursorBlink = selectedCursorBlink,
                                cursorColor = selectedCursorColor,
                                backgroundOpacity = selectedBackgroundOpacity,
                                boldIsBright = selectedBoldIsBright,
                                underlineStyle = selectedUnderlineStyle,
                                lineSpacing = selectedLineSpacing,
                                terminalPadding = selectedTerminalPadding,
                                scrollbackLines = selectedScrollbackLines
                            )
                        )
                    },
                    enabled = sessionIsSelected,
                    style = AppButtonStyle.Primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(tokens.space.lg)
                )
            }
        }
    ) { padding ->
        AppLazyColumn(
            state = contentListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = tokens.space.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.space.sm)
        ) {
                // =============================================================
                // SECTION 1: Session Selection
                // =============================================================

                item(key = "session_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = t("Session").uppercase(),
                            style = tokens.type.sectionHeader,
                            color = tokens.colors.primary,
                            modifier = Modifier
                                .weight(1f)
                                .padding(
                                    top = tokens.space.lg,
                                    bottom = tokens.space.xs,
                                    start = tokens.space.xs
                                )
                        )
                        if (showSessionPicker) {
                            AppIconButton(
                                icon = Icons.Filled.Refresh,
                                onClick = { sessionViewModel.refreshAllServers() },
                                contentDescription = "Refresh sessions",
                                role = AppIconRole.OnSurfaceVariant,
                                modifier = Modifier.size(28.dpUnit())
                            )
                        }
                    }
                }

                // Show selected session card when session is selected and user
                // is NOT in "change" mode
                if (sessionIsSelected && !isChangingSession) {
                    item(key = "selected_session") {
                        SelectedSessionCard(
                            serverName = selectedServerName,
                            sessionName = selectedSessionName,
                            onChangeClick = { isChangingSession = true }
                        )
                    }
                }

                // Show session picker when no session selected or user tapped "Change"
                if (showSessionPicker) {
                    for (sws in serversWithSessions) {
                        item(key = "server_${sws.server.id}") {
                            AppCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(tokens.space.md)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Dns, null,
                                        tint = tokens.colors.primary,
                                        modifier = Modifier.size(20.dpUnit())
                                    )
                                    Spacer(Modifier.width(tokens.space.sm))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            sws.server.displayName,
                                            style = tokens.type.titleSmall,
                                            color = tokens.colors.onSurface
                                        )
                                        Text(
                                            "${sws.server.username}@${sws.server.hostname}",
                                            style = tokens.type.monoSmall,
                                            color = tokens.colors.onSurfaceVariant
                                        )
                                    }
                                    if (sws.status.status == ServerStatus.CONNECTED) {
                                        AppIconButton(
                                            icon = Icons.Filled.Add,
                                            onClick = { createDialogServerId = sws.server.id },
                                            contentDescription = "New session",
                                            role = AppIconRole.OnSurfaceVariant,
                                            modifier = Modifier.size(28.dpUnit())
                                        )
                                    }
                                    val refreshState = serverRefreshStates[sws.server.id]
                                    if (refreshState is SessionViewModel.ServerRefreshState.Loading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dpUnit()),
                                            strokeWidth = 2.dpUnit(),
                                            color = tokens.colors.primary
                                        )
                                    } else {
                                        AppIconButton(
                                            icon = Icons.Filled.Refresh,
                                            onClick = { sessionViewModel.refreshServer(sws.server.id) },
                                            contentDescription = t("Refresh {name}", "name" to sws.server.displayName),
                                            role = AppIconRole.OnSurfaceVariant,
                                            modifier = Modifier.size(28.dpUnit())
                                        )
                                    }
                                }
                            }
                        }

                        if (sws.status.status != ServerStatus.CONNECTED || sws.sessions.isEmpty()) {
                            item(key = "disc_${sws.server.id}") {
                                ServerStatusRow(
                                    server = sws.server,
                                    status = sws.status,
                                    hasSessions = sws.sessions.isNotEmpty()
                                )
                            }
                        }

                        if (sws.sessions.isNotEmpty()) {
                            items(sws.sessions, key = { "${sws.server.id}:${it.name}" }) { session ->
                                AppCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        selectedServerId = sws.server.id
                                        selectedSessionName = session.name
                                        isChangingSession = false
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(tokens.space.md)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.Terminal,
                                            null,
                                            tint = tokens.colors.tertiary,
                                            modifier = Modifier.size(20.dpUnit())
                                        )
                                        Spacer(Modifier.width(tokens.space.sm))
                                        Column {
                                            Text(
                                                session.name,
                                                style = tokens.type.mono,
                                                color = tokens.colors.onSurface
                                            )
                                            Text(
                                                t(
                                                    if (session.windowCount == 1) "{count} window" else "{count} windows",
                                                    "count" to session.windowCount
                                                ),
                                                style = tokens.type.bodySmall,
                                                color = tokens.colors.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (serversWithSessions.isEmpty()) {
                        item {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(tokens.space.xxl),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    t("No servers configured"),
                                    style = tokens.type.titleMedium,
                                    color = tokens.colors.onSurfaceVariant
                                )
                                Spacer(Modifier.height(tokens.space.sm))
                                Text(
                                    t("Add a server first, then return here to pick a tmux session."),
                                    style = tokens.type.bodyMedium,
                                    color = tokens.colors.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // =============================================================
                // SECTION 2: Widget Settings (shown after session is selected)
                // =============================================================

                if (sessionIsSelected && !isChangingSession) {

                    // ---------------------------------------------------------
                    // WIDGET section
                    // ---------------------------------------------------------

                    item(key = "widget_header") {
                        Spacer(modifier = Modifier.height(tokens.space.sm))
                        AppSectionHeader("Widget")
                    }

                    item(key = "widget_card") {
                        AppListCard {
                            SettingsSliderItem(
                                icon = Icons.Filled.Opacity,
                                title = "Overall Opacity",
                                description = "Transparency applied to the whole widget",
                                value = selectedOpacity,
                                valueRange = 0f..100f,
                                steps = 19,
                                valueLabel = { "${it}%" },
                                onValueChange = { selectedOpacity = it }
                            )

                            AppHorizontalDivider(inset = true)

                            SettingValueRenderer(
                                setting = Settings.terminalBackgroundOpacity,
                                value = selectedBackgroundOpacity,
                                onValueChange = { selectedBackgroundOpacity = it }
                            )

                            AppHorizontalDivider(inset = true)

                            SettingSwitchItem(
                                icon = Icons.Filled.Visibility,
                                title = "Show Title Bar",
                                description = "Display session name on the widget",
                                checked = selectedShowTitleBar,
                                onCheckedChange = { selectedShowTitleBar = it }
                            )

                            AppHorizontalDivider(inset = true)

                            WidgetOrientationSelector(
                                currentOrientation = selectedOrientation,
                                onOrientationChange = { selectedOrientation = it }
                            )
                        }
                    }

                    // ---------------------------------------------------------
                    // FONT section
                    // ---------------------------------------------------------

                    item(key = "font_header") {
                        Spacer(modifier = Modifier.height(tokens.space.sm))
                        AppSectionHeader("Font")
                    }

                    item(key = "font_card") {
                        AppListCard {
                            SettingValueRenderer(
                                setting = Settings.terminalFontFamily,
                                value = selectedFontFamily,
                                onValueChange = { selectedFontFamily = it }
                            )

                            AppHorizontalDivider(inset = true)

                            SettingSwitchItem(
                                icon = Icons.Filled.FormatSize,
                                title = "Auto Font Size",
                                description = "Fit terminal text to the widget size",
                                checked = selectedFontSize <= 0f,
                                onCheckedChange = { auto ->
                                    selectedFontSize = if (auto) 0f else 14f
                                }
                            )

                            if (selectedFontSize > 0f) {
                                AppHorizontalDivider(inset = true)

                                SettingValueRenderer(
                                    setting = Settings.terminalFontSize,
                                    value = selectedFontSize.roundToInt(),
                                    onValueChange = { selectedFontSize = it.toFloat() }
                                )

                                AppHorizontalDivider(inset = true)
                            } else {
                                AppHorizontalDivider(inset = true)
                            }

                            SettingValueRenderer(
                                setting = Settings.terminalFontWeight,
                                value = selectedFontWeight,
                                onValueChange = { selectedFontWeight = it }
                            )
                        }
                    }

                    // ---------------------------------------------------------
                    // COLORS section
                    // ---------------------------------------------------------

                    item(key = "colors_header") {
                        Spacer(modifier = Modifier.height(tokens.space.sm))
                        AppSectionHeader("Colors")
                    }

                    item(key = "colors_card") {
                        AppListCard {
                            ColorSchemeSelector(
                                currentScheme = selectedColorScheme,
                                customSchemes = customSchemes,
                                onSchemeChange = { selectedColorScheme = it }
                            )

                            AppHorizontalDivider(inset = true)

                            ColorChipSetting(
                                icon = Icons.Filled.Palette,
                                title = "Title Accent",
                                description = "Accent strip shown in the widget title bar",
                                currentColor = selectedTitleAccentColor,
                                onColorChange = { selectedTitleAccentColor = it }
                            )

                            AppHorizontalDivider(inset = true)

                            SettingValueRenderer(
                                setting = Settings.terminalCursorColor,
                                value = selectedCursorColor,
                                onValueChange = { selectedCursorColor = it }
                            )
                        }
                    }

                    // ---------------------------------------------------------
                    // CURSOR section
                    // ---------------------------------------------------------

                    item(key = "cursor_header") {
                        Spacer(modifier = Modifier.height(tokens.space.sm))
                        AppSectionHeader("Cursor")
                    }

                    item(key = "cursor_card") {
                        AppListCard {
                            SettingValueRenderer(
                                setting = Settings.terminalCursorStyle,
                                value = selectedCursorStyle,
                                onValueChange = { selectedCursorStyle = it }
                            )

                            AppHorizontalDivider(inset = true)

                            SettingSwitchItem(
                                icon = Icons.Filled.Visibility,
                                title = "Show Cursor",
                                description = "Render the terminal cursor in the widget preview",
                                checked = selectedCursorBlink,
                                onCheckedChange = { selectedCursorBlink = it }
                            )
                        }
                    }

                    // ---------------------------------------------------------
                    // TEXT RENDERING section
                    // ---------------------------------------------------------

                    item(key = "text_rendering_header") {
                        Spacer(modifier = Modifier.height(tokens.space.sm))
                        AppSectionHeader("Text Rendering")
                    }

                    item(key = "text_rendering_card") {
                        AppListCard {
                            SettingValueRenderer(
                                setting = Settings.terminalBoldIsBright,
                                value = selectedBoldIsBright,
                                onValueChange = { selectedBoldIsBright = it }
                            )

                            AppHorizontalDivider(inset = true)

                            SettingValueRenderer(
                                setting = Settings.terminalUnderlineStyle,
                                value = selectedUnderlineStyle,
                                onValueChange = { selectedUnderlineStyle = it }
                            )

                            AppHorizontalDivider(inset = true)

                            SettingValueRenderer(
                                setting = Settings.terminalLineSpacing,
                                value = selectedLineSpacing,
                                onValueChange = { selectedLineSpacing = it }
                            )

                            AppHorizontalDivider(inset = true)

                            SettingValueRenderer(
                                setting = Settings.terminalPadding,
                                value = selectedTerminalPadding,
                                onValueChange = { selectedTerminalPadding = it }
                            )
                        }
                    }

                    // ---------------------------------------------------------
                    // SCROLLBACK section
                    // ---------------------------------------------------------

                    item(key = "scrollback_header") {
                        Spacer(modifier = Modifier.height(tokens.space.sm))
                        AppSectionHeader("Scrollback")
                    }

                    item(key = "scrollback_card") {
                        AppListCard {
                            SettingValueRenderer(
                                setting = Settings.terminalScrollbackLines,
                                value = selectedScrollbackLines,
                                onValueChange = { selectedScrollbackLines = it }
                            )
                        }
                    }

                    // Extra bottom padding so content is not obscured by the confirm button
                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(tokens.space.lg))
                    }
                }
            }
        }
    }

// ---------------------------------------------------------------------------
// Selected session card (shown on reconfigure or after picking a session)
// ---------------------------------------------------------------------------

@Composable
private fun SelectedSessionCard(
    serverName: String,
    sessionName: String,
    onChangeClick: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = tokens.space.lg,
            vertical = tokens.space.md
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Terminal,
                contentDescription = null,
                tint = tokens.colors.primary,
                modifier = Modifier.size(20.dpUnit())
            )
            Spacer(Modifier.width(tokens.space.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sessionName,
                    style = tokens.type.mono,
                    color = tokens.colors.onSurface
                )
                Text(
                    text = t("on {server}", "server" to serverName),
                    style = tokens.type.bodySmall,
                    color = tokens.colors.onSurfaceVariant
                )
            }
            AppIconButton(
                icon = Icons.Filled.Edit,
                onClick = onChangeClick,
                contentDescription = "Change session",
                role = AppIconRole.Primary
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Widget Orientation selector (FilterChips: 0, 90, 180, 270)
// ---------------------------------------------------------------------------

@Composable
private fun WidgetOrientationSelector(
    currentOrientation: Int,
    onOrientationChange: (Int) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val orientations = listOf(0, 90, 180, 270)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.space.lg, vertical = tokens.space.md + tokens.space.xxs)
    ) {
        Text(
            text = t("Orientation"),
            style = tokens.type.bodyLarge,
            color = tokens.colors.onSurface
        )
        Spacer(modifier = Modifier.height(tokens.space.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.space.sm)
        ) {
            orientations.forEach { degrees ->
                AppFilterChip(
                    selected = currentOrientation == degrees,
            label = "${degrees}°",
                    onClick = { onOrientationChange(degrees) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Server status row (unchanged from original)
// ---------------------------------------------------------------------------

@Composable
private fun ServerStatusRow(
    server: ServerEntity,
    status: ServerConnectionState,
    hasSessions: Boolean
) {
    val tokens = MaterialTheme.appTokens
    val isErrorStatus = status.status == ServerStatus.AUTH_FAILED ||
        status.status == ServerStatus.NETWORK_ERROR ||
        status.status == ServerStatus.PARENT_FAILED
    val isConnecting = server.isEnabled &&
        status.status != ServerStatus.CONNECTED &&
        !isErrorStatus &&
        status.status != ServerStatus.PAUSED &&
        status.status != ServerStatus.NO_NETWORK

    // Error-with-scheduled-retry uses the live RetryCountdownLabel (decrements
    // each second). All other states are static one-liners.
    val errorWithCountdown = isErrorStatus && status.nextRetryAt != null
    val staticMessage = when {
        !server.isEnabled -> t("Server is paused in app settings.")
        isErrorStatus -> status.errorMessage
            ?: t("Connection failed. Tap refresh to retry now.")
        status.status == ServerStatus.NO_NETWORK -> t("Offline. Reconnects automatically when online.")
        status.status == ServerStatus.CONNECTED && !hasSessions ->
            t("Connected. No tmux sessions found yet. This list refreshes automatically.")
        else -> t("Connecting automatically. This list refreshes automatically.")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = tokens.space.sm, top = tokens.space.xs, bottom = tokens.space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isConnecting) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dpUnit()),
                strokeWidth = 2.dpUnit(),
                color = tokens.colors.primary
            )
            Spacer(modifier = Modifier.width(tokens.space.sm))
        }

        if (errorWithCountdown) {
            com.tmuxes.ui.components.app.RetryCountdownLabel(
                nextRetryAt = status.nextRetryAt,
                retryCount = status.retryCount,
                verbose = true
            )
        } else {
            Text(
                text = staticMessage,
                style = tokens.type.bodySmall,
                color = tokens.colors.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Host key prompt dialog (unchanged from original)
// ---------------------------------------------------------------------------

@Composable
private fun WidgetHostKeyPromptDialog(
    app: TmuxesApp,
    promptState: TmuxesApp.HostKeyPromptState?
) {
    val tokens = MaterialTheme.appTokens
    if (promptState == null) return

    val event = promptState.event
    val (title, message) = when (event) {
        is HostKeyEvent.Unknown -> Pair(
            "Unknown Host Key",
            t(
                "The server {host}:{port} presented an unknown {type} key.\n\nFingerprint:\n{fingerprint}\n\nDo you want to trust this key?",
                "host" to event.hostname,
                "port" to event.port,
                "type" to event.keyType,
                "fingerprint" to event.fingerprint
            )
        )

        is HostKeyEvent.Changed -> Pair(
            "Host Key Changed",
            t(
                "WARNING: The {type} key for {host}:{port} has changed!\n\nOld fingerprint:\n{oldFingerprint}\n\nNew fingerprint:\n{newFingerprint}\n\nThis could indicate a security issue. Accept the new key?",
                "type" to event.keyType,
                "host" to event.hostname,
                "port" to event.port,
                "oldFingerprint" to event.oldFingerprint,
                "newFingerprint" to event.newFingerprint
            )
        )
    }

    val acceptLabel = when (event) {
        is HostKeyEvent.Unknown -> "Trust & Remember"
        is HostKeyEvent.Changed -> "Accept New Key"
    }
    AppDialog(
        title = title,
        onDismiss = { app.respondToHostKeyPrompt(HostKeyPromptResult.REJECT) },
        confirmLabel = acceptLabel,
        onConfirm = { app.respondToHostKeyPrompt(HostKeyPromptResult.ACCEPT) },
        dismissLabel = "Reject",
        neutralLabel = "Trust Once",
        onNeutral = { app.respondToHostKeyPrompt(HostKeyPromptResult.TRUST_ONCE) },
        neutralStyle = AppButtonStyle.Outlined,
        content = {
            Text(
                text = message,
                style = tokens.type.monoSmall,
                color = tokens.colors.onSurfaceVariant
            )
        }
    )
}

private fun Int.dpUnit() = androidx.compose.ui.unit.Dp(this.toFloat())
