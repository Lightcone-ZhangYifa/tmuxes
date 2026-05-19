package com.tmuxes.ui.screens.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.tmuxes.ui.components.app.appElasticVerticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tmuxes.data.model.AuthMethod
import com.tmuxes.data.model.KnownHostEntity
import com.tmuxes.data.model.ServerEntity
import com.tmuxes.i18n.I18nRuntime
import com.tmuxes.i18n.t
import com.tmuxes.ssh.SshConfigHost
import com.tmuxes.ssh.ServerConnectionState
import com.tmuxes.ssh.ServerStatus
import com.tmuxes.ui.components.app.AppButton
import com.tmuxes.ui.components.app.AppButtonStyle
import com.tmuxes.ui.components.app.AppCard
import com.tmuxes.ui.components.app.AppIconButton
import com.tmuxes.ui.components.app.AppIconRole
import com.tmuxes.ui.components.app.AppScaffold
import com.tmuxes.ui.components.app.AppSectionHeader
import com.tmuxes.ui.components.app.StatusDot
import com.tmuxes.ui.components.app.rememberAppEntryScrollState
import com.tmuxes.ui.design.IdentityColors
import com.tmuxes.ui.design.appTokens
import com.tmuxes.ui.viewmodel.ServerDetailViewModel

@Composable
fun ServerDetailScreen(
    serverId: Long,
    onNavigateBack: () -> Unit,
    onEditServer: (Long) -> Unit,
    onAddChildServer: (parentId: Long) -> Unit,
    onImportConfigHost: (parentId: Long, name: String, hostname: String, username: String?, port: Int?) -> Unit,
    onNavigateToChild: (Long) -> Unit,
    onOpenSessions: (Long) -> Unit,
    onNavigateToYamlEditor: () -> Unit = {},
    viewModel: ServerDetailViewModel = viewModel()
) {
    val tokens = MaterialTheme.appTokens
    val server by viewModel.server.collectAsState()
    val parentServer by viewModel.parentServer.collectAsState()
    val children by viewModel.children.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val configHosts by viewModel.configHosts.collectAsState()
    val isFetchingConfig by viewModel.isFetchingConfig.collectAsState()
    val configStatus by viewModel.configStatus.collectAsState()
    val serverStatus by viewModel.serverStatus.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val systemInfo by viewModel.systemInfo.collectAsState()
    val isFetchingSystemInfo by viewModel.isFetchingSystemInfo.collectAsState()
    val systemInfoError by viewModel.systemInfoError.collectAsState()
    val hostFingerprints by viewModel.hostFingerprints.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberAppEntryScrollState(serverId)
    // Edit and Delete operations are in ServerListScreen's swipe actions

    LaunchedEffect(serverId) {
        viewModel.loadServer(serverId)
    }

    // Refresh data when returning from child screens
    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }

    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(I18nRuntime.t(msg))
        viewModel.clearError()
    }

    AppScaffold(
        title = server?.displayName ?: "Server",
        onBack = onNavigateBack,
        actions = {
            // Primary edit action — goes to the AddEditServer
            // form, not the YAML editor. The YAML editor is a
            // power-user escape hatch reachable via Settings >
            // Edit Config (YAML); it should not be the default
            // action on a server detail page because editing
            // raw YAML is error-prone and doesn't match how
            // users expect "Edit" to behave.
            AppIconButton(
                icon = Icons.Filled.Edit,
                onClick = { onEditServer(serverId) },
                contentDescription = "Edit server",
                role = AppIconRole.OnSurfaceVariant
            )
        },
        snackbarHostState = snackbarHostState
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = tokens.colors.primary)
                }
            }
            loadError != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dpUnit()),
                            tint = tokens.colors.error
                        )
                        Spacer(modifier = Modifier.height(tokens.space.md))
                        // Capture into a local so a concurrent state update
                        // that nulls loadError between the `!= null` check
                        // and this Text composition can't throw.
                        Text(
                            text = loadError ?: "",
                            style = tokens.type.bodyLarge,
                            color = tokens.colors.error
                        )
                    }
                }
            }
            else -> {
                val s = server ?: return@AppScaffold
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .appElasticVerticalScroll(scrollState)
                        .padding(horizontal = tokens.space.lg),
                    verticalArrangement = Arrangement.spacedBy(tokens.space.lg)
                ) {
                    Spacer(modifier = Modifier.height(tokens.space.xs))

                    // Server Info Card
                    ServerInfoCard(
                        server = s, parentServer = parentServer, serverStatus = serverStatus,
                        onRefreshStatus = { viewModel.refreshProbe() }
                    )

                    // Connection Info Section
                    ConnectionInfoSection(
                        serverStatus = serverStatus,
                        hostFingerprints = hostFingerprints,
                        sshVersion = systemInfo?.sshVersion,
                        onConnect = { onOpenSessions(serverId) }
                    )

                    // System Information Section
                    SystemInfoSection(
                        systemInfo = systemInfo,
                        isFetching = isFetchingSystemInfo,
                        error = systemInfoError,
                        onRefresh = { viewModel.refreshSystemInfo() }
                    )

                    // Sessions Section
                    SessionsSection(
                        tmuxSessionCount = systemInfo?.tmuxSessions,
                        onOpenSessions = { onOpenSessions(serverId) }
                    )

                    // SSH Config Hosts Section
                    SshConfigSection(
                        configHosts = configHosts,
                        configStatus = configStatus,
                        isFetching = isFetchingConfig,
                        serverId = serverId,
                        viewModel = viewModel,
                        onRefresh = { viewModel.refreshSshConfig() },
                        onImport = { configHost ->
                            onImportConfigHost(
                                serverId,
                                configHost.host,
                                configHost.hostName ?: configHost.host,
                                configHost.user,
                                configHost.port
                            )
                        }
                    )

                    // Child Servers Section
                    ChildServersSection(
                        children = children,
                        serverId = serverId,
                        onAddChild = { onAddChildServer(serverId) },
                        onNavigateToChild = onNavigateToChild
                    )

                    Spacer(modifier = Modifier.height(tokens.space.xxl))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Server Info Card
// ---------------------------------------------------------------------------

@Composable
private fun ServerInfoCard(
    server: ServerEntity,
    parentServer: ServerEntity?,
    serverStatus: ServerConnectionState,
    onRefreshStatus: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val identityBackground = IdentityColors.containerColor(server.color, tokens.colors)
    val identityOutline = IdentityColors.outlineColor(server.color, tokens.colors)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shape.lg)
            .background(identityBackground)
            .border(1.dpUnit(), identityOutline, tokens.shape.lg)
            .padding(tokens.space.lg)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(tokens.space.sm)
        ) {
            // Connection + target string
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(
                    color = tokens.status.forServerStatus(serverStatus.status),
                    sizeDp = 12
                )
                Spacer(modifier = Modifier.width(tokens.space.md))
                Text(
                    text = "${server.username}@${server.hostname}:${server.port}",
                    style = tokens.type.mono,
                    color = tokens.colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                AppIconButton(
                    icon = Icons.Filled.Refresh,
                    onClick = onRefreshStatus,
                    contentDescription = "Refresh status",
                    role = AppIconRole.OnSurfaceVariant
                )
            }

            // Auth method
            val authLabel = when (server.authMethod) {
                AuthMethod.PASSWORD -> "Password"
                AuthMethod.KEY -> "SSH Key"
                AuthMethod.KEY_WITH_PASSPHRASE -> "SSH Key + Passphrase"
            }
            InfoRow(label = "Auth", value = authLabel)
            if (server.parentId != null) {
                InfoRow(label = "ProxyJump", value = "via parent server")
            }

            // Parent info
            if (server.parentId != null) {
                InfoRow(
                    label = "Parent",
                    value = parentServer?.displayName ?: "Unknown (id=${server.parentId})"
                )
            }

            // Enabled status
            InfoRow(label = "Status", value = if (server.isEnabled) "Enabled" else "Paused")

            // SSH connection status
            InfoRow(label = "Connection", value = serverStatus.status.label)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val tokens = MaterialTheme.appTokens
    Row {
        Text(
            text = "${t(label)}: ",
            style = tokens.type.bodySmall,
            color = tokens.colors.onSurfaceVariant
        )
        Text(
            text = t(value),
            style = tokens.type.bodySmall,
            color = tokens.colors.onSurface
        )
    }
}

// ---------------------------------------------------------------------------
// Connection Info Section
// ---------------------------------------------------------------------------

@Composable
private fun ConnectionInfoSection(
    serverStatus: ServerConnectionState,
    hostFingerprints: List<KnownHostEntity>,
    sshVersion: String?,
    onConnect: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    AppSectionHeader(text = "Connection Info")

    val isConnected = serverStatus.status == ServerStatus.CONNECTED

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(tokens.space.sm)
        ) {
            // Connection status row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isConnected) Icons.Filled.Link else Icons.Filled.LinkOff,
                    contentDescription = null,
                    modifier = Modifier.size(20.dpUnit()),
                    tint = if (isConnected) tokens.status.connected else tokens.colors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(tokens.space.sm))
                Text(
                    text = t(if (isConnected) "Connected" else "Not connected"),
                    style = tokens.type.titleSmall,
                    color = if (isConnected) tokens.status.connected else tokens.colors.onSurfaceVariant
                )
                if (!isConnected) {
                    Spacer(modifier = Modifier.weight(1f))
                    AppButton(
                        text = "Connect",
                        onClick = onConnect,
                        style = AppButtonStyle.Secondary
                    )
                }
            }

            // Host key fingerprints
            if (hostFingerprints.isNotEmpty()) {
                hostFingerprints.forEach { entry ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Filled.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dpUnit())
                                .padding(top = tokens.space.xxs),
                            tint = tokens.colors.primary
                        )
                        Spacer(modifier = Modifier.width(tokens.space.sm))
                        Column {
                            Text(
                                text = entry.keyType,
                                style = tokens.type.labelSmall,
                                color = tokens.colors.primary
                            )
                            Text(
                                text = entry.fingerprint,
                                style = tokens.type.monoSmall,
                                color = tokens.colors.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = t("No stored host key"),
                    style = tokens.type.bodySmall,
                    color = tokens.colors.onSurfaceVariant
                )
            }

            // SSH version (from system info)
            if (sshVersion != null) {
                SysInfoRow(label = "SSH", value = sshVersion)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sessions Section
// ---------------------------------------------------------------------------

@Composable
private fun SessionsSection(
    tmuxSessionCount: String?,
    onOpenSessions: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    AppSectionHeader(text = "Sessions")

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenSessions
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.space.md)
        ) {
            Icon(
                Icons.Filled.Terminal,
                contentDescription = null,
                tint = tokens.colors.primary
            )
            val sessionsSubtitle = tmuxSessionCount?.let {
                t("{count} session(s) available - Create or open sessions", "count" to it)
            } ?: t("Create new sessions or open existing ones on this server")
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = t("Open tmux sessions"),
                    style = tokens.type.titleSmall,
                    color = tokens.colors.onSurface
                )
                Text(
                    text = sessionsSubtitle,
                    style = tokens.type.bodySmall,
                    color = tokens.colors.onSurfaceVariant
                )
            }
            Text(
                text = t("Open"),
                style = tokens.type.labelLarge,
                color = tokens.colors.primary
            )
        }
    }
}

// ---------------------------------------------------------------------------
// SSH Config Section
// ---------------------------------------------------------------------------

@Composable
private fun SshConfigSection(
    configHosts: List<SshConfigHost>,
    configStatus: ServerDetailViewModel.ConfigStatus,
    isFetching: Boolean,
    serverId: Long,
    viewModel: ServerDetailViewModel,
    onRefresh: () -> Unit,
    onImport: (SshConfigHost) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            AppSectionHeader(text = "SSH Config Hosts")
        }
        AppIconButton(
            icon = Icons.Filled.Refresh,
            onClick = onRefresh,
            contentDescription = "Refresh config",
            enabled = !isFetching,
            role = AppIconRole.Primary
        )
    }

    // Status indicator
    when (configStatus) {
        is ServerDetailViewModel.ConfigStatus.Idle -> {}
        is ServerDetailViewModel.ConfigStatus.WaitingForConnection -> {
            Text(
                text = t("Waiting for connection..."),
                style = tokens.type.bodySmall,
                color = tokens.colors.outline
            )
        }
        is ServerDetailViewModel.ConfigStatus.Fetching -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.space.sm)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dpUnit()),
                    strokeWidth = 2.dp,
                    color = tokens.colors.primary
                )
                Text(
                    t("Fetching SSH config..."),
                    style = tokens.type.bodySmall,
                    color = tokens.colors.onSurfaceVariant
                )
            }
        }
        is ServerDetailViewModel.ConfigStatus.Loaded -> {
            Text(
                text = t("{count} hosts found", "count" to configStatus.count),
                style = tokens.type.bodySmall,
                color = tokens.colors.primary
            )
        }
        is ServerDetailViewModel.ConfigStatus.Error -> {
            Text(
                text = t(configStatus.message),
                style = tokens.type.bodySmall,
                color = tokens.colors.error
            )
        }
    }

    if (configHosts.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.space.sm)) {
            configHosts.forEach { configHost ->
                val alreadyImported = viewModel.isAlreadyImported(serverId, configHost)
                ConfigHostCard(
                    configHost = configHost,
                    alreadyImported = alreadyImported,
                    onImport = { onImport(configHost) }
                )
            }
        }
    }
}


// ---------------------------------------------------------------------------
// Child Servers Section
// ---------------------------------------------------------------------------

@Composable
private fun ChildServersSection(
    children: List<ServerEntity>,
    serverId: Long,
    onAddChild: () -> Unit,
    onNavigateToChild: (Long) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            AppSectionHeader(text = t("Child Servers ({count})", "count" to children.size))
        }
        AppButton(
            text = "Add Child",
            onClick = onAddChild,
            style = AppButtonStyle.Secondary,
            leadingIcon = Icons.Filled.Add
        )
    }

    if (children.isEmpty()) {
        Text(
            text = t("No child servers. Add one manually or import from SSH config above."),
            style = tokens.type.bodySmall,
            color = tokens.colors.onSurfaceVariant
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.space.sm)) {
            children.forEach { child ->
                ChildServerCard(server = child, onClick = { onNavigateToChild(child.id) })
            }
        }
    }
}

// ---------------------------------------------------------------------------
// System Info Section
// ---------------------------------------------------------------------------

@Composable
private fun SystemInfoSection(
    systemInfo: ServerDetailViewModel.ServerSystemInfo?,
    isFetching: Boolean,
    error: String?,
    onRefresh: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            AppSectionHeader(text = "System Information")
        }
        AppIconButton(
            icon = Icons.Filled.Refresh,
            onClick = onRefresh,
            contentDescription = "Refresh system info",
            enabled = !isFetching,
            role = AppIconRole.Primary
        )
    }

    if (isFetching && systemInfo == null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.space.sm)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dpUnit()),
                strokeWidth = 2.dp,
                color = tokens.colors.primary
            )
            Text(
                t("Fetching system info..."),
                style = tokens.type.bodySmall,
                color = tokens.colors.onSurfaceVariant
            )
        }
    } else if (error != null && systemInfo == null) {
        Text(
            text = error,
            style = tokens.type.bodySmall,
            color = tokens.colors.onSurfaceVariant
        )
    } else if (systemInfo != null) {
        // System card
        InfoCard(title = "System Info") {
            systemInfo.hostname?.let { SysInfoRow("Hostname", it) }
            systemInfo.os?.let { SysInfoRow("OS", it) }
            systemInfo.kernel?.let { SysInfoRow("Kernel", it) }
            systemInfo.arch?.let { SysInfoRow("Arch", it) }
            systemInfo.uptime?.let { SysInfoRow("Uptime", it) }
            systemInfo.shell?.let { SysInfoRow("Shell", it) }
            systemInfo.sshVersion?.let { SysInfoRow("SSH", it) }
        }

        // Hardware card
        InfoCard(title = "Hardware") {
            systemInfo.cpuModel?.let { SysInfoRow("CPU", it) }
            systemInfo.cpuCount?.let { SysInfoRow("Cores", it) }
            systemInfo.loadAvg?.let { SysInfoRow("Load", it) }
            if (systemInfo.memoryTotal != null) {
                SysInfoRow("Memory", t(
                    "{used} / {total} (free: {free})",
                    "used" to (systemInfo.memoryUsed ?: "?"),
                    "total" to systemInfo.memoryTotal,
                    "free" to (systemInfo.memoryFree ?: "?")
                ))
            }
            if (systemInfo.diskTotal != null) {
                SysInfoRow("Disk /", "${systemInfo.diskUsed ?: "?"} / ${systemInfo.diskTotal} (${systemInfo.diskUsage ?: "?"})")
            }
        }

        // Network card
        InfoCard(title = "Network") {
            systemInfo.ipAddresses?.let { SysInfoRow("IP", it.trim()) }
        }

        // Sessions card
        InfoCard(title = "Sessions") {
            systemInfo.loggedInUsers?.let { SysInfoRow("Logged in", t("{count} user(s)", "count" to it)) }
            systemInfo.tmuxSessions?.let { SysInfoRow("Tmux", t("{count} session(s)", "count" to it)) }
        }
    } else {
        Text(
            text = t("Connect via Sessions tab to view system info"),
            style = tokens.type.bodySmall,
            color = tokens.colors.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    val tokens = MaterialTheme.appTokens
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.space.xs)) {
            Text(
                text = t(title),
                style = tokens.type.labelSmall,
                color = tokens.colors.primary
            )
            content()
        }
    }
}

@Composable
private fun SysInfoRow(label: String, value: String) {
    val tokens = MaterialTheme.appTokens
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = t(label),
            style = tokens.type.labelMedium,
            color = tokens.colors.onSurfaceVariant,
            modifier = Modifier.width(80.dpUnit())
        )
        Text(
            text = value,
            style = tokens.type.monoSmall,
            color = tokens.colors.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

// ---------------------------------------------------------------------------
// Config Host Card
// ---------------------------------------------------------------------------

@Composable
private fun ConfigHostCard(
    configHost: SshConfigHost,
    alreadyImported: Boolean,
    onImport: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    // Conditional background: imported items get a muted surface variant.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shape.lg)
            .background(
                if (alreadyImported) tokens.colors.surfaceVariant
                else tokens.colors.surfaceContainer
            )
            .padding(tokens.space.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Dns, contentDescription = null,
                modifier = Modifier.size(24.dpUnit()),
                tint = if (alreadyImported) tokens.colors.onSurfaceVariant
                else tokens.colors.primary
            )
            Spacer(modifier = Modifier.width(tokens.space.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = configHost.host,
                    style = tokens.type.titleSmall,
                    color = if (alreadyImported) tokens.colors.onSurfaceVariant
                    else tokens.colors.onSurface
                )
                val detail = buildString {
                    if (configHost.user != null) append("${configHost.user}@")
                    append(configHost.hostName ?: configHost.host)
                    if (configHost.port != null && configHost.port != 22) append(":${configHost.port}")
                }
                Text(
                    text = detail,
                    style = tokens.type.monoSmall,
                    color = tokens.colors.onSurfaceVariant
                )
                if (configHost.proxyJump != null) {
                    Text(
                        text = t("ProxyJump: {value}", "value" to configHost.proxyJump),
                        style = tokens.type.labelSmall,
                        color = tokens.colors.tertiary
                    )
                }
            }
            if (alreadyImported) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = t("Already added"),
                    modifier = Modifier.size(20.dpUnit()),
                    tint = tokens.colors.primary
                )
            } else {
                AppButton(
                    text = "Import",
                    onClick = onImport,
                    style = AppButtonStyle.Secondary,
                    leadingIcon = Icons.Filled.FileDownload
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Child Server Card
// ---------------------------------------------------------------------------

@Composable
private fun ChildServerCard(server: ServerEntity, onClick: () -> Unit) {
    val tokens = MaterialTheme.appTokens
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Dns, contentDescription = null,
                modifier = Modifier.size(24.dpUnit()),
                tint = tokens.colors.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(tokens.space.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.displayName,
                    style = tokens.type.titleSmall,
                    color = tokens.colors.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${server.username}@${server.hostname}:${server.port}",
                    style = tokens.type.monoSmall,
                    color = tokens.colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun Int.dpUnit() = androidx.compose.ui.unit.Dp(this.toFloat())
