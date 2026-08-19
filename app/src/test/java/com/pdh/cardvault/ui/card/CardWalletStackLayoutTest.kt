package com.pdh.cardvault.ui.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class CardWalletStackLayoutTest {
    @Test
    fun cardsUseStablePeekOffsetsAndTheStackEndsAtTheLastCardBottom() {
        val offsets = (0 until 4).map { index ->
            walletCardOffset(index = index, peek = 72f)
        }

        assertEquals(listOf(0f, 72f, 144f, 216f), offsets)
        assertEquals(
            436f,
            walletStackHeight(cardCount = 4, cardHeight = 220f, peek = 72f),
            0f,
        )
    }

    @Test
    fun aSingleCardStackUsesExactlyTheCardHeight() {
        assertEquals(
            220f,
            walletStackHeight(cardCount = 1, cardHeight = 220f, peek = 72f),
            0f,
        )
    }

    @Test
    fun dragTargetUsesNearestPeekStepAndClampsToTheStack() {
        assertEquals(2, dragTargetIndex(2, dragDistancePx = 34f, itemStepPx = 70f, itemCount = 5))
        assertEquals(3, dragTargetIndex(2, dragDistancePx = 36f, itemStepPx = 70f, itemCount = 5))
        assertEquals(2, dragTargetIndex(2, dragDistancePx = -34f, itemStepPx = 70f, itemCount = 5))
        assertEquals(1, dragTargetIndex(2, dragDistancePx = -36f, itemStepPx = 70f, itemCount = 5))
        assertEquals(4, dragTargetIndex(2, dragDistancePx = 10_000f, itemStepPx = 70f, itemCount = 5))
        assertEquals(0, dragTargetIndex(2, dragDistancePx = -10_000f, itemStepPx = 70f, itemCount = 5))
    }

    @Test
    fun moveItemSupportsForwardAndBackwardMovesWithoutMutatingTheInput() {
        val original = listOf("a", "b", "c", "d")

        val movedForward = moveItem(original, fromIndex = 0, toIndex = 2)
        val movedBackward = moveItem(original, fromIndex = 3, toIndex = 1)

        assertEquals(listOf("b", "c", "a", "d"), movedForward)
        assertEquals(listOf("a", "d", "b", "c"), movedBackward)
        assertEquals(listOf("a", "b", "c", "d"), original)
        assertNotSame(original, movedForward)
        assertNotSame(original, movedBackward)
    }

    @Test
    fun movingAnItemToItsCurrentIndexKeepsTheOriginalListInstance() {
        val original = listOf("a", "b", "c")

        assertSame(original, moveItem(original, fromIndex = 1, toIndex = 1))
    }

    @Test
    fun invalidLayoutAndDragArgumentsFailFast() {
        assertThrows(IllegalArgumentException::class.java) {
            walletCardOffset(index = -1, peek = 72f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            walletStackHeight(cardCount = 0, cardHeight = 220f, peek = 72f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            dragTargetIndex(
                startIndex = 0,
                dragDistancePx = 0f,
                itemStepPx = 0f,
                itemCount = 1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            moveItem(listOf("a"), fromIndex = 0, toIndex = 1)
        }
    }
}
