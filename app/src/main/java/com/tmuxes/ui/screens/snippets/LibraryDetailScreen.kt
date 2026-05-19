package com.tmuxes.ui.screens.snippets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.tmuxes.ui.components.app.AppLazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tmuxes.data.model.CommandSnippet
import com.tmuxes.data.model.SnippetLibrary
import com.tmuxes.i18n.I18nRuntime
import com.tmuxes.i18n.t
import com.tmuxes.ui.components.app.AppRowAction
import com.tmuxes.ui.components.app.AppRowSwipe
import com.tmuxes.ui.components.app.AppMultiSelectTopBar
import com.tmuxes.ui.components.app.AppCard
import com.tmuxes.ui.components.app.AppCheckbox
import com.tmuxes.ui.components.app.AppDeleteDialog
import com.tmuxes.ui.components.app.AppDialog
import com.tmuxes.ui.components.app.AppEmptyState
import com.tmuxes.ui.components.app.AppFab
import com.tmuxes.ui.components.app.AppIconButton
import com.tmuxes.ui.components.app.AppIconRole
import com.tmuxes.ui.components.app.AppScaffold
import com.tmuxes.ui.components.app.AppTextField
import com.tmuxes.ui.components.app.libraryIcon
import com.tmuxes.ui.components.app.rememberAppEntryLazyListState
import com.tmuxes.ui.components.app.rememberAppMultiSelectState
import com.tmuxes.ui.design.appTokens
import com.tmuxes.ui.design.listPlacementSpec
import com.tmuxes.ui.viewmodel.SnippetViewModel
import com.tmuxes.util.safeLaunch
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// Element-physical sizes (icon glyphs / touch targets) — not layout spacing.
// ---------------------------------------------------------------------------

private val DragHandleTouchSize = 48.dp
private val DragHandleIconSize = 20.dp
private val PickerLibraryIconSize = 24.dp
private val FabClearance = 80.dp

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryDetailScreen(
    libraryId: Long,
    onNavigateBack: () -> Unit,
    viewModel: SnippetViewModel = viewModel()
) {
    val tokens = MaterialTheme.appTokens
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val multiSelectState = rememberAppMultiSelectState()

    val allLibraries by viewModel.libraries.collectAsState()
    val library by remember(allLibraries, libraryId) {
        derivedStateOf { allLibraries.find { it.id == libraryId } }
    }
    // YAML-native: snippets live INSIDE the library (composition).
    val librarySnippets by remember(library) {
        derivedStateOf { library?.snippets.orEmpty() }
    }
    val lastDeletedSnippet by viewModel.lastDeletedSnippet.collectAsState()

    // Search
    var searchQuery by remember { mutableStateOf("") }
    val filteredSnippets by remember(librarySnippets, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                librarySnippets
            } else {
                val q = searchQuery.lowercase()
                librarySnippets.filter { snippet ->
                    snippet.name.lowercase().contains(q) ||
                        snippet.command.lowercase().contains(q)
                }
            }
        }
    }
    val showSearchBar by remember(librarySnippets) {
        derivedStateOf { librarySnippets.size >= 5 }
    }

    // Dialog state
    var showAddEditSnippetDialog by remember { mutableStateOf(false) }
    var editingSnippet by remember { mutableStateOf<CommandSnippet?>(null) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var movingSnippet by remember { mutableStateOf<CommandSnippet?>(null) }

    // Multi-select batch dialog state
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showBatchMoveDialog by remember { mutableStateOf(false) }

    // Drag-to-reorder state
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val localDragList = remember(filteredSnippets) { filteredSnippets.toMutableStateList() }
    val listState = rememberAppEntryLazyListState(libraryId)
    val haptic = LocalHapticFeedback.current

    fun findSwapTarget(): Int {
        if (draggedIndex < 0) return -1
        val layoutInfo = listState.layoutInfo
        val allVisible = layoutInfo.visibleItemsInfo

        // Find dragged item's layout info by matching key to localDragList[draggedIndex].id
        val draggedKey = localDragList.getOrNull(draggedIndex)?.id ?: return -1
        val draggedInfo = allVisible.firstOrNull { it.key == draggedKey } ?: return -1
        val draggedCenter = draggedInfo.offset + dragOffsetY.roundToInt() + draggedInfo.size / 2

        for (info in allVisible) {
            if (info.key == draggedKey) continue
            if (info.key !is Long) continue // skip non-snippet items
            val targetIndex = localDragList.indexOfFirst { it.id == info.key }
            if (targetIndex < 0) continue

            val itemCenter = info.offset + info.size / 2
            if (draggedIndex < targetIndex && draggedCenter > itemCenter) return targetIndex
            if (draggedIndex > targetIndex && draggedCenter < itemCenter) return targetIndex
        }
        return -1
    }

    fun handleSnippetDragEnd() {
        if (draggedIndex >= 0 && draggedIndex < localDragList.size) {
            viewModel.reorderSnippets(libraryId, localDragList.map { it.id })
        }
        draggedIndex = -1
        dragOffsetY = 0f
    }

    // Back handler for multi-select
    BackHandler(enabled = multiSelectState.isActive) {
        multiSelectState.deactivate()
    }

    // -- Undo delete snackbar --
    LaunchedEffect(lastDeletedSnippet) {
        val deleted = lastDeletedSnippet ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = I18nRuntime.t("Snippet deleted"),
            actionLabel = I18nRuntime.t("Undo")
        )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.undoDeleteSnippet()
            SnackbarResult.Dismissed -> viewModel.clearLastDeletedSnippet()
        }
    }

    // -- Add/edit snippet dialog --
    if (showAddEditSnippetDialog) {
        SnippetDialog(
            snippet = editingSnippet,
            onDismiss = {
                showAddEditSnippetDialog = false
                editingSnippet = null
            },
            onSave = { name, command ->
                // Capture the snippet into a local so a concurrent state
                // update that nulls editingSnippet between the `!= null`
                // check and the `!!.copy` call can't throw — the whole
                // onSave lambda executes on the main thread, so racing is
                // unlikely, but the pattern is safer against future edits.
                val editing = editingSnippet
                if (editing != null) {
                    viewModel.updateSnippet(
                        libraryId,
                        editing.copy(name = name, command = command)
                    )
                } else {
                    viewModel.addSnippet(
                        libraryId,
                        CommandSnippet(
                            id = 0L,
                            name = name,
                            command = command
                        )
                    )
                }
                showAddEditSnippetDialog = false
                editingSnippet = null
            }
        )
    }

    // -- Move-to-library picker dialog (single snippet) --
    val movingSnippetLocal = movingSnippet
    if (showMoveDialog && movingSnippetLocal != null) {
        LibraryPickerDialog(
            libraries = allLibraries.filter { it.id != libraryId },
            onDismiss = {
                showMoveDialog = false
                movingSnippet = null
            },
            onSelect = { targetLibraryId ->
                viewModel.moveSnippet(libraryId, movingSnippetLocal.id, targetLibraryId)
                showMoveDialog = false
                movingSnippet = null
                scope.safeLaunch(tag = "LibraryDetail") {
                    snackbarHostState.showSnackbar(I18nRuntime.t("Snippet moved"))
                }
            }
        )
    }

    // -- Batch delete confirmation --
    if (showBatchDeleteConfirm) {
        val count = multiSelectState.selectedCount
        AppDeleteDialog(
            title = "Delete Snippets",
            message = "Delete $count selected snippet${if (count != 1) "s" else ""}? This cannot be undone.",
            onConfirm = {
                // `mapNotNull { it as? Long }` rather than `map { it as Long }`
                // — the selectedKeys set is typed as Set<Any> and a stale
                // element from a previous screen's selection state could
                // otherwise ClassCastException the whole batch operation.
                val selectedIds = multiSelectState.selectedKeys.mapNotNull { it as? Long }.toSet()
                librarySnippets.filter { it.id in selectedIds }.forEach { snippet ->
                    viewModel.deleteSnippet(libraryId, snippet)
                }
                showBatchDeleteConfirm = false
                multiSelectState.deactivate()
                scope.safeLaunch(tag = "LibraryDetail") {
                    val message = if (count == 1) {
                        I18nRuntime.t("{count} snippet deleted", "count" to count)
                    } else {
                        I18nRuntime.t("{count} snippets deleted", "count" to count)
                    }
                    snackbarHostState.showSnackbar(message)
                }
            },
            onDismiss = { showBatchDeleteConfirm = false }
        )
    }

    // -- Batch move dialog --
    if (showBatchMoveDialog) {
        LibraryPickerDialog(
            libraries = allLibraries.filter { it.id != libraryId },
            onDismiss = { showBatchMoveDialog = false },
            onSelect = { targetLibraryId ->
                // `mapNotNull { it as? Long }` rather than `map { it as Long }`
                // — the selectedKeys set is typed as Set<Any> and a stale
                // element from a previous screen's selection state could
                // otherwise ClassCastException the whole batch operation.
                val selectedIds = multiSelectState.selectedKeys.mapNotNull { it as? Long }.toSet()
                selectedIds.forEach { snippetId ->
                    viewModel.moveSnippet(libraryId, snippetId, targetLibraryId)
                }
                val count = selectedIds.size
                showBatchMoveDialog = false
                multiSelectState.deactivate()
                scope.safeLaunch(tag = "LibraryDetail") {
                    val message = if (count == 1) {
                        I18nRuntime.t("{count} snippet moved", "count" to count)
                    } else {
                        I18nRuntime.t("{count} snippets moved", "count" to count)
                    }
                    snackbarHostState.showSnackbar(message)
                }
            }
        )
    }

    AppScaffold(
        title = library?.name ?: "Library",
        onBack = onNavigateBack,
        actions = {
            AppIconButton(
                icon = Icons.Filled.Checklist,
                onClick = { multiSelectState.activate() },
                contentDescription = "Select"
            )
        },
        fab = {
            if (!multiSelectState.isActive) {
                AppFab(
                    icon = Icons.Filled.Add,
                    onClick = {
                        editingSnippet = null
                        showAddEditSnippetDialog = true
                    },
                    contentDescription = "Add Snippet"
                )
            }
        },
        snackbarHostState = snackbarHostState,
        topBar = if (multiSelectState.isActive) {
            {
                AppMultiSelectTopBar(
                    selectedCount = multiSelectState.selectedCount,
                    totalCount = filteredSnippets.size,
                    isAllSelected = multiSelectState.isAllSelected(
                        filteredSnippets.map { it.id }
                    ),
                    onSelectAllToggle = {
                        val allKeys = filteredSnippets.map { it.id }
                        if (multiSelectState.isAllSelected(allKeys)) {
                            multiSelectState.deselectAll()
                        } else {
                            multiSelectState.selectAll(allKeys)
                        }
                    },
                    onClose = { multiSelectState.deactivate() },
                    actions = {
                        AppIconButton(
                            icon = Icons.AutoMirrored.Filled.DriveFileMove,
                            onClick = { showBatchMoveDialog = true },
                            contentDescription = "Move",
                            enabled = multiSelectState.selectedCount > 0
                        )
                        AppIconButton(
                            icon = Icons.Filled.Delete,
                            onClick = { showBatchDeleteConfirm = true },
                            contentDescription = "Delete",
                            enabled = multiSelectState.selectedCount > 0
                        )
                    }
                )
            }
        } else null
    ) { padding ->
        if (librarySnippets.isEmpty()) {
            AppEmptyState(
                icon = libraryIcon(library?.iconName ?: "Code"),
                title = "No snippets",
                subtitle = "Tap + to add a command snippet",
                modifier = Modifier.padding(padding)
            )
        } else {
            // Edge auto-scroll during drag. LaunchedEffect body runs on
            // the composition scope; an uncaught throw crashes the root
            // composition. listState.scrollBy / layoutInfo access can
            // race with a concurrent layout invalidation and throw —
            // protect the whole loop + each iteration.
            LaunchedEffect(draggedIndex) {
                if (draggedIndex < 0) return@LaunchedEffect
                try {
                    while (draggedIndex >= 0) {
                        try {
                            val layoutInfo = listState.layoutInfo
                            val draggedKey = localDragList.getOrNull(draggedIndex)?.id
                            val draggedInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggedKey }
                            if (draggedInfo != null) {
                                val draggedTop = draggedInfo.offset + dragOffsetY.roundToInt()
                                val draggedBottom = draggedTop + draggedInfo.size
                                val viewportEnd = layoutInfo.viewportEndOffset
                                val viewportStart = layoutInfo.viewportStartOffset
                                val edgeZone = 100

                                val scrollSpeed = when {
                                    draggedTop - viewportStart < edgeZone -> {
                                        val proximity = (edgeZone - (draggedTop - viewportStart)).coerceIn(0, edgeZone)
                                        -(proximity * 0.5f)  // negative = scroll up
                                    }
                                    viewportEnd - draggedBottom < edgeZone -> {
                                        val proximity = (edgeZone - (viewportEnd - draggedBottom)).coerceIn(0, edgeZone)
                                        proximity * 0.5f  // positive = scroll down
                                    }
                                    else -> 0f
                                }
                                if (scrollSpeed != 0f) {
                                    listState.scrollBy(scrollSpeed)
                                }
                            }
                        } catch (ce: kotlinx.coroutines.CancellationException) {
                            throw ce
                        } catch (_: Throwable) {
                            // One frame failed — keep the loop going so drag
                            // remains functional on the next tick.
                        }
                        delay(16) // ~60fps
                    }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // drag ended / screen left — normal flow
                } catch (_: Throwable) {
                    // Unexpected loop exit — swallow so composition stays alive.
                }
            }

            AppLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = tokens.space.lg),
                verticalArrangement = Arrangement.spacedBy(tokens.space.xs)
            ) {
                // Search bar when >= 5 snippets
                if (showSearchBar) {
                    item(key = "search_bar") {
                        AppTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Search snippets",
                            singleLine = true,
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
                                        icon = Icons.Filled.Clear,
                                        onClick = { searchQuery = "" },
                                        contentDescription = "Clear search",
                                        role = AppIconRole.OnSurfaceVariant
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = tokens.space.sm)
                        )
                    }
                }

                // Filtered snippet list
                itemsIndexed(localDragList, key = { _, s -> s.id }) { index, snippet ->
                    val isDragged = draggedIndex == index

                    Box(
                        modifier = if (isDragged) {
                            Modifier
                                .zIndex(1f)
                                .offset { IntOffset(0, dragOffsetY.roundToInt()) }
                                .graphicsLayer {
                                    scaleX = tokens.motion.dragLiftScale
                                    scaleY = tokens.motion.dragLiftScale
                                    alpha = tokens.motion.dragLiftAlpha
                                }
                        } else {
                            Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = tokens.motion.listPlacementSpec()
                            )
                        }
                    ) {
                        LibrarySnippetListItem(
                            snippet = snippet,
                            isMultiSelectActive = multiSelectState.isActive,
                            isSelected = multiSelectState.isSelected(snippet.id),
                            isDragged = isDragged,
                            onTap = {
                                if (multiSelectState.isActive) {
                                    multiSelectState.toggleItem(snippet.id)
                                } else {
                                    editingSnippet = snippet
                                    showAddEditSnippetDialog = true
                                }
                            },
                            onMoveTo = {
                                movingSnippet = snippet
                                showMoveDialog = true
                            },
                            onDuplicate = {
                                viewModel.duplicateSnippet(libraryId, snippet)
                                scope.safeLaunch(tag = "LibraryDetail") {
                                    snackbarHostState.showSnackbar(I18nRuntime.t("Snippet duplicated"))
                                }
                            },
                            onDelete = {
                                viewModel.deleteSnippet(libraryId, snippet)
                            },
                            onDragStart = {
                                draggedIndex = index
                                dragOffsetY = 0f
                            },
                            onDrag = { deltaY ->
                                dragOffsetY += deltaY
                                val target = findSwapTarget()
                                if (target >= 0 && target != draggedIndex) {
                                    // Calculate offset adjustment to keep finger aligned with item
                                    val targetKey = localDragList[target].id
                                    val targetInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == targetKey }
                                    if (targetInfo != null) {
                                        val direction = if (target > draggedIndex) -1 else 1
                                        dragOffsetY += direction * targetInfo.size
                                    }

                                    // Swap in local list
                                    val item = localDragList.removeAt(draggedIndex)
                                    localDragList.add(target, item)
                                    draggedIndex = target

                                    // Haptic feedback
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragEnd = { handleSnippetDragEnd() },
                            onDragCancel = { draggedIndex = -1; dragOffsetY = 0f }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(FabClearance)) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Snippet list item with AppRowSwipe + multi-select + disabled state
// ---------------------------------------------------------------------------

@Composable
private fun LibrarySnippetListItem(
    snippet: CommandSnippet,
    isMultiSelectActive: Boolean,
    isSelected: Boolean,
    isDragged: Boolean,
    onTap: () -> Unit,
    onMoveTo: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val revealActions = remember(snippet, tokens) {
        listOf(
            AppRowAction(
                icon = Icons.AutoMirrored.Filled.DriveFileMove,
                color = tokens.status.connected,
                onClick = onMoveTo,
                label = "Move"
            ),
            AppRowAction(
                icon = Icons.Filled.ContentCopy,
                color = tokens.status.info,
                onClick = onDuplicate,
                label = "Copy"
            ),
            AppRowAction(
                icon = Icons.Filled.Delete,
                color = tokens.colors.error,
                onClick = onDelete,
                label = "Delete"
            )
        )
    }

    val contentAlpha = if (snippet.isEnabled) 1f else 0.45f

    if (isMultiSelectActive) {
        // Multi-select mode: no swipe, show checkbox (no drag handle)
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onTap,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(tokens.space.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppCheckbox(
                    checked = isSelected,
                    onCheckedChange = { onTap() }
                )
                Spacer(modifier = Modifier.width(tokens.space.sm))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(contentAlpha)
                ) {
                    Text(
                        text = snippet.name,
                        style = tokens.type.titleSmall,
                        color = tokens.colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(tokens.space.xxs))
                    Text(
                        text = snippet.command,
                        style = tokens.type.monoSmall,
                        color = tokens.colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    } else {
        // Normal mode: AppRowSwipe with actions + drag handle on right
        AppRowSwipe(
            modifier = Modifier.fillMaxWidth(),
            actions = revealActions
        ) {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onTap,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = tokens.space.md,
                    top = tokens.space.md,
                    end = 0.dp,
                    bottom = tokens.space.md
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .alpha(contentAlpha)
                    ) {
                        Text(
                            text = snippet.name,
                            style = tokens.type.titleSmall,
                            color = tokens.colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(tokens.space.xxs))
                        Text(
                            text = snippet.command,
                            style = tokens.type.monoSmall,
                            color = tokens.colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Drag handle - drag to reorder
                    Box(
                        modifier = Modifier
                            .size(DragHandleTouchSize)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { onDragStart() },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onDrag(dragAmount.y)
                                    },
                                    onDragEnd = { onDragEnd() },
                                    onDragCancel = { onDragCancel() }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.DragIndicator,
                            contentDescription = t("Drag to reorder"),
                            modifier = Modifier.size(DragHandleIconSize),
                            tint = tokens.colors.outline
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Add / Edit snippet dialog (name + command only)
// ---------------------------------------------------------------------------

@Composable
private fun SnippetDialog(
    snippet: CommandSnippet?,
    onDismiss: () -> Unit,
    onSave: (name: String, command: String) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val isEdit = snippet != null
    var name by remember(snippet) { mutableStateOf(snippet?.name ?: "") }
    var command by remember(snippet) { mutableStateOf(snippet?.command ?: "") }
    var nameError by remember { mutableStateOf(false) }
    var commandError by remember { mutableStateOf(false) }

    AppDialog(
        title = if (isEdit) "Edit Snippet" else "New Snippet",
        onDismiss = onDismiss,
        confirmLabel = if (isEdit) "Save" else "Add",
        onConfirm = {
            var valid = true
            if (name.isBlank()) { nameError = true; valid = false }
            if (command.isBlank()) { commandError = true; valid = false }
            if (valid) {
                onSave(name.trim(), command.trim())
            }
        },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.space.md)) {
                AppTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = "Name",
                    placeholder = "Restart nginx",
                    isError = nameError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                AppTextField(
                    value = command,
                    onValueChange = { command = it; commandError = false },
                    label = "Command",
                    placeholder = "sudo systemctl restart nginx",
                    isError = commandError,
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Library picker dialog (for "Move to...")
// ---------------------------------------------------------------------------

@Composable
private fun LibraryPickerDialog(
    libraries: List<SnippetLibrary>,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    val tokens = MaterialTheme.appTokens
    AppDialog(
        title = "Move to Library",
        onDismiss = onDismiss,
        confirmLabel = "Cancel",
        onConfirm = onDismiss,
        confirmStyle = com.tmuxes.ui.components.app.AppButtonStyle.Text,
        dismissLabel = null,
        contentScrollable = false,
        content = {
            if (libraries.isEmpty()) {
                Text(
                    text = t("No other libraries available"),
                    style = tokens.type.bodyMedium,
                    color = tokens.colors.onSurfaceVariant
                )
            } else {
                AppLazyColumn(verticalArrangement = Arrangement.spacedBy(tokens.space.xs)) {
                    items(libraries, key = { it.id }) { lib ->
                        AppCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelect(lib.id) },
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
                                    libraryIcon(lib.iconName),
                                    contentDescription = null,
                                    modifier = Modifier.size(PickerLibraryIconSize),
                                    tint = tokens.colors.primary
                                )
                                Spacer(modifier = Modifier.width(tokens.space.md))
                                Column {
                                    Text(
                                        text = lib.name,
                                        style = tokens.type.titleSmall,
                                        color = tokens.colors.onSurface
                                    )
                                    if (lib.description.isNotBlank()) {
                                        Text(
                                            text = lib.description,
                                            style = tokens.type.bodySmall,
                                            color = tokens.colors.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
