package com.pdh.cardvault.sync

import java.security.MessageDigest
import java.util.Collections
import java.util.TreeMap
import java.util.UUID

object SyncProtocolLimits {
    const val FILE_BYTES: Int = 4 * 1024 * 1024
    const val CARD_PAYLOAD_BYTES: Int = 8 * 1024
    const val ADDRESS_PAYLOAD_BYTES: Int = 16 * 1024
    const val ACTIVE_CARDS: Int = 256
    const val ACTIVE_ADDRESSES: Int = 256
    const val RECORDS: Int = 1024
    const val ADDRESS_RECORDS: Int = 1024
    const val DEVICES_PER_VECTOR: Int = 16
    const val RECENT_PACKAGE_IDS: Int = 1024
}

enum class SyncErrorCode {
    INVALID_FORMAT,
    UNSUPPORTED_VERSION,
    AUTHENTICATION_FAILED,
    LIMIT_EXCEEDED,
    INVALID_PAIRING_CODE,
    REPLAYED_PACKAGE,
    STALE_PACKAGE,
    VAULT_MISMATCH,
    INVARIANT_VIOLATION,
}

class SyncProtocolException(
    val code: SyncErrorCode,
    cause: Throwable? = null,
) : Exception(code.safeMessage(), cause)

private fun SyncErrorCode.safeMessage(): String = when (this) {
    SyncErrorCode.INVALID_FORMAT -> "The CardVault exchange file is invalid."
    SyncErrorCode.UNSUPPORTED_VERSION -> "The CardVault exchange version is unsupported."
    SyncErrorCode.AUTHENTICATION_FAILED -> "The CardVault exchange file could not be authenticated."
    SyncErrorCode.LIMIT_EXCEEDED -> "The CardVault exchange file exceeds a safety limit."
    SyncErrorCode.INVALID_PAIRING_CODE -> "The CardVault pairing code is invalid."
    SyncErrorCode.REPLAYED_PACKAGE -> "The CardVault exchange file was already imported."
    SyncErrorCode.STALE_PACKAGE -> "The CardVault exchange file is older than an imported file."
    SyncErrorCode.VAULT_MISMATCH -> "The CardVault exchange file belongs to another vault."
    SyncErrorCode.INVARIANT_VIOLATION -> "The CardVault exchange data is inconsistent."
}

/** A defensive, closeable secret container whose textual form is always redacted. */
class SecretBytes(bytes: ByteArray) : AutoCloseable {
    private var value: ByteArray? = bytes.copyOf()

    val size: Int
        get() = value?.size ?: 0

    fun copyBytes(): ByteArray = value?.copyOf()
        ?: throw IllegalStateException("The secret is no longer available.")

    override fun close() {
        value?.fill(0)
        value = null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SecretBytes) return false
        val left = value ?: return false
        val right = other.value ?: return false
        return MessageDigest.isEqual(left, right)
    }

    override fun hashCode(): Int = value?.contentHashCode() ?: 0

    override fun toString(): String = "SecretBytes(redacted)"
}

enum class VectorRelation {
    EQUAL,
    DOMINATES,
    IS_DOMINATED,
    CONCURRENT,
}

class VersionVector private constructor(private val values: Map<String, Long>) {
    val entries: Map<String, Long>
        get() = values

    val isEmpty: Boolean
        get() = values.isEmpty()

    operator fun get(deviceId: String): Long = values[deviceId] ?: 0L

    fun increment(deviceId: String): VersionVector {
        ProtocolValidation.requireUuid(deviceId)
        val current = this[deviceId]
        if (current == Long.MAX_VALUE) {
            throw SyncProtocolException(SyncErrorCode.LIMIT_EXCEEDED)
        }
        return of(values + (deviceId to current + 1L))
    }

    fun merge(other: VersionVector): VersionVector {
        val merged = (values.keys + other.values.keys).associateWith { deviceId ->
            maxOf(this[deviceId], other[deviceId])
        }
        return of(merged)
    }

    fun compare(other: VersionVector): VectorRelation {
        if (values == other.values) return VectorRelation.EQUAL
        val deviceIds = values.keys + other.values.keys
        val greaterOrEqual = deviceIds.all { this[it] >= other[it] }
        val lessOrEqual = deviceIds.all { this[it] <= other[it] }
        return when {
            greaterOrEqual -> VectorRelation.DOMINATES
            lessOrEqual -> VectorRelation.IS_DOMINATED
            else -> VectorRelation.CONCURRENT
        }
    }

    override fun equals(other: Any?): Boolean = other is VersionVector && values == other.values

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = "VersionVector(entries=${values.size})"

    companion object {
        fun empty(): VersionVector = VersionVector(emptyMap())

        fun of(entries: Map<String, Long>): VersionVector {
            if (entries.size > SyncProtocolLimits.DEVICES_PER_VECTOR) {
                throw IllegalArgumentException("Invalid version vector metadata.")
            }
            val sorted = TreeMap(entries)
            sorted.forEach { (deviceId, counter) ->
                ProtocolValidation.requireUuid(deviceId)
                require(counter > 0L) { "Invalid version vector metadata." }
            }
            return VersionVector(Collections.unmodifiableMap(sorted))
        }
    }
}

data class CardSyncPayload(
    val schemaVersion: Int = 1,
    val nickname: String,
    val issuerName: String,
    val cardNumber: String,
    val expiryMonth: Int,
    val expiryYear: Int,
    val saveCvv: Boolean,
    val cvv: String?,
    val cardTemplateId: String,
    val notes: String,
) {
    init {
        require(schemaVersion == 1) { "Invalid card exchange data." }
        require(nickname == nickname.trim() && nickname.codePointLength() in 1..50) {
            "Invalid card exchange data."
        }
        require(issuerName == issuerName.trim() && issuerName.codePointLength() in 1..80) {
            "Invalid card exchange data."
        }
        require(cardNumber.length in 12..19 && cardNumber.all(Char::isAsciiDigit)) {
            "Invalid card exchange data."
        }
        require(expiryMonth in 1..12 && expiryYear in 1000..9999) {
            "Invalid card exchange data."
        }
        require(
            if (saveCvv) {
                cvv?.length in 3..4 && cvv?.all(Char::isAsciiDigit) == true
            } else {
                cvv == null
            },
        ) { "Invalid card exchange data." }
        require(cardTemplateId.isNotBlank() && cardTemplateId.length <= 100) {
            "Invalid card exchange data."
        }
        require(notes.codePointLength() <= 1000) { "Invalid card exchange data." }
    }

    override fun toString(): String = "CardSyncPayload(sensitiveFields=redacted)"
}

/**
 * The decrypted address representation carried only inside an authenticated exchange package.
 * An empty country is accepted so a device can migrate an address created before the country
 * field existed. Current clients are expected to require a country for newly-created addresses.
 */
data class AddressSyncPayload(
    val schemaVersion: Int = 1,
    val nickname: String,
    val detailedAddress: String,
    val city: String,
    val other: String,
    val postalCode: String,
    val country: String,
    val cardTemplateId: String,
) {
    init {
        require(schemaVersion == 1) { "Invalid address exchange data." }
        require(nickname == nickname.trim() && nickname.codePointLength() in 1..50) {
            "Invalid address exchange data."
        }
        require(
            detailedAddress == detailedAddress.trim() &&
                detailedAddress.codePointLength() in 1..500,
        ) { "Invalid address exchange data." }
        require(city == city.trim() && city.codePointLength() in 1..100) {
            "Invalid address exchange data."
        }
        require(other == other.trim() && other.codePointLength() <= 200) {
            "Invalid address exchange data."
        }
        require(postalCode == postalCode.trim() && postalCode.codePointLength() in 1..20) {
            "Invalid address exchange data."
        }
        require(country == country.trim() && country.codePointLength() <= 100) {
            "Invalid address exchange data."
        }
        require(cardTemplateId.isNotBlank() && cardTemplateId.length <= 400) {
            "Invalid address exchange data."
        }
    }

    override fun toString(): String = "AddressSyncPayload(sensitiveFields=redacted)"
}

sealed interface SyncRecordValue {
    data class Active(
        val payload: CardSyncPayload,
        val createdAtEpochMillis: Long,
        val updatedAtEpochMillis: Long,
    ) : SyncRecordValue {
        init {
            require(createdAtEpochMillis >= 0L && updatedAtEpochMillis >= createdAtEpochMillis) {
                "Invalid card exchange metadata."
            }
        }

        override fun toString(): String = "Active(payload=redacted, createdAt=$createdAtEpochMillis, updatedAt=$updatedAtEpochMillis)"
    }

    data class Tombstone(val deletedAtEpochMillis: Long) : SyncRecordValue {
        init {
            require(deletedAtEpochMillis >= 0L) { "Invalid card exchange metadata." }
        }
    }
}

data class SyncRecord(
    val recordId: String,
    val version: VersionVector,
    val value: SyncRecordValue,
) {
    init {
        ProtocolValidation.requireUuid(recordId)
        require(!version.isEmpty) { "Invalid card exchange metadata." }
    }

    override fun toString(): String = "SyncRecord(recordId=$recordId, version=$version, value=$value)"
}

data class SyncOrder(
    val version: VersionVector,
    val updatedAtEpochMillis: Long,
    val recordIds: List<String>,
) {
    init {
        require(!version.isEmpty && updatedAtEpochMillis >= 0L) { "Invalid order metadata." }
        require(recordIds.size <= SyncProtocolLimits.ACTIVE_CARDS && recordIds.distinct().size == recordIds.size) {
            "Invalid order metadata."
        }
        recordIds.forEach(ProtocolValidation::requireUuid)
    }
}

sealed interface AddressSyncRecordValue {
    data class Active(
        val payload: AddressSyncPayload,
        val createdAtEpochMillis: Long,
        val updatedAtEpochMillis: Long,
    ) : AddressSyncRecordValue {
        init {
            require(createdAtEpochMillis >= 0L && updatedAtEpochMillis >= createdAtEpochMillis) {
                "Invalid address exchange metadata."
            }
        }

        override fun toString(): String =
            "Active(payload=redacted, createdAt=$createdAtEpochMillis, updatedAt=$updatedAtEpochMillis)"
    }

    data class Tombstone(val deletedAtEpochMillis: Long) : AddressSyncRecordValue {
        init {
            require(deletedAtEpochMillis >= 0L) { "Invalid address exchange metadata." }
        }
    }
}

data class AddressSyncRecord(
    val recordId: String,
    val version: VersionVector,
    val value: AddressSyncRecordValue,
) {
    init {
        ProtocolValidation.requireUuid(recordId)
        require(!version.isEmpty) { "Invalid address exchange metadata." }
    }

    override fun toString(): String =
        "AddressSyncRecord(recordId=$recordId, version=$version, value=$value)"
}

data class AddressSyncSnapshot(
    val records: List<AddressSyncRecord>,
    val order: SyncOrder,
) {
    init {
        require(records.size <= SyncProtocolLimits.ADDRESS_RECORDS) {
            "Invalid address snapshot metadata."
        }
        val recordIds = records.map(AddressSyncRecord::recordId)
        require(recordIds.distinct().size == recordIds.size) {
            "Invalid address snapshot metadata."
        }
        val activeIds = records
            .filter { it.value is AddressSyncRecordValue.Active }
            .map(AddressSyncRecord::recordId)
            .toSet()
        require(
            activeIds.size <= SyncProtocolLimits.ACTIVE_ADDRESSES &&
                order.recordIds.toSet() == activeIds,
        ) { "Invalid address snapshot metadata." }
    }

    override fun toString(): String =
        "AddressSyncSnapshot(records=${records.size}, active=${order.recordIds.size})"

    companion object {
        /** A present address collection with no records; its order still has causal metadata. */
        fun empty(
            orderVersion: VersionVector,
            updatedAtEpochMillis: Long = 0L,
        ): AddressSyncSnapshot = AddressSyncSnapshot(
            records = emptyList(),
            order = SyncOrder(
                version = orderVersion,
                updatedAtEpochMillis = updatedAtEpochMillis,
                recordIds = emptyList(),
            ),
        )
    }
}

data class SyncSnapshot(
    val records: List<SyncRecord>,
    val order: SyncOrder,
    /** null means a legacy v1 package that did not carry an address collection. */
    val addresses: AddressSyncSnapshot? = null,
) {
    init {
        require(records.size <= SyncProtocolLimits.RECORDS) { "Invalid snapshot metadata." }
        val recordIds = records.map(SyncRecord::recordId)
        require(recordIds.distinct().size == recordIds.size) { "Invalid snapshot metadata." }
        val activeIds = records.filter { it.value is SyncRecordValue.Active }.map(SyncRecord::recordId).toSet()
        require(activeIds.size <= SyncProtocolLimits.ACTIVE_CARDS && order.recordIds.toSet() == activeIds) {
            "Invalid snapshot metadata."
        }
    }

    override fun toString(): String =
        "SyncSnapshot(records=${records.size}, active=${order.recordIds.size}, " +
            "addresses=${addresses?.records?.size ?: "not-included"})"
}

data class PairingFilePayload(
    val packageId: String,
    val vaultId: String,
    val sourceDeviceId: String,
    val exportSequence: Long,
    val exportedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val keyEpoch: Int,
    val syncSecret: SecretBytes,
    val snapshot: SyncSnapshot,
) {
    init {
        ProtocolValidation.requireUuid(packageId)
        ProtocolValidation.requireUuid(vaultId)
        ProtocolValidation.requireUuid(sourceDeviceId)
        require(exportSequence > 0L && exportedAtEpochMillis >= 0L && expiresAtEpochMillis > exportedAtEpochMillis) {
            "Invalid pairing metadata."
        }
        require(keyEpoch > 0 && syncSecret.size == 32) { "Invalid pairing metadata." }
    }

    override fun toString(): String = "PairingFilePayload(metadata=redacted, snapshot=$snapshot)"
}

data class SyncFilePayload(
    val packageId: String,
    val vaultId: String,
    val sourceDeviceId: String,
    val exportSequence: Long,
    val exportedAtEpochMillis: Long,
    val keyEpoch: Int,
    val snapshot: SyncSnapshot,
) {
    init {
        ProtocolValidation.requireUuid(packageId)
        ProtocolValidation.requireUuid(vaultId)
        ProtocolValidation.requireUuid(sourceDeviceId)
        require(exportSequence > 0L && exportedAtEpochMillis >= 0L && keyEpoch > 0) {
            "Invalid sync metadata."
        }
    }

    override fun toString(): String = "SyncFilePayload(metadata=redacted, snapshot=$snapshot)"
}

internal object ProtocolValidation {
    fun requireUuid(value: String) {
        val parsed = runCatching { UUID.fromString(value) }.getOrNull()
        require(parsed != null && parsed.toString() == value) { "Invalid exchange metadata." }
    }
}

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

private fun String.codePointLength(): Int = codePointCount(0, length)
