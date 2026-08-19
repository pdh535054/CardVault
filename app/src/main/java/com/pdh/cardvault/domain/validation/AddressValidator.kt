package com.pdh.cardvault.domain.validation

import com.pdh.cardvault.domain.model.AddressInput

enum class AddressValidationError {
    Nickname,
    DetailedAddress,
    City,
    Other,
    PostalCode,
    Country,
    Template,
}

data class AddressValidationResult(
    val normalizedInput: AddressInput,
    val errors: Set<AddressValidationError>,
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

class AddressValidator(
    private val templateIdLookup: (String) -> Boolean,
) {
    fun validate(input: AddressInput): AddressValidationResult {
        val normalized = input.copy(
            nickname = input.nickname.trim(),
            detailedAddress = input.detailedAddress.trim(),
            city = input.city.trim(),
            other = input.other.trim(),
            postalCode = input.postalCode.trim(),
            country = input.country.trim(),
            cardTemplateId = input.cardTemplateId.trim(),
        )
        val errors = buildSet {
            if (normalized.nickname.codePointLength() !in 1..50) {
                add(AddressValidationError.Nickname)
            }
            if (normalized.detailedAddress.codePointLength() !in 1..500) {
                add(AddressValidationError.DetailedAddress)
            }
            if (normalized.city.codePointLength() !in 1..100) {
                add(AddressValidationError.City)
            }
            if (normalized.other.codePointLength() > 200) {
                add(AddressValidationError.Other)
            }
            if (normalized.postalCode.codePointLength() !in 1..20) {
                add(AddressValidationError.PostalCode)
            }
            if (normalized.country.codePointLength() !in 1..100) {
                add(AddressValidationError.Country)
            }
            if (!templateIdLookup(normalized.cardTemplateId)) {
                add(AddressValidationError.Template)
            }
        }
        return AddressValidationResult(normalized, errors)
    }

    fun requireValid(input: AddressInput): AddressInput {
        val validation = validate(input)
        if (!validation.isValid) throw AddressValidationException(validation.errors)
        return validation.normalizedInput
    }
}

class AddressValidationException(
    val validationErrors: Set<AddressValidationError>,
) : IllegalArgumentException("The address record is invalid.")

private fun String.codePointLength(): Int = codePointCount(0, length)
