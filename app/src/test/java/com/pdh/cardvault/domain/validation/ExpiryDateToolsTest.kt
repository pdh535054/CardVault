package com.pdh.cardvault.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ExpiryDateToolsTest {
    @Test
    fun editableValueKeepsOnlyFourAsciiDigitsInInputOrder() {
        assertEquals("", ExpiryDateTools.normalizeInputDigits(""))
        assertEquals("0", ExpiryDateTools.normalizeInputDigits("0"))
        assertEquals("06", ExpiryDateTools.normalizeInputDigits("06"))
        assertEquals("062", ExpiryDateTools.normalizeInputDigits("062"))
        assertEquals("0629", ExpiryDateTools.normalizeInputDigits("0629"))
        assertEquals("0629", ExpiryDateTools.normalizeInputDigits("06/29"))
        assertEquals("0629", ExpiryDateTools.normalizeInputDigits("0A6-2 9X8"))
        assertEquals("29", ExpiryDateTools.normalizeInputDigits("０６/29"))
    }

    @Test
    fun editableDigitsGainOnlyAVisualSeparatorWithoutReorderingTheYear() {
        assertEquals("", ExpiryDateTools.formatInputDigits(""))
        assertEquals("0", ExpiryDateTools.formatInputDigits("0"))
        assertEquals("06/", ExpiryDateTools.formatInputDigits("06"))
        assertEquals("06/2", ExpiryDateTools.formatInputDigits("062"))
        assertEquals("06/29", ExpiryDateTools.formatInputDigits("0629"))
        assertEquals("06/29", ExpiryDateTools.formatInputDigits("06/29"))
        assertEquals("06/29", ExpiryDateTools.formatInputDigits("06298"))
    }

    @Test
    fun fourEditableDigitsFormatIntoAParseableDomainDate() {
        assertEquals(
            ParsedExpiryDate(month = 6, year = 2_029),
            ExpiryDateTools.parse(ExpiryDateTools.formatInputDigits("0629")),
        )
    }

    @Test
    fun parseMapsTwoDigitYearToFixedTwentyFirstCentury() {
        assertEquals(
            ParsedExpiryDate(month = 6, year = 2_029),
            ExpiryDateTools.parse("06/29"),
        )
        assertEquals(
            ParsedExpiryDate(month = 1, year = 2_000),
            ExpiryDateTools.parse("01/00"),
        )
        assertEquals(
            ParsedExpiryDate(month = 12, year = 2_099),
            ExpiryDateTools.parse("12/99"),
        )
    }

    @Test
    fun formatUsesExactlyTwoDigitsForMonthAndYear() {
        assertEquals("06/29", ExpiryDateTools.format(month = 6, year = 2_029))
        assertEquals("01/00", ExpiryDateTools.format(month = 1, year = 2_000))
        assertEquals("12/99", ExpiryDateTools.format(month = 12, year = 2_099))
    }

    @Test
    fun parseRejectsAnythingThatIsNotExactlyFiveCharacters() {
        listOf(
            "6/29",
            "006/29",
            "06/029",
            "06/29 ",
            " 06/29",
            "",
        ).forEach { input -> assertNull(ExpiryDateTools.parse(input)) }
    }

    @Test
    fun parseRejectsWrongSeparatorOrNonAsciiDigits() {
        listOf(
            "06-29",
            "06 29",
            "AA/29",
            "06/2X",
            "０６/29",
        ).forEach { input -> assertNull(ExpiryDateTools.parse(input)) }
    }

    @Test
    fun parseRejectsMonthOutsideOneThroughTwelve() {
        assertNull(ExpiryDateTools.parse("00/29"))
        assertNull(ExpiryDateTools.parse("13/29"))
    }

    @Test
    fun formatRejectsUnsupportedInternalValues() {
        assertNull(ExpiryDateTools.format(month = 0, year = 2_029))
        assertNull(ExpiryDateTools.format(month = 13, year = 2_029))
        assertNull(ExpiryDateTools.format(month = 6, year = 1_999))
        assertNull(ExpiryDateTools.format(month = 6, year = 2_100))
    }

    @Test
    fun parsedValueStringDoesNotRevealTheExpiry() {
        val parsed = requireNotNull(ExpiryDateTools.parse("06/29"))

        assertFalse(parsed.toString().contains("06"))
        assertFalse(parsed.toString().contains("29"))
        assertFalse(parsed.toString().contains("2029"))
    }
}
