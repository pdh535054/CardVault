package com.pdh.cardvault.desktop.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class CardVisualsTest {
    @Test
    fun standardCardAspectRatioMatchesId1CardDimensions() {
        assertEquals(85.60f / 53.98f, STANDARD_CARD_ASPECT_RATIO, absoluteTolerance = 0.0001f)
    }
}
