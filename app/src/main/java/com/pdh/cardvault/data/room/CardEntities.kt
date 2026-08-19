package com.pdh.cardvault.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = CardEntity.TABLE_NAME)
internal data class CardEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "ciphertext", typeAffinity = ColumnInfo.BLOB)
    val ciphertext: ByteArray,
    @ColumnInfo(name = "recordIv", typeAffinity = ColumnInfo.BLOB)
    val recordIv: ByteArray,
    @ColumnInfo(name = "payloadSchemaVersion")
    val payloadSchemaVersion: Int,
    @ColumnInfo(name = "cryptoVersion")
    val cryptoVersion: Int,
    @ColumnInfo(name = "sortOrder")
    val sortOrder: Int,
    @ColumnInfo(name = "createdAt")
    val createdAt: Long,
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long,
    @ColumnInfo(name = "versionVector", typeAffinity = ColumnInfo.BLOB, defaultValue = "X''")
    val versionVector: ByteArray = ByteArray(0),
) {
    override fun toString(): String = "CardEntity(metadata=redacted)"

    companion object {
        const val TABLE_NAME = "cards"
    }
}

@Entity(tableName = AddressEntity.TABLE_NAME)
internal data class AddressEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "ciphertext", typeAffinity = ColumnInfo.BLOB)
    val ciphertext: ByteArray,
    @ColumnInfo(name = "recordIv", typeAffinity = ColumnInfo.BLOB)
    val recordIv: ByteArray,
    @ColumnInfo(name = "payloadSchemaVersion")
    val payloadSchemaVersion: Int,
    @ColumnInfo(name = "cryptoVersion")
    val cryptoVersion: Int,
    @ColumnInfo(name = "sortOrder")
    val sortOrder: Int,
    @ColumnInfo(name = "createdAt")
    val createdAt: Long,
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long,
    @ColumnInfo(name = "versionVector", typeAffinity = ColumnInfo.BLOB, defaultValue = "X''")
    val versionVector: ByteArray = ByteArray(0),
) {
    override fun toString(): String = "AddressEntity(metadata=redacted)"

    companion object {
        const val TABLE_NAME = "addresses"
    }
}

@Entity(tableName = AddressSyncOrderStateEntity.TABLE_NAME)
internal data class AddressSyncOrderStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "singletonId")
    val singletonId: Int = SINGLETON_ID,
    @ColumnInfo(name = "versionVector", typeAffinity = ColumnInfo.BLOB)
    val versionVector: ByteArray,
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long,
) {
    override fun toString(): String = "AddressSyncOrderStateEntity(metadata=redacted)"

    companion object {
        const val TABLE_NAME = "address_sync_order_state"
        const val SINGLETON_ID = 1
    }
}

@Entity(tableName = AddressSyncTombstoneEntity.TABLE_NAME)
internal data class AddressSyncTombstoneEntity(
    @PrimaryKey
    @ColumnInfo(name = "recordId")
    val recordId: String,
    @ColumnInfo(name = "versionVector", typeAffinity = ColumnInfo.BLOB)
    val versionVector: ByteArray,
    @ColumnInfo(name = "deletedAt")
    val deletedAt: Long,
) {
    override fun toString(): String = "AddressSyncTombstoneEntity(metadata=redacted)"

    companion object {
        const val TABLE_NAME = "address_sync_tombstones"
    }
}

/**
 * Non-secret synchronization identity and locally encrypted shared-sync-key envelope.
 *
 * [encryptedSyncKey] is encrypted with the foreground-only local DEK before it reaches Room.
 * Neither the Android Keystore KEK nor the local DEK is ever stored or exported here.
 */
@Entity(tableName = SyncVaultStateEntity.TABLE_NAME)
internal data class SyncVaultStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "singletonId")
    val singletonId: Int = SINGLETON_ID,
    @ColumnInfo(name = "vaultId")
    val vaultId: String,
    @ColumnInfo(name = "deviceId")
    val deviceId: String,
    @ColumnInfo(name = "keyEpoch")
    val keyEpoch: Int,
    @ColumnInfo(name = "encryptedSyncKey", typeAffinity = ColumnInfo.BLOB)
    val encryptedSyncKey: ByteArray?,
    @ColumnInfo(name = "syncKeyIv", typeAffinity = ColumnInfo.BLOB)
    val syncKeyIv: ByteArray?,
    @ColumnInfo(name = "syncKeyCryptoVersion")
    val syncKeyCryptoVersion: Int,
    @ColumnInfo(name = "exportSequence")
    val exportSequence: Long,
    @ColumnInfo(name = "createdAt")
    val createdAt: Long,
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long,
) {
    override fun toString(): String = "SyncVaultStateEntity(keyMaterial=redacted)"

    companion object {
        const val TABLE_NAME = "sync_vault_state"
        const val SINGLETON_ID = 1
    }
}

@Entity(tableName = SyncOrderStateEntity.TABLE_NAME)
internal data class SyncOrderStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "singletonId")
    val singletonId: Int = SINGLETON_ID,
    @ColumnInfo(name = "versionVector", typeAffinity = ColumnInfo.BLOB)
    val versionVector: ByteArray,
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long,
) {
    override fun toString(): String = "SyncOrderStateEntity(metadata=redacted)"

    companion object {
        const val TABLE_NAME = "sync_order_state"
        const val SINGLETON_ID = 1
    }
}

@Entity(tableName = SyncTombstoneEntity.TABLE_NAME)
internal data class SyncTombstoneEntity(
    @PrimaryKey
    @ColumnInfo(name = "recordId")
    val recordId: String,
    @ColumnInfo(name = "versionVector", typeAffinity = ColumnInfo.BLOB)
    val versionVector: ByteArray,
    @ColumnInfo(name = "deletedAt")
    val deletedAt: Long,
) {
    override fun toString(): String = "SyncTombstoneEntity(metadata=redacted)"

    companion object {
        const val TABLE_NAME = "sync_tombstones"
    }
}

@Entity(tableName = ImportedSyncPackageEntity.TABLE_NAME)
internal data class ImportedSyncPackageEntity(
    @PrimaryKey
    @ColumnInfo(name = "packageId")
    val packageId: String,
    @ColumnInfo(name = "sourceDeviceId")
    val sourceDeviceId: String,
    @ColumnInfo(name = "exportSequence")
    val exportSequence: Long,
    @ColumnInfo(name = "importedAt")
    val importedAt: Long,
) {
    override fun toString(): String = "ImportedSyncPackageEntity(metadata=redacted)"

    companion object {
        const val TABLE_NAME = "imported_sync_packages"
    }
}

@Entity(tableName = SyncSourceSequenceEntity.TABLE_NAME)
internal data class SyncSourceSequenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "sourceDeviceId")
    val sourceDeviceId: String,
    @ColumnInfo(name = "highestSequence")
    val highestSequence: Long,
) {
    override fun toString(): String = "SyncSourceSequenceEntity(metadata=redacted)"

    companion object {
        const val TABLE_NAME = "sync_source_sequences"
    }
}

@Entity(tableName = VaultMetadataEntity.TABLE_NAME)
internal data class VaultMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "kekAliasVersion")
    val kekAliasVersion: Int,
    @ColumnInfo(name = "wrappedDek", typeAffinity = ColumnInfo.BLOB)
    val wrappedDek: ByteArray,
    @ColumnInfo(name = "wrappingIv", typeAffinity = ColumnInfo.BLOB)
    val wrappingIv: ByteArray,
    @ColumnInfo(name = "wrappingFormatVersion")
    val wrappingFormatVersion: Int,
    @ColumnInfo(name = "createdAt")
    val createdAt: Long,
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long,
) {
    override fun toString(): String = "VaultMetadataEntity(metadata=redacted)"

    companion object {
        const val TABLE_NAME = "vault_metadata"
    }
}

internal class DatabaseInvariantException : IllegalStateException(
    "The encrypted database invariant is invalid.",
)
