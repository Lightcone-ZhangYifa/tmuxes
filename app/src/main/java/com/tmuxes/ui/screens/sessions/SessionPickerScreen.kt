// allow-bypass-B6: ExposedDropdownMenuBox anchor (Material 3 API requires raw OutlinedTextField)
package com.tmuxes.ui.screens.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import com.tmuxes.ui.components.app.AppLazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tmuxes.data.model.ServerEntity
import com.tmuxes.i18n.I18nRuntime
import com.tmuxes.i18n.t
import com.tmuxes.ssh.ServerConnectionState
import com.tmuxes.ssh.ServerStatus
import com.tmuxes.tmux.TmuxSession
import com.tmuxes.ui.components.app.AppRowAction
import com.tmuxes.ui.components.app.AppRowSwipe
import com.tmuxes.ui.components.app.AppButton
import com.tmuxes.ui.components.app.AppButtonStyle
import com.tmuxes.ui.components.app.AppDialog
import com.tmuxes.ui.components.app.AppEmptyState
import com.tmuxes.ui.components.app.AppFab
import com.tmuxes.ui.components.app.AppIconButton
import com.tmuxes.ui.components.app.AppIconRole
import com.tmuxes.ui.components.app.AppScaffold
import com.tmuxes.ui.components.app.AppTextField
import com.tmuxes.ui.components.app.IdentityColorPicker
import com.tmuxes.ui.components.app.RetryCountdownLabel
import com.tmuxes.ui.components.app.StatusDot
import com.tmuxes.ui.components.app.appPressable
import com.tmuxes.ui.components.app.rememberAppEntryLazyListState
import com.tmuxes.ui.design.IdentityColors
import com.tmuxes.ui.design.appTokens
import com.tmuxes.ui.viewmodel.SessionViewModel
import com.tmuxes.util.findComponentActivity

/**
 * Sessions tab — every enabled server appears in this list with its
 * status row, regardless of whether it is connected. Adding a server
 * makes it appear here immediately so the user can see what's
 * happening to their connection. The top-bar refresh button issues
 * a user-initiated reset that bypasses the supervisor's backoff
 * timers and waits up to 10 s for a terminal status before flipping
 * the per-server header back to its idle state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionPickerScreen(
    serverId: Long?,
    onNavigateBack: () -> Unit,
    onOpenTerminal: (Long, String) -> Unit,
    onNavigateToServerDetail: (Long) -> Unit = {},
    viewModel: SessionViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current.findComponentActivity()
            ?: LocalViewModelStoreOwner.current!!
    )
) {
    val tokens = MaterialTheme.appTokens
    val sessions by viewModel.sessions.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val favoriteSessions by viewModel.favoriteSessions.collectAsState()
    val sessionColors by viewModel.sessionColors.collectAsState()
    val serverStates by viewModel.serverStates.collectAsState()
    val allServers by viewModel.servers.collectAsState()
    val serverRefreshStates by viewModel.serverRefreshStates.collectAsState()
    val isAnyRefreshing by viewModel.isAnyRefreshing.collectAsState()
    val tabListState = rememberLazyListState()
    val entryListState = rememberAppEntryLazyListState(serverId)
    val listState = if (serverId == null) tabListState else entryListState

    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createDialogFixedServerId by remember { mutableStateOf<Long?>(null) }

    val connectedServers = remember(serverStates, allServers) {
        allServers
            .filter { serverStates[it.id]?.status == ServerStatus.CONNECTED }
            .map { it.id to it.displayName }
    }
    var searchQuery by remember { mutableStateOf("") }
    val visibleSessions = remember(sessions, searchQuery, serverId) {
        val scopedSessions = if (serverId != null) {
            sessions.filter { it.serverId == serverId }
        } else {
            sessions
        }
        if (serverId == null && searchQuery.isNotBlank()) {
            scopedSessions.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.serverName.contains(searchQuery, ignoreCase = true)
            }
        } else {
            scopedSessions
        }
    }

    var renameSession by remember { mutableStateOf<TmuxSession?>(null) }
    var renameText by remember { mutableStateOf("") }
    var colorSession by remember { mutableStateOf<TmuxSession?>(null) }
    var killConfirmSession by remember { mutableStateOf<TmuxSession?>(null) }

    // Refresh on navigation entry — supervisor reset + suspend-wait so
    // every fresh entry into this screen guarantees an up-to-date list.
    LaunchedEffect(serverId) {
        if (serverId == null) viewModel.refreshAllServers()
        else viewModel.refreshServer(serverId)
    }

    // Auto-refresh when a server transitions into CONNECTED so the
    // sessions list populates as soon as a previously-failing server
    // comes online (e.g., user fixed credentials elsewhere).
    var previouslyConnected by remember(serverId) { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(serverStates, serverId) {
        val nowConnected = serverStates
            .filterValues { it.status == ServerStatus.CONNECTED }
            .keys
        val newlyConnected = nowConnected - previouslyConnected
        previouslyConnected = nowConnected
        if (newlyConnected.isEmpty()) return@LaunchedEffect
        if (serverId == null) {
            for (id in newlyConnected) viewModel.refreshServer(id)
        } else if (serverId in newlyConnected) {
            viewModel.refreshServer(serverId)
        }
    }

    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(I18nRuntime.t(msg))
        viewModel.clearError()
    }

    if (showCreateDialog) {
        val effectiveFixedServerId = createDialogFixedServerId ?: serverId
        val dialogServers = if (effectiveFixedServerId != null && connectedServers.none { it.first == effectiveFixedServerId }) {
            val name = allServers.firstOrNull { it.id == effectiveFixedServerId }?.displayName ?: ""
            connectedServers + (effectiveFixedServerId to name)
        } else {
            connectedServers
        }
        CreateSessionDialog(
            onDismiss = {
                showCreateDialog = false
                createDialogFixedServerId = null
            },
            connectedServers = dialogServers,
            fixedServerId = effectiveFixedServerId,
            onCreate = { targetServerId, name ->
                showCreateDialog = false
                createDialogFixedServerId = null
                viewModel.createSession(targetServerId, name)
            },
            onCreateAndAttach = { targetServerId, name ->
                showCreateDialog = false
                createDialogFixedServerId = null
                viewModel.createAndAttachSession(targetServerId, name) { resolvedName ->
                    onOpenTerminal(targetServerId, resolvedName)
                }
            }
        )
    }

    renameSession?.let { session ->
        AppDialog(
            title = "Rename Session",
            onDismiss = { renameSession = null },
            confirmLabel = "Rename",
            confirmEnabled = renameText.trim().isNotBlank() && renameText.trim() != session.name,
            onConfirm = {
                val newName = renameText.trim()
                if (newName.isNotBlank() && newName != session.name) {
                    viewModel.renameSession(session.serverId, session.name, newName)
                }
                renameSession = null
            },
            content = {
                Column {
                    Text(
                        text = t("Enter a new name for session '{name}'.", "name" to session.name),
                        style = tokens.type.bodyMedium,
                        color = tokens.colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(tokens.space.lg))
                    AppTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = "New Name",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    colorSession?.let { session ->
        val key = "${session.serverId}:${session.name}"
        SessionColorDialog(
            session = session,
            currentColor = sessionColors[key] ?: 0,
            onDismiss = { colorSession = null },
            onConfirm = { color ->
                viewModel.setSessionColor(session.serverId, session.name, color)
                colorSession = null
            }
        )
    }

    killConfirmSession?.let { session ->
        AppDialog(
            title = "Kill Session",
            text = "Kill session '${session.name}'? Running processes will be terminated.",
            onDismiss = { killConfirmSession = null },
            confirmLabel = "Kill",
            confirmStyle = AppButtonStyle.Danger,
            onConfirm = {
                viewModel.killSession(session.serverId, session.name)
                killConfirmSession = null
            }
        )
    }

    AppScaffold(
        title = if (serverId != null) "Server Sessions" else "Sessions",
        titleIcon = if (serverId == null) Icons.Filled.Terminal else null,
        titleMeta = visibleSessions.size.toString(),
        onBack = if (serverId != null) onNavigateBack else null,
        actions = {
            if (isAnyRefreshing) {
                Box(
                    modifier = Modifier.size(48.dpUnit()),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dpUnit()),
                        strokeWidth = 2.dpUnit(),
                        color = tokens.colors.primary
                    )
                }
            } else {
                AppIconButton(
                    icon = Icons.Filled.Refresh,
                    onClick = {
                        if (serverId != null) viewModel.refreshServer(serverId)
                        else viewModel.refreshAllServers()
                    },
                    contentDescription = "Refresh"
                )
            }
        },
        fab = {
            val canCreate = serverId != null || connectedServers.isNotEmpty()
            if (canCreate) {
                AppFab(
                    icon = Icons.Filled.Add,
                    onClick = { showCreateDialog = true },
                    contentDescription = "New session"
                )
            }
        },
        snackbarHostState = snackbarHostState
    ) { padding ->
        val (favoriteSorted, nonFavoriteSessions) = remember(visibleSessions, favoriteSessions) {
            visibleSessions.partition { "${it.serverId}:${it.name}" in favoriteSessions }
        }
        val sessionsByServer = remember(nonFavoriteSessions) {
            nonFavoriteSessions.groupBy { it.serverId }
        }

        // The list of servers to render as sections. In per-server mode,
        // exactly one section. In all-sessions mode, every enabled server,
        // ordered by sortOrder then displayName.
        val serverSections = remember(allServers, serverId) {
            if (serverId != null) allServers.filter { it.id == serverId }
            else allServers
                .filter { it.isEnabled }
                .sortedWith(compareBy<ServerEntity> { it.sortOrder }.thenBy { it.displayName })
        }

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (allServers.isEmpty()) {
                AppEmptyState(
                    icon = Icons.Filled.Terminal,
                    title = "No servers yet",
                    subtitle = "Add a server in the Servers tab to start a session.",
                    modifier = Modifier.fillMaxSize()
                )
                return@Box
            }
            AppLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = tokens.space.lg,
                    end = tokens.space.lg,
                    top = tokens.space.sm,
                    bottom = tokens.space.xxxl + tokens.space.xl
                ),
                verticalArrangement = Arrangement.spacedBy(tokens.space.sm)
            ) {
                if (serverId == null) {
                    item(key = "search_bar") {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(t("Search sessions...")) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = t("Search"),
                                    tint = tokens.colors.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    AppIconButton(
                                        icon = Icons.Filled.Close,
                                        onClick = { searchQuery = "" },
                                        contentDescription = "Clear search",
                                        role = AppIconRole.OnSurfaceVariant
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(tokens.shape.md),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = tokens.colors.surfaceVariant,
                                unfocusedContainerColor = tokens.colors.surfaceVariant,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = tokens.colors.primary
                            )
                        )
                    }
                }

                if (favoriteSorted.isNotEmpty()) {
                    item(key = "header_favorites") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = tokens.space.sm)
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                modifier = Modifier.size(16.dpUnit()),
                                tint = tokens.colors.primary
                            )
                            Spacer(modifier = Modifier.width(tokens.space.xs + tokens.space.xxs))
                            Text(
                                text = t("Favorites"),
                                style = tokens.type.labelMedium,
                                color = tokens.colors.primary
                            )
                            Spacer(modifier = Modifier.width(tokens.space.sm))
                            SessionCountText(count = favoriteSorted.size)
                        }
                    }
                    items(items = favoriteSorted, key = { "fav_${it.serverId}:${it.name}" }) { session ->
                        SessionRow(
                            session = session,
                            isFavorite = true,
                            customColor = sessionColors["${session.serverId}:${session.name}"] ?: 0,
                            onOpen = { onOpenTerminal(session.serverId, session.name) },
                            onToggleFavorite = { viewModel.toggleFavorite(session.serverId, session.name) },
                            onColor = { colorSession = session },
                            onRename = { renameText = session.name; renameSession = session },
                            onKill = { killConfirmSession = session }
                        )
                    }
                }

                serverSections.forEach { server ->
                    val status = serverStates[server.id]
                        ?: ServerConnectionState(ServerStatus.IDLE)
                    val refreshState = serverRefreshStates[server.id]
                    val serverSessions = sessionsByServer[server.id].orEmpty()

                    item(key = "header_${server.id}") {
                        ServerSessionHeader(
                            server = server,
                            status = status,
                            refreshState = refreshState,
                            sessionCount = serverSessions.size,
                            onRefresh = { viewModel.refreshServer(server.id) },
                            onTapStatus = { onNavigateToServerDetail(server.id) }
                        )
                    }

                    if (serverSessions.isNotEmpty()) {
                        items(items = serverSessions, key = { "${it.serverId}:${it.name}" }) { session ->
                            val isFavorite = "${session.serverId}:${session.name}" in favoriteSessions
                            SessionRow(
                                session = session,
                                isFavorite = isFavorite,
                                customColor = sessionColors["${session.serverId}:${session.name}"] ?: 0,
                                onOpen = { onOpenTerminal(session.serverId, session.name) },
                                onToggleFavorite = { viewModel.toggleFavorite(session.serverId, session.name) },
                                onColor = { colorSession = session },
                                onRename = { renameText = session.name; renameSession = session },
                                onKill = { killConfirmSession = session }
                            )
                        }
                    } else {
                        item(key = "body_${server.id}") {
                            ServerBodyRow(
                                status = status,
                                onCreateSession = {
                                    createDialogFixedServerId = server.id
                                    showCreateDialog = true
                                },
                                onTapStatus = { onNavigateToServerDetail(server.id) }
                            )
                        }
                    }
                }

                if (visibleSessions.isEmpty() && searchQuery.isNotBlank()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(tokens.space.xxl),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = t("No sessions match '{query}'", "query" to searchQuery),
                                style = tokens.type.bodyLarge,
                                color = tokens.colors.onSurfaceVariant
                            )
                        }
                    }
                }

                if (serverId != null) {
                    item { Spacer(modifier = Modifier.height(tokens.space.xxxl + tokens.space.xl)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Per-server header — name + status + (refresh icon | spinner)
// ---------------------------------------------------------------------------

@Composable
private fun ServerSessionHeader(
    server: ServerEntity,
    status: ServerConnectionState,
    refreshState: SessionViewModel.ServerRefreshState?,
    sessionCount: Int,
    onRefresh: () -> Unit,
    onTapStatus: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val actionSlotSize = 48.dpUnit()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = tokens.space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(tokens.shape.sm)
                .appPressable(onClick = onTapStatus)
                .padding(vertical = tokens.space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(color = tokens.status.forServerStatus(status.status))
            Spacer(modifier = Modifier.width(tokens.space.sm))
            Text(
                text = server.displayName,
                style = tokens.type.labelMedium,
                color = tokens.colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(tokens.space.sm))
            SessionCountText(count = sessionCount)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            if (refreshState is SessionViewModel.ServerRefreshState.Error) {
                Surface(
                    shape = tokens.shape.pill,
                    color = tokens.colors.errorContainer,
                    modifier = Modifier
                        .widthIn(max = 180.dpUnit())
                        .padding(start = tokens.space.sm, end = tokens.space.xs)
                ) {
                    Text(
                        text = t(refreshState.message),
                        style = tokens.type.labelSmall,
                        color = tokens.colors.onErrorContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(horizontal = tokens.space.sm, vertical = tokens.space.xs)
                    )
                }
            }
            Box(
                modifier = Modifier.size(actionSlotSize),
                contentAlignment = Alignment.Center
            ) {
                if (refreshState is SessionViewModel.ServerRefreshState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dpUnit()),
                        strokeWidth = 2.dpUnit(),
                        color = tokens.colors.primary
                    )
                } else {
                    AppIconButton(
                        icon = Icons.Filled.Refresh,
                        onClick = onRefresh,
                        contentDescription = t("Refresh {name}", "name" to server.displayName),
                        role = AppIconRole.OnSurfaceVariant,
                        modifier = Modifier.size(actionSlotSize)
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionCountText(count: Int) {
    val tokens = MaterialTheme.appTokens
    Text(
        text = count.toString(),
        style = tokens.type.labelSmall,
        color = tokens.colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

// ---------------------------------------------------------------------------
// Per-server body row — what shows under the header when there are no
// sessions to render. Status-aware: "Connected → No sessions + New Session
// button", "Connecting → progress + label", "Auth/Network/Parent error →
// reason + tap-to-edit hint", etc.
// ---------------------------------------------------------------------------

@Composable
private fun ServerBodyRow(
    status: ServerConnectionState,
    onCreateSession: () -> Unit,
    onTapStatus: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    when (status.status) {
        ServerStatus.CONNECTED -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.space.sm, vertical = tokens.space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    t("No tmux sessions"),
                    style = tokens.type.bodySmall,
                    color = tokens.colors.onSurfaceVariant
                )
                AppButton(
                    text = "New Session",
                    onClick = onCreateSession,
                    style = AppButtonStyle.Text,
                    leadingIcon = Icons.Filled.Add
                )
            }
        }
        ServerStatus.CONNECTING,
        ServerStatus.WAITING_PARENT,
        ServerStatus.WAITING_HOST_KEY -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.space.sm, vertical = tokens.space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dpUnit()),
                    strokeWidth = 2.dpUnit(),
                    color = tokens.colors.primary
                )
                Spacer(Modifier.width(tokens.space.sm))
                Text(
                    text = when (status.status) {
                        ServerStatus.CONNECTING -> t("Connecting…")
                        ServerStatus.WAITING_PARENT ->
                            t("Waiting for {name}…", "name" to (status.parentInfo?.parentName ?: t("parent server")))
                        ServerStatus.WAITING_HOST_KEY -> t("Waiting for host-key approval…")
                        else -> t("Connecting…")
                    },
                    style = tokens.type.bodySmall,
                    color = tokens.colors.onSurfaceVariant
                )
            }
        }
        ServerStatus.AUTH_FAILED -> {
            ErrorBodyRow(
                title = t("Authentication failed"),
                detail = t("Tap to edit credentials"),
                onTap = onTapStatus
            )
        }
        ServerStatus.NETWORK_ERROR -> {
            ErrorBodyRow(
                title = t("Network error"),
                detail = if (status.nextRetryAt != null) t("Automatic retry scheduled") else t("Tap to inspect"),
                onTap = onTapStatus,
                trailing = {
                    if (status.nextRetryAt != null) {
                        RetryCountdownLabel(
                            nextRetryAt = status.nextRetryAt,
                            retryCount = status.retryCount,
                            verbose = false
                        )
                    }
                }
            )
        }
        ServerStatus.PARENT_FAILED -> {
            ErrorBodyRow(
                title = t("Parent server failed"),
                detail = status.parentInfo?.parentName?.let { t("Parent: {name}", "name" to it) } ?: t("Tap to inspect"),
                onTap = onTapStatus
            )
        }
        ServerStatus.NO_NETWORK -> {
            InfoBodyRow(text = "No network", hint = "Reconnects automatically when online")
        }
        ServerStatus.PAUSED -> {
            InfoBodyRow(text = "Paused", hint = "Re-enable in server settings", onTap = onTapStatus)
        }
        ServerStatus.IDLE,
        ServerStatus.DISCONNECTED -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.space.sm, vertical = tokens.space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dpUnit()),
                    strokeWidth = 2.dpUnit(),
                    color = tokens.colors.primary
                )
                Spacer(Modifier.width(tokens.space.sm))
                Text(
                text = t("Starting…"),
                    style = tokens.type.bodySmall,
                    color = tokens.colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ErrorBodyRow(
    title: String,
    detail: String,
    onTap: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    val tokens = MaterialTheme.appTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shape.md)
            .background(tokens.colors.surfaceContainer)
            .appPressable(onClick = onTap)
            .padding(horizontal = tokens.space.sm, vertical = tokens.space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = tokens.shape.sm,
            color = tokens.colors.errorContainer,
            contentColor = tokens.colors.onErrorContainer
        ) {
            Box(
                modifier = Modifier.size(tokens.space.xxl),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(tokens.space.lg + tokens.space.xs)
                )
            }
        }
        Spacer(modifier = Modifier.width(tokens.space.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = tokens.type.labelLarge,
                color = tokens.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(tokens.space.xxs))
            Text(
                text = detail,
                style = tokens.type.labelSmall,
                color = tokens.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(tokens.space.sm))
            trailing()
        }
    }
}

@Composable
private fun InfoBodyRow(text: String, hint: String, onTap: (() -> Unit)? = null) {
    val tokens = MaterialTheme.appTokens
    val baseModifier = Modifier
        .fillMaxWidth()
        .let { if (onTap != null) it.clip(tokens.shape.sm).appPressable(onClick = onTap) else it }
        .padding(horizontal = tokens.space.sm, vertical = tokens.space.xs + tokens.space.xxs)
    Row(modifier = baseModifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = t(text),
                style = tokens.type.bodySmall,
                color = tokens.colors.onSurfaceVariant
            )
            Text(
                text = t(hint),
                style = tokens.type.labelSmall,
                color = tokens.colors.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Session row (with swipe actions)
// ---------------------------------------------------------------------------

@Composable
private fun SessionRow(
    session: TmuxSession,
    isFavorite: Boolean,
    customColor: Int,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onColor: () -> Unit,
    onRename: () -> Unit,
    onKill: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    AppRowSwipe(
        actions = listOf(
            AppRowAction(
                icon = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                color = tokens.status.warning,
                onClick = onToggleFavorite,
                label = if (isFavorite) "Unfav" else "Fav"
            ),
            AppRowAction(
                icon = Icons.Filled.Palette,
                color = tokens.colors.primary,
                onClick = onColor,
                label = "Color"
            ),
            AppRowAction(
                icon = Icons.Filled.Edit,
                color = tokens.status.info,
                onClick = onRename,
                label = "Rename"
            ),
            AppRowAction(
                icon = Icons.Filled.Delete,
                color = tokens.colors.danger,
                onClick = onKill,
                label = "Kill"
            )
        )
    ) {
        SessionCard(
            session = session,
            onClick = onOpen,
            isFavorite = isFavorite,
            customColor = customColor
        )
    }
}

// ---------------------------------------------------------------------------
// Session card
// ---------------------------------------------------------------------------

@Composable
private fun SessionCard(
    session: TmuxSession,
    onClick: () -> Unit,
    isFavorite: Boolean,
    customColor: Int
) {
    val tokens = MaterialTheme.appTokens
    val identityColor = customColor.takeIf { it != 0 } ?: session.serverColor
    val backgroundColor = IdentityColors.containerColor(identityColor, tokens.colors)
    val outlineColor = IdentityColors.outlineColor(identityColor, tokens.colors)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shape.lg)
            .background(backgroundColor)
            .border(1.dpUnit(), outlineColor, tokens.shape.lg)
            .appPressable(onClick = onClick)
            .padding(tokens.space.lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Icon(
                    Icons.Filled.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(32.dpUnit()),
                    tint = tokens.colors.onSurfaceVariant
                )
                if (session.attached) {
                    StatusDot(
                        color = tokens.status.connected,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }
            Spacer(modifier = Modifier.width(tokens.space.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.name,
                    style = tokens.type.mono,
                    color = tokens.colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(tokens.space.xxs))
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.space.md)) {
                    Text(
                        text = session.serverName,
                        style = tokens.type.bodySmall,
                        color = tokens.colors.onSurfaceVariant
                    )
                    Text(
                        text = t(
                            if (session.windowCount == 1) "{count} window" else "{count} windows",
                            "count" to session.windowCount
                        ),
                        style = tokens.type.bodySmall,
                        color = tokens.colors.onSurfaceVariant
                    )
                }
                if (session.createdAt.isNotBlank() && session.createdAt != "unknown") {
                    Spacer(modifier = Modifier.height(tokens.space.xxs))
                    Text(
                        text = t("Created {date}", "date" to session.createdAt),
                        style = tokens.type.labelSmall,
                        color = tokens.colors.onSurfaceVariant
                    )
                }
            }
            if (isFavorite) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = t("Favorite"),
                    modifier = Modifier.size(18.dpUnit()),
                    tint = tokens.colors.primary
                )
                Spacer(modifier = Modifier.width(tokens.space.sm))
            }
            if (session.attached) {
                Box(
                    modifier = Modifier
                        .clip(tokens.shape.xs)
                        .background(tokens.colors.primaryContainer)
                        .padding(horizontal = tokens.space.sm, vertical = tokens.space.xs)
                ) {
                    Text(
                        text = t("attached"),
                        style = tokens.type.labelSmall,
                        color = tokens.colors.onPrimaryContainer
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Session color dialog
// ---------------------------------------------------------------------------

@Composable
private fun SessionColorDialog(
    session: TmuxSession,
    currentColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    var selectedColor by remember(session.serverId, session.name, currentColor) { mutableStateOf(currentColor) }
    AppDialog(
        title = "Session Color",
        onDismiss = onDismiss,
        confirmLabel = "Apply",
        onConfirm = { onConfirm(selectedColor) },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.space.md)) {
                Text(
                    text = session.name,
                    style = tokens.type.mono,
                    color = tokens.colors.onSurface
                )
                IdentityColorPicker(
                    selectedColor = selectedColor,
                    onColorSelected = { selectedColor = it }
                )
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Create session dialog
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSessionDialog(
    onDismiss: () -> Unit,
    connectedServers: List<Pair<Long, String>> = emptyList(),
    fixedServerId: Long? = null,
    onCreate: (Long, String) -> Unit,
    onCreateAndAttach: (Long, String) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    var sessionName by remember { mutableStateOf("") }
    var selectedServerId by remember(connectedServers, fixedServerId) {
        mutableStateOf(fixedServerId ?: connectedServers.firstOrNull()?.first ?: 0L)
    }
    var serverDropdownExpanded by remember { mutableStateOf(false) }
    val needsServerPicker = fixedServerId == null && connectedServers.size > 1
    val selectedServerName = connectedServers.find { it.first == selectedServerId }?.second ?: ""
    val hasValidServer = selectedServerId > 0L

    AppDialog(
        title = "New Session",
        onDismiss = onDismiss,
        confirmLabel = "Create & Open",
        confirmEnabled = hasValidServer,
        onConfirm = { onCreateAndAttach(selectedServerId, sessionName) },
        dismissLabel = "Cancel",
        neutralLabel = "Create",
        onNeutral = { onCreate(selectedServerId, sessionName) },
        neutralEnabled = hasValidServer,
        neutralStyle = AppButtonStyle.Outlined,
        content = {
            Column {
                if (needsServerPicker) {
                    Text(
                        text = t("Server"),
                        style = tokens.type.labelMedium,
                        color = tokens.colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(tokens.space.xs))
                    ExposedDropdownMenuBox(
                        expanded = serverDropdownExpanded,
                        onExpandedChange = { serverDropdownExpanded = it }
                    ) {
                        // Anchor must remain a raw OutlinedTextField — ExposedDropdownMenuBox
                        // requires the M3 anchor API. Token-tinted manually.
                        OutlinedTextField(
                            value = selectedServerName,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = serverDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            shape = tokens.shape.md,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = tokens.colors.primary,
                                unfocusedBorderColor = tokens.colors.divider
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = serverDropdownExpanded,
                            onDismissRequest = { serverDropdownExpanded = false }
                        ) {
                            connectedServers.forEach { (id, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        selectedServerId = id
                                        serverDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(tokens.space.md))
                } else if (fixedServerId == null && connectedServers.size == 1) {
                    Text(
                        text = t("On {server}", "server" to connectedServers.first().second),
                        style = tokens.type.bodyMedium,
                        color = tokens.colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(tokens.space.sm))
                } else if (fixedServerId != null && selectedServerName.isNotEmpty()) {
                    Text(
                        text = t("On {server}", "server" to selectedServerName),
                        style = tokens.type.bodyMedium,
                        color = tokens.colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(tokens.space.sm))
                }

                Text(
                    text = t("Enter a name for the new tmux session, or leave blank for the default."),
                    style = tokens.type.bodyMedium,
                    color = tokens.colors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(tokens.space.lg))
                AppTextField(
                    value = sessionName,
                    onValueChange = { sessionName = it },
                    label = "Session Name",
                    placeholder = "my-session",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Convert an Int to Dp for element-physical sizes (icon, FAB) that
 * legitimately need a fixed dimension and aren't part of the spacing
 * rhythm. Tokens are still preferred for any padding / spacer. */
private fun Int.dpUnit() = androidx.compose.ui.unit.Dp(this.toFloat())

private fun Float.dpUnit() = androidx.compose.ui.unit.Dp(this)
