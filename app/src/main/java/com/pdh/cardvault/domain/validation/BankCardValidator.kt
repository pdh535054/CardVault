package com.pdh.cardvault.domain.validation

import com.pdh.cardvault.domain.model.BankCardInput
import java.time.YearMonth

fun interface CardTemplateIdLookup {
    fun contains(templateId: String): Boolean
}

enum class BankCardValidationError {
    NICKNAME_REQUIRED,
    NICKNAME_TOO_LONG,
    ISSUER_REQUIRED,
    ISSUER_TOO_LONG,
    CARD_NUMBER_ILLEGAL_CHARACTER,
    CARD_NUMBER_LENGTH,
    EXPIRY_MONTH_OUT_OF_RANGE,
    EXPIRY_YEAR_OUT_OF_SUPPORTED_RANGE,
    CVV_REQUIRED_WHEN_ENABLED,
    CVV_FORMAT,
    NOTES_TOO_LONG,
    UNKNOWN_TEMPLATE_ID,
}

enum class BankCardValidationWarning {
    LUHN_CHECK_FAILED,
}

data class BankCardValidationResult(
    val normalizedInput: BankCardInput,
    val errors: Set<BankCardValidationError>,
    val warnings: Set<BankCardValidationWarning>,
) {
    val isValid: Boolean
        get() = errors.isEmpty()

    override fun toString(): String =
        "BankCardValidationResult(isValid=$isValid, errors=$errors, warnings=$warnings, " +
            "sensitiveFields=redacted)"
}

class BankCardValidationException(
    val validationErrors: Set<BankCardValidationError>,
) : IllegalArgumentException(
    "Card validation failed: " + validationErrors.joinToString(separator = ",") { error ->
        error.name
    },
)

class BankCardValidator(
    private val templateIdLookup: CardTemplateIdLookup,
) {
    fun validate(input: BankCardInput): BankCardValidationResult {
        val errors = linkedSetOf<BankCardValidationError>()
        val warnings = linkedSetOf<BankCardValidationWarning>()
        val normalizedNickname = input.nickname.trim()
        val normalizedIssuerName = input.issuerName.trim()
        val normalizedCardNumber = CardNumberTools.normalize(input.cardNumber)
        val normalizedCvv = input.cvv.takeIf { input.saveCvv }

        when {
            normalizedNickname.isEmpty() -> errors += BankCardValidationError.NICKNAME_REQUIRED
            normalizedNickname.codePointLength() > MAX_NICKNAME_LENGTH ->
                errors += BankCardValidationError.NICKNAME_TOO_LONG
        }

        when {
            normalizedIssuerName.isEmpty() -> errors += BankCardValidationError.ISSUER_REQUIRED
            normalizedIssuerName.codePointLength() > MAX_ISSUER_LENGTH ->
                errors += BankCardValidationError.ISSUER_TOO_LONG
        }

        if (normalizedCardNumber == null) {
            errors += BankCardValidationError.CARD_NUMBER_ILLEGAL_CHARACTER
        } else if (normalizedCardNumber.length !in MIN_CARD_NUMBER_LENGTH..MAX_CARD_NUMBER_LENGTH) {
            errors += BankCardValidationError.CARD_NUMBER_LENGTH
        } else if (!CardNumberTools.isLuhnValid(normalizedCardNumber)) {
            warnings += BankCardValidationWarning.LUHN_CHECK_FAILED
        }

        if (input.expiryMonth !in 1..12) {
            errors += BankCardValidationError.EXPIRY_MONTH_OUT_OF_RANGE
        }
        if (input.expiryYear !in MIN_EXPIRY_YEAR..MAX_EXPIRY_YEAR) {
            errors += BankCardValidationError.EXPIRY_YEAR_OUT_OF_SUPPORTED_RANGE
        }

        if (input.saveCvv) {
            when {
                normalizedCvv.isNullOrEmpty() ->
                    errors += BankCardValidationError.CVV_REQUIRED_WHEN_ENABLED
                normalizedCvv.length !in 3..4 || normalizedCvv.any { !it.isAsciiDigit() } ->
                    errors += BankCardValidationError.CVV_FORMAT
            }
        }

        if (input.notes.codePointLength() > MAX_NOTES_LENGTH) {
            errors += BankCardValidationError.NOTES_TOO_LONG
        }
        if (!templateIdLookup.contains(input.cardTemplateId)) {
            errors += BankCardValidationError.UNKNOWN_TEMPLATE_ID
        }

        val normalizedInput = input.copy(
            nickname = normalizedNickname,
            issuerName = normalizedIssuerName,
            cardNumber = normalizedCardNumber.orEmpty(),
            cvv = normalizedCvv,
        )
        return BankCardValidationResult(
            normalizedInput = normalizedInput,
            errors = errors,
            warnings = warnings,
        )
    }

    fun requireValid(input: BankCardInput): BankCardInput {
        val result = validate(input)
        if (!result.isValid) throw BankCardValidationException(result.errors)
        return result.normalizedInput
    }

    fun isExpired(
        expiryMonth: Int,
        expiryYear: Int,
        referenceMonth: YearMonth = YearMonth.now(),
    ): Boolean {
        require(expiryMonth in 1..12) { "Expiry month is outside the supported range." }
        require(expiryYear in MIN_EXPIRY_YEAR..MAX_EXPIRY_YEAR) {
            "Expiry year is outside the supported range."
        }
        return YearMonth.of(expiryYear, expiryMonth).isBefore(referenceMonth)
    }

    private companion object {
        const val MIN_CARD_NUMBER_LENGTH = 12
        const val MAX_CARD_NUMBER_LENGTH = 19
        const val MAX_NICKNAME_LENGTH = 50
        const val MAX_ISSUER_LENGTH = 80
        const val MAX_NOTES_LENGTH = 1_000
        const val MIN_EXPIRY_YEAR = 2_000
        const val MAX_EXPIRY_YEAR = 2_099
    }
}

private fun String.codePointLength(): Int = codePointCount(0, length)

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
