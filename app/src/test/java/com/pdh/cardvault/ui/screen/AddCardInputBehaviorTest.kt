package com.pdh.cardvault.ui.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddCardInputBehaviorTest {
    @Test
    fun expiryFocusAdvancesOnlyWhenTheFourthDigitIsNewlyCompleted() {
        assertFalse(shouldAdvanceExpiryFocusToCvv(previousDigits = "06", normalizedDigits = "062"))
        assertTrue(shouldAdvanceExpiryFocusToCvv(previousDigits = "062", normalizedDigits = "0629"))
        assertFalse(shouldAdvanceExpiryFocusToCvv(previousDigits = "0629", normalizedDigits = "0629"))
        assertFalse(shouldAdvanceExpiryFocusToCvv(previousDigits = "0629", normalizedDigits = "062"))
    }
}
