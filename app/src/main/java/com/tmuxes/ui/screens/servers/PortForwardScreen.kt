package com.tmuxes.ui.screens.servers

import androidx.compose.foundation.background
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
import com.tmuxes.ui.components.app.AppLazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tmuxes.i18n.I18nRuntime
import com.tmuxes.i18n.t
import com.tmuxes.ui.components.app.AppRowAction
import com.tmuxes.ui.components.app.AppRowSwipe
import com.tmuxes.ui.components.app.AppButtonStyle
import com.tmuxes.ui.components.app.AppDialog
import com.tmuxes.ui.components.app.AppEmptyState
import com.tmuxes.ui.components.app.AppFab
import com.tmuxes.ui.components.app.AppScaffold
import com.tmuxes.ui.components.app.AppTextField
import com.tmuxes.ui.components.app.rememberAppEntryLazyListState
import com.tmuxes.ui.design.appTokens
import com.tmuxes.ui.viewmodel.ServerDetailViewModel
import com.tmuxes.util.findComponentActivity

@Composable
fun PortForwardScreen(
    serverId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ServerDetailViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current.findComponentActivity()
            ?: LocalViewModelStoreOwner.current!!
    )
) {
    val tokens = MaterialTheme.appTokens
    val snackbarHostState = remember { SnackbarHostState() }
    val portForwards by viewModel.portForwards.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val serverStatus by viewModel.serverStatus.collectAsState()
    val isConnected = serverStatus.status == com.tmuxes.ssh.ServerStatus.CONNECTED
    val listState = rememberAppEntryLazyListState(serverId)

    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(I18nRuntime.t(msg))
        viewModel.clearError()
    }

    if (showAddDialog) {
        AddPortForwardDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { localPort, remoteHost, remotePort ->
                showAddDialog = false
                viewModel.setupPortForward(serverId, localPort, remoteHost, remotePort)
            }
        )
    }

    AppScaffold(
        title = "Port Forwarding",
        onBack = onNavigateBack,
        fab = {
            AppFab(
                icon = Icons.Filled.Add,
                onClick = {
                    if (isConnected) showAddDialog = true
                    else viewModel.setError("Connect to the server first")
                },
                contentDescription = "Add port forward"
            )
        },
        snackbarHostState = snackbarHostState
    ) { padding ->
        if (portForwards.isEmpty()) {
            AppEmptyState(
                icon = Icons.Filled.SwapHoriz,
                title = "No Port Forwards",
                subtitle = if (isConnected) "Tap + to add a port forward"
                else "Connect to the server first",
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        } else {
            AppLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding)
                    .padding(horizontal = tokens.space.lg),
                verticalArrangement = Arrangement.spacedBy(tokens.space.sm)
            ) {
                item { Spacer(modifier = Modifier.height(tokens.space.sm)) }
                items(
                    items = portForwards,
                    key = { "${it.localPort}-${it.remoteHost}-${it.remotePort}" }
                ) { entry ->
                    AppRowSwipe(
                        actions = listOf(
                            AppRowAction(
                                icon = Icons.Filled.Delete,
                                color = tokens.colors.danger,
                                onClick = { viewModel.removePortForward(entry) },
                                label = "Delete"
                            )
                        )
                    ) {
                        PortForwardCard(entry)
                    }
                }
                item { Spacer(modifier = Modifier.height(tokens.space.xxxl + tokens.space.xl)) }
            }
        }
    }
}

@Composable
private fun PortForwardCard(entry: ServerDetailViewModel.PortForward) {
    val tokens = MaterialTheme.appTokens
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(tokens.shape.md)
            .background(tokens.colors.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(tokens.space.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(10.dpUnit()).clip(CircleShape)
                    .background(if (entry.isActive) tokens.colors.success else tokens.colors.outline)
            )
            Spacer(modifier = Modifier.width(tokens.space.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = t(
                        "localhost:{localPort} -> {remoteHost}:{remotePort}",
                        "localPort" to entry.localPort,
                        "remoteHost" to entry.remoteHost,
                        "remotePort" to entry.remotePort
                    ),
                    style = tokens.type.bodyLarge,
                    color = tokens.colors.onSurface
                )
                Spacer(modifier = Modifier.height(tokens.space.xxs))
                Text(
                    text = t(if (entry.isActive) "Active" else "Inactive"),
                    style = tokens.type.bodySmall,
                    color = if (entry.isActive) tokens.colors.success else tokens.colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AddPortForwardDialog(
    onDismiss: () -> Unit,
    onConfirm: (localPort: Int, remoteHost: String, remotePort: Int) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    var localPort by remember { mutableStateOf("") }
    var remoteHost by remember { mutableStateOf("127.0.0.1") }
    var remotePort by remember { mutableStateOf("") }
    var localPortError by remember { mutableStateOf<String?>(null) }
    var remoteHostError by remember { mutableStateOf<String?>(null) }
    var remotePortError by remember { mutableStateOf<String?>(null) }

    AppDialog(
        title = "Add Port Forward",
        onDismiss = onDismiss,
        confirmLabel = "Add",
        confirmStyle = AppButtonStyle.Primary,
        onConfirm = {
            val localPortValue = localPort.toIntOrNull()?.takeIf { it in 1..65535 }
            val remotePortValue = remotePort.toIntOrNull()?.takeIf { it in 1..65535 }
            val remoteHostValue = remoteHost.trim()

            localPortError = if (localPortValue == null) "Enter a valid port (1-65535)" else null
            remotePortError = if (remotePortValue == null) "Enter a valid port (1-65535)" else null
            remoteHostError = if (remoteHostValue.isEmpty()) "Enter a remote host" else null

            if (localPortValue != null && remotePortValue != null && remoteHostValue.isNotEmpty()) {
                onConfirm(localPortValue, remoteHostValue, remotePortValue)
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.space.md)) {
            Text(
                text = t("Forward a local port to a remote host through the SSH tunnel."),
                style = tokens.type.bodySmall,
                color = tokens.colors.onSurfaceVariant
            )
            AppTextField(
                value = localPort,
                onValueChange = {
                    localPort = it.filter { c -> c.isDigit() }
                    localPortError = null
                },
                label = "Local Port",
                placeholder = "8080",
                isError = localPortError != null,
                supportingText = localPortError,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth()
            )
            AppTextField(
                value = remoteHost,
                onValueChange = {
                    remoteHost = it
                    remoteHostError = null
                },
                label = "Remote Host",
                placeholder = "127.0.0.1",
                isError = remoteHostError != null,
                supportingText = remoteHostError,
                modifier = Modifier.fillMaxWidth()
            )
            AppTextField(
                value = remotePort,
                onValueChange = {
                    remotePort = it.filter { c -> c.isDigit() }
                    remotePortError = null
                },
                label = "Remote Port",
                placeholder = "80",
                isError = remotePortError != null,
                supportingText = remotePortError,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun Int.dpUnit() = androidx.compose.ui.unit.Dp(this.toFloat())
