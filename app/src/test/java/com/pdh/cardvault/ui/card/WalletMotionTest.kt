package com.pdh.cardvault.ui.card

import com.pdh.cardvault.R
import com.pdh.cardvault.ui.screen.greetingResForHour
import com.pdh.cardvault.ui.theme.flipSpecularIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletMotionTest {
    @Test
    fun flipSpecularIntensityIsZeroAtRestAndPeaksDuringMidFlip() {
        assertEquals(0f, flipSpecularIntensity(0f), 0.0001f)
        assertEquals(0f, flipSpecularIntensity(180f), 0.0001f)
        assertEquals(1f, flipSpecularIntensity(45f), 0.001f)
        assertEquals(1f, flipSpecularIntensity(135f), 0.001f)
        assertTrue(flipSpecularIntensity(30f) > 0.5f)
    }

    @Test
    fun draggingDownOpensTheGapBelowAndPullsInterveningCardsUp() {
        // Card 1 dragged to slot 3: cards 2 and 3 move up one slot, others stay.
        assertEquals(listOf(0, 1, 1, 2, 4), (0..4).map { liveStackIndex(it, from = 1, target = 3) })
    }

    @Test
    fun draggingUpPushesInterveningCardsDown() {
        // Card 3 dragged to slot 0: cards 0..2 move down one slot.
        assertEquals(listOf(1, 2, 3, 3, 4), (0..4).map { liveStackIndex(it, from = 3, target = 0) })
    }

    @Test
    fun noMovementWhenTargetEqualsSource() {
        assertEquals(listOf(0, 1, 2), (0..2).map { liveStackIndex(it, from = 1, target = 1) })
    }

    @Test
    fun liveIndicesMatchTheFinalReorderSoTheDropNeedsNoExtraJump() {
        val cards = listOf("a", "b", "c", "d", "e")
        for (from in cards.indices) {
            for (target in cards.indices) {
                val finalOrder = moveItem(cards, from, target)
                cards.forEachIndexed { index, card ->
                    if (index != from) {
                        assertEquals(finalOrder.indexOf(card), liveStackIndex(index, from, target))
                    }
                }
                assertEquals(target, finalOrder.indexOf(cards[from]))
            }
        }
    }

    @Test
    fun greetingFollowsTheTimeOfDay() {
        assertEquals(R.string.home_greeting_night, greetingResForHour(3))
        assertEquals(R.string.home_greeting_morning, greetingResForHour(7))
        assertEquals(R.string.home_greeting_noon, greetingResForHour(12))
        assertEquals(R.string.home_greeting_afternoon, greetingResForHour(15))
        assertEquals(R.string.home_greeting_evening, greetingResForHour(21))
        assertEquals(R.string.home_greeting_night, greetingResForHour(23))
    }
}
