// allow-bypass-B6: ExposedDropdownMenuBox anchor (Material 3 API requires raw OutlinedTextField)
package com.tmuxes.ui.screens.snippets

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import com.tmuxes.ui.components.app.AppLazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tmuxes.data.model.SnippetLibrary
import com.tmuxes.i18n.I18nRuntime
import com.tmuxes.i18n.t
import com.tmuxes.ui.components.app.AppRowAction
import com.tmuxes.ui.components.app.AppRowSwipe
import com.tmuxes.ui.components.app.AppMultiSelectTopBar
import com.tmuxes.ui.components.app.AppButton
import com.tmuxes.ui.components.app.AppButtonStyle
import com.tmuxes.ui.components.app.AppCard
import com.tmuxes.ui.components.app.AppCheckbox
import com.tmuxes.ui.components.app.AppDeleteDialog
import com.tmuxes.ui.components.app.AppDialog
import com.tmuxes.ui.components.app.AppFab
import com.tmuxes.ui.components.app.AppIconButton
import com.tmuxes.ui.components.app.AppScaffold
import com.tmuxes.ui.components.app.AppSwitch
import com.tmuxes.ui.components.app.libraryIcon
import com.tmuxes.ui.components.app.rememberAppEntryLazyListState
import com.tmuxes.ui.components.app.rememberAppMultiSelectState
import com.tmuxes.ui.design.appTokens
import com.tmuxes.ui.viewmodel.SnippetViewModel
import com.tmuxes.util.safeLaunch

// ---------------------------------------------------------------------------
// Element-physical sizes (icon glyphs / touch targets) — not layout spacing.
// ---------------------------------------------------------------------------

private val LibraryIconSize = 32.dp
private val PickerIconSize = 20.dp
private val FabClearanceNormal = 80.dp
private val FabClearanceMultiSelect = 140.dp

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SnippetsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLibraryDetail: (Long) -> Unit = {},
    viewModel: SnippetViewModel = viewModel()
) {
    val tokens = MaterialTheme.appTokens
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val libraries by viewModel.libraries.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var libraryToDelete by remember { mutableStateOf<SnippetLibrary?>(null) }
    val listState = rememberAppEntryLazyListState("snippets")

    val multiSelect = rememberAppMultiSelectState()

    // Count enabled snippets per library — derived from each library's
    // own snippets list (composition; no flat snippet collection).
    val enabledCountByLibrary = remember(libraries) {
        libraries.associate { lib -> lib.id to lib.snippets.count { it.isEnabled } }
    }

    // Back handler for multi-select mode
    BackHandler(enabled = multiSelect.isActive) {
        multiSelect.deactivate()
    }

    // Delete confirmation dialog (single item from swipe)
    libraryToDelete?.let { library ->
        AppDeleteDialog(
            title = "Delete Library",
            message = "Delete ${library.name} and all its snippets? This cannot be undone.",
            onConfirm = {
                viewModel.deleteLibrary(library)
                libraryToDelete = null
                scope.safeLaunch(tag = "SnippetsScreen") {
                    snackbarHostState.showSnackbar(I18nRuntime.t("Library deleted"))
                }
            },
            onDismiss = { libraryToDelete = null }
        )
    }

    if (showCreateDialog) {
        LibraryCreateDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { name, description, iconName ->
                viewModel.addLibrary(
                    SnippetLibrary(
                        id = 0L,
                        name = name,
                        description = description,
                        iconName = iconName
                    )
                )
                showCreateDialog = false
            }
        )
    }

    AppScaffold(
        title = "Snippets",
        onBack = onNavigateBack,
        actions = {
            if (libraries.isNotEmpty()) {
                AppIconButton(
                    icon = Icons.Filled.Checklist,
                    onClick = { multiSelect.activate() },
                    contentDescription = "Select"
                )
            }
        },
        fab = {
            if (!multiSelect.isActive) {
                AppFab(
                    icon = Icons.Filled.Add,
                    onClick = { showCreateDialog = true },
                    contentDescription = "Add Library"
                )
            }
        },
        snackbarHostState = snackbarHostState,
        topBar = if (multiSelect.isActive) {
            {
                val allKeys = libraries.map { it.id as Any }
                AppMultiSelectTopBar(
                    selectedCount = multiSelect.selectedCount,
                    totalCount = libraries.size,
                    isAllSelected = multiSelect.isAllSelected(allKeys),
                    onSelectAllToggle = {
                        if (multiSelect.isAllSelected(allKeys)) {
                            multiSelect.deselectAll()
                        } else {
                            multiSelect.selectAll(allKeys)
                        }
                    },
                    onClose = { multiSelect.deactivate() },
                    actions = {}
                )
            }
        } else null
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Always show the list — user libraries at top, preset libraries below
            AppLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = tokens.space.lg),
                verticalArrangement = Arrangement.spacedBy(tokens.space.sm)
            ) {
                item { Spacer(modifier = Modifier.height(tokens.space.sm)) }
                items(libraries, key = { it.id }) { library ->
                    LibraryListItem(
                        library = library,
                        enabledSnippetCount = enabledCountByLibrary[library.id] ?: 0,
                        isMultiSelectActive = multiSelect.isActive,
                        isSelected = multiSelect.isSelected(library.id),
                        onTap = {
                            if (multiSelect.isActive) {
                                multiSelect.toggleItem(library.id)
                            } else {
                                onNavigateToLibraryDetail(library.id)
                            }
                        },
                        onToggleEnabled = { viewModel.toggleLibraryEnabled(library.id) },
                        onEdit = { onNavigateToLibraryDetail(library.id) },
                        onDelete = { libraryToDelete = library }
                    )
                }
                // Extra bottom space for FAB and bottom bar
                item {
                    Spacer(
                        modifier = Modifier.height(
                            if (multiSelect.isActive) FabClearanceMultiSelect else FabClearanceNormal
                        )
                    )
                }
            }

            // Bottom action bar for multi-select
            AnimatedVisibility(
                visible = multiSelect.isActive,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                Surface(
                    tonalElevation = tokens.elevation.level2,
                    shadowElevation = tokens.elevation.level3,
                    color = tokens.colors.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = tokens.space.lg,
                                vertical = tokens.space.md
                            ),
                        horizontalArrangement = Arrangement.spacedBy(tokens.space.sm)
                    ) {
                        AppButton(
                            text = "Delete All",
                            style = AppButtonStyle.Danger,
                            leadingIcon = Icons.Filled.Delete,
                            enabled = multiSelect.selectedCount > 0,
                            onClick = {
                                val selected = multiSelect.selectedKeys
                                libraries.filter { it.id in selected }.forEach { lib ->
                                    viewModel.deleteLibrary(lib)
                                }
                                val count = selected.size
                                multiSelect.deactivate()
                                scope.safeLaunch(tag = "SnippetsScreen") {
                                    val message = if (count == 1) {
                                        I18nRuntime.t("{count} library deleted", "count" to count)
                                    } else {
                                        I18nRuntime.t("{count} libraries deleted", "count" to count)
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        AppButton(
                            text = "Enable",
                            style = AppButtonStyle.Primary,
                            leadingIcon = Icons.Filled.ToggleOn,
                            enabled = multiSelect.selectedCount > 0,
                            onClick = {
                                val selected = multiSelect.selectedKeys
                                libraries.filter { it.id in selected && !it.isEnabled }
                                    .forEach { viewModel.toggleLibraryEnabled(it.id) }
                                multiSelect.deactivate()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        AppButton(
                            text = "Disable",
                            style = AppButtonStyle.Secondary,
                            leadingIcon = Icons.Filled.ToggleOff,
                            enabled = multiSelect.selectedCount > 0,
                            onClick = {
                                val selected = multiSelect.selectedKeys
                                libraries.filter { it.id in selected && it.isEnabled }
                                    .forEach { viewModel.toggleLibraryEnabled(it.id) }
                                multiSelect.deactivate()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Library list item with AppRowSwipe + multi-select
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryListItem(
    library: SnippetLibrary,
    enabledSnippetCount: Int,
    isMultiSelectActive: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val disabledAlpha = if (!library.isEnabled) 0.45f else 1f

    val revealActions = listOf(
        AppRowAction(
            icon = if (library.isEnabled) Icons.Filled.ToggleOff else Icons.Filled.ToggleOn,
            color = tokens.status.warning,
            onClick = onToggleEnabled,
            label = if (library.isEnabled) "Disable" else "Enable"
        ),
        AppRowAction(
            icon = Icons.Filled.Edit,
            color = tokens.status.info,
            onClick = onEdit,
            label = "Edit"
        ),
        AppRowAction(
            icon = Icons.Filled.Delete,
            color = tokens.colors.error,
            onClick = onDelete,
            label = "Delete"
        )
    )

    if (isMultiSelectActive) {
        // In multi-select mode: no swipe, show checkbox
        LibraryCardContent(
            library = library,
            enabledSnippetCount = enabledSnippetCount,
            disabledAlpha = disabledAlpha,
            showCheckbox = true,
            isSelected = isSelected,
            onTap = onTap,
            onToggleEnabled = onToggleEnabled
        )
    } else {
        // Normal mode: AppRowSwipe with actions
        AppRowSwipe(
            actions = revealActions
        ) {
            LibraryCardContent(
                library = library,
                enabledSnippetCount = enabledSnippetCount,
                disabledAlpha = disabledAlpha,
                showCheckbox = false,
                isSelected = false,
                onTap = onTap,
                onToggleEnabled = onToggleEnabled
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Card content (shared between normal and multi-select mode)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryCardContent(
    library: SnippetLibrary,
    enabledSnippetCount: Int,
    disabledAlpha: Float,
    showCheckbox: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onTap,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(tokens.space.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showCheckbox) {
                AppCheckbox(
                    checked = isSelected,
                    onCheckedChange = { onTap() }
                )
                Spacer(modifier = Modifier.width(tokens.space.xs))
            }
            Icon(
                imageVector = libraryIcon(library.iconName),
                contentDescription = null,
                modifier = Modifier
                    .size(LibraryIconSize)
                    .alpha(disabledAlpha),
                tint = if (library.isEnabled)
                    tokens.colors.primary
                else
                    tokens.colors.outline
            )
            Spacer(modifier = Modifier.width(tokens.space.md))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(disabledAlpha)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.space.sm)
                ) {
                    Text(
                        text = library.name,
                        style = tokens.type.titleSmall,
                        color = tokens.colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (enabledSnippetCount > 0) {
                        Badge(
                            containerColor = tokens.colors.primaryContainer,
                            contentColor = tokens.colors.onPrimaryContainer
                        ) {
                            Text(text = enabledSnippetCount.toString())
                        }
                    }
                }
                if (library.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(tokens.space.xxs))
                    Text(
                        text = library.description,
                        style = tokens.type.bodySmall,
                        color = tokens.colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(tokens.space.sm))
            AppSwitch(
                checked = library.isEnabled,
                onCheckedChange = { onToggleEnabled() }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Create library dialog
// ---------------------------------------------------------------------------

private val iconNameOptions = listOf(
    "Code", "Dvr", "MiscellaneousServices", "Inventory2", "Folder", "Lan",
    "Monitor", "GetApp", "Article", "Security", "Terminal", "Storage",
    "Build", "BugReport", "Cloud"
)

@Composable
private fun LibraryCreateDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, iconName: String) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("Code") }
    var nameError by remember { mutableStateOf(false) }
    var iconMenuExpanded by remember { mutableStateOf(false) }

    AppDialog(
        title = "New Library",
        onDismiss = onDismiss,
        confirmLabel = "Create",
        onConfirm = {
            if (name.isBlank()) {
                nameError = true
            } else {
                onSave(name.trim(), description.trim(), selectedIcon)
            }
        },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.space.md)) {
                com.tmuxes.ui.components.app.AppTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = "Name",
                    placeholder = "My Commands",
                    isError = nameError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                com.tmuxes.ui.components.app.AppTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Description (optional)",
                    placeholder = "Frequently used commands",
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Icon picker — readOnly text field with leading icon + dropdown.
                // AppTextField doesn't support leadingIcon/readOnly, so the
                // raw OutlinedTextField is kept here; colors come from token
                // primary/outline/onSurface, no hex literals.
                Box {
                    OutlinedTextField(
                        value = selectedIcon,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(t("Icon")) },
                        leadingIcon = {
                            Icon(
                                libraryIcon(selectedIcon),
                                contentDescription = null,
                                tint = tokens.colors.primary,
                                modifier = Modifier.size(PickerIconSize)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { iconMenuExpanded = true },
                        shape = tokens.shape.sm,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = tokens.colors.primary,
                            unfocusedBorderColor = tokens.colors.outline,
                            disabledBorderColor = tokens.colors.outline,
                            focusedLabelColor = tokens.colors.primary,
                            unfocusedLabelColor = tokens.colors.onSurfaceVariant,
                            disabledLabelColor = tokens.colors.onSurfaceVariant,
                            focusedTextColor = tokens.colors.onSurface,
                            unfocusedTextColor = tokens.colors.onSurface,
                            disabledTextColor = tokens.colors.onSurface,
                            disabledLeadingIconColor = tokens.colors.primary
                        ),
                        enabled = true
                    )
                    DropdownMenu(
                        expanded = iconMenuExpanded,
                        onDismissRequest = { iconMenuExpanded = false }
                    ) {
                        iconNameOptions.forEach { iconName ->
                            DropdownMenuItem(
                                text = { Text(iconName) },
                                leadingIcon = {
                                    Icon(
                                        libraryIcon(iconName),
                                        contentDescription = null,
                                        modifier = Modifier.size(PickerIconSize)
                                    )
                                },
                                onClick = {
                                    selectedIcon = iconName
                                    iconMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}
