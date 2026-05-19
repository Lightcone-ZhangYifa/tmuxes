// allow-bypass-B6: ExposedDropdownMenuBox anchors (Material 3 API requires raw OutlinedTextField)
package com.tmuxes.ui.screens.servers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.tmuxes.ui.components.app.appElasticVerticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tmuxes.data.model.AuthMethod
import com.tmuxes.data.model.ServerEntity
import com.tmuxes.data.settings.Settings
import com.tmuxes.i18n.t
import com.tmuxes.ssh.SshKeyManager
import com.tmuxes.ssh.SshKeyType
import com.tmuxes.ui.components.app.AppButton
import com.tmuxes.ui.components.app.AppButtonStyle
import com.tmuxes.ui.components.app.AppCard
import com.tmuxes.ui.components.app.AppCardVariant
import com.tmuxes.ui.components.app.AppDeleteDialog
import com.tmuxes.ui.components.app.AppHorizontalDivider
import com.tmuxes.ui.components.app.AppDialog
import com.tmuxes.ui.components.app.AppIconButton
import com.tmuxes.ui.components.app.AppIconRole
import com.tmuxes.ui.components.app.IdentityColorPicker
import com.tmuxes.ui.components.app.AppRadioButton
import com.tmuxes.ui.components.app.AppScaffold
import com.tmuxes.ui.components.app.AppSectionHeader
import com.tmuxes.ui.components.app.AppSwitch
import com.tmuxes.ui.components.app.AppTextField
import com.tmuxes.ui.components.app.appPressable
import com.tmuxes.ui.components.app.rememberAppEntryScrollState
import com.tmuxes.ui.design.appTokens
import com.tmuxes.ui.settings.NullableSettingOverrideRenderer
import com.tmuxes.ui.viewmodel.ServerViewModel
import com.tmuxes.util.safeLaunch

private data class ServerFormSnapshot(
    val name: String,
    val hostname: String,
    val port: String,
    val username: String,
    val authMethod: AuthMethod,
    val password: String,
    val privateKeyData: String,
    val passphrase: String,
    val color: Int,
    val parentId: Long?,
    val isEnabled: Boolean,
    val termType: String?,
    val connectionTimeout: Int?,
    val transportTimeout: Int?,
    val readTimeout: Int?,
    val keepAliveInterval: Int?,
    val keepaliveMaxCount: Int?,
    val compression: Boolean?,
    val strictHostKey: String?,
    val envVars: String?,
    val preferredCiphers: String?,
    val preferredKex: String?,
    val preferredMacs: String?,
    val preferredHostKeyAlgs: String?,
    val remoteForwards: String,
    val localForwards: String
)

// Element-physical dimensions (icon size, swatch size) keep raw dp via this helper.
private fun Int.dpUnit() = androidx.compose.ui.unit.Dp(this.toFloat())

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditServerScreen(
    serverId: Long?,
    onNavigateBack: () -> Unit,
    initialParentId: Long? = null,
    initialName: String? = null,
    initialHostname: String? = null,
    initialUsername: String? = null,
    initialPort: Int? = null,
    viewModel: ServerViewModel = viewModel()
) {
    val tokens = MaterialTheme.appTokens
    val isEditMode = serverId != null
    val servers by viewModel.servers.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberAppEntryScrollState(
        serverId,
        initialParentId,
        initialName,
        initialHostname,
        initialUsername
    )

    // Form state
    var name by rememberSaveable { mutableStateOf(initialName ?: "") }
    var hostname by rememberSaveable { mutableStateOf(initialHostname ?: "") }
    var port by rememberSaveable { mutableStateOf(initialPort?.toString() ?: "22") }
    var username by rememberSaveable { mutableStateOf(initialUsername ?: "") }
    var authMethod by rememberSaveable { mutableStateOf(AuthMethod.PASSWORD) }
    var password by remember { mutableStateOf("") }
    var privateKeyData by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var serverColor by remember { mutableIntStateOf(0) }
    var parentId by rememberSaveable { mutableStateOf(initialParentId) }
    var isEnabled by rememberSaveable { mutableStateOf(true) }

    // SSH advanced form state (null = use global default)
    var termType by rememberSaveable { mutableStateOf<String?>(null) }
    var connectionTimeout by rememberSaveable { mutableStateOf<Int?>(null) }
    var transportTimeout by rememberSaveable { mutableStateOf<Int?>(null) }
    var readTimeout by rememberSaveable { mutableStateOf<Int?>(null) }
    var keepAliveInterval by rememberSaveable { mutableStateOf<Int?>(null) }
    var keepaliveMaxCount by rememberSaveable { mutableStateOf<Int?>(null) }
    var compression by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var strictHostKey by rememberSaveable { mutableStateOf<String?>(null) }
    var envVars by rememberSaveable { mutableStateOf<String?>(null) }
    var preferredCiphers by rememberSaveable { mutableStateOf<String?>(null) }
    var preferredKex by rememberSaveable { mutableStateOf<String?>(null) }
    var preferredMacs by rememberSaveable { mutableStateOf<String?>(null) }
    var preferredHostKeyAlgs by rememberSaveable { mutableStateOf<String?>(null) }
    var remoteForwards by rememberSaveable { mutableStateOf("") }
    var localForwards by rememberSaveable { mutableStateOf("") }

    var showAdvancedSsh by rememberSaveable { mutableStateOf(false) }
    var showAlgorithmOverrides by rememberSaveable { mutableStateOf(false) }
    var showRemoteForwards by rememberSaveable { mutableStateOf(false) }
    var showAddRemoteForwardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var passphraseVisible by rememberSaveable { mutableStateOf(false) }
    var isLoaded by remember { mutableStateOf(!isEditMode) }
    var savedSnapshot by remember { mutableStateOf<ServerFormSnapshot?>(null) }

    // Validation state
    var hostnameError by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }

    fun currentSnapshot(): ServerFormSnapshot {
        return ServerFormSnapshot(
            name = name,
            hostname = hostname,
            port = port,
            username = username,
            authMethod = authMethod,
            password = password,
            privateKeyData = privateKeyData,
            passphrase = passphrase,
            color = serverColor,
            parentId = parentId,
            isEnabled = isEnabled,
            termType = termType,
            connectionTimeout = connectionTimeout,
            transportTimeout = transportTimeout,
            readTimeout = readTimeout,
            keepAliveInterval = keepAliveInterval,
            keepaliveMaxCount = keepaliveMaxCount,
            compression = compression,
            strictHostKey = strictHostKey,
            envVars = envVars,
            preferredCiphers = preferredCiphers,
            preferredKex = preferredKex,
            preferredMacs = preferredMacs,
            preferredHostKeyAlgs = preferredHostKeyAlgs,
            remoteForwards = remoteForwards,
            localForwards = localForwards
        )
    }

    // Load existing server data. LaunchedEffect body runs on the
    // composition scope — an uncaught throw propagates and crashes
    // the root composition. Wrap defensively so a bad repository
    // read (DB corruption, mid-migration race) can't crash the
    // edit screen.
    LaunchedEffect(serverId) {
        try {
        if (serverId != null) {
            val server = viewModel.getServerById(serverId)
            if (server == null) {
                isLoaded = true
                savedSnapshot = currentSnapshot()
                return@LaunchedEffect
            }
            name = server.name ?: ""
            hostname = server.hostname
            port = server.port.toString()
            username = server.username
            authMethod = server.authMethod
            password = server.password ?: ""
            privateKeyData = server.privateKeyData ?: ""
            passphrase = server.passphrase ?: ""
            serverColor = server.color
            parentId = server.parentId
            isEnabled = server.isEnabled
            // SSH advanced fields
            termType = server.termType
            connectionTimeout = server.connectionTimeout
            transportTimeout = server.transportTimeout
            readTimeout = server.readTimeout
            keepAliveInterval = server.keepAliveInterval
            keepaliveMaxCount = server.keepaliveMaxCount
            compression = server.compression
            strictHostKey = server.strictHostKey
            envVars = server.envVars
            preferredCiphers = server.preferredCiphers
            preferredKex = server.preferredKex
            preferredMacs = server.preferredMacs
            preferredHostKeyAlgs = server.preferredHostKeyAlgs
            remoteForwards = server.remoteForwards ?: ""
            localForwards = server.localForwards ?: ""
            isLoaded = true
            savedSnapshot = currentSnapshot()
        } else {
            savedSnapshot = currentSnapshot()
        }
        } catch (_: Throwable) {
            // Failed to load server — screen opens with blank form.
            // User can either navigate back or re-enter data manually.
            isLoaded = true
            savedSnapshot = currentSnapshot()
        }
    }

    val hasUnsavedChanges = isLoaded && savedSnapshot?.let { currentSnapshot() != it } == true

    fun requestNavigateBack() {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler(
        enabled = isLoaded &&
            !showDeleteDialog &&
            !showAddRemoteForwardDialog &&
            !showUnsavedDialog
    ) {
        requestNavigateBack()
    }

    fun validate(): Boolean {
        var valid = true

        if (hostname.isBlank()) {
            hostnameError = "Hostname is required"
            valid = false
        } else hostnameError = null

        if (username.isBlank()) {
            usernameError = "Username is required"
            valid = false
        } else usernameError = null

        return valid
    }

    fun save() {
        if (!validate()) return

        val server = ServerEntity(
            id = serverId ?: 0,
            name = name.trim().ifBlank { null },
            hostname = hostname.trim(),
            port = port.toIntOrNull() ?: 22,
            username = username.trim(),
            authMethod = authMethod,
            password = password.takeIf { authMethod == AuthMethod.PASSWORD },
            privateKeyData = privateKeyData.takeIf { authMethod == AuthMethod.KEY || authMethod == AuthMethod.KEY_WITH_PASSPHRASE },
            passphrase = passphrase.takeIf { authMethod == AuthMethod.KEY_WITH_PASSPHRASE },
            color = serverColor,
            parentId = parentId,
            isEnabled = isEnabled,
            // SSH advanced fields (null = use global default)
            termType = termType,
            connectionTimeout = connectionTimeout,
            transportTimeout = transportTimeout,
            readTimeout = readTimeout,
            keepAliveInterval = keepAliveInterval,
            keepaliveMaxCount = keepaliveMaxCount,
            compression = compression,
            strictHostKey = strictHostKey,
            envVars = envVars?.trimBlankToNull(),
            preferredCiphers = preferredCiphers?.trimBlankToNull(),
            preferredKex = preferredKex?.trimBlankToNull(),
            preferredMacs = preferredMacs?.trimBlankToNull(),
            preferredHostKeyAlgs = preferredHostKeyAlgs?.trimBlankToNull(),
            remoteForwards = normalizeForwardLines(remoteForwards).ifBlank { null },
            localForwards = normalizeForwardLines(localForwards).ifBlank { null }
        )

        if (isEditMode) {
            viewModel.updateServer(server)
        } else {
            viewModel.addServer(server)
        }
        savedSnapshot = currentSnapshot()
        onNavigateBack()
    }

    if (showUnsavedDialog) {
        AppDialog(
            title = "Unsaved Changes",
            text = "You have unsaved server changes. Save before leaving?",
            onDismiss = { showUnsavedDialog = false },
            confirmLabel = "Save",
            onConfirm = {
                showUnsavedDialog = false
                save()
            },
            dismissLabel = "Cancel",
            neutralLabel = "Discard",
            onNeutral = {
                showUnsavedDialog = false
                onNavigateBack()
            },
            neutralStyle = AppButtonStyle.Outlined
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AppDeleteDialog(
            title = "Delete Server",
            message = "Are you sure you want to delete \"{name}\"? This action cannot be undone.",
            messageArgs = mapOf("name" to name.ifBlank { "$username@$hostname" }),
            onConfirm = {
                showDeleteDialog = false
                if (serverId != null) {
                    scope.safeLaunch(tag = "AddEditServer") {
                        val server = viewModel.getServerById(serverId)
                        if (server != null) viewModel.deleteServer(server)
                        onNavigateBack()
                    }
                }
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    AppScaffold(
        title = if (isEditMode) "Edit Server" else "Add Server",
        onBack = { requestNavigateBack() },
        actions = {
            if (isEditMode) {
                AppIconButton(
                    icon = Icons.Filled.Delete,
                    onClick = { showDeleteDialog = true },
                    contentDescription = "Delete",
                    role = AppIconRole.Danger
                )
            }
        },
        snackbarHostState = snackbarHostState
    ) { padding ->
        if (!isLoaded) return@AppScaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .appElasticVerticalScroll(scrollState)
                .padding(horizontal = tokens.space.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.space.lg)
        ) {
            FormSection(
                title = "Connection Endpoint",
                description = "Host, port, and login username"
            ) {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Name (optional)",
                    placeholder = "Friendly label",
                    modifier = Modifier.fillMaxWidth()
                )

                AppTextField(
                    value = hostname,
                    onValueChange = { hostname = it; hostnameError = null },
                    label = "Hostname",
                    placeholder = "192.168.1.100 or example.com",
                    isError = hostnameError != null,
                    supportingText = hostnameError,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.space.md)
                ) {
                    AppTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = "Port",
                        placeholder = "22",
                        modifier = Modifier.weight(0.35f),
                        keyboardType = KeyboardType.Number
                    )
                    AppTextField(
                        value = username,
                        onValueChange = { username = it; usernameError = null },
                        label = "Username",
                        placeholder = "root",
                        modifier = Modifier.weight(0.65f),
                        isError = usernameError != null,
                        supportingText = usernameError
                    )
                }
            }

            FormSection(
                title = "Authentication",
                description = "Credentials used by this server"
            ) {
                if (parentId != null) {
                    Text(
                        text = t("This server reaches its host through the parent as a ProxyJump tunnel. It still authenticates with its own credentials below."),
                        style = tokens.type.bodySmall,
                        color = tokens.colors.onSurfaceVariant
                    )
                }

                AuthMethodSelector(
                    selected = authMethod,
                    availableMethods = authMethodOptions(),
                    onSelect = { authMethod = it }
                )

                when (authMethod) {
                    AuthMethod.PASSWORD -> {
                        AppTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Password",
                            placeholder = "Enter password",
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (passwordVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            trailingIcon = {
                                AppIconButton(
                                    icon = if (passwordVisible) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                    onClick = { passwordVisible = !passwordVisible },
                                    contentDescription = "Toggle visibility",
                                    role = AppIconRole.OnSurfaceVariant
                                )
                            }
                        )
                    }

                    AuthMethod.KEY -> {
                        KeyInput(
                            keyData = privateKeyData,
                            onKeyDataChange = { privateKeyData = it }
                        )
                    }

                    AuthMethod.KEY_WITH_PASSPHRASE -> {
                        KeyInput(
                            keyData = privateKeyData,
                            onKeyDataChange = { privateKeyData = it }
                        )
                        AppTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            label = "Passphrase",
                            placeholder = "Key passphrase",
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (passphraseVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            trailingIcon = {
                                AppIconButton(
                                    icon = if (passphraseVisible) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                    onClick = { passphraseVisible = !passphraseVisible },
                                    contentDescription = "Toggle visibility",
                                    role = AppIconRole.OnSurfaceVariant
                                )
                            }
                        )
                    }
                }
            }

            FormSection(
                title = "Server Profile",
                description = "Display color, ProxyJump parent, and connection state"
            ) {
                Text(
                    text = t("Server Color"),
                    style = tokens.type.bodySmall,
                    color = tokens.colors.onSurfaceVariant
                )
                IdentityColorPicker(
                    selectedColor = serverColor,
                    onColorSelected = { serverColor = it }
                )

                JumpHostSelector(
                    selectedId = parentId,
                    servers = servers.filter { it.id != serverId },
                    onSelect = { parentId = it }
                )

                SettingSwitch(
                    label = "Enabled",
                    description = "Auto-connect and keep this server connected",
                    checked = isEnabled,
                    onCheckedChange = { isEnabled = it }
                )
            }

            // ---------------------------------------------------------------
            // SSH advanced section
            // ---------------------------------------------------------------

            ExpandableHeader(
                text = "SSH Advanced Settings",
                subtitle = "Per-server overrides for global SSH defaults",
                expanded = showAdvancedSsh,
                onToggle = { showAdvancedSsh = !showAdvancedSsh }
            )

            AnimatedVisibility(
                visible = showAdvancedSsh,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(tokens.space.lg)) {
                    AppSectionHeader("Connection Overrides")
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(tokens.space.xs)
                    ) {
                        Column {
                            NullableSettingOverrideRenderer(
                                setting = Settings.sshDefaultTerm,
                                value = termType,
                                onValueChange = { termType = it }
                            )
                            AppHorizontalDivider(inset = true)
                            NullableSettingOverrideRenderer(
                                setting = Settings.sshConnectionTimeout,
                                value = connectionTimeout,
                                onValueChange = { connectionTimeout = it }
                            )
                            AppHorizontalDivider(inset = true)
                            NullableSettingOverrideRenderer(
                                setting = Settings.sshTransportTimeout,
                                value = transportTimeout,
                                onValueChange = { transportTimeout = it }
                            )
                            AppHorizontalDivider(inset = true)
                            NullableSettingOverrideRenderer(
                                setting = Settings.sshReadTimeout,
                                value = readTimeout,
                                onValueChange = { readTimeout = it }
                            )
                            AppHorizontalDivider(inset = true)
                            NullableSettingOverrideRenderer(
                                setting = Settings.sshKeepaliveInterval,
                                value = keepAliveInterval,
                                onValueChange = { keepAliveInterval = it }
                            )
                            AppHorizontalDivider(inset = true)
                            NullableSettingOverrideRenderer(
                                setting = Settings.sshKeepaliveMaxCount,
                                value = keepaliveMaxCount,
                                onValueChange = { keepaliveMaxCount = it }
                            )
                            AppHorizontalDivider(inset = true)
                            NullableSettingOverrideRenderer(
                                setting = Settings.sshCompression,
                                value = compression,
                                onValueChange = { compression = it }
                            )
                            AppHorizontalDivider(inset = true)
                            NullableSettingOverrideRenderer(
                                setting = Settings.sshStrictHostKey,
                                value = strictHostKey,
                                onValueChange = { strictHostKey = it }
                            )
                            AppHorizontalDivider(inset = true)
                            NullableSettingOverrideRenderer(
                                setting = Settings.sshEnvVars,
                                value = envVars,
                                onValueChange = { envVars = it }
                            )
                        }
                    }

                    // -------------------------------------------------------
                    // Algorithm Overrides (nested collapsible)
                    // -------------------------------------------------------

                    ExpandableHeader(
                        text = "Algorithm Overrides",
                        subtitle = "Cipher, KEX, MAC, and host key preference lists",
                        expanded = showAlgorithmOverrides,
                        onToggle = { showAlgorithmOverrides = !showAlgorithmOverrides }
                    )

                    AnimatedVisibility(
                        visible = showAlgorithmOverrides,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        AppCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(tokens.space.xs)
                        ) {
                            Column {
                                NullableSettingOverrideRenderer(
                                    setting = Settings.sshPreferredCiphers,
                                    value = preferredCiphers,
                                    onValueChange = { preferredCiphers = it }
                                )
                                AppHorizontalDivider(inset = true)
                                NullableSettingOverrideRenderer(
                                    setting = Settings.sshPreferredKex,
                                    value = preferredKex,
                                    onValueChange = { preferredKex = it }
                                )
                                AppHorizontalDivider(inset = true)
                                NullableSettingOverrideRenderer(
                                    setting = Settings.sshPreferredMacs,
                                    value = preferredMacs,
                                    onValueChange = { preferredMacs = it }
                                )
                                AppHorizontalDivider(inset = true)
                                NullableSettingOverrideRenderer(
                                    setting = Settings.sshPreferredHostKeyAlgs,
                                    value = preferredHostKeyAlgs,
                                    onValueChange = { preferredHostKeyAlgs = it }
                                )
                            }
                        }
                    }

                    // -------------------------------------------------------
                    // Remote Port Forwarding (nested collapsible)
                    // -------------------------------------------------------

                    ExpandableHeader(
                        text = "Port Forwarding",
                        subtitle = "Local and remote SSH tunnel rules",
                        expanded = showRemoteForwards,
                        onToggle = { showRemoteForwards = !showRemoteForwards }
                    )

                    AnimatedVisibility(
                        visible = showRemoteForwards,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(tokens.space.lg)) {
                            Column(verticalArrangement = Arrangement.spacedBy(tokens.space.sm)) {
                                Text(
                                    text = t("Remote Port Forwarding"),
                                    style = tokens.type.bodyLarge,
                                    color = tokens.colors.onSurface
                                )
                                HelpText("Forward connections from remote ports to this device. One rule is applied on connect.")

                                val forwardList = parseRemoteForwards(remoteForwards)

                                if (forwardList.isEmpty()) {
                                    Text(
                                        text = t("No remote forwards configured"),
                                        style = tokens.type.bodySmall,
                                        color = tokens.colors.onSurfaceVariant
                                    )
                                } else {
                                    forwardList.forEachIndexed { index, fwd ->
                                        RemoteForwardItem(
                                            forward = fwd,
                                            onDelete = {
                                                val updated = forwardList.toMutableList()
                                                updated.removeAt(index)
                                                remoteForwards = serializeRemoteForwards(updated)
                                            }
                                        )
                                    }
                                }

                                AppButton(
                                    text = "Add Remote Forward",
                                    onClick = { showAddRemoteForwardDialog = true },
                                    style = AppButtonStyle.Secondary,
                                    leadingIcon = Icons.Filled.Add
                                )
                            }

                            AppCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(tokens.space.md)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(tokens.space.sm)) {
                                    Text(
                                        text = t("Local Port Forwarding"),
                                        style = tokens.type.bodyLarge,
                                        color = tokens.colors.onSurface
                                    )
                                    HelpText("One rule per line: localPort:remoteHost:remotePort")
                                    AppTextField(
                                        value = localForwards,
                                        onValueChange = { localForwards = it },
                                        label = "Local Forwards",
                                        placeholder = "8080:127.0.0.1:80",
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = false,
                                        minLines = 2,
                                        maxLines = 6
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Add Remote Forward dialog
            if (showAddRemoteForwardDialog) {
                AddRemoteForwardDialog(
                    onDismiss = { showAddRemoteForwardDialog = false },
                    onConfirm = { remotePort, localHost, localPort ->
                        showAddRemoteForwardDialog = false
                        val entry = "$remotePort:$localHost:$localPort"
                        remoteForwards = if (remoteForwards.isBlank()) entry
                        else "${normalizeForwardLines(remoteForwards)}\n$entry"
                    }
                )
            }

            Spacer(modifier = Modifier.height(tokens.space.sm))

            // ---------------------------------------------------------------
            // Save button
            // ---------------------------------------------------------------

            AppButton(
                text = if (isEditMode) "Save Changes" else "Add Server",
                onClick = { save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dpUnit()),
                style = AppButtonStyle.Primary,
                leadingIcon = Icons.Filled.Save
            )

            Spacer(modifier = Modifier.height(88.dpUnit()))
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable components
// ---------------------------------------------------------------------------

@Composable
private fun FormSection(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Column(verticalArrangement = Arrangement.spacedBy(tokens.space.sm)) {
        AppSectionHeader(text = title, modifier = Modifier.padding(top = tokens.space.sm))
        Text(
            text = t(description),
            style = tokens.type.bodySmall,
            color = tokens.colors.onSurfaceVariant,
            modifier = Modifier.padding(start = tokens.space.xs)
        )
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(tokens.space.lg)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(tokens.space.lg),
                content = content
            )
        }
    }
}

@Composable
private fun AuthMethodSelector(
    selected: AuthMethod,
    availableMethods: List<AuthMethod>,
    onSelect: (AuthMethod) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Column(verticalArrangement = Arrangement.spacedBy(tokens.space.xs)) {
        availableMethods.forEach { method ->
            val label = authMethodLabel(method)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(tokens.shape.sm)
                    .appPressable { onSelect(method) }
                    .padding(vertical = tokens.space.xs, horizontal = tokens.space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppRadioButton(
                    selected = selected == method,
                    onClick = { onSelect(method) }
                )
                Spacer(modifier = Modifier.width(tokens.space.xs))
                Text(
                    text = t(label),
                    style = tokens.type.bodyMedium,
                    color = tokens.colors.onSurface
                )
            }
        }
    }
}

private fun authMethodLabel(method: AuthMethod): String {
    return when (method) {
        AuthMethod.PASSWORD -> "Password"
        AuthMethod.KEY -> "SSH Key"
        AuthMethod.KEY_WITH_PASSPHRASE -> "SSH Key + Passphrase"
    }
}

private fun authMethodOptions(): List<AuthMethod> =
    listOf(AuthMethod.PASSWORD, AuthMethod.KEY, AuthMethod.KEY_WITH_PASSPHRASE)

@Composable
private fun KeyInput(
    keyData: String,
    onKeyDataChange: (String) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showGenerateDialog by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var copyResultMessage by remember { mutableStateOf<String?>(null) }

    // File picker launcher for importing PEM/OpenSSH private key files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.safeLaunch(tag = "AddEditServer") {
                try {
                    // File I/O on IO dispatcher — composition scope runs on
                    // Main.immediate so reading a large file here would ANR.
                    val content = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    }
                    if (content.isNotBlank()) {
                        onKeyDataChange(content)
                    }
                } catch (_: Exception) {
                    // Silently fail if file cannot be read
                }
            }
        }
    }

    // Generate key dialog
    if (showGenerateDialog) {
        val keyTypes = listOf(
            SshKeyType.ED25519,
            SshKeyType.ECDSA_256,
            SshKeyType.RSA_2048,
            SshKeyType.RSA_4096
        )
        var selectedType by remember { mutableStateOf(SshKeyType.ED25519) }

        AppDialog(
            title = "Generate SSH Key",
            onDismiss = { if (!isGenerating) showGenerateDialog = false },
            confirmLabel = "Generate",
            confirmEnabled = !isGenerating,
            dismissLabel = "Cancel",
            onConfirm = {
                isGenerating = true
                scope.safeLaunch(tag = "AddEditServer") {
                    try {
                        val generated = SshKeyManager.generateKeyPair(selectedType)
                        onKeyDataChange(generated.privatePem)
                    } catch (_: Exception) {
                        // Generation failed
                    } finally {
                        isGenerating = false
                        showGenerateDialog = false
                    }
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.space.xs)) {
                Text(
                    text = t("Select key type:"),
                    style = tokens.type.bodyMedium,
                    color = tokens.colors.onSurfaceVariant
                )
                keyTypes.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(tokens.shape.sm)
                            .appPressable(enabled = !isGenerating) { selectedType = type }
                            .padding(vertical = tokens.space.xs, horizontal = tokens.space.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppRadioButton(
                            selected = selectedType == type,
                            onClick = { if (!isGenerating) selectedType = type }
                        )
                        Spacer(modifier = Modifier.width(tokens.space.xs))
                        Text(
                            text = type.displayName,
                            style = tokens.type.bodyMedium,
                            color = tokens.colors.onSurface
                        )
                    }
                }
                if (isGenerating) {
                    Text(
                        text = t("Generating key..."),
                        style = tokens.type.bodySmall,
                        color = tokens.colors.primary
                    )
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(tokens.space.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.space.sm)) {
            AppButton(
                text = "Import File",
                onClick = { filePickerLauncher.launch("*/*") },
                style = AppButtonStyle.Secondary,
                leadingIcon = Icons.Filled.Key
            )
            AppButton(
                text = "Generate New",
                onClick = { showGenerateDialog = true },
                style = AppButtonStyle.Secondary
            )
        }

        AppTextField(
            value = keyData,
            onValueChange = onKeyDataChange,
            label = "Private Key (PEM)",
            placeholder = "Paste your private key here...",
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 6
        )

        if (keyData.isNotBlank()) {
            Text(
                text = t("{count} key lines loaded", "count" to keyData.lines().size),
                style = tokens.type.labelSmall,
                color = tokens.colors.tertiary
            )

            AppButton(
                text = "Copy Public Key",
                onClick = {
                    scope.safeLaunch(tag = "AddEditServer") {
                        try {
                            val publicKey = SshKeyManager.getPublicKeyString(keyData)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SSH Public Key", publicKey))
                            copyResultMessage = "Public key copied to clipboard"
                        } catch (_: Exception) {
                            copyResultMessage = "Failed to extract public key"
                        }
                    }
                },
                style = AppButtonStyle.Secondary,
                leadingIcon = Icons.Filled.ContentCopy
            )

            val currentCopyResultMessage = copyResultMessage
            if (currentCopyResultMessage != null) {
                Text(
                    text = t(currentCopyResultMessage),
                    style = tokens.type.labelSmall,
                    color = tokens.colors.tertiary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JumpHostSelector(
    selectedId: Long?,
    servers: List<ServerEntity>,
    onSelect: (Long?) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    var expanded by remember { mutableStateOf(false) }
    val selectedServer = servers.find { it.id == selectedId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedServer?.let { "${it.displayName} (${it.hostname})" } ?: t("None"),
            onValueChange = {},
            readOnly = true,
            label = { Text(t("Parent Server")) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = tokens.shape.sm,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = tokens.colors.primary,
                unfocusedBorderColor = tokens.colors.outline,
                focusedLabelColor = tokens.colors.primary,
                unfocusedLabelColor = tokens.colors.onSurfaceVariant,
                focusedTextColor = tokens.colors.onSurface,
                unfocusedTextColor = tokens.colors.onSurface,
                cursorColor = tokens.colors.primary
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(t("None")) },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            servers.forEach { server ->
                DropdownMenuItem(
                    text = { Text("${server.displayName} (${server.hostname})") },
                    onClick = {
                        onSelect(server.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shape.md)
            .appPressable { onCheckedChange(!checked) }
            .padding(vertical = tokens.space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = t(label),
                style = tokens.type.bodyLarge,
                color = tokens.colors.onSurface
            )
            Text(
                text = t(description),
                style = tokens.type.bodySmall,
                color = tokens.colors.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(tokens.space.lg))
        AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

// ---------------------------------------------------------------------------
// Expandable header (reused for SSH advanced and nested sections)
// ---------------------------------------------------------------------------

@Composable
private fun ExpandableHeader(
    text: String,
    subtitle: String? = null,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shape.md)
            .appPressable(onClick = onToggle)
            .padding(vertical = tokens.space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(tokens.space.xxs)
        ) {
            Text(
                text = t(text),
                style = tokens.type.titleSmall,
                color = tokens.colors.primary
            )
            if (subtitle != null) {
                Text(
                    text = t(subtitle),
                    style = tokens.type.bodySmall,
                    color = tokens.colors.onSurfaceVariant
                )
            }
        }
        androidx.compose.material3.Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = t("Toggle {name}", "name" to t(text)),
            tint = tokens.colors.primary
        )
    }
}

// ---------------------------------------------------------------------------
// Help text
// ---------------------------------------------------------------------------

@Composable
private fun HelpText(text: String) {
    val tokens = MaterialTheme.appTokens
    Text(
        text = t(text),
        style = tokens.type.bodySmall,
        color = tokens.colors.onSurfaceVariant,
        modifier = Modifier.padding(start = tokens.space.xs)
    )
}

// ---------------------------------------------------------------------------
// Remote forward helpers
// ---------------------------------------------------------------------------

private data class RemoteForwardEntry(
    val remotePort: Int,
    val localHost: String,
    val localPort: Int
)

private fun parseRemoteForwards(text: String): List<RemoteForwardEntry> {
    if (text.isBlank()) return emptyList()
    return text.lineSequence().flatMap { it.split(',') }.mapNotNull { entry ->
        val parts = entry.trim().split(':')
        if (parts.size == 3) {
            val rp = parts[0].toIntOrNull() ?: return@mapNotNull null
            val lh = parts[1]
            val lp = parts[2].toIntOrNull() ?: return@mapNotNull null
            RemoteForwardEntry(rp, lh, lp)
        } else null
    }.toList()
}

private fun serializeRemoteForwards(list: List<RemoteForwardEntry>): String {
    return list.joinToString("\n") { "${it.remotePort}:${it.localHost}:${it.localPort}" }
}

private fun normalizeForwardLines(text: String): String =
    text.lineSequence()
        .flatMap { it.split(',') }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

private fun String.trimBlankToNull(): String? = trim().ifBlank { null }

@Composable
private fun RemoteForwardItem(
    forward: RemoteForwardEntry,
    onDelete: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        variant = AppCardVariant.Filled,
        contentPadding = PaddingValues(
            horizontal = tokens.space.md,
            vertical = tokens.space.sm
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = t(
                        "remote:{remotePort} -> {localHost}:{localPort}",
                        "remotePort" to forward.remotePort,
                        "localHost" to forward.localHost,
                        "localPort" to forward.localPort
                    ),
                    style = tokens.type.bodyMedium,
                    color = tokens.colors.onSurface
                )
            }
            AppIconButton(
                icon = Icons.Filled.Delete,
                onClick = onDelete,
                contentDescription = "Remove",
                role = AppIconRole.Danger
            )
        }
    }
}

@Composable
private fun AddRemoteForwardDialog(
    onDismiss: () -> Unit,
    onConfirm: (remotePort: Int, localHost: String, localPort: Int) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    var remotePort by remember { mutableStateOf("") }
    var localHost by remember { mutableStateOf("127.0.0.1") }
    var localPort by remember { mutableStateOf("") }
    var remotePortError by remember { mutableStateOf<String?>(null) }
    var localHostError by remember { mutableStateOf<String?>(null) }
    var localPortError by remember { mutableStateOf<String?>(null) }

    AppDialog(
        title = "Add Remote Forward",
        onDismiss = onDismiss,
        confirmLabel = "Add",
        confirmStyle = AppButtonStyle.Primary,
        onConfirm = {
            val remotePortValue = remotePort.toIntOrNull()?.takeIf { it in 1..65535 }
            val localPortValue = localPort.toIntOrNull()?.takeIf { it in 1..65535 }
            val localHostValue = localHost.trim()

            remotePortError = if (remotePortValue == null) "Enter a valid port (1-65535)" else null
            localPortError = if (localPortValue == null) "Enter a valid port (1-65535)" else null
            localHostError = if (localHostValue.isEmpty()) "Enter a local host" else null

            if (remotePortValue != null && localPortValue != null && localHostValue.isNotEmpty()) {
                onConfirm(remotePortValue, localHostValue, localPortValue)
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.space.md)) {
            Text(
                text = t("Forward connections from a remote port to a local address."),
                style = tokens.type.bodySmall,
                color = tokens.colors.onSurfaceVariant
            )

            AppTextField(
                value = remotePort,
                onValueChange = {
                    remotePort = it.filter { c -> c.isDigit() }
                    remotePortError = null
                },
                label = "Remote Port",
                placeholder = "8080",
                isError = remotePortError != null,
                supportingText = remotePortError,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth()
            )

            AppTextField(
                value = localHost,
                onValueChange = {
                    localHost = it
                    localHostError = null
                },
                label = "Local Host",
                placeholder = "127.0.0.1",
                isError = localHostError != null,
                supportingText = localHostError,
                modifier = Modifier.fillMaxWidth()
            )

            AppTextField(
                value = localPort,
                onValueChange = {
                    localPort = it.filter { c -> c.isDigit() }
                    localPortError = null
                },
                label = "Local Port",
                placeholder = "80",
                isError = localPortError != null,
                supportingText = localPortError,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
