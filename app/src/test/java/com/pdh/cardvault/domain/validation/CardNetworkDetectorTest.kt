package com.pdh.cardvault.domain.validation

import com.pdh.cardvault.domain.model.CardNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CardNetworkDetectorTest {
    @Test
    fun detectsVisaFromACompleteStructurallyValidLength() {
        val fictional = "4" + "0".repeat(11)

        assertEquals(CardNetwork.Visa, CardNetworkDetector.detect(fictional))
        assertFalse(CardNumberTools.isLuhnValid(fictional))
    }

    @Test
    fun detectionAcceptsOnlyTheSameSpacesAndHyphensAsCardNumberNormalization() {
        assertEquals(
            CardNetwork.Visa,
            CardNetworkDetector.detect("4000 0000-0000 0000"),
        )
        assertNull(CardNetworkDetector.detect("4000_0000_0000_0000"))
    }

    @Test
    fun detectsBothOfficialMastercardAccountRangesAtTheirBoundaries() {
        listOf("510000", "559999", "222100", "272099").forEach { prefix ->
            assertEquals(
                CardNetwork.Mastercard,
                CardNetworkDetector.detect(prefix + "0".repeat(10)),
            )
        }
    }

    @Test
    fun rejectsNumbersImmediatelyOutsideMastercardRanges() {
        listOf("509999", "560000", "222099", "272100").forEach { prefix ->
            assertNull(CardNetworkDetector.detect(prefix + "0".repeat(10)))
        }
    }

    @Test
    fun mastercardAccountRangeStillRequiresExactlySixteenDigits() {
        assertNull(CardNetworkDetector.detect("510000" + "0".repeat(9)))
        assertNull(CardNetworkDetector.detect("510000" + "0".repeat(11)))
    }

    @Test
    fun detectsUnionPay62And81Prefixes() {
        assertEquals(
            CardNetwork.UnionPay,
            CardNetworkDetector.detect("62" + "0".repeat(14)),
        )
        assertEquals(
            CardNetwork.UnionPay,
            CardNetworkDetector.detect("81" + "0".repeat(14)),
        )
    }

    @Test
    fun incompleteIllegalOrUnknownNumbersDoNotProduceAMark() {
        assertNull(CardNetworkDetector.detect("4"))
        assertNull(CardNetworkDetector.detect("510000"))
        assertNull(CardNetworkDetector.detect("62-AB"))
        assertNull(CardNetworkDetector.detect("7" + "0".repeat(15)))
        assertNull(CardNetworkDetector.detect("4" + "0".repeat(19)))
    }
}
