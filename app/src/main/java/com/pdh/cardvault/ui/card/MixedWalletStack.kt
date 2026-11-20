package com.pdh.cardvault.ui.card

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pdh.cardvault.ui.theme.CardVaultMotion
import com.pdh.cardvault.ui.theme.pressScale
import com.pdh.cardvault.wallet.FolderDropHover
import com.pdh.cardvault.wallet.FolderDropIntent
import com.pdh.cardvault.wallet.WalletDisplayOrder
import java.util.UUID

@Immutable
internal data class WalletStackEntry(val id: UUID, val isFolder: Boolean) {
    val key: String
        get() = if (isFolder) {
            WalletDisplayOrder.folder(id.toString())
        } else {
            WalletDisplayOrder.record(id.toString())
        }
}

internal fun orderedWalletEntries(
    order: List<String>,
    entries: List<WalletStackEntry>,
): List<WalletStackEntry> {
    val byKey = entries.associateBy(WalletStackEntry::key)
    return WalletDisplayOrder.normalize(order, entries.map(WalletStackEntry::key)).mapNotNull(byKey::get)
}

/**
 * High-frame-rate interleaved stack of folders and cards.
 *
 * Uses direct draw-phase `graphicsLayer` lambdas, zero-allocation `Modifier.Node` press physics,
 * and cached contact-lip gradients instead of multi-card RenderNode shadow passes.
 */
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
            bounds.contains(pointer) &&
                latestEntries.any { it.isFolder && it.id == id } &&
                latestCanMove(source, id)
        }?.key
    }

    fun refreshHover() {
        val next = intent.update(
            folderUnderPointer()?.toString(),
            pointer.x,
            pointer.y,
            SystemClock.uptimeMillis(),
        )
        if (next.armed && !hover.armed) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        hover = next
    }

    // Frame-synced progress ticker runs ONLY while dwelling over an unarmed folder drop target.
    val activeHoverFolderId = hover.folderId
    val isHoverArmed = hover.armed
    LaunchedEffect(dragging, enabled, activeHoverFolderId, isHoverArmed) {
        if (dragging == null || !enabled) {
            intent.cancel()
            hover = intent.hover
        } else if (activeHoverFolderId != null && !isHoverArmed) {
            while (!hover.armed && hover.folderId == activeHoverFolderId) {
                withFrameNanos { refreshHover() }
            }
        }
    }

    LaunchedEffect(entries, dragging) {
        if (dragging == null) draft = entries
    }

    BoxWithConstraints(modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        val peek = 76.dp
        val step = with(density) { peek.toPx() }
        val cardShape = MaterialTheme.shapes.extraLarge
        val height = maxWidth / 1.586f
        Box(Modifier.fillMaxWidth().height(height + peek * (draft.size - 1))) {
            draft.forEachIndexed { index, entry ->
                key(entry.key) {
                    var origin by remember { mutableStateOf(Offset.Zero) }
                    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                    val isDragging = dragging == entry.key
                    val displayIndex = if (dragging == null || isDragging) {
                        index
                    } else {
                        liveStackIndex(index, from, target)
                    }
                    val animatedSlotY by animateFloatAsState(
                        targetValue = displayIndex * step,
                        animationSpec = CardVaultMotion.snappySpring(),
                        label = "mixed-slot-y",
                    )
                    val dragScale by animateFloatAsState(
                        targetValue = if (isDragging) 1.03f else 1f,
                        animationSpec = CardVaultMotion.tactileSpring(),
                        label = "mixed-drag-scale",
                    )
                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) draft.size + 1f else displayIndex.toFloat())
                            .pressScale(interactionSource, pressedScale = 0.984f)
                            .graphicsLayer {
                                translationX = if (isDragging) delta.x else 0f
                                translationY = if (isDragging) from * step + delta.y else animatedSlotY
                                scaleX = dragScale
                                scaleY = dragScale
                                rotationZ = if (isDragging) -0.55f else 0f
                                shadowElevation = 0f
                                shape = cardShape
                                clip = false
                                alpha = if (isDragging && hover.folderId != null) 0.74f else 1f
                            }
                            .onGloballyPositioned { coordinates ->
                                layoutCoordinates = coordinates
                                if (!isDragging) origin = coordinates.positionInRoot()
                                if (entry.isFolder) {
                                    val visible = coordinates.boundsInRoot()
                                    val topLeft = coordinates.positionInRoot()
                                    val insetX = with(density) { 16.dp.toPx() }
                                    val insetY = with(density) { 10.dp.toPx() }
                                    val zone = Rect(
                                        maxOf(visible.left, topLeft.x + insetX),
                                        maxOf(visible.top, topLeft.y + insetY),
                                        minOf(visible.right, topLeft.x + coordinates.size.width - insetX),
                                        minOf(visible.bottom, topLeft.y + step - insetY),
                                    )
                                    if (zone.width > 0 && zone.height >= with(density) { 24.dp.toPx() }) {
                                        folderBounds[entry.id] = zone
                                    } else {
                                        folderBounds.remove(entry.id)
                                    }
                                }
                            }
                            .pointerInput(entry.key, enabled, density.density) {
                                if (!enabled) return@pointerInput
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { local ->
                                        intent.cancel()
                                        hover = intent.hover
                                        startOrder = latestDraft
                                        from = latestDraft.indexOfFirst { it.key == entry.key }
                                        target = from
                                        delta = Offset.Zero
                                        dragging = entry.key
                                        pointer = layoutCoordinates
                                            ?.takeIf { it.isAttached }
                                            ?.localToRoot(local)
                                            ?: (origin + local)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        val rootPointer = layoutCoordinates
                                            ?.takeIf { it.isAttached }
                                            ?.localToRoot(change.position)
                                            ?: (pointer + amount)
                                        delta += rootPointer - pointer
                                        pointer = rootPointer
                                        val nextTarget = dragTargetIndex(from, delta.y, step, startOrder.size)
                                        if (nextTarget != target) {
                                            target = nextTarget
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        refreshHover()
                                    },
                                    onDragEnd = {
                                        val destination = intent.release(folderUnderPointer()?.toString())
                                        val available = latestEntries.map(WalletStackEntry::key).toSet()
                                        if (entry.key in available && destination != null) {
                                            latestMove(entry, UUID.fromString(destination))
                                        } else if (available == startOrder.map(WalletStackEntry::key).toSet()) {
                                            draft = moveItem(startOrder, from, target)
                                            if (target != from) latestReorder(draft.map(WalletStackEntry::key))
                                        } else {
                                            draft = latestEntries
                                        }
                                        hover = intent.hover
                                        dragging = null
                                        delta = Offset.Zero
                                    },
                                    onDragCancel = {
                                        intent.cancel()
                                        hover = intent.hover
                                        draft = latestEntries
                                        dragging = null
                                        delta = Offset.Zero
                                    },
                                )
                            }
                            .clickable(
                                enabled = enabled && dragging == null,
                                interactionSource = interactionSource,
                                indication = null,
                            ) { onOpen(entry) }
                            .semantics {
                                contentDescription = if (entry.isFolder) "文件夹，点击打开" else recordOpenLabel
                            },
                    ) {
                        content(entry)
                    }

                    if (entry.isFolder && hover.folderId == entry.id.toString()) {
                        val highlight = if (hover.armed) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                        val foreground = if (hover.armed) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .zIndex(draft.size + 3f)
                                .graphicsLayer { translationY = animatedSlotY }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .height(56.dp)
                                .background(highlight, RoundedCornerShape(16.dp))
                                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                folderName(entry.id),
                                Modifier.weight(1f),
                                color = foreground,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (hover.armed) "松手移入" else "停留移入",
                                    color = foreground,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Box(Modifier.width(64.dp).height(3.dp).background(foreground.copy(alpha = 0.22f))) {
                                    Box(Modifier.fillMaxWidth(hover.progress).height(3.dp).background(foreground))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Where a non-dragged entry should sit while the entry at [from] hovers over slot [target]:
 * entries between the two slide one position to open a gap at the drop slot.
 */
internal fun liveStackIndex(index: Int, from: Int, target: Int): Int = when {
    from < target && index in (from + 1)..target -> index - 1
    from > target && index in target until from -> index + 1
    else -> index
}
