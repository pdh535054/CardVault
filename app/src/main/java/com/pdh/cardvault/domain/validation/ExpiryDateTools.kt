package com.pdh.cardvault.domain.validation

data class ParsedExpiryDate(
    val month: Int,
    val year: Int,
) {
    override fun toString(): String = "ParsedExpiryDate(sensitiveFields=redacted)"
}

/** Strict conversion between the UI's MM/YY text and the four-digit domain values. */
object ExpiryDateTools {
    private const val INPUT_LENGTH = 5
    private const val SEPARATOR_INDEX = 2
    private const val CENTURY_BASE = 2_000
    private const val MIN_INTERNAL_YEAR = 2_000
    private const val MAX_INTERNAL_YEAR = 2_099

    /** Keeps the editable value cursor-safe: the slash is visual only, never stored here. */
    fun normalizeInputDigits(input: String): String =
        input.filter(Char::isAsciiDigit).take(4)

    /** Formats zero to four editable digits without interpreting them as a valid date. */
    fun formatInputDigits(input: String): String {
        val digits = normalizeInputDigits(input)
        return when {
            digits.length < SEPARATOR_INDEX -> digits
            else -> digits.take(SEPARATOR_INDEX) + "/" + digits.drop(SEPARATOR_INDEX)
        }
    }

    /**
     * Parses exactly MM/YY. The two-digit year always maps to 2000 + YY; no sliding
     * century window or current-date-dependent behavior is used.
     */
    fun parse(input: String): ParsedExpiryDate? {
        if (input.length != INPUT_LENGTH || input[SEPARATOR_INDEX] != '/') return null

        val monthText = input.substring(startIndex = 0, endIndex = SEPARATOR_INDEX)
        val yearText = input.substring(startIndex = SEPARATOR_INDEX + 1)
        if (!monthText.all(Char::isAsciiDigit) || !yearText.all(Char::isAsciiDigit)) return null

        val month = monthText.toInt()
        if (month !in 1..12) return null

        return ParsedExpiryDate(
            month = month,
            year = CENTURY_BASE + yearText.toInt(),
        )
    }

    /** Returns zero-padded MM/YY, or null when the internal values are unsupported. */
    fun format(month: Int, year: Int): String? {
        if (month !in 1..12 || year !in MIN_INTERNAL_YEAR..MAX_INTERNAL_YEAR) return null

        val monthText = month.toString().padStart(length = 2, padChar = '0')
        val yearText = (year - CENTURY_BASE).toString().padStart(length = 2, padChar = '0')
        return "$monthText/$yearText"
    }
}

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
