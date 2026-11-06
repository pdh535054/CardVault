package com.pdh.cardvault.desktop.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.DialogProperties
import com.pdh.cardvault.desktop.model.DesktopFolder
import com.pdh.cardvault.wallet.WalletDisplayOrder
import com.pdh.cardvault.wallet.FolderAppearance
import com.pdh.cardvault.wallet.FolderDropIntent
import com.pdh.cardvault.wallet.FolderDropHover
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal data class DesktopWalletEntry(val id: String, val isFolder: Boolean) {
    val key: String get() = if (isFolder) WalletDisplayOrder.folder(id) else WalletDisplayOrder.record(id)
}

internal fun orderedDesktopWalletEntries(order: List<String>, entries: List<DesktopWalletEntry>): List<DesktopWalletEntry> {
    val byKey = entries.associateBy(DesktopWalletEntry::key)
    return WalletDisplayOrder.normalize(order, entries.map(DesktopWalletEntry::key)).mapNotNull(byKey::get)
}

internal fun canDropDesktopWalletEntry(entry: DesktopWalletEntry, destination: String, folders: List<DesktopFolder>): Boolean {
    val target = folders.firstOrNull { it.id == destination } ?: return false
    if (!entry.isFolder) return true
    val source = folders.firstOrNull { it.id == entry.id } ?: return false
    return source.kind == target.kind && !wouldCreateDesktopFolderCycle(folders, entry.id, destination)
}

@Composable
internal fun DesktopMixedWalletStack(
    entries: List<DesktopWalletEntry>,
    onOpen: (DesktopWalletEntry) -> Unit,
    onReorder: (List<String>) -> Unit,
    folderName: (String) -> String,
    canMoveIntoFolder: (DesktopWalletEntry, String) -> Boolean,
    onMoveIntoFolder: (DesktopWalletEntry, String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (DesktopWalletEntry) -> Unit,
) {
    var draft by remember { mutableStateOf(entries) }
    var start by remember { mutableStateOf(entries) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var delta by remember { mutableStateOf(Offset.Zero) }
    var from by remember { mutableIntStateOf(0) }
    var target by remember { mutableIntStateOf(0) }
    var pointer by remember { mutableStateOf(Offset.Unspecified) }
    var hover by remember { mutableStateOf(FolderDropHover()) }
    val folderBounds = remember { mutableStateMapOf<String, Rect>() }
    val latest by rememberUpdatedState(draft)
    val latestEntries by rememberUpdatedState(entries)
    val latestReorder by rememberUpdatedState(onReorder)
    val latestMove by rememberUpdatedState(onMoveIntoFolder)
    val latestCanMove by rememberUpdatedState(canMoveIntoFolder)
    val density = LocalDensity.current
    val step = with(density) { DESKTOP_STACK_STEP_DP.dp.toPx() }
    val intent = remember(density.density) { FolderDropIntent(slop = with(density) { 12.dp.toPx() }) }
    val clockOrigin = remember { System.nanoTime() }
    fun folderUnderPointer(): String? {
        if (!pointer.isSpecified) return null
        val source = start.firstOrNull { it.key == dragging } ?: return null
        return folderBounds.entries.firstOrNull { (id, bounds) ->
            bounds.contains(pointer) && latestEntries.any { it.isFolder && it.id == id } && latestCanMove(source, id)
        }?.key
    }
    fun refreshHover() {
        hover = intent.update(folderUnderPointer(), pointer.x, pointer.y, (System.nanoTime() - clockOrigin) / 1_000_000L)
    }
    LaunchedEffect(dragging, intent) {
        if (dragging != null) while (true) { refreshHover(); delay(32) }
        else { intent.cancel(); hover = intent.hover }
    }
    LaunchedEffect(entries, dragging) { if (dragging == null) draft = entries }
    DesktopStackViewport(draft.size, modifier) {
        draft.forEachIndexed { index, entry ->
            key(entry.key) {
                var origin by remember { mutableStateOf(Offset.Zero) }
                var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                val isDragging = dragging == entry.key
                val y by animateDpAsState((index * DESKTOP_STACK_STEP_DP).dp,
                    spring(dampingRatio = .86f, stiffness = 460f), label = "mixed-wallet-offset")
                Box(Modifier.fillMaxWidth().zIndex(if (isDragging) draft.size + 1f else index.toFloat())
                    .graphicsLayer {
                        translationX = if (isDragging) delta.x else 0f
                        translationY = if (isDragging) from * step + delta.y else y.toPx()
                        scaleX = if (isDragging) 1.018f else 1f
                        scaleY = scaleX
                        alpha = if (isDragging && hover.folderId != null) .72f else 1f
                    }
                    .onGloballyPositioned { coordinates ->
                        layoutCoordinates = coordinates
                        if (!isDragging) origin = coordinates.positionInRoot()
                        if (entry.isFolder) {
                            val visible = coordinates.boundsInRoot()
                            val topLeft = coordinates.positionInRoot()
                            val insetX = with(density) { 16.dp.toPx() }
                            val insetY = with(density) { 10.dp.toPx() }
                            val zone = Rect(maxOf(visible.left, topLeft.x + insetX), maxOf(visible.top, topLeft.y + insetY),
                                minOf(visible.right, topLeft.x + coordinates.size.width - insetX),
                                minOf(visible.bottom, topLeft.y + step - insetY))
                            if (zone.width > 0 && zone.height >= with(density) { 24.dp.toPx() }) folderBounds[entry.id] = zone
                            else folderBounds.remove(entry.id)
                        }
                    }
                    .clickable(enabled = dragging == null) { onOpen(entry) }
                    .pointerInput(entry.key, density.density) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { local -> intent.cancel(); hover = intent.hover; start = latest; from = latest.indexOfFirst { it.key == entry.key }; target = from; dragging = entry.key; delta = Offset.Zero; pointer = layoutCoordinates?.takeIf { it.isAttached }?.localToRoot(local) ?: (origin + local) },
                            onDrag = { change, amount ->
                                change.consume()
                                val rootPointer = layoutCoordinates?.takeIf { it.isAttached }?.localToRoot(change.position) ?: (pointer + amount)
                                delta += rootPointer - pointer
                                pointer = rootPointer
                                target = (from + delta.y / step).roundToInt().coerceIn(start.indices)
                                refreshHover()
                            },
                            onDragEnd = {
                                val destination = intent.release(folderUnderPointer())
                                val available = latestEntries.map(DesktopWalletEntry::key).toSet()
                                if (entry.key in available && destination != null) latestMove(entry, destination)
                                else if (available == start.map(DesktopWalletEntry::key).toSet()) {
                                    draft = moveDesktopFolderItem(start, from, target)
                                    if (target != from) latestReorder(draft.map(DesktopWalletEntry::key))
                                } else draft = latestEntries
                                hover = intent.hover
                                dragging = null; delta = Offset.Zero
                            },
                            onDragCancel = { intent.cancel(); hover = intent.hover; draft = latestEntries; dragging = null; delta = Offset.Zero },
                        )
                    }) { content(entry) }
                if (entry.isFolder && hover.folderId == entry.id) {
                    val highlight = if (hover.armed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                    val foreground = if (hover.armed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    Row(Modifier.fillMaxWidth().zIndex(draft.size + 3f).graphicsLayer { translationY = y.toPx() }
                        .padding(horizontal = 16.dp, vertical = 10.dp).height((DESKTOP_STACK_STEP_DP - 20).dp)
                        .background(highlight, RoundedCornerShape(16.dp)).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(folderName(entry.id), Modifier.weight(1f), color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (hover.armed) "松手移入" else "停留移入", color = foreground, style = MaterialTheme.typography.labelLarge)
                            Box(Modifier.width(64.dp).height(3.dp).background(foreground.copy(alpha = .2f))) {
                                Box(Modifier.fillMaxWidth(hover.progress).height(3.dp).background(foreground))
                            }
                        }
                    }
                }
            }
        }
        if (dragging != null && !hover.armed) Box(Modifier.fillMaxWidth().height(3.dp)
            .zIndex(draft.size + 2f).graphicsLayer { translationY = target * step }
            .background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
internal fun DesktopNewFolderButton(onCreate: (String) -> Unit) {
    var create by remember { mutableStateOf(false) }
    TextButton(onClick = { create = true }) { Text("新建文件夹") }
    if (create) DesktopFolderNameDialog("新建文件夹", "", { create = false }) { create = false; onCreate(it) }
}

@Composable
internal fun DesktopFolderWalletCard(
    folder: DesktopFolder, folders: List<DesktopFolder>, count: Int,
    onRename: (String, String) -> Unit, onMove: (String, String?) -> Unit, onDelete: (String) -> Unit,
    onSetColor: (String, Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var move by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var editColor by remember { mutableStateOf(false) }
    val base = Color(0xFF000000L or folder.colorRgb.toLong())
    val light = base.luminance() > .45f
    val foreground = if (light) Color(0xFF111827) else Color.White
    Surface(Modifier.fillMaxWidth().aspectRatio(STANDARD_CARD_ASPECT_RATIO), shape = RoundedCornerShape(28.dp), color = base) {
        Column(Modifier.background(Brush.linearGradient(listOf(base, lerp(base, Color.Black, if (light) .14f else .45f))))
            .padding(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("▰", color = foreground.copy(alpha = .85f), fontSize = 26.sp)
                Spacer(Modifier.width(14.dp))
                Text(folder.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = foreground, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
                Box {
                    IconButton(onClick = { menu = true }) { Text("•••", color = foreground) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; rename = true })
                        DropdownMenuItem(text = { Text("设置颜色") }, onClick = { menu = false; editColor = true })
                        DropdownMenuItem(text = { Text("移动到…") }, onClick = { menu = false; move = true })
                        DropdownMenuItem(text = { Text("删除文件夹") }, onClick = { menu = false; delete = true })
                    }
                }
            }
            Text("文件夹 · $count 张卡片", color = foreground.copy(alpha = .75f), fontSize = 14.sp)
        }
    }
    if (editColor) DesktopFolderColorDialog(folder, { editColor = false }) { editColor = false; onSetColor(folder.id, it) }
    if (rename) DesktopFolderNameDialog("重命名文件夹", folder.name, { rename = false }) { rename = false; onRename(folder.id, it) }
    if (move) AlertDialog(onDismissRequest = { move = false }, title = { Text("移动文件夹") },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            TextButton(onClick = { move = false; onMove(folder.id, null) }) { Text("顶层") }
            folders.filterNot { wouldCreateDesktopFolderCycle(folders, folder.id, it.id) }.forEach { destination ->
                TextButton(onClick = { move = false; onMove(folder.id, destination.id) }) { Text(desktopFolderPath(folders, destination.id)) }
            }
        } }, confirmButton = { TextButton(onClick = { move = false }) { Text("取消") } })
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("删除文件夹？") },
        text = { Text("卡片和子文件夹会移至上一级，不会删除内容。") },
        confirmButton = { TextButton(onClick = { delete = false; onDelete(folder.id) }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("取消") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesktopFolderColorDialog(folder: DesktopFolder, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    var selected by remember(folder.colorRgb) { mutableIntStateOf(folder.colorRgb) }
    var custom by remember { mutableStateOf(false) }
    val color = Color(0xFF000000L or selected.toLong())
    if (custom) BasicAlertDialog(onDismissRequest = { custom = false },
        modifier = Modifier.width(760.dp).height(640.dp), properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ColorPickerOverlay(color, { custom = false }) { onSelect(it.toArgb() and 0xFFFFFF) }
    } else AlertDialog(onDismissRequest = onDismiss, title = { Text("文件夹颜色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(Modifier.fillMaxWidth().height(86.dp), shape = RoundedCornerShape(20.dp), color = color) {
                    Box(Modifier.padding(18.dp), contentAlignment = Alignment.CenterStart) {
                        Text("▰  ${folder.name}", color = if (color.luminance() > .45f) Color(0xFF111827) else Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    }
                }
                FolderAppearance.presetColors.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { rgb ->
                            Box(Modifier.size(34.dp).background(Color(0xFF000000L or rgb.toLong()), CircleShape)
                                .border(if (rgb == selected) 3.dp else 1.dp,
                                    if (rgb == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { selected = rgb })
                        }
                    }
                }
                TextButton(onClick = { custom = true }) { Text("自定义颜色…") }
            }
        }, confirmButton = { TextButton(onClick = { onSelect(selected) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
