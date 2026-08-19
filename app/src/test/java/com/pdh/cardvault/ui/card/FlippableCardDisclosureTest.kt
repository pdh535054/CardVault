package com.pdh.cardvault.ui.card

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlippableCardDisclosureTest {
    @Test
    fun completeAtomicRevealCanBeRendered() {
        assertTrue(
            shouldDisplayCardSecrets(
                cardNumber = "synthetic-number-token",
                expiryText = "synthetic-expiry-token",
            ),
        )
    }

    @Test
    fun partialOrEmptyRevealFailsClosed() {
        assertFalse(shouldDisplayCardSecrets(null, "synthetic-expiry-token"))
        assertFalse(shouldDisplayCardSecrets("synthetic-number-token", null))
        assertFalse(shouldDisplayCardSecrets("", "synthetic-expiry-token"))
        assertFalse(shouldDisplayCardSecrets("synthetic-number-token", ""))
    }
}
