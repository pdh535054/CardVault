package com.pdh.cardvault.desktop.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class CardVisualsTest {
    @Test
    fun standardCardAspectRatioMatchesId1CardDimensions() {
        assertEquals(85.60f / 53.98f, STANDARD_CARD_ASPECT_RATIO, absoluteTolerance = 0.0001f)
    }

    @Test
    fun desktopFlipSpecularIntensityRestsAtZeroAndPeaksMidFlip() {
        assertEquals(0f, desktopFlipSpecularIntensity(0f), absoluteTolerance = 0.0001f)
        assertEquals(0f, desktopFlipSpecularIntensity(180f), absoluteTolerance = 0.0001f)
        assertEquals(1f, desktopFlipSpecularIntensity(45f), absoluteTolerance = 0.001f)
        assertEquals(1f, desktopFlipSpecularIntensity(135f), absoluteTolerance = 0.001f)
    }

    @Test
    fun desktopLiveStackIndexPartsNeighboursSmoothlyDuringDrag() {
        assertEquals(listOf(0, 1, 1, 2, 4), (0..4).map { desktopLiveStackIndex(it, from = 1, target = 3, active = true) })
        assertEquals(listOf(0, 1, 2, 3, 4), (0..4).map { desktopLiveStackIndex(it, from = 1, target = 3, active = false) })
    }
}
