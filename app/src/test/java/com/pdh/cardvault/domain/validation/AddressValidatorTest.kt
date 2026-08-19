package com.pdh.cardvault.domain.validation

import com.pdh.cardvault.domain.model.AddressInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressValidatorTest {
    private val validator = AddressValidator { templateId -> templateId == "local-style" }

    @Test
    fun trimsAndAcceptsValidSyntheticAddress() {
        val result = validator.validate(validInput().copy(nickname = "  常用地址  "))

        assertTrue(result.isValid)
        assertEquals("常用地址", result.normalizedInput.nickname)
    }

    @Test
    fun requiresNicknameAddressCityPostalCodeCountryAndKnownTemplate() {
        val result = validator.validate(
            validInput().copy(
                nickname = "",
                detailedAddress = "",
                city = "",
                postalCode = "",
                country = "",
                cardTemplateId = "unknown",
            ),
        )

        assertFalse(result.isValid)
        assertEquals(
            setOf(
                AddressValidationError.Nickname,
                AddressValidationError.DetailedAddress,
                AddressValidationError.City,
                AddressValidationError.PostalCode,
                AddressValidationError.Country,
                AddressValidationError.Template,
            ),
            result.errors,
        )
    }

    @Test
    fun boundsOptionalOtherField() {
        val result = validator.validate(validInput().copy(other = "X".repeat(201)))

        assertTrue(AddressValidationError.Other in result.errors)
        assertFalse(result.isValid)
    }

    @Test
    fun modelAndExceptionDoNotExposeAddressValues() {
        val input = validInput()
        val failure = runCatching {
            validator.requireValid(input.copy(postalCode = ""))
        }.exceptionOrNull()
        val output = listOf(input.toString(), failure.toString()).joinToString()

        assertFalse(output.contains(input.detailedAddress))
        assertFalse(output.contains(input.postalCode))
    }

    private fun validInput(): AddressInput = AddressInput(
        nickname = "常用地址",
        detailedAddress = "虚构道路 88 号",
        city = "示例城市",
        other = "虚构楼层",
        postalCode = "TEST-000",
        country = "示例国家",
        cardTemplateId = "local-style",
    )
}
