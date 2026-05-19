package com.tmuxes.ui.screens.keys

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.FileProvider
import com.tmuxes.i18n.I18nRuntime
import com.tmuxes.i18n.t
import com.tmuxes.ssh.SshKeyManager
import com.tmuxes.ssh.SshKeyType
import com.tmuxes.ui.components.app.AppRowAction
import com.tmuxes.ui.components.app.AppRowSwipe
import com.tmuxes.ui.components.app.AppButton
import com.tmuxes.ui.components.app.AppButtonStyle
import com.tmuxes.ui.components.app.AppCard
import com.tmuxes.ui.components.app.AppDeleteDialog
import com.tmuxes.ui.components.app.AppDialog
import com.tmuxes.ui.components.app.AppEmptyState
import com.tmuxes.ui.components.app.AppFab
import com.tmuxes.ui.components.app.AppHorizontalDivider
import com.tmuxes.ui.components.app.AppIconButton
import com.tmuxes.ui.components.app.AppIconRole
import com.tmuxes.ui.components.app.AppRadioButton
import com.tmuxes.ui.components.app.AppScaffold
import com.tmuxes.ui.components.app.AppTextField
import com.tmuxes.ui.components.app.rememberAppEntryLazyListState
import com.tmuxes.ui.design.appTokens
import com.tmuxes.util.safeLaunch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Key data holder used in the UI
// ---------------------------------------------------------------------------

private data class KeyInfo(
    val name: String,
    val type: String,
    val fingerprint: String = "",
    val createdAt: Long? = null
)

private fun detectKeyType(content: String): String {
    return when {
        content.contains("BEGIN OPENSSH PRIVATE KEY") -> "OpenSSH"
        content.contains("BEGIN EC PRIVATE KEY") -> "ECDSA"
        content.contains("BEGIN RSA PRIVATE KEY") -> "RSA"
        content.contains("BEGIN DSA PRIVATE KEY") -> "DSA"
        content.contains("BEGIN PRIVATE KEY") -> "PKCS#8"
        content.contains("Ed25519", ignoreCase = true) ||
                content.contains("ed25519", ignoreCase = true) -> "Ed25519"
        else -> "Unknown"
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun KeyManagerScreen(
    onNavigateBack: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var keys by remember { mutableStateOf<List<KeyInfo>>(emptyList()) }
    var showGenerateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf<KeyInfo?>(null) }
    var showRenameDialog by remember { mutableStateOf<KeyInfo?>(null) }
    var publicKeyText by remember { mutableStateOf("") }
    var detailFingerprint by remember { mutableStateOf("") }
    var detailCreatedAt by remember { mutableStateOf<Long?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    val listState = rememberAppEntryLazyListState("key_manager")

    // Load keys
    fun refreshKeys() {
        scope.safeLaunch(tag = "KeyManager") {
            val keyNames = SshKeyManager.listKeys(context)
            keys = keyNames.map { name ->
                val content = try {
                    SshKeyManager.loadKey(name, context)
                } catch (_: Exception) {
                    ""
                }
                val fingerprint = try {
                    if (content.isNotBlank()) SshKeyManager.getKeyFingerprint(content) else ""
                } catch (_: Exception) {
                    ""
                }
                val createdAt = try {
                    SshKeyManager.getKeyCreationDate(name, context)
                } catch (_: Exception) {
                    null
                }
                KeyInfo(
                    name = name,
                    type = detectKeyType(content),
                    fingerprint = fingerprint,
                    createdAt = createdAt
                )
            }
        }
    }

    LaunchedEffect(Unit) { refreshKeys() }

    // File picker for import
    var importKeyName by remember { mutableStateOf("") }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.safeLaunch(tag = "KeyManager") {
                try {
                    // File I/O on IO dispatcher — the composition scope runs on
                    // Main.immediate, so reading a large key file here would ANR.
                    val content = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    }
                    if (content.isNotBlank()) {
                        val keyName = importKeyName.ifBlank {
                            "imported_key_${System.currentTimeMillis()}"
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            SshKeyManager.saveKey(keyName, content, context)
                        }
                        refreshKeys()
                        snackbarHostState.showSnackbar(I18nRuntime.t("Key imported successfully"))
                        showImportDialog = false
                        importKeyName = ""
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar(
                        I18nRuntime.t("Failed to import key: {error}", "error" to e.message)
                    )
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
        var keyName by remember { mutableStateOf("") }

        AppDialog(
            title = "Generate SSH Key",
            onDismiss = { if (!isGenerating) showGenerateDialog = false },
            confirmLabel = "Generate",
            confirmEnabled = !isGenerating && keyName.isNotBlank(),
            onConfirm = {
                if (keyName.isBlank()) return@AppDialog
                isGenerating = true
                scope.safeLaunch(tag = "KeyManager") {
                    try {
                        val generated = SshKeyManager.generateKeyPair(selectedType)
                        SshKeyManager.saveGeneratedKey(keyName, generated, context)
                        refreshKeys()
                        snackbarHostState.showSnackbar(I18nRuntime.t("Key generated successfully"))
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(
                            I18nRuntime.t("Failed to generate key: {error}", "error" to e.message)
                        )
                    } finally {
                        isGenerating = false
                        showGenerateDialog = false
                    }
                }
            },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(tokens.space.md)) {
                    AppTextField(
                        value = keyName,
                        onValueChange = { keyName = it },
                        label = "Key Name",
                        placeholder = "my_server_key",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = t("Key type:"),
                        style = tokens.type.bodyMedium,
                        color = tokens.colors.onSurfaceVariant
                    )
                    keyTypes.forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(tokens.shape.sm)
                                .clickable { if (!isGenerating) selectedType = type }
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
        )
    }

    // Import dialog (for naming before file pick)
    if (showImportDialog) {
        AppDialog(
            title = "Import SSH Key",
            onDismiss = {
                showImportDialog = false
                importKeyName = ""
            },
            confirmLabel = "Choose File",
            confirmEnabled = importKeyName.isNotBlank(),
            onConfirm = { filePickerLauncher.launch("*/*") },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(tokens.space.sm)) {
                    Text(
                        text = t("Enter a name for the key, then choose a file."),
                        style = tokens.type.bodyMedium,
                        color = tokens.colors.onSurfaceVariant
                    )
                    AppTextField(
                        value = importKeyName,
                        onValueChange = { importKeyName = it },
                        label = "Key Name",
                        placeholder = "my_imported_key",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    // Rename dialog
    if (showRenameDialog != null) {
        val keyInfo = showRenameDialog!!
        var newName by remember { mutableStateOf(keyInfo.name) }

        AppDialog(
            title = "Rename Key",
            onDismiss = { showRenameDialog = null },
            confirmLabel = "Rename",
            confirmEnabled = newName.isNotBlank() && newName.trim() != keyInfo.name,
            onConfirm = {
                scope.safeLaunch(tag = "KeyManager") {
                    val trimmed = newName.trim()
                    if (trimmed.isNotBlank() && trimmed != keyInfo.name) {
                        val success = SshKeyManager.renameKey(keyInfo.name, trimmed, context)
                        if (success) {
                            refreshKeys()
                            // If the detail dialog was open for this key, close it
                            if (showDetailDialog?.name == keyInfo.name) {
                                showDetailDialog = null
                                publicKeyText = ""
                                detailFingerprint = ""
                                detailCreatedAt = null
                            }
                            snackbarHostState.showSnackbar(
                                I18nRuntime.t("Key renamed to \"{name}\"", "name" to trimmed)
                            )
                        } else {
                            snackbarHostState.showSnackbar(
                                I18nRuntime.t("Failed to rename key (name may already exist)")
                            )
                        }
                    }
                    showRenameDialog = null
                }
            },
            content = {
                AppTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = "New Name",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    // Key detail dialog
    if (showDetailDialog != null) {
        val keyInfo = showDetailDialog!!
        LaunchedEffect(keyInfo) {
            publicKeyText = try {
                SshKeyManager.getStoredPublicKey(keyInfo.name, context)
            } catch (_: Exception) {
                "Failed to extract public key"
            }
            detailFingerprint = try {
                val keyData = SshKeyManager.loadKey(keyInfo.name, context)
                SshKeyManager.getKeyFingerprint(keyData)
            } catch (_: Exception) {
                ""
            }
            detailCreatedAt = try {
                SshKeyManager.getKeyCreationDate(keyInfo.name, context)
            } catch (_: Exception) {
                null
            }
        }

        AppDialog(
            title = keyInfo.name,
            onDismiss = {
                showDetailDialog = null
                publicKeyText = ""
                detailFingerprint = ""
                detailCreatedAt = null
            },
            confirmLabel = "Copy Public Key",
            confirmEnabled = publicKeyText.isNotEmpty() && !publicKeyText.startsWith("Failed"),
            confirmStyle = AppButtonStyle.Primary,
            dismissLabel = "Close",
            onConfirm = {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("SSH Public Key", publicKeyText))
                } catch (_: Throwable) {} // allow-bypass-D5: clipboard set best-effort; snackbar follows regardless
                scope.safeLaunch(tag = "KeyManager") {
                    snackbarHostState.showSnackbar(I18nRuntime.t("Public key copied to clipboard"))
                }
                showDetailDialog = null
                publicKeyText = ""
                detailFingerprint = ""
                detailCreatedAt = null
            },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(tokens.space.sm)) {
                    // Type
                    Text(
                        text = t("Type: {type}", "type" to keyInfo.type),
                        style = tokens.type.bodyMedium,
                        color = tokens.colors.onSurfaceVariant
                    )

                    // Fingerprint
                    if (detailFingerprint.isNotEmpty()) {
                        Text(
                            text = t("Fingerprint:"),
                            style = tokens.type.labelMedium,
                            color = tokens.colors.primary
                        )
                        Text(
                            text = detailFingerprint,
                            style = tokens.type.monoSmall,
                            color = tokens.colors.onSurface
                        )
                    }

                    // Creation date — capture the timestamp into a local so
                    // we don't rely on smart-cast inside the remember lambda
                    // (which Kotlin cannot narrow, hence the previous `!!`
                    // that would NPE if the Compose state flipped back to
                    // null between the check and the lambda body).
                    val createdAtMs = detailCreatedAt
                    if (createdAtMs != null) {
                        val dateStr = remember(createdAtMs) {
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                                .format(Date(createdAtMs))
                        }
                        Text(
                            text = t("Created: {date}", "date" to dateStr),
                            style = tokens.type.bodySmall,
                            color = tokens.colors.onSurfaceVariant
                        )
                    }

                    AppHorizontalDivider()

                    // Public key
                    Text(
                        text = t("Public key (authorized_keys format):"),
                        style = tokens.type.labelMedium,
                        color = tokens.colors.primary
                    )
                    Text(
                        text = publicKeyText.ifEmpty { t("Loading...") },
                        style = tokens.type.monoSmall,
                        color = tokens.colors.onSurface,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(tokens.space.sm)
                    ) {
                        // Rename
                        AppButton(
                            text = "Rename",
                            onClick = { showRenameDialog = keyInfo },
                            style = AppButtonStyle.Text,
                            leadingIcon = Icons.Filled.DriveFileRenameOutline
                        )

                        // Export
                        AppButton(
                            text = "Export",
                            onClick = {
                                scope.safeLaunch(tag = "KeyManager") {
                                    exportKey(keyInfo.name, context, snackbarHostState)
                                }
                            },
                            style = AppButtonStyle.Text,
                            leadingIcon = Icons.Filled.Share
                        )
                    }
                }
            }
        )
    }

    AppScaffold(
        title = "SSH Keys",
        onBack = onNavigateBack,
        actions = {
            AppIconButton(
                icon = Icons.Filled.Upload,
                onClick = { showImportDialog = true },
                contentDescription = "Import Key"
            )
        },
        fab = {
            AppFab(
                icon = Icons.Filled.Add,
                onClick = { showGenerateDialog = true },
                contentDescription = "Generate New Key"
            )
        },
        snackbarHostState = snackbarHostState
    ) { padding ->
        if (keys.isEmpty()) {
            AppEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                icon = Icons.Filled.Key,
                title = "No SSH keys",
                subtitle = "Generate or import a key to get started"
            )
        } else {
            AppLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = tokens.space.lg),
                verticalArrangement = Arrangement.spacedBy(tokens.space.sm)
            ) {
                item { Spacer(modifier = Modifier.height(tokens.space.sm)) }
                items(keys, key = { it.name }) { keyInfo ->
                    KeyListItem(
                        keyInfo = keyInfo,
                        onTap = { showDetailDialog = keyInfo },
                        onDelete = {
                            scope.safeLaunch(tag = "KeyManager") {
                                try {
                                    SshKeyManager.deleteKey(keyInfo.name, context)
                                    refreshKeys()
                                    snackbarHostState.showSnackbar(I18nRuntime.t("Key deleted"))
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(
                                        I18nRuntime.t("Failed to delete key: {error}", "error" to e.message)
                                    )
                                }
                            }
                        },
                        onCopyPublicKey = {
                            scope.safeLaunch(tag = "KeyManager") {
                                try {
                                    val pubKey = SshKeyManager.getStoredPublicKey(keyInfo.name, context)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("SSH Public Key", pubKey))
                                    snackbarHostState.showSnackbar(I18nRuntime.t("Public key copied"))
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(
                                        I18nRuntime.t("Failed to get public key: {error}", "error" to e.message)
                                    )
                                }
                            }
                        },
                        onRename = { showRenameDialog = keyInfo }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dpUnit())) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Export helper
// ---------------------------------------------------------------------------

private suspend fun exportKey(
    keyName: String,
    context: Context,
    snackbarHostState: SnackbarHostState
) {
    try {
        // File I/O must not run on the main thread — switch to IO dispatcher.
        // safeLaunch() uses the composition scope which defaults to Main.immediate,
        // so without this withContext the writeText() call below blocks the UI
        // thread and can ANR on large key files.
        val exportFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val keyData = SshKeyManager.loadKey(keyName, context)
            val cacheDir = File(context.cacheDir, "export_keys")
            cacheDir.mkdirs()
            val file = File(cacheDir, keyName)
            file.writeText(keyData)
            file
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            exportFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SSH Key: $keyName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export SSH Key"))
    } catch (e: Exception) {
        snackbarHostState.showSnackbar(
            I18nRuntime.t("Failed to export key: {error}", "error" to e.message)
        )
    }
}

// ---------------------------------------------------------------------------
// Key list item with reveal-swipe actions
// ---------------------------------------------------------------------------

@Composable
private fun KeyListItem(
    keyInfo: KeyInfo,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    onCopyPublicKey: () -> Unit,
    onRename: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AppDeleteDialog(
            title = "Delete Key",
            message = "Delete \"${keyInfo.name}\"? This cannot be undone.",
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    AppRowSwipe(
        actions = listOf(
            AppRowAction(
                icon = Icons.Filled.DriveFileRenameOutline,
                color = tokens.colors.tertiary,
                onClick = onRename,
                label = "Rename"
            ),
            AppRowAction(
                icon = Icons.Filled.Delete,
                color = tokens.colors.danger,
                onClick = { showDeleteConfirm = true },
                label = "Delete"
            )
        )
    ) {
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onTap
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    Icons.Filled.Key,
                    contentDescription = null,
                    tint = tokens.colors.primary,
                    modifier = Modifier.size(32.dpUnit())
                )
                Spacer(modifier = Modifier.width(tokens.space.lg))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = keyInfo.name,
                        style = tokens.type.titleSmall,
                        color = tokens.colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = keyInfo.type,
                        style = tokens.type.bodySmall,
                        color = tokens.colors.onSurfaceVariant
                    )
                    if (keyInfo.fingerprint.isNotEmpty()) {
                        Text(
                            text = keyInfo.fingerprint,
                            style = tokens.type.monoSmall,
                            color = tokens.colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                AppIconButton(
                    icon = Icons.Filled.ContentCopy,
                    onClick = onCopyPublicKey,
                    contentDescription = "Copy Public Key",
                    role = AppIconRole.OnSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Element-physical sizes (icon stroke widths, fixed-pixel offsets, etc.)
// stay outside the spacing scale.
// ---------------------------------------------------------------------------
private fun Int.dpUnit() = androidx.compose.ui.unit.Dp(this.toFloat())
