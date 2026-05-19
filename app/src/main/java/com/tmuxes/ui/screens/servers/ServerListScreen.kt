package com.tmuxes.ui.screens.servers

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tmuxes.data.model.ServerEntity
import com.tmuxes.i18n.I18nRuntime
import com.tmuxes.i18n.t
import com.tmuxes.ssh.ServerConnectionState
import com.tmuxes.ssh.ServerStatus
import com.tmuxes.ui.components.app.AppRowAction
import com.tmuxes.ui.components.app.AppRowSwipe
import com.tmuxes.ui.components.app.AppDeleteDialog
import com.tmuxes.ui.components.app.AppEmptyState
import com.tmuxes.ui.components.app.AppFab
import com.tmuxes.ui.components.app.AppScaffold
import com.tmuxes.ui.components.app.appPressable
import com.tmuxes.ui.components.app.StatusDot
import com.tmuxes.ui.design.IdentityColors
import com.tmuxes.ui.design.appTokens
import com.tmuxes.ui.viewmodel.ServerViewModel
import com.tmuxes.util.safeLaunch
import kotlin.math.roundToInt

private data class ServerTreeNode(
    val server: ServerEntity,
    val depth: Int,
    val childCount: Int
)

/** Sentinel value: drop target is the root zone (make server a root server). */
private const val DROP_TARGET_ROOT = -2

@Composable
fun ServerListScreen(
    onAddServer: () -> Unit,
    onServerClick: (Long) -> Unit,
    onEditServer: (Long) -> Unit,
    viewModel: ServerViewModel = viewModel()
) {
    val tokens = MaterialTheme.appTokens
    val servers by viewModel.servers.collectAsState()
    val lastDeleted by viewModel.lastDeletedServer.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val serverStates by viewModel.serverStates.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var serverToDelete by remember { mutableStateOf<ServerEntity?>(null) }

    val treeNodes = remember(servers) { buildTree(servers) }

    LaunchedEffect(servers) { viewModel.refreshServerStates() }

    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dropTargetIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(lastDeleted) {
        val deleted = lastDeleted ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = I18nRuntime.t("{name} deleted", "name" to deleted.displayName),
            actionLabel = I18nRuntime.t("Undo"),
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        else viewModel.clearLastDeleted()
    }

    LaunchedEffect(errorMessage) {
        val msg = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = I18nRuntime.t(msg), duration = SnackbarDuration.Short)
        viewModel.clearError()
    }

    fun resolveDropTarget(): Int {
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty() || draggedIndex < 0) return -1
        val serverItems = visibleItems.filter { it.key != "drop_root_zone" && it.size > 0 }
        if (serverItems.isEmpty()) return -1
        val avgItemHeight = serverItems.map { it.size }.average().toFloat()
        if (avgItemHeight <= 0f) return -1
        val indexOffset = (dragOffsetY / avgItemHeight).roundToInt()
        val targetIndex = draggedIndex + indexOffset
        if (targetIndex >= treeNodes.size) return DROP_TARGET_ROOT
        if (targetIndex < 0) return -1
        if (targetIndex == draggedIndex) return -1
        return targetIndex.coerceIn(0, treeNodes.size - 1)
    }

    fun handleDragEnd() {
        if (draggedIndex < 0 || draggedIndex >= treeNodes.size) {
            draggedIndex = -1; dragOffsetY = 0f; dropTargetIndex = -1
            return
        }
        val draggedNode = treeNodes[draggedIndex]
        when {
            dropTargetIndex == DROP_TARGET_ROOT -> {
                viewModel.updateParent(draggedNode.server.id, null)
            }
            dropTargetIndex in treeNodes.indices -> {
                val targetNode = treeNodes[dropTargetIndex]
                if (isDescendant(servers, draggedNode.server.id, targetNode.server.id)) {
                    scope.safeLaunch(tag = "ServerList") {
                        snackbarHostState.showSnackbar(
                            message = I18nRuntime.t("Cannot nest a server under its own child"),
                            duration = SnackbarDuration.Short
                        )
                    }
                } else {
                    viewModel.updateParent(draggedNode.server.id, targetNode.server.id)
                }
            }
        }
        draggedIndex = -1; dragOffsetY = 0f; dropTargetIndex = -1
    }

    AppScaffold(
        title = "Servers",
        titleIcon = Icons.Filled.Dns,
        titleMeta = servers.size.toString(),
        fab = { AppFab(icon = Icons.Filled.Add, onClick = onAddServer, contentDescription = "Add server") },
        snackbarHostState = snackbarHostState
    ) { padding ->
        if (servers.isEmpty()) {
            AppEmptyState(
                icon = Icons.Filled.Dns,
                title = "No servers yet",
                subtitle = "Tap + to add your first SSH server",
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        } else {
            AppLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = tokens.space.lg, end = tokens.space.lg,
                    top = tokens.space.sm, bottom = tokens.space.xxxl + tokens.space.xl
                ),
                verticalArrangement = Arrangement.spacedBy(tokens.space.xs)
            ) {
                itemsIndexed(items = treeNodes, key = { _, node -> node.server.id }) { index, node ->
                    val isDragged = draggedIndex == index
                    val isDropTarget = dropTargetIndex == index && draggedIndex != index
                    val elevation by animateDpAsState(
                        targetValue = if (isDragged) tokens.elevation.level4 else tokens.elevation.level0,
                        label = "drag_elev"
                    )
                    val serverStatus = serverStates[node.server.id] ?: ServerConnectionState(ServerStatus.IDLE)

                    Box(
                        modifier = Modifier.then(
                            if (isDragged) Modifier.zIndex(1f)
                                .offset { IntOffset(0, dragOffsetY.roundToInt()) }
                            else Modifier
                        )
                    ) {
                        val swipeActions = listOf(
                            AppRowAction(
                                icon = if (node.server.isEnabled) Icons.Filled.ToggleOff else Icons.Filled.ToggleOn,
                                color = if (node.server.isEnabled) tokens.status.warning else tokens.colors.success,
                                onClick = {
                                    viewModel.setServerEnabled(node.server, !node.server.isEnabled)
                                },
                                label = if (node.server.isEnabled) "Disable" else "Enable"
                            ),
                            AppRowAction(
                                icon = Icons.Filled.Edit,
                                color = tokens.colors.info,
                                onClick = { onEditServer(node.server.id) },
                                label = "Edit"
                            ),
                            AppRowAction(
                                icon = Icons.Filled.Delete,
                                color = tokens.colors.danger,
                                onClick = { serverToDelete = node.server },
                                label = "Delete"
                            )
                        )
                        ServerTreeCard(
                            node = node,
                            actions = swipeActions,
                            swipeEnabled = draggedIndex < 0,
                            closeSignal = if (draggedIndex >= 0) draggedIndex else null,
                            isDropTarget = isDropTarget,
                            elevation = elevation,
                            serverStatus = serverStatus,
                            onClick = { onServerClick(node.server.id) },
                            onDragStart = { draggedIndex = index; dragOffsetY = 0f },
                            onDrag = { deltaY ->
                                dragOffsetY += deltaY
                                dropTargetIndex = resolveDropTarget()
                            },
                            onDragEnd = { handleDragEnd() },
                            onDragCancel = {
                                draggedIndex = -1; dragOffsetY = 0f; dropTargetIndex = -1
                            }
                        )
                    }
                }

                if (draggedIndex >= 0) {
                    item(key = "drop_root_zone") {
                        val isRootTarget = dropTargetIndex == DROP_TARGET_ROOT
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dpUnit())
                                .clip(tokens.shape.md)
                                .background(
                                    if (isRootTarget) tokens.colors.primaryContainer
                                    else tokens.colors.surfaceContainer
                                )
                                .padding(horizontal = tokens.space.lg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = t("Drop here to make root server"),
                                style = tokens.type.bodyMedium,
                                color = if (isRootTarget) tokens.colors.onPrimaryContainer
                                else tokens.colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        serverToDelete?.let { server ->
            AppDeleteDialog(
                title = "Delete ${server.displayName}?",
                message = "This will permanently delete the server. Child servers will become root servers.",
                onConfirm = {
                    viewModel.deleteServer(server)
                    serverToDelete = null
                },
                onDismiss = { serverToDelete = null }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Server tree card
// ---------------------------------------------------------------------------

@Composable
private fun ServerTreeCard(
    node: ServerTreeNode,
    actions: List<AppRowAction>,
    swipeEnabled: Boolean,
    closeSignal: Any?,
    isDropTarget: Boolean,
    elevation: androidx.compose.ui.unit.Dp,
    serverStatus: ServerConnectionState,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val safeDepth = node.depth.coerceIn(0, 16)
    val indentDp = (safeDepth * 32).dpUnit()

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (safeDepth > 0) {
            Spacer(modifier = Modifier.width(indentDp - 24.dpUnit()))
            Icon(
                Icons.Filled.SubdirectoryArrowRight, contentDescription = null,
                modifier = Modifier.size(20.dpUnit()),
                tint = tokens.colors.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(tokens.space.xs))
        }

        AppRowSwipe(
            actions = actions,
            modifier = Modifier.weight(1f),
            shape = tokens.shape.lg,
            swipeEnabled = swipeEnabled,
            closeSignal = closeSignal
        ) {
            ServerTreeCardBody(
                node = node,
                isDropTarget = isDropTarget,
                elevation = elevation,
                serverStatus = serverStatus,
                onClick = onClick,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel
            )
        }
    }
}

@Composable
private fun ServerTreeCardBody(
    node: ServerTreeNode,
    isDropTarget: Boolean,
    elevation: androidx.compose.ui.unit.Dp,
    serverStatus: ServerConnectionState,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val tokens = MaterialTheme.appTokens
    val isDisabled = !node.server.isEnabled
    val effectiveStatus = if (isDisabled) ServerStatus.PAUSED else serverStatus.status
    val identityBackground = if (isDisabled) {
        tokens.colors.surfaceContainerLow
    } else {
        IdentityColors.containerColor(node.server.color, tokens.colors)
    }
    val identityOutline = if (isDisabled) {
        tokens.colors.outlineVariant
    } else {
        IdentityColors.outlineColor(node.server.color, tokens.colors)
    }
    val titleColor = if (isDisabled) tokens.colors.onSurfaceVariant else tokens.colors.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation, tokens.shape.lg)
            .clip(tokens.shape.lg)
            .background(
                if (isDropTarget) tokens.colors.primaryContainer
                else identityBackground
            )
            .border(
                width = 1.dpUnit(),
                color = if (isDropTarget) tokens.colors.primary else identityOutline,
                shape = tokens.shape.lg
            )
            .appPressable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = tokens.space.lg, top = tokens.space.md,
                         bottom = tokens.space.md, end = tokens.space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(color = tokens.status.forServerStatus(effectiveStatus), sizeDp = 10)
            Spacer(modifier = Modifier.width(tokens.space.md))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = node.server.displayName,
                        style = tokens.type.titleMedium,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isDisabled) {
                        Spacer(modifier = Modifier.width(tokens.space.sm))
                        DisabledServerPill()
                    }
                }
                Spacer(modifier = Modifier.height(tokens.space.xxs))
                Text(
                    text = "${node.server.username}@${node.server.hostname}:${node.server.port}",
                    style = tokens.type.monoSmall,
                    color = tokens.colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (node.childCount > 0) {
                    Spacer(modifier = Modifier.height(tokens.space.xxs))
                    Text(
                        text = "${node.childCount} child${if (node.childCount > 1) "ren" else ""}",
                        style = tokens.type.labelSmall,
                        color = if (isDisabled) tokens.colors.onSurfaceVariant else tokens.colors.primary
                    )
                }
            }

            Box(
                modifier = Modifier.size(48.dpUnit())
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDrag = { change, dragAmount ->
                                change.consume(); onDrag(dragAmount.y)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragCancel() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.DragIndicator,
                    contentDescription = t("Drag to reparent"),
                    tint = tokens.colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DisabledServerPill() {
    val tokens = MaterialTheme.appTokens
    Surface(
        shape = tokens.shape.pill,
        color = tokens.colors.surfaceContainerHighest,
        contentColor = tokens.colors.onSurfaceVariant
    ) {
        Text(
            text = t("Disabled"),
            style = tokens.type.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = tokens.space.sm, vertical = tokens.space.xs)
        )
    }
}

// ---------------------------------------------------------------------------
// Tree utilities (unchanged from prior implementation)
// ---------------------------------------------------------------------------

private fun buildTree(servers: List<ServerEntity>): List<ServerTreeNode> {
    val childrenMap = servers.groupBy { it.parentId }
    val result = mutableListOf<ServerTreeNode>()
    val visited = mutableSetOf<Long>()
    fun addNode(server: ServerEntity, depth: Int) {
        if (!visited.add(server.id)) return
        if (depth > 256) return
        val children = childrenMap[server.id] ?: emptyList()
        result.add(ServerTreeNode(server = server, depth = depth, childCount = children.size))
        for (child in children) addNode(child, depth + 1)
    }
    val roots = childrenMap[null] ?: emptyList()
    for (root in roots) addNode(root, 0)
    val allIds = servers.map { it.id }.toSet()
    val orphans = servers.filter { it.parentId != null && it.parentId !in allIds }
    for (orphan in orphans) addNode(orphan, 0)
    for (server in servers) if (server.id !in visited) addNode(server, 0)
    return result
}

private fun isDescendant(servers: List<ServerEntity>, ancestorId: Long, candidateChildId: Long): Boolean {
    val childrenMap = servers.groupBy { it.parentId }
    val visited = mutableSetOf<Long>()
    fun check(currentId: Long): Boolean {
        if (!visited.add(currentId)) return false
        if (currentId == candidateChildId) return true
        return (childrenMap[currentId] ?: return false).any { check(it.id) }
    }
    return check(ancestorId)
}

private fun Int.dpUnit() = androidx.compose.ui.unit.Dp(this.toFloat())
