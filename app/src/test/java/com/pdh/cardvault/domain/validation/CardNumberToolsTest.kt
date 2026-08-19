package com.pdh.cardvault.domain.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardNumberToolsTest {
    @Test
    fun normalizeRemovesSpacesAndHyphens() {
        val expected = syntheticDigits(count = 12)
        val raw = expected.chunked(size = 2).joinToString(separator = " - ")

        assertTrue(CardNumberTools.normalize(raw) == expected)
    }

    @Test
    fun normalizeRejectsCharactersOutsideTheAllowedSet() {
        val raw = "${"0".repeat(11)}X"

        assertTrue(CardNumberTools.normalize(raw) == null)
        assertFalse(CardNumberTools.isStructurallyValid(raw))
    }

    @Test
    fun formatGroupsDigitsFromTheLeftInFours() {
        val number = syntheticDigits(count = 19)

        assertTrue(CardNumberTools.formatGrouped(number) == number.chunked(4).joinToString(" "))
    }

    @Test
    fun lastFourAndMaskRevealOnlyTheSuffix() {
        val number = syntheticDigits(count = 16)
        val suffix = number.takeLast(4)

        assertTrue(CardNumberTools.lastFour(number) == suffix)
        assertTrue(CardNumberTools.mask(number) == "•••• •••• •••• $suffix")
        assertFalse(requireNotNull(CardNumberTools.mask(number)).contains(number.dropLast(4)))
    }

    @Test
    fun twelveAndNineteenDigitsAreAccepted() {
        assertTrue(CardNumberTools.isStructurallyValid("0".repeat(12)))
        assertTrue(CardNumberTools.isStructurallyValid("0".repeat(19)))
    }

    @Test
    fun lengthsOutsideTwelveThroughNineteenAreRejected() {
        assertFalse(CardNumberTools.isStructurallyValid("0".repeat(11)))
        assertFalse(CardNumberTools.isStructurallyValid("0".repeat(20)))
        assertTrue(CardNumberTools.formatGrouped("0".repeat(11)) == null)
        assertTrue(CardNumberTools.mask("0".repeat(20)) == null)
    }

    @Test
    fun obviouslySyntheticAllZeroNumberPassesLuhn() {
        assertTrue(CardNumberTools.isLuhnValid("0".repeat(12)))
    }

    @Test
    fun obviouslySyntheticAlteredNumberFailsLuhn() {
        assertFalse(CardNumberTools.isLuhnValid("${"0".repeat(11)}1"))
    }

    private fun syntheticDigits(count: Int): String = buildString {
        repeat(count) { index -> append(index % 10) }
    }
}
