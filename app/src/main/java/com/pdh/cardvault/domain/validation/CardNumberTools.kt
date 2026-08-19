package com.pdh.cardvault.domain.validation

object CardNumberTools {
    private const val MIN_CARD_NUMBER_LENGTH = 12
    private const val MAX_CARD_NUMBER_LENGTH = 19
    private const val MASK_PREFIX = "•••• •••• ••••"

    /** Returns digits only, or null when the input contains anything except digits, spaces or '-'. */
    fun normalize(input: String): String? {
        if (input.any { character -> !character.isAsciiDigit() && character != ' ' && character != '-' }) {
            return null
        }
        return input.filter { character -> character.isAsciiDigit() }
    }

    fun isStructurallyValid(input: String): Boolean = validatedNumber(input) != null

    fun formatGrouped(input: String): String? =
        validatedNumber(input)?.chunked(size = 4)?.joinToString(separator = " ")

    fun lastFour(input: String): String? = validatedNumber(input)?.takeLast(4)

    fun mask(input: String): String? = lastFour(input)?.let { suffix -> "$MASK_PREFIX $suffix" }

    /**
     * Luhn is advisory only. A false result must never become a blocking validation error.
     */
    fun isLuhnValid(input: String): Boolean {
        val normalized = validatedNumber(input) ?: return false
        var sum = 0
        var doubleDigit = false

        for (index in normalized.lastIndex downTo 0) {
            var digit = normalized[index].digitToInt()
            if (doubleDigit) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
            doubleDigit = !doubleDigit
        }

        return sum % 10 == 0
    }

    private fun validatedNumber(input: String): String? =
        normalize(input)?.takeIf { normalized ->
            normalized.length in MIN_CARD_NUMBER_LENGTH..MAX_CARD_NUMBER_LENGTH
        }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
}
