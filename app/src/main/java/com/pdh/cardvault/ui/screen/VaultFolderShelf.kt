package com.pdh.cardvault.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pdh.cardvault.presentation.VaultFolderUiModel
import java.util.UUID

@Composable
internal fun VaultFolderShelf(
    folders: List<VaultFolderUiModel>,
    unfiledCount: Int,
    folderOrder: List<UUID?>,
    selectedFolderId: UUID?,
    onSelect: (UUID?) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (UUID, String) -> Unit,
    onMove: (UUID, UUID?) -> Unit,
    onDelete: (UUID) -> Unit,
    onReorder: (List<UUID?>) -> Unit,
    onTargetBoundsChanged: (UUID?, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    var createDialog by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<VaultFolderUiModel?>(null) }
    var movingFolder by remember { mutableStateOf<VaultFolderUiModel?>(null) }
    var deletingFolder by remember { mutableStateOf<VaultFolderUiModel?>(null) }
    val sourceItems = remember(folders, unfiledCount, folderOrder, selectedFolderId) {
        buildFolderShelfItems(folders, unfiledCount, folderOrder, selectedFolderId)
    }
    var draftItems by remember { mutableStateOf(sourceItems) }
    var dragStartItems by remember { mutableStateOf(sourceItems) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragDistancePx by remember { mutableFloatStateOf(0f) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragTargetIndex by remember { mutableIntStateOf(-1) }
    val latestSourceItems by rememberUpdatedState(sourceItems)
    val latestDraftItems by rememberUpdatedState(draftItems)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    LaunchedEffect(sourceItems, draggingKey) {
        if (draggingKey == null) draftItems = sourceItems
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (selectedFolderId != null) {
            val current = folders.firstOrNull { it.id == selectedFolderId }
            TextButton(
                onClick = { onSelect(current?.parentFolderId) },
                modifier = Modifier.onGloballyPositioned {
                    onTargetBoundsChanged(current?.parentFolderId, it.boundsInRoot())
                },
            ) {
                Text("‹ 返回上级")
            }
            Text(folderPath(folders, selectedFolderId), style = MaterialTheme.typography.labelLarge)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (selectedFolderId == null) "文件夹" else "子文件夹", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = { createDialog = true }) { Text("新建文件夹") }
        }
        val stepPx = with(density) { FOLDER_TILE_STEP.toPx() }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            draftItems.forEachIndexed { index, item ->
                key(item.key) {
                    val isDragging = draggingKey == item.key
                    val dragCompensation = if (isDragging) {
                        dragDistancePx - (dragTargetIndex - dragStartIndex) * stepPx
                    } else 0f
                    FolderTile(
                        title = item.title,
                        count = item.count,
                        selected = selectedFolderId == item.id,
                        onClick = { onSelect(item.id) },
                        onBoundsChanged = { onTargetBoundsChanged(item.id, it) },
                        onEdit = item.folder?.let { folder -> { editingFolder = folder } },
                        onMove = item.folder?.let { folder -> { movingFolder = folder } },
                        onDelete = item.folder?.let { folder -> { deletingFolder = folder } },
                        modifier = Modifier
                            .zIndex(if (isDragging) draftItems.size + 1f else index.toFloat())
                            .graphicsLayer {
                                translationX = dragCompensation
                                scaleX = if (isDragging) 1.04f else 1f
                                scaleY = if (isDragging) 1.04f else 1f
                                shadowElevation = if (isDragging) 18.dp.toPx() else 0f
                            }
                            .pointerInput(item.key) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        val start = latestDraftItems.indexOfFirst { it.key == item.key }
                                        if (start >= 0) {
                                            dragStartItems = latestDraftItems
                                            dragStartIndex = start
                                            dragTargetIndex = start
                                            dragDistancePx = 0f
                                            draggingKey = item.key
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    },
                                    onDrag = { change, amount ->
                                        if (draggingKey == item.key) {
                                            change.consume()
                                            dragDistancePx += amount.x
                                            val target = folderDragTargetIndex(
                                                dragStartIndex,
                                                dragDistancePx,
                                                stepPx,
                                                dragStartItems.size,
                                            )
                                            if (target != dragTargetIndex) {
                                                dragTargetIndex = target
                                                draftItems = moveFolderShelfItem(
                                                    dragStartItems,
                                                    dragStartIndex,
                                                    target,
                                                )
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (draggingKey == item.key) onReorder(
                                            mergeFolderShelfOrder(folderOrder, draftItems.map { it.id }),
                                        )
                                        draggingKey = null
                                        dragDistancePx = 0f
                                    },
                                    onDragCancel = {
                                        draftItems = latestSourceItems
                                        draggingKey = null
                                        dragDistancePx = 0f
                                    },
                                )
                            },
                    )
                }
            }
            Spacer(Modifier.width(2.dp))
        }
        Text(
            "拖动排序；在文件夹上停稳，显示「松手移入」后松手归档",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (createDialog) {
        FolderNameDialog(
            title = "新建文件夹",
            initialValue = "",
            onDismiss = { createDialog = false },
            onConfirm = {
                createDialog = false
                onCreate(it)
            },
        )
    }
    editingFolder?.let { folder ->
        FolderNameDialog(
            title = "重命名文件夹",
            initialValue = folder.name,
            onDismiss = { editingFolder = null },
            onConfirm = {
                editingFolder = null
                onRename(folder.id, it)
            },
        )
    }
    movingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { movingFolder = null },
            title = { Text("移动文件夹") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { movingFolder = null; onMove(folder.id, null) }) { Text("顶层") }
                    folders.filterNot { wouldCreateFolderCycle(folders, folder.id, it.id) }
                        .forEach { destination ->
                            TextButton(onClick = { movingFolder = null; onMove(folder.id, destination.id) }) {
                                Text(folderPath(folders, destination.id), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                }
            },
            confirmButton = { TextButton(onClick = { movingFolder = null }) { Text("取消") } },
        )
    }
    deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text("删除文件夹？") },
            text = { Text("卡片和子文件夹会移至上一级，不会删除内容。") },
            confirmButton = {
                TextButton(onClick = {
                    deletingFolder = null
                    onDelete(folder.id)
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deletingFolder = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun FolderTile(
    title: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
    onMove: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .width(150.dp)
            .height(94.dp)
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 34.dp, height = 24.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("▰", color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(5.dp))
                Text("$count 张", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                if (onEdit != null) Text("✎", modifier = Modifier.clickable(onClick = onEdit))
                if (onMove != null) Text(" ↗", modifier = Modifier.clickable(onClick = onMove))
                if (onDelete != null) Text(" ×", modifier = Modifier.clickable(onClick = onDelete))
            }
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private data class FolderShelfItem(
    val id: UUID?,
    val title: String,
    val count: Int,
    val folder: VaultFolderUiModel?,
) {
    val key: String = id?.toString() ?: UNFILED_KEY
}

private fun buildFolderShelfItems(
    folders: List<VaultFolderUiModel>,
    unfiledCount: Int,
    requestedOrder: List<UUID?>,
    selectedFolderId: UUID?,
): List<FolderShelfItem> {
    val byId = folders.associateBy(VaultFolderUiModel::id)
    return normalizeFolderOrder(requestedOrder, folders.map(VaultFolderUiModel::id)).mapNotNull { id ->
        if (id == null) {
            if (selectedFolderId == null) FolderShelfItem(null, "未归档", unfiledCount, null) else null
        } else byId[id]?.takeIf { it.parentFolderId == selectedFolderId }
            ?.let { FolderShelfItem(id, it.name, it.itemCount, it) }
    }
}

/** Reorder only siblings; all other folders retain their existing global order. */
internal fun mergeFolderShelfOrder(globalOrder: List<UUID?>, reorderedSiblings: List<UUID?>): List<UUID?> {
    if (reorderedSiblings.size != reorderedSiblings.distinct().size) return globalOrder
    val siblingIds = reorderedSiblings.toSet()
    if (!globalOrder.containsAll(reorderedSiblings)) return globalOrder
    val replacement = reorderedSiblings.iterator()
    return globalOrder.map { if (it in siblingIds) replacement.next() else it }
}

internal fun wouldCreateFolderCycle(folders: List<VaultFolderUiModel>, movedId: UUID, destinationId: UUID): Boolean {
    val byId = folders.associateBy(VaultFolderUiModel::id)
    val visited = mutableSetOf<UUID>()
    var next: UUID? = destinationId
    while (next != null) {
        if (next == movedId || !visited.add(next)) return true
        next = byId[next]?.parentFolderId
    }
    return false
}

internal fun folderPath(folders: List<VaultFolderUiModel>, id: UUID): String {
    val byId = folders.associateBy(VaultFolderUiModel::id)
    val names = mutableListOf<String>()
    val visited = mutableSetOf<UUID>()
    var next: UUID? = id
    while (next != null && visited.add(next)) {
        val folder = byId[next] ?: break
        names += folder.name
        next = folder.parentFolderId
    }
    return names.asReversed().joinToString(" / ")
}

internal fun normalizeFolderOrder(
    requestedOrder: List<UUID?>,
    folderIds: List<UUID>,
): List<UUID?> = buildList {
    val validIds = folderIds.toSet()
    val seen = mutableSetOf<UUID?>()
    requestedOrder.forEach { id ->
        if ((id == null || id in validIds) && seen.add(id)) add(id)
    }
    if (seen.add(null)) add(0, null)
    folderIds.forEach { id -> if (seen.add(id)) add(id) }
}

internal fun <T> moveFolderShelfItem(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return items
    return items.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}

internal fun folderDragTargetIndex(
    startIndex: Int,
    dragDistancePx: Float,
    itemStepPx: Float,
    itemCount: Int,
): Int {
    if (itemCount <= 0 || startIndex !in 0 until itemCount || itemStepPx <= 0f) return startIndex
    return (startIndex + (dragDistancePx / itemStepPx).toInt()).coerceIn(0, itemCount - 1)
}

private val FOLDER_TILE_STEP = 160.dp
private const val UNFILED_KEY = "__unfiled__"

@Composable
internal fun FolderNameDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialValue) { mutableStateOf(initialValue) }
    val normalized = name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(50) },
                singleLine = true,
                label = { Text("文件夹名称") },
            )
        },
        confirmButton = {
            TextButton(
                enabled = normalized.isNotEmpty(),
                onClick = { onConfirm(normalized) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
