package com.pdh.cardvault.ui.card

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import com.pdh.cardvault.wallet.WalletDisplayOrder
import com.pdh.cardvault.wallet.FolderDropIntent
import com.pdh.cardvault.wallet.FolderDropHover
import kotlinx.coroutines.delay
import java.util.UUID

internal data class WalletStackEntry(val id: UUID, val isFolder: Boolean) {
    val key: String get() = if (isFolder) WalletDisplayOrder.folder(id.toString()) else WalletDisplayOrder.record(id.toString())
}

internal fun orderedWalletEntries(order: List<String>, entries: List<WalletStackEntry>): List<WalletStackEntry> {
    val byKey = entries.associateBy(WalletStackEntry::key)
    return WalletDisplayOrder.normalize(order, entries.map(WalletStackEntry::key)).mapNotNull(byKey::get)
}

/** One shared stack: folders and records have identical geometry and can be interleaved. */
@Composable
internal fun MixedWalletStack(
    entries: List<WalletStackEntry>,
    enabled: Boolean,
    recordOpenLabel: String,
    onOpen: (WalletStackEntry) -> Unit,
    onReorder: (List<String>) -> Unit,
    folderName: (UUID) -> String,
    canMoveIntoFolder: (WalletStackEntry, UUID) -> Boolean,
    onMoveIntoFolder: (WalletStackEntry, UUID) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (WalletStackEntry) -> Unit,
) {
    if (entries.isEmpty()) return
    var draft by remember { mutableStateOf(entries) }
    var startOrder by remember { mutableStateOf(entries) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var delta by remember { mutableStateOf(Offset.Zero) }
    var from by remember { mutableIntStateOf(0) }
    var target by remember { mutableIntStateOf(0) }
    var pointer by remember { mutableStateOf(Offset.Unspecified) }
    var hover by remember { mutableStateOf(FolderDropHover()) }
    val folderBounds = remember { mutableStateMapOf<UUID, Rect>() }
    val latestEntries by rememberUpdatedState(entries)
    val latestDraft by rememberUpdatedState(draft)
    val latestReorder by rememberUpdatedState(onReorder)
    val latestMove by rememberUpdatedState(onMoveIntoFolder)
    val latestCanMove by rememberUpdatedState(canMoveIntoFolder)
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val intent = remember(density.density) { FolderDropIntent(slop = with(density) { 12.dp.toPx() }) }
    fun folderUnderPointer(): UUID? {
        if (!enabled || !pointer.isSpecified) return null
        val source = startOrder.firstOrNull { it.key == dragging } ?: return null
        return folderBounds.entries.firstOrNull { (id, bounds) ->
            bounds.contains(pointer) && latestEntries.any { it.isFolder && it.id == id } && latestCanMove(source, id)
        }?.key
    }
    fun refreshHover() {
        val next = intent.update(folderUnderPointer()?.toString(), pointer.x, pointer.y, android.os.SystemClock.uptimeMillis())
        if (next.armed && !hover.armed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        hover = next
    }
    LaunchedEffect(dragging, enabled, intent) {
        if (dragging != null && enabled) {
            while (true) { refreshHover(); delay(32) }
        } else { intent.cancel(); hover = intent.hover }
    }
    LaunchedEffect(entries, dragging) { if (dragging == null) draft = entries }
    BoxWithConstraints(modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        val peek = 76.dp
        val step = with(density) { peek.toPx() }
        val height = maxWidth / 1.586f
        Box(Modifier.fillMaxWidth().height(height + peek * (draft.size - 1))) {
            draft.forEachIndexed { index, entry ->
                key(entry.key) {
                    var origin by remember { mutableStateOf(Offset.Zero) }
                    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                    val isDragging = dragging == entry.key
                    val y by animateDpAsState(peek * index, spring(dampingRatio = 0.86f, stiffness = 460f), label = "mixed-entry-offset")
                    Box(
                        Modifier.fillMaxWidth()
                            .zIndex(if (isDragging) draft.size + 1f else index.toFloat())
                            .graphicsLayer {
                                translationX = if (isDragging) delta.x else 0f
                                translationY = if (isDragging) from * step + delta.y else y.toPx()
                                scaleX = if (isDragging) 1.018f else 1f
                                scaleY = scaleX
                                shadowElevation = if (isDragging) 24.dp.toPx() else 6.dp.toPx()
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
                            .pointerInput(entry.key, enabled, density.density) {
                                if (!enabled) return@pointerInput
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { local ->
                                        intent.cancel(); hover = intent.hover
                                        startOrder = latestDraft
                                        from = latestDraft.indexOfFirst { it.key == entry.key }
                                        target = from
                                        delta = Offset.Zero
                                        dragging = entry.key
                                        pointer = layoutCoordinates?.takeIf { it.isAttached }?.localToRoot(local) ?: (origin + local)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        val rootPointer = layoutCoordinates?.takeIf { it.isAttached }?.localToRoot(change.position) ?: (pointer + amount)
                                        delta += rootPointer - pointer
                                        pointer = rootPointer
                                        target = dragTargetIndex(from, delta.y, step, startOrder.size)
                                        refreshHover()
                                    },
                                    onDragEnd = {
                                        val destination = intent.release(folderUnderPointer()?.toString())
                                        val available = latestEntries.map(WalletStackEntry::key).toSet()
                                        if (entry.key in available && destination != null) latestMove(entry, UUID.fromString(destination))
                                        else if (available == startOrder.map(WalletStackEntry::key).toSet()) {
                                            draft = moveItem(startOrder, from, target)
                                            if (target != from) latestReorder(draft.map(WalletStackEntry::key))
                                        } else draft = latestEntries
                                        hover = intent.hover
                                        dragging = null
                                        delta = Offset.Zero
                                    },
                                    onDragCancel = { intent.cancel(); hover = intent.hover; draft = latestEntries; dragging = null; delta = Offset.Zero },
                                )
                            }
                            .clickable(enabled = enabled && dragging == null) { onOpen(entry) }
                            .semantics { contentDescription = if (entry.isFolder) "文件夹，点击打开" else recordOpenLabel },
                    ) { content(entry) }
                    if (entry.isFolder && hover.folderId == entry.id.toString()) {
                        val highlight = if (hover.armed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                        val foreground = if (hover.armed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        Row(Modifier.fillMaxWidth().zIndex(draft.size + 3f)
                            .graphicsLayer { translationY = y.toPx() }.padding(horizontal = 16.dp, vertical = 10.dp)
                            .height(56.dp).background(highlight, RoundedCornerShape(16.dp))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
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
}
