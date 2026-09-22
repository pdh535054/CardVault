package com.pdh.cardvault.domain.model

/**
 * Raw business fields supplied to validation and encrypted repository operations.
 *
 * This object is intentionally memory-only. Its string representation is redacted so an
 * exception or debugger helper cannot accidentally print card data through interpolation.
 */
data class BankCardInput(
    val nickname: String,
    val issuerName: String,
    val cardNumber: String,
    val expiryMonth: Int,
    val expiryYear: Int,
    val saveCvv: Boolean,
    val cvv: String?,
    val cardTemplateId: String,
    val notes: String,
    val folderId: String? = null,
) {
    override fun toString(): String = "BankCardInput(sensitiveFields=redacted)"
}
