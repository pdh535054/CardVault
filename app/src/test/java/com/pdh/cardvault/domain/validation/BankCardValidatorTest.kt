package com.pdh.cardvault.domain.validation

import com.pdh.cardvault.domain.model.BankCardInput
import com.pdh.cardvault.ui.card.CardTemplateRegistry
import java.time.YearMonth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BankCardValidatorTest {
    private val validator = BankCardValidator { templateId ->
        CardTemplateRegistry.findById(templateId) != null
    }

    @Test
    fun nicknameAndIssuerAreTrimmed() {
        val normalized = validator.requireValid(
            validInput(
                nickname = "  Fictional vault alias  ",
                issuerName = "  Fictional offline issuer  ",
            ),
        )

        assertTrue(normalized.nickname == "Fictional vault alias")
        assertTrue(normalized.issuerName == "Fictional offline issuer")
    }

    @Test
    fun nicknameMustContainOneThroughFiftyCharactersAfterTrimming() {
        assertHasError(validInput(nickname = "   "), BankCardValidationError.NICKNAME_REQUIRED)
        assertHasError(
            validInput(nickname = "N".repeat(51)),
            BankCardValidationError.NICKNAME_TOO_LONG,
        )
        assertTrue(validator.validate(validInput(nickname = "N".repeat(50))).isValid)
    }

    @Test
    fun issuerMustContainOneThroughEightyCharactersAfterTrimming() {
        assertHasError(validInput(issuerName = "   "), BankCardValidationError.ISSUER_REQUIRED)
        assertHasError(
            validInput(issuerName = "I".repeat(81)),
            BankCardValidationError.ISSUER_TOO_LONG,
        )
        assertTrue(validator.validate(validInput(issuerName = "I".repeat(80))).isValid)
    }

    @Test
    fun monthBoundariesAreEnforced() {
        assertHasError(
            validInput(expiryMonth = 0),
            BankCardValidationError.EXPIRY_MONTH_OUT_OF_RANGE,
        )
        assertHasError(
            validInput(expiryMonth = 13),
            BankCardValidationError.EXPIRY_MONTH_OUT_OF_RANGE,
        )
        assertTrue(validator.validate(validInput(expiryMonth = 1)).isValid)
        assertTrue(validator.validate(validInput(expiryMonth = 12)).isValid)
    }

    @Test
    fun expiryYearMustMapToTheSupportedTwoDigitUiCentury() {
        assertHasError(
            validInput(expiryYear = 1_999),
            BankCardValidationError.EXPIRY_YEAR_OUT_OF_SUPPORTED_RANGE,
        )
        assertHasError(
            validInput(expiryYear = 2_100),
            BankCardValidationError.EXPIRY_YEAR_OUT_OF_SUPPORTED_RANGE,
        )
        assertTrue(validator.validate(validInput(expiryYear = 2_000)).isValid)
        assertTrue(validator.validate(validInput(expiryYear = 2_099)).isValid)
    }

    @Test
    fun expiryIsDerivedWithoutBlockingExpiredCards() {
        val reference = YearMonth.of(2_030, 6)
        val expiredInput = validInput(expiryMonth = 5, expiryYear = 2_030)

        assertTrue(validator.validate(expiredInput).isValid)
        assertTrue(validator.isExpired(5, 2_030, reference))
        assertFalse(validator.isExpired(6, 2_030, reference))
        assertFalse(validator.isExpired(7, 2_030, reference))
    }

    @Test
    fun threeAndFourDigitCvvValuesAreAcceptedWhenEnabled() {
        val threeDigits = validator.requireValid(
            validInput(saveCvv = true, cvv = "0".repeat(3)),
        )
        val fourDigits = validator.requireValid(
            validInput(saveCvv = true, cvv = "0".repeat(4)),
        )

        assertTrue(threeDigits.cvv == "0".repeat(3))
        assertTrue(fourDigits.cvv == "0".repeat(4))
    }

    @Test
    fun missingShortLongAndNonNumericCvvValuesAreRejectedWhenEnabled() {
        assertHasError(
            validInput(saveCvv = true, cvv = null),
            BankCardValidationError.CVV_REQUIRED_WHEN_ENABLED,
        )
        assertHasError(
            validInput(saveCvv = true, cvv = "0".repeat(2)),
            BankCardValidationError.CVV_FORMAT,
        )
        assertHasError(
            validInput(saveCvv = true, cvv = "0".repeat(5)),
            BankCardValidationError.CVV_FORMAT,
        )
        assertHasError(
            validInput(saveCvv = true, cvv = "00X"),
            BankCardValidationError.CVV_FORMAT,
        )
    }

    @Test
    fun disablingCvvStorageAlwaysClearsTheValue() {
        val normalized = validator.requireValid(
            validInput(saveCvv = false, cvv = "9".repeat(3)),
        )

        assertFalse(normalized.saveCvv)
        assertTrue(normalized.cvv == null)
    }

    @Test
    fun notesMayContainAtMostOneThousandCharacters() {
        assertTrue(validator.validate(validInput(notes = "N".repeat(1_000))).isValid)
        assertHasError(
            validInput(notes = "N".repeat(1_001)),
            BankCardValidationError.NOTES_TOO_LONG,
        )
    }

    @Test
    fun unknownTemplateIdIsRejectedAgainstTheCurrentRegistry() {
        assertHasError(
            validInput(cardTemplateId = "fictional_unknown_template"),
            BankCardValidationError.UNKNOWN_TEMPLATE_ID,
        )
        CardTemplateRegistry.templates.forEach { template ->
            assertTrue(validator.validate(validInput(cardTemplateId = template.id)).isValid)
        }
    }

    @Test
    fun validatedVersionThreeCustomColorIsAcceptedButMalformedColorIsRejected() {
        val validCustomId = CardTemplateRegistry.composeTemplateId(
            colorId = CardTemplateRegistry.customColorId(
                androidx.compose.ui.graphics.Color(0xFF12ABEF),
            ),
            patternId = "contours",
            nicknameStyleId = "orbit",
            nicknameColorId = "ivory",
        )

        assertTrue(validator.validate(validInput(cardTemplateId = validCustomId)).isValid)
        assertHasError(
            validInput(cardTemplateId = "custom:v3:rgb-12abef:contours:orbit:ivory"),
            BankCardValidationError.UNKNOWN_TEMPLATE_ID,
        )
    }

    @Test
    fun failedLuhnCheckIsOnlyAWarning() {
        val result = validator.validate(
            validInput(cardNumber = "${"0".repeat(11)}1"),
        )

        assertTrue(result.isValid)
        assertTrue(BankCardValidationWarning.LUHN_CHECK_FAILED in result.warnings)
    }

    @Test
    fun validationObjectsAndExceptionsRedactSensitiveValues() {
        val fullNumber = "${"0".repeat(11)}X"
        val cvv = "9".repeat(5)
        val input = validInput(cardNumber = fullNumber, saveCvv = true, cvv = cvv)
        val result = validator.validate(input)
        val exception = assertThrows(BankCardValidationException::class.java) {
            validator.requireValid(input)
        }

        listOf(input.toString(), result.toString(), exception.toString()).forEach { output ->
            assertFalse(output.contains(fullNumber))
            assertFalse(output.contains(cvv))
        }
    }

    private fun assertHasError(input: BankCardInput, expected: BankCardValidationError) {
        assertTrue(expected in validator.validate(input).errors)
    }

    private fun validInput(
        nickname: String = "Fictional vault alias",
        issuerName: String = "Fictional offline issuer",
        cardNumber: String = "0".repeat(12),
        expiryMonth: Int = 12,
        expiryYear: Int = 2_099,
        saveCvv: Boolean = false,
        cvv: String? = null,
        cardTemplateId: String = CardTemplateRegistry.DEFAULT_TEMPLATE_ID,
        notes: String = "Clearly fictional local-only note",
    ): BankCardInput = BankCardInput(
        nickname = nickname,
        issuerName = issuerName,
        cardNumber = cardNumber,
        expiryMonth = expiryMonth,
        expiryYear = expiryYear,
        saveCvv = saveCvv,
        cvv = cvv,
        cardTemplateId = cardTemplateId,
        notes = notes,
    )
}
