package com.pdh.cardvault.ui.card

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pdh.cardvault.R
import com.pdh.cardvault.presentation.AddressListItemUiModel
import java.util.UUID

/** Address counterpart of [CardWalletStack], including the same long-press drag interaction. */
@Composable
fun AddressWalletStack(
    addresses: List<AddressListItemUiModel>,
    sortingInProgress: Boolean,
    openActionLabel: String,
    moveUpActionLabel: String,
    moveDownActionLabel: String,
    onAddressOpened: (UUID) -> Unit,
    onAddressesReordered: (List<UUID>) -> Unit,
    modifier: Modifier = Modifier,
    onAddressDropped: ((UUID, Offset) -> Boolean)? = null,
) {
    if (addresses.isEmpty()) return

    var draftAddresses by remember { mutableStateOf(addresses) }
    var dragStartOrder by remember { mutableStateOf(addresses) }
    var draggingAddressId by remember { mutableStateOf<UUID?>(null) }
    var dragDistancePx by remember { mutableFloatStateOf(0f) }
    var dragDistanceXPx by remember { mutableFloatStateOf(0f) }
    var dragPointerInRoot by remember { mutableStateOf(Offset.Unspecified) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragTargetIndex by remember { mutableIntStateOf(-1) }
    val latestAddresses by rememberUpdatedState(addresses)
    val latestDraftAddresses by rememberUpdatedState(draftAddresses)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    LaunchedEffect(addresses, draggingAddressId) {
        if (draggingAddressId == null) draftAddresses = addresses
    }

    BoxWithConstraints(
        modifier = modifier.widthIn(max = 560.dp).fillMaxWidth(),
    ) {
        val cardShape = MaterialTheme.shapes.extraLarge
        val cardHeight = maxWidth / ADDRESS_CARD_ASPECT_RATIO
        val stackHeight = walletStackHeight(
            cardCount = draftAddresses.size,
            cardHeight = cardHeight.value,
            peek = ADDRESS_CARD_PEEK.value,
        ).dp
        val peekPx = with(density) { ADDRESS_CARD_PEEK.toPx() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stackHeight)
                .semantics { isTraversalGroup = true },
        ) {
            draftAddresses.forEachIndexed { index, address ->
                key(address.id) {
                    val isDragging = draggingAddressId == address.id
                    var hasEntered by remember(address.id) { mutableStateOf(false) }
                    var cardOriginInRoot by remember(address.id) { mutableStateOf(Offset.Zero) }
                    LaunchedEffect(address.id) { hasEntered = true }
                    val targetOffset = walletCardOffset(index, ADDRESS_CARD_PEEK.value).dp
                    val animatedOffset by animateDpAsState(
                        targetValue = targetOffset,
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = 430f),
                        label = "address-card-offset",
                    )
                    val interactionSource = remember(address.id) { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = when {
                            isDragging -> 1.018f
                            isPressed -> 0.982f
                            else -> 1f
                        },
                        animationSpec = spring(dampingRatio = 0.76f, stiffness = 560f),
                        label = "address-card-scale",
                    )
                    val entryProgress by animateFloatAsState(
                        targetValue = if (hasEntered) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.88f, stiffness = 420f),
                        label = "address-card-entry",
                    )
                    val dragRotation by animateFloatAsState(
                        targetValue = if (isDragging) -0.65f else 0f,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 520f),
                        label = "address-card-drag-rotation",
                    )
                    val dragCompensationPx = if (isDragging) {
                        dragDistancePx - (dragTargetIndex - dragStartIndex) * peekPx
                    } else {
                        0f
                    }
                    val safeDescription = stringResource(
                        R.string.address_stack_item_content_description,
                        index + 1,
                        draftAddresses.size,
                    )
                    val accessibilityActions = buildList {
                        if (index > 0 && !sortingInProgress) {
                            add(
                                CustomAccessibilityAction(moveUpActionLabel) {
                                    onAddressesReordered(
                                        moveItem(draftAddresses, index, index - 1)
                                            .map(AddressListItemUiModel::id),
                                    )
                                    true
                                },
                            )
                        }
                        if (index < draftAddresses.lastIndex && !sortingInProgress) {
                            add(
                                CustomAccessibilityAction(moveDownActionLabel) {
                                    onAddressesReordered(
                                        moveItem(draftAddresses, index, index + 1)
                                            .map(AddressListItemUiModel::id),
                                    )
                                    true
                                },
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(
                                if (isDragging) draftAddresses.size + 1f else index.toFloat(),
                            )
                            .graphicsLayer {
                                translationX = if (isDragging) dragDistanceXPx else 0f
                                translationY = if (isDragging) {
                                    targetOffset.toPx() + dragCompensationPx
                                } else {
                                    animatedOffset.toPx()
                                }
                                val entryScale = 0.965f + (0.035f * entryProgress)
                                scaleX = scale * entryScale
                                scaleY = scale * entryScale
                                alpha = entryProgress
                                rotationZ = dragRotation
                                shadowElevation = if (isDragging) 26.dp.toPx() else 8.dp.toPx()
                                shape = cardShape
                                clip = false
                            }
                            .onGloballyPositioned { coordinates ->
                                if (!isDragging) cardOriginInRoot = coordinates.boundsInRoot().topLeft
                            }
                            .pointerInput(address.id, sortingInProgress) {
                                if (sortingInProgress) return@pointerInput
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { localOffset ->
                                        val start = latestDraftAddresses.indexOfFirst {
                                            it.id == address.id
                                        }
                                        if (start >= 0) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            dragStartOrder = latestDraftAddresses
                                            dragStartIndex = start
                                            dragTargetIndex = start
                                            dragDistancePx = 0f
                                            dragDistanceXPx = 0f
                                            dragPointerInRoot = cardOriginInRoot + localOffset
                                            draggingAddressId = address.id
                                        }
                                    },
                                    onDrag = { change, amount ->
                                        if (draggingAddressId == address.id) {
                                            change.consume()
                                            dragDistanceXPx += amount.x
                                            dragDistancePx += amount.y
                                            dragPointerInRoot += amount
                                            val target = dragTargetIndex(
                                                startIndex = dragStartIndex,
                                                dragDistancePx = dragDistancePx,
                                                itemStepPx = peekPx,
                                                itemCount = dragStartOrder.size,
                                            )
                                            if (target != dragTargetIndex) {
                                                dragTargetIndex = target
                                                draftAddresses = moveItem(
                                                    dragStartOrder,
                                                    dragStartIndex,
                                                    target,
                                                )
                                                haptics.performHapticFeedback(
                                                    HapticFeedbackType.TextHandleMove,
                                                )
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (draggingAddressId == address.id) {
                                            val dropped = onAddressDropped?.invoke(
                                                address.id,
                                                dragPointerInRoot,
                                            ) == true
                                            if (!dropped) {
                                                onAddressesReordered(
                                                    draftAddresses.map(AddressListItemUiModel::id),
                                                )
                                            }
                                        }
                                        draggingAddressId = null
                                        dragDistancePx = 0f
                                        dragDistanceXPx = 0f
                                        dragPointerInRoot = Offset.Unspecified
                                    },
                                    onDragCancel = {
                                        draftAddresses = latestAddresses
                                        draggingAddressId = null
                                        dragDistancePx = 0f
                                        dragDistanceXPx = 0f
                                        dragPointerInRoot = Offset.Unspecified
                                    },
                                )
                            }
                            .clickable(
                                enabled = !sortingInProgress && draggingAddressId == null,
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onAddressOpened(address.id) },
                            )
                            .clearAndSetSemantics {
                                traversalIndex = index.toFloat()
                                contentDescription = safeDescription
                                customActions = accessibilityActions
                                onClick(label = openActionLabel) {
                                    onAddressOpened(address.id)
                                    true
                                }
                            },
                    ) {
                        CardTemplatePreview(
                            template = CardTemplateRegistry.findOrDefault(address.cardTemplateId),
                            nickname = address.nickname,
                            modifier = Modifier.fillMaxWidth(),
                            shadowElevation = 0.dp,
                        )
                    }
                }
            }
        }
    }
}

private const val ADDRESS_CARD_ASPECT_RATIO = 1.586f
private val ADDRESS_CARD_PEEK = 68.dp
