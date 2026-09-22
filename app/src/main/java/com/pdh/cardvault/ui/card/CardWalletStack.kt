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
import com.pdh.cardvault.presentation.CardListItemUiModel
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun CardWalletStack(
    cards: List<CardListItemUiModel>,
    sortingInProgress: Boolean,
    openActionLabel: String,
    moveUpActionLabel: String,
    moveDownActionLabel: String,
    onCardOpened: (UUID) -> Unit,
    onCardsReordered: (List<UUID>) -> Unit,
    modifier: Modifier = Modifier,
    onCardDropped: ((UUID, Offset) -> Boolean)? = null,
) {
    if (cards.isEmpty()) return

    var draftCards by remember { mutableStateOf(cards) }
    var dragStartOrder by remember { mutableStateOf(cards) }
    var draggingCardId by remember { mutableStateOf<UUID?>(null) }
    var dragDistancePx by remember { mutableFloatStateOf(0f) }
    var dragDistanceXPx by remember { mutableFloatStateOf(0f) }
    var dragPointerInRoot by remember { mutableStateOf(Offset.Unspecified) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragTargetIndex by remember { mutableIntStateOf(-1) }
    val latestCards by rememberUpdatedState(cards)
    val latestDraftCards by rememberUpdatedState(draftCards)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    LaunchedEffect(cards, draggingCardId) {
        if (draggingCardId == null) draftCards = cards
    }

    BoxWithConstraints(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
    ) {
        val cardShape = MaterialTheme.shapes.extraLarge
        val cardHeight = maxWidth / CARD_ASPECT_RATIO
        val stackHeight = walletStackHeight(
            cardCount = draftCards.size,
            cardHeight = cardHeight.value,
            peek = CARD_PEEK.value,
        ).dp
        val peekPx = with(density) { CARD_PEEK.toPx() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stackHeight)
                .semantics { isTraversalGroup = true },
        ) {
            draftCards.forEachIndexed { index, card ->
                key(card.id) {
                    val isDragging = draggingCardId == card.id
                    var hasEntered by remember(card.id) { mutableStateOf(false) }
                    var cardOriginInRoot by remember(card.id) { mutableStateOf(Offset.Zero) }
                    LaunchedEffect(card.id) { hasEntered = true }
                    val targetOffset = walletCardOffset(index = index, peek = CARD_PEEK.value).dp
                    val animatedOffset by animateDpAsState(
                        targetValue = targetOffset,
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = 430f),
                        label = "wallet-card-offset",
                    )
                    val interactionSource = remember(card.id) { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = when {
                            isDragging -> 1.018f
                            isPressed -> 0.982f
                            else -> 1f
                        },
                        animationSpec = spring(dampingRatio = 0.76f, stiffness = 560f),
                        label = "wallet-card-scale",
                    )
                    val entryProgress by animateFloatAsState(
                        targetValue = if (hasEntered) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.88f, stiffness = 420f),
                        label = "wallet-card-entry",
                    )
                    val dragRotation by animateFloatAsState(
                        targetValue = if (isDragging) -0.65f else 0f,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 520f),
                        label = "wallet-card-drag-rotation",
                    )
                    val dragCompensationPx = if (isDragging) {
                        dragDistancePx - (dragTargetIndex - dragStartIndex) * peekPx
                    } else {
                        0f
                    }
                    val safeDescription = stringResource(
                        R.string.card_stack_item_content_description,
                        index + 1,
                        draftCards.size,
                    )
                    val accessibleActions = buildList {
                        if (index > 0 && !sortingInProgress) {
                            add(
                                CustomAccessibilityAction(moveUpActionLabel) {
                                    onCardsReordered(
                                        moveItem(draftCards, index, index - 1)
                                            .map(CardListItemUiModel::id),
                                    )
                                    true
                                },
                            )
                        }
                        if (index < draftCards.lastIndex && !sortingInProgress) {
                            add(
                                CustomAccessibilityAction(moveDownActionLabel) {
                                    onCardsReordered(
                                        moveItem(draftCards, index, index + 1)
                                            .map(CardListItemUiModel::id),
                                    )
                                    true
                                },
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) draftCards.size + 1f else index.toFloat())
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
                            .pointerInput(card.id, sortingInProgress) {
                                if (sortingInProgress) return@pointerInput
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { localOffset ->
                                        val start = latestDraftCards.indexOfFirst { it.id == card.id }
                                        if (start >= 0) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            dragStartOrder = latestDraftCards
                                            dragStartIndex = start
                                            dragTargetIndex = start
                                            dragDistancePx = 0f
                                            dragDistanceXPx = 0f
                                            dragPointerInRoot = cardOriginInRoot + localOffset
                                            draggingCardId = card.id
                                        }
                                    },
                                    onDrag = { change, amount ->
                                        if (draggingCardId == card.id) {
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
                                                draftCards = moveItem(
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
                                        if (draggingCardId == card.id) {
                                            val dropped = onCardDropped?.invoke(card.id, dragPointerInRoot) == true
                                            if (!dropped) {
                                                onCardsReordered(draftCards.map(CardListItemUiModel::id))
                                            }
                                        }
                                        draggingCardId = null
                                        dragDistancePx = 0f
                                        dragDistanceXPx = 0f
                                        dragPointerInRoot = Offset.Unspecified
                                    },
                                    onDragCancel = {
                                        draftCards = latestCards
                                        draggingCardId = null
                                        dragDistancePx = 0f
                                        dragDistanceXPx = 0f
                                        dragPointerInRoot = Offset.Unspecified
                                    },
                                )
                            }
                            .clickable(
                                enabled = !sortingInProgress && draggingCardId == null,
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onCardOpened(card.id) },
                            )
                            .clearAndSetSemantics {
                                traversalIndex = index.toFloat()
                                contentDescription = safeDescription
                                customActions = accessibleActions
                                onClick(label = openActionLabel) {
                                    onCardOpened(card.id)
                                    true
                                }
                            },
                    ) {
                        CardTemplatePreview(
                            template = CardTemplateRegistry.findOrDefault(card.cardTemplateId),
                            modifier = Modifier.fillMaxWidth(),
                            nickname = card.nickname,
                            cardNetwork = card.cardNetwork,
                            shadowElevation = 0.dp,
                        )
                    }
                }
            }
        }
    }
}

private const val CARD_ASPECT_RATIO = 1.586f
private val CARD_PEEK = 70.dp

internal fun walletCardOffset(index: Int, peek: Float): Float {
    require(index >= 0) { "Card index must not be negative." }
    return peek * index
}

internal fun walletStackHeight(
    cardCount: Int,
    cardHeight: Float,
    peek: Float,
): Float {
    require(cardCount > 0) { "A wallet stack requires at least one card." }
    return walletCardOffset(cardCount - 1, peek) + cardHeight
}

internal fun dragTargetIndex(
    startIndex: Int,
    dragDistancePx: Float,
    itemStepPx: Float,
    itemCount: Int,
): Int {
    require(itemCount > 0 && startIndex in 0 until itemCount && itemStepPx > 0f)
    return (startIndex + (dragDistancePx / itemStepPx).roundToInt())
        .coerceIn(0, itemCount - 1)
}

internal fun <T> moveItem(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
    require(fromIndex in items.indices && toIndex in items.indices)
    if (fromIndex == toIndex) return items
    return items.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}
