package com.pdh.cardvault.data.room

import com.pdh.cardvault.domain.model.BankCardInput
import com.pdh.cardvault.domain.model.AddressInput
import com.pdh.cardvault.domain.model.CardNetwork
import java.util.UUID

/** Minimal decrypted projection permitted for the foreground card list. */
data class PersistentCardListItem(
    val id: UUID,
    val nickname: String,
    val issuerName: String,
    val cardTemplateId: String,
    val cardNetwork: CardNetwork?,
    val folderId: UUID? = null,
) {
    override fun toString(): String = "PersistentCardListItem(sensitiveFields=redacted)"
}

/** Minimal decrypted projection permitted for the default masked detail screen. */
data class PersistentCardDetail(
    val nickname: String,
    val issuerName: String,
    val maskedCardNumber: String,
    val cardTemplateId: String,
    val folderId: UUID? = null,
    val notes: String,
    val cvvSaved: Boolean,
    val cardNetwork: CardNetwork?,
) {
    override fun toString(): String = "PersistentCardDetail(sensitiveFields=redacted)"
}

/**
 * Short-lived projection returned only after the caller has consumed a record-bound
 * system-authentication authorization. Keeping all reveal values in one projection ensures a
 * single decrypt produces the complete 15-second reveal state.
 */
data class PersistentCardSecrets(
    val cardNumber: String,
    val expiryMonth: Int,
    val expiryYear: Int,
    val cvv: String?,
) {
    override fun toString(): String = "PersistentCardSecrets(values=redacted)"
}

interface PersistentBankCardRepository {
    suspend fun unlockOrCreateVault()

    fun lock()

    fun isUnlocked(): Boolean

    suspend fun add(input: BankCardInput): UUID

    suspend fun getList(): List<PersistentCardListItem>

    suspend fun getMaskedDetail(id: UUID): PersistentCardDetail?

    suspend fun getSecretsForAuthenticatedUse(id: UUID): PersistentCardSecrets?

    suspend fun getEditInputForAuthenticatedUse(id: UUID): BankCardInput?

    suspend fun update(id: UUID, input: BankCardInput): Boolean

    suspend fun delete(id: UUID): Boolean

    suspend fun reorder(orderedIds: List<UUID>)
}

data class PersistentAddressListItem(
    val id: UUID,
    val nickname: String,
    val cardTemplateId: String,
    val folderId: UUID? = null,
) {
    override fun toString(): String = "PersistentAddressListItem(sensitiveFields=redacted)"
}

data class PersistentAddressDetail(
    val nickname: String,
    val detailedAddress: String,
    val city: String,
    val other: String,
    val postalCode: String,
    val country: String,
    val cardTemplateId: String,
    val folderId: UUID? = null,
) {
    override fun toString(): String = "PersistentAddressDetail(sensitiveFields=redacted)"
}

enum class VaultFolderKind { CARDS, ADDRESSES }

data class PersistentVaultFolder(
    val id: UUID,
    val name: String,
    val kind: VaultFolderKind,
) {
    override fun toString(): String = "PersistentVaultFolder(name=redacted, kind=$kind)"
}

interface PersistentVaultFolderRepository {
    suspend fun getFolders(kind: VaultFolderKind): List<PersistentVaultFolder>
    suspend fun getFolderOrder(kind: VaultFolderKind): List<UUID?>
    suspend fun reorderFolders(kind: VaultFolderKind, orderedIds: List<UUID?>)
    suspend fun createFolder(kind: VaultFolderKind, name: String): UUID
    suspend fun renameFolder(id: UUID, name: String): Boolean
    suspend fun deleteFolder(id: UUID): Boolean
    suspend fun moveCardToFolder(cardId: UUID, folderId: UUID?): Boolean
    suspend fun moveAddressToFolder(addressId: UUID, folderId: UUID?): Boolean
}

interface PersistentAddressRepository {
    fun isUnlocked(): Boolean

    suspend fun addAddress(input: AddressInput): UUID

    suspend fun getAddressList(): List<PersistentAddressListItem>

    suspend fun getAddressDetail(id: UUID): PersistentAddressDetail?

    suspend fun getAddressEditInput(id: UUID): AddressInput?

    suspend fun updateAddress(id: UUID, input: AddressInput): Boolean

    suspend fun deleteAddress(id: UUID): Boolean

    suspend fun reorderAddresses(orderedIds: List<UUID>)
}
