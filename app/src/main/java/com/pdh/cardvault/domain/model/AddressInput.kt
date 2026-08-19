package com.pdh.cardvault.domain.model

data class AddressInput(
    val nickname: String,
    val detailedAddress: String,
    val city: String,
    val other: String,
    val postalCode: String,
    val country: String,
    val cardTemplateId: String,
) {
    override fun toString(): String = "AddressInput(sensitiveFields=redacted)"
}
