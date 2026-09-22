package com.pdh.cardvault.domain.model

data class AddressInput(
    val nickname: String,
    val detailedAddress: String,
    val city: String,
    val other: String,
    val postalCode: String,
    val country: String,
    val cardTemplateId: String,
    val folderId: String? = null,
) {
    override fun toString(): String = "AddressInput(sensitiveFields=redacted)"
}
