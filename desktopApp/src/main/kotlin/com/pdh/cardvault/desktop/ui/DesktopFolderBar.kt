package com.pdh.cardvault.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.pdh.cardvault.desktop.model.DesktopFolder

@Composable
internal fun DesktopFolderBar(
    folders: List<DesktopFolder>,
    folderOrder: List<String?>,
    selectedId: String?,
    unfiledCount: Int,
    itemCount: (String) -> Int,
    onSelect: (String?) -> Unit,
    onCreate: (String, String?) -> Unit,
    onRename: (String, String) -> Unit,
    onMove: (String, String?) -> Unit,
    onDelete: (String) -> Unit,
    onReorder: (List<String?>) -> Unit,
    onBounds: (String?, Rect) -> Unit,
) {
    var create by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<DesktopFolder?>(null) }
    var moving by remember { mutableStateOf<DesktopFolder?>(null) }
    var delete by remember { mutableStateOf<DesktopFolder?>(null) }
    val sourceItems = remember(folders, folderOrder, unfiledCount, selectedId) {
        buildDesktopFolderItems(folders, folderOrder, unfiledCount, itemCount, selectedId)
    }
    var draftItems by remember { mutableStateOf(sourceItems) }
    var dragStartItems by remember { mutableStateOf(sourceItems) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragDistancePx by remember { mutableFloatStateOf(0f) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragTargetIndex by remember { mutableIntStateOf(-1) }
    val latestSourceItems by rememberUpdatedState(sourceItems)
    val latestDraftItems by rememberUpdatedState(draftItems)
    val density = LocalDensity.current

    LaunchedEffect(sourceItems, draggingKey) {
        if (draggingKey == null) draftItems = sourceItems
    }
    Column {
        if (selectedId != null) {
            val current = folders.firstOrNull { it.id == selectedId }
            Text("‹ 返回上级  ·  ${desktopFolderPath(folders, selectedId)}",
                color = Color(0xFFAFC6FF), fontSize = 14.sp,
                modifier = Modifier.onGloballyPositioned { onBounds(current?.parentFolderId, it.boundsInRoot()) }
                    .clickable { onSelect(current?.parentFolderId) }.padding(bottom = 8.dp))
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (selectedId == null) "文件夹" else "子文件夹", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            Text("新建", color = Color(0xFFAFC6FF), modifier = Modifier.clickable { create = true }, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        val stepPx = with(density) { DESKTOP_FOLDER_ITEM_STEP.toPx() }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            draftItems.forEachIndexed { index, item ->
                key(item.key) {
                    val isDragging = draggingKey == item.key
                    val dragCompensation = if (isDragging) {
                        dragDistancePx - (dragTargetIndex - dragStartIndex) * stepPx
                    } else 0f
                    DesktopFolderChip(
                        name = item.name,
                        count = item.count,
                        selected = selectedId == item.id,
                        onClick = { onSelect(item.id) },
                        onBounds = { onBounds(item.id, it) },
                        onEdit = item.folder?.let { folder -> { rename = folder } },
                        onMove = item.folder?.let { folder -> { moving = folder } },
                        onDelete = item.folder?.let { folder -> { delete = folder } },
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
                                        }
                                    },
                                    onDrag = { change, amount ->
                                        if (draggingKey == item.key) {
                                            change.consume()
                                            dragDistancePx += amount.x
                                            val target = desktopFolderDragTargetIndex(
                                                dragStartIndex,
                                                dragDistancePx,
                                                stepPx,
                                                dragStartItems.size,
                                            )
                                            if (target != dragTargetIndex) {
                                                dragTargetIndex = target
                                                draftItems = moveDesktopFolderItem(dragStartItems, dragStartIndex, target)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (draggingKey == item.key) onReorder(
                                            mergeDesktopFolderOrder(folderOrder, draftItems.map { it.id }),
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
        }
        Spacer(Modifier.height(6.dp))
        Text("拖动排序；在文件夹上停稳，显示「松手移入」后松手归档", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
    }
    if (create) DesktopFolderNameDialog("新建文件夹", "", { create = false }) { create = false; onCreate(it, selectedId) }
    rename?.let { folder ->
        DesktopFolderNameDialog("重命名文件夹", folder.name, { rename = null }) {
            rename = null
            onRename(folder.id, it)
        }
    }
    moving?.let { folder ->
        AlertDialog(
            onDismissRequest = { moving = null },
            title = { Text("移动文件夹") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { moving = null; onMove(folder.id, null) }) { Text("顶层") }
                    folders.filterNot { wouldCreateDesktopFolderCycle(folders, folder.id, it.id) }
                        .forEach { destination ->
                            TextButton(onClick = { moving = null; onMove(folder.id, destination.id) }) {
                                Text(desktopFolderPath(folders, destination.id), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                }
            },
            confirmButton = { TextButton(onClick = { moving = null }) { Text("取消") } },
        )
    }
    delete?.let { folder ->
        AlertDialog(
            onDismissRequest = { delete = null },
            title = { Text("删除文件夹？") },
            text = { Text("卡片和子文件夹会移至上一级，不会删除内容。") },
            confirmButton = { TextButton(onClick = { delete = null; onDelete(folder.id) }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun DesktopFolderChip(
    name: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
    onMove: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Column(
        modifier.width(132.dp).height(66.dp)
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .background(if (selected) Color(0xFF35466D) else Color(0xFF26272B), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row {
            Text("▰", color = Color(0xFFAFC6FF), fontSize = 13.sp)
            if (count > 0) Text("  $count", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            if (onEdit != null) Text("✎", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.clickable(onClick = onEdit))
            if (onMove != null) Text(" ↗", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.clickable(onClick = onMove))
            if (onDelete != null) Text(" ×", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.clickable(onClick = onDelete))
        }
        Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private data class DesktopFolderItem(
    val id: String?,
    val name: String,
    val count: Int,
    val folder: DesktopFolder?,
) {
    val key: String = id ?: DESKTOP_UNFILED_KEY
}

private fun buildDesktopFolderItems(
    folders: List<DesktopFolder>,
    requestedOrder: List<String?>,
    unfiledCount: Int,
    itemCount: (String) -> Int,
    selectedId: String?,
): List<DesktopFolderItem> {
    val byId = folders.associateBy(DesktopFolder::id)
    return normalizeDesktopFolderOrder(requestedOrder, folders.map(DesktopFolder::id)).mapNotNull { id ->
        if (id == null) {
            if (selectedId == null) DesktopFolderItem(null, "未归档", unfiledCount, null) else null
        } else byId[id]?.takeIf { it.parentFolderId == selectedId }
            ?.let { folder -> DesktopFolderItem(id, folder.name, itemCount(id), folder) }
    }
}

internal fun mergeDesktopFolderOrder(globalOrder: List<String?>, reorderedSiblings: List<String?>): List<String?> {
    if (reorderedSiblings.size != reorderedSiblings.distinct().size) return globalOrder
    val siblings = reorderedSiblings.toSet()
    if (!globalOrder.containsAll(reorderedSiblings)) return globalOrder
    val replacement = reorderedSiblings.iterator()
    return globalOrder.map { if (it in siblings) replacement.next() else it }
}

internal fun wouldCreateDesktopFolderCycle(folders: List<DesktopFolder>, movedId: String, destinationId: String): Boolean {
    val byId = folders.associateBy(DesktopFolder::id)
    val visited = mutableSetOf<String>()
    var next: String? = destinationId
    while (next != null) {
        if (next == movedId || !visited.add(next)) return true
        next = byId[next]?.parentFolderId
    }
    return false
}

internal fun countDesktopFolderTreeItems(folders: List<DesktopFolder>, folderId: String, itemFolderIds: List<String?>): Int {
    val children = folders.groupBy(DesktopFolder::parentFolderId)
    val seen = mutableSetOf<String>()
    val pending = ArrayDeque<String>().apply { add(folderId) }
    while (pending.isNotEmpty()) {
        val current = pending.removeFirst()
        if (seen.add(current)) children[current].orEmpty().forEach { pending.add(it.id) }
    }
    return itemFolderIds.count { it in seen }
}

internal fun desktopFolderPath(folders: List<DesktopFolder>, id: String): String {
    val byId = folders.associateBy(DesktopFolder::id)
    val names = mutableListOf<String>()
    val visited = mutableSetOf<String>()
    var next: String? = id
    while (next != null && visited.add(next)) {
        val folder = byId[next] ?: break
        names += folder.name
        next = folder.parentFolderId
    }
    return names.asReversed().joinToString(" / ")
}

internal fun normalizeDesktopFolderOrder(
    requestedOrder: List<String?>,
    folderIds: List<String>,
): List<String?> = buildList {
    val validIds = folderIds.toSet()
    val seen = mutableSetOf<String?>()
    requestedOrder.forEach { id ->
        if ((id == null || id in validIds) && seen.add(id)) add(id)
    }
    if (seen.add(null)) add(0, null)
    folderIds.forEach { id -> if (seen.add(id)) add(id) }
}

internal fun <T> moveDesktopFolderItem(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return items
    return items.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}

internal fun desktopFolderDragTargetIndex(
    startIndex: Int,
    dragDistancePx: Float,
    itemStepPx: Float,
    itemCount: Int,
): Int {
    if (itemCount <= 0 || startIndex !in 0 until itemCount || itemStepPx <= 0f) return startIndex
    return (startIndex + (dragDistancePx / itemStepPx).toInt()).coerceIn(0, itemCount - 1)
}

private val DESKTOP_FOLDER_ITEM_STEP = 140.dp
private const val DESKTOP_UNFILED_KEY = "__unfiled__"

@Composable
internal fun DesktopFolderNameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it.take(50) }, label = { Text("文件夹名称") }, singleLine = true) },
        confirmButton = { TextButton(enabled = value.trim().isNotEmpty(), onClick = { onSave(value.trim()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
