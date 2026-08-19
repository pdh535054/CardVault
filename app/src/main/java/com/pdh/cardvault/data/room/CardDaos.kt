package com.pdh.cardvault.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pdh.cardvault.sync.android.StoredVersionVectors

@Dao
internal abstract class CardDao {
    @Query("SELECT * FROM cards ORDER BY sortOrder ASC")
    abstract suspend fun getAll(): List<CardEntity>

    @Query("SELECT * FROM cards WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: String): CardEntity?

    @Query("SELECT COUNT(*) FROM cards")
    abstract suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(entity: CardEntity)

    @Query("UPDATE cards SET sortOrder = sortOrder + 1")
    protected abstract suspend fun shiftAllForFrontInsert()

    @Query(
        """
        UPDATE cards
        SET ciphertext = :ciphertext,
            recordIv = :recordIv,
            payloadSchemaVersion = :payloadSchemaVersion,
            cryptoVersion = :cryptoVersion,
            updatedAt = :updatedAt,
            versionVector = :versionVector
        WHERE id = :id
        """,
    )
    protected abstract suspend fun updateEncryptedPayload(
        id: String,
        ciphertext: ByteArray,
        recordIv: ByteArray,
        payloadSchemaVersion: Int,
        cryptoVersion: Int,
        updatedAt: Long,
        versionVector: ByteArray,
    ): Int

    @Transaction
    open suspend fun updateEncryptedPayloadAtomically(
        id: String,
        ciphertext: ByteArray,
        recordIv: ByteArray,
        payloadSchemaVersion: Int,
        cryptoVersion: Int,
        proposedUpdatedAt: Long,
        localDeviceId: String,
    ): CardEntity? {
        val existing = getById(id) ?: return null
        if (existing.updatedAt == Long.MAX_VALUE) throw DatabaseInvariantException()
        val updatedAt = maxOf(proposedUpdatedAt.coerceAtLeast(0L), existing.updatedAt + 1L)
        val versionVector = StoredVersionVectors.increment(
            encoded = existing.versionVector,
            deviceId = localDeviceId,
        )
        if (
            updateEncryptedPayload(
                id = id,
                ciphertext = ciphertext,
                recordIv = recordIv,
                payloadSchemaVersion = payloadSchemaVersion,
                cryptoVersion = cryptoVersion,
                updatedAt = updatedAt,
                versionVector = versionVector,
            ) != 1
        ) {
            throw DatabaseInvariantException()
        }
        return existing.copy(
            ciphertext = ciphertext.copyOf(),
            recordIv = recordIv.copyOf(),
            payloadSchemaVersion = payloadSchemaVersion,
            cryptoVersion = cryptoVersion,
            updatedAt = updatedAt,
            versionVector = versionVector,
        )
    }

    @Query("DELETE FROM cards WHERE id = :id")
    protected abstract suspend fun deleteById(id: String): Int

    @Query("UPDATE cards SET sortOrder = sortOrder - 1 WHERE sortOrder > :deletedOrder")
    protected abstract suspend fun compactAfterDelete(deletedOrder: Int)

    @Query("UPDATE cards SET sortOrder = :sortOrder WHERE id = :id")
    protected abstract suspend fun updateSortOrder(id: String, sortOrder: Int): Int

    @Query("SELECT * FROM sync_order_state WHERE singletonId = 1 LIMIT 1")
    protected abstract suspend fun getOrderState(): SyncOrderStateEntity?

    @Query(
        """
        UPDATE sync_order_state
        SET versionVector = :versionVector,
            updatedAt = :updatedAt
        WHERE singletonId = 1
        """,
    )
    protected abstract suspend fun updateOrderState(
        versionVector: ByteArray,
        updatedAt: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertTombstone(entity: SyncTombstoneEntity)

    @Transaction
    open suspend fun insertAtFront(
        entity: CardEntity,
        localDeviceId: String,
        proposedUpdatedAt: Long,
    ) {
        val current = getAll()
        current.requireContinuousOrder()
        if (entity.sortOrder != 0) throw DatabaseInvariantException()
        shiftAllForFrontInsert()
        insert(
            entity.copy(
                versionVector = StoredVersionVectors.initial(localDeviceId),
            ),
        )
        touchOrder(localDeviceId, proposedUpdatedAt)
    }

    @Transaction
    open suspend fun deleteAndCompact(
        id: String,
        localDeviceId: String,
        proposedDeletedAt: Long,
    ): Boolean {
        val current = getAll()
        current.requireContinuousOrder()
        val target = current.firstOrNull { entity -> entity.id == id } ?: return false
        val deletedAt = maxOf(proposedDeletedAt.coerceAtLeast(0L), target.updatedAt)
        insertTombstone(
            SyncTombstoneEntity(
                recordId = target.id,
                versionVector = StoredVersionVectors.increment(
                    encoded = target.versionVector,
                    deviceId = localDeviceId,
                ),
                deletedAt = deletedAt,
            ),
        )
        if (deleteById(id) != 1) throw DatabaseInvariantException()
        compactAfterDelete(target.sortOrder)
        touchOrder(localDeviceId, deletedAt)
        return true
    }

    @Transaction
    open suspend fun reorderAtomically(
        orderedIds: List<String>,
        localDeviceId: String,
        proposedUpdatedAt: Long,
    ) {
        val current = getAll()
        current.requireContinuousOrder()
        val currentIds = current.map(CardEntity::id)
        if (
            orderedIds.size != currentIds.size ||
            orderedIds.toSet().size != orderedIds.size ||
            orderedIds.toSet() != currentIds.toSet()
        ) {
            throw DatabaseInvariantException()
        }
        orderedIds.forEachIndexed { index, id ->
            if (updateSortOrder(id, index) != 1) throw DatabaseInvariantException()
        }
        if (orderedIds != currentIds) {
            touchOrder(localDeviceId, proposedUpdatedAt)
        }
    }

    private suspend fun touchOrder(localDeviceId: String, proposedUpdatedAt: Long) {
        val order = getOrderState() ?: throw DatabaseInvariantException()
        if (order.updatedAt == Long.MAX_VALUE) throw DatabaseInvariantException()
        val updatedAt = maxOf(proposedUpdatedAt.coerceAtLeast(0L), order.updatedAt + 1L)
        val nextVector = StoredVersionVectors.increment(order.versionVector, localDeviceId)
        if (updateOrderState(nextVector, updatedAt) != 1) {
            throw DatabaseInvariantException()
        }
    }
}

@Dao
internal abstract class AddressDao {
    @Query("SELECT * FROM addresses ORDER BY sortOrder ASC")
    abstract suspend fun getAll(): List<AddressEntity>

    @Query("SELECT * FROM addresses WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: String): AddressEntity?

    @Query("SELECT COUNT(*) FROM addresses")
    abstract suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(entity: AddressEntity)

    @Query("UPDATE addresses SET sortOrder = sortOrder + 1")
    protected abstract suspend fun shiftAllForFrontInsert()

    @Query(
        """
        UPDATE addresses
        SET ciphertext = :ciphertext,
            recordIv = :recordIv,
            payloadSchemaVersion = :payloadSchemaVersion,
            cryptoVersion = :cryptoVersion,
            updatedAt = :updatedAt,
            versionVector = :versionVector
        WHERE id = :id
        """,
    )
    protected abstract suspend fun updateEncryptedPayload(
        id: String,
        ciphertext: ByteArray,
        recordIv: ByteArray,
        payloadSchemaVersion: Int,
        cryptoVersion: Int,
        updatedAt: Long,
        versionVector: ByteArray,
    ): Int

    @Query("DELETE FROM addresses WHERE id = :id")
    protected abstract suspend fun deleteById(id: String): Int

    @Query("UPDATE addresses SET sortOrder = sortOrder - 1 WHERE sortOrder > :deletedOrder")
    protected abstract suspend fun compactAfterDelete(deletedOrder: Int)

    @Query("UPDATE addresses SET sortOrder = :sortOrder WHERE id = :id")
    protected abstract suspend fun updateSortOrder(id: String, sortOrder: Int): Int

    @Query("SELECT * FROM address_sync_order_state WHERE singletonId = 1 LIMIT 1")
    protected abstract suspend fun getAddressOrderState(): AddressSyncOrderStateEntity?

    @Query(
        """
        UPDATE address_sync_order_state
        SET versionVector = :versionVector,
            updatedAt = :updatedAt
        WHERE singletonId = 1
        """,
    )
    protected abstract suspend fun updateAddressOrderState(
        versionVector: ByteArray,
        updatedAt: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAddressTombstone(entity: AddressSyncTombstoneEntity)

    @Transaction
    open suspend fun insertAtFront(
        entity: AddressEntity,
        localDeviceId: String,
        proposedUpdatedAt: Long,
    ) {
        val current = getAll()
        current.requireContinuousAddressOrder()
        if (entity.sortOrder != 0) throw DatabaseInvariantException()
        shiftAllForFrontInsert()
        insert(
            entity.copy(
                versionVector = StoredVersionVectors.initial(localDeviceId),
            ),
        )
        touchAddressOrder(localDeviceId, proposedUpdatedAt)
    }

    @Transaction
    open suspend fun updateEncryptedPayloadAtomically(
        id: String,
        ciphertext: ByteArray,
        recordIv: ByteArray,
        payloadSchemaVersion: Int,
        cryptoVersion: Int,
        proposedUpdatedAt: Long,
        localDeviceId: String,
    ): AddressEntity? {
        val existing = getById(id) ?: return null
        if (existing.updatedAt == Long.MAX_VALUE) throw DatabaseInvariantException()
        val updatedAt = maxOf(proposedUpdatedAt.coerceAtLeast(0L), existing.updatedAt + 1L)
        val versionVector = StoredVersionVectors.increment(
            encoded = existing.versionVector,
            deviceId = localDeviceId,
        )
        if (
            updateEncryptedPayload(
                id = id,
                ciphertext = ciphertext,
                recordIv = recordIv,
                payloadSchemaVersion = payloadSchemaVersion,
                cryptoVersion = cryptoVersion,
                updatedAt = updatedAt,
                versionVector = versionVector,
            ) != 1
        ) {
            throw DatabaseInvariantException()
        }
        return existing.copy(
            ciphertext = ciphertext.copyOf(),
            recordIv = recordIv.copyOf(),
            payloadSchemaVersion = payloadSchemaVersion,
            cryptoVersion = cryptoVersion,
            updatedAt = updatedAt,
            versionVector = versionVector,
        )
    }

    @Transaction
    open suspend fun deleteAndCompact(
        id: String,
        localDeviceId: String,
        proposedDeletedAt: Long,
    ): Boolean {
        val current = getAll()
        current.requireContinuousAddressOrder()
        val target = current.firstOrNull { entity -> entity.id == id } ?: return false
        val deletedAt = maxOf(proposedDeletedAt.coerceAtLeast(0L), target.updatedAt)
        insertAddressTombstone(
            AddressSyncTombstoneEntity(
                recordId = target.id,
                versionVector = StoredVersionVectors.increment(
                    encoded = target.versionVector,
                    deviceId = localDeviceId,
                ),
                deletedAt = deletedAt,
            ),
        )
        if (deleteById(id) != 1) throw DatabaseInvariantException()
        compactAfterDelete(target.sortOrder)
        touchAddressOrder(localDeviceId, deletedAt)
        return true
    }

    @Transaction
    open suspend fun reorderAtomically(
        orderedIds: List<String>,
        localDeviceId: String,
        proposedUpdatedAt: Long,
    ) {
        val current = getAll()
        current.requireContinuousAddressOrder()
        val currentIds = current.map(AddressEntity::id)
        if (
            orderedIds.size != currentIds.size ||
            orderedIds.toSet().size != orderedIds.size ||
            orderedIds.toSet() != currentIds.toSet()
        ) {
            throw DatabaseInvariantException()
        }
        orderedIds.forEachIndexed { index, id ->
            if (updateSortOrder(id, index) != 1) throw DatabaseInvariantException()
        }
        if (orderedIds != currentIds) {
            touchAddressOrder(localDeviceId, proposedUpdatedAt)
        }
    }

    private suspend fun touchAddressOrder(localDeviceId: String, proposedUpdatedAt: Long) {
        val order = getAddressOrderState() ?: throw DatabaseInvariantException()
        if (order.updatedAt == Long.MAX_VALUE) throw DatabaseInvariantException()
        val updatedAt = maxOf(proposedUpdatedAt.coerceAtLeast(0L), order.updatedAt + 1L)
        val nextVector = StoredVersionVectors.increment(order.versionVector, localDeviceId)
        if (updateAddressOrderState(nextVector, updatedAt) != 1) {
            throw DatabaseInvariantException()
        }
    }
}

private fun List<AddressEntity>.requireContinuousAddressOrder() {
    if (map(AddressEntity::sortOrder) != indices.toList()) throw DatabaseInvariantException()
}

@Dao
internal abstract class VaultMetadataDao {
    @Query("SELECT * FROM vault_metadata")
    abstract suspend fun getAll(): List<VaultMetadataEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(metadata: VaultMetadataEntity)

    @Transaction
    open suspend fun insertInitial(metadata: VaultMetadataEntity) {
        if (getAll().isNotEmpty()) throw DatabaseInvariantException()
        insert(metadata)
    }
}

@Dao
internal abstract class SyncStateDao {
    @Query("SELECT * FROM sync_vault_state")
    abstract suspend fun getAllVaultStates(): List<SyncVaultStateEntity>

    @Query("SELECT * FROM sync_vault_state WHERE singletonId = 1 LIMIT 1")
    abstract suspend fun getVaultState(): SyncVaultStateEntity?

    @Query("SELECT * FROM sync_order_state WHERE singletonId = 1 LIMIT 1")
    abstract suspend fun getOrderState(): SyncOrderStateEntity?

    @Query("SELECT * FROM sync_tombstones ORDER BY recordId ASC")
    abstract suspend fun getTombstones(): List<SyncTombstoneEntity>

    @Query("SELECT * FROM address_sync_order_state WHERE singletonId = 1 LIMIT 1")
    abstract suspend fun getAddressOrderState(): AddressSyncOrderStateEntity?

    @Query("SELECT * FROM address_sync_tombstones ORDER BY recordId ASC")
    abstract suspend fun getAddressTombstones(): List<AddressSyncTombstoneEntity>

    @Query(
        """
        SELECT * FROM imported_sync_packages
        ORDER BY importedAt DESC, packageId DESC
        LIMIT 1024
        """,
    )
    abstract suspend fun getRecentImportedPackages(): List<ImportedSyncPackageEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM imported_sync_packages WHERE packageId = :packageId)")
    abstract suspend fun hasImportedPackage(packageId: String): Boolean

    @Query(
        """
        SELECT highestSequence
        FROM sync_source_sequences
        WHERE sourceDeviceId = :sourceDeviceId
        LIMIT 1
        """,
    )
    abstract suspend fun maxImportedSequence(sourceDeviceId: String): Long?

    @Query("SELECT COUNT(*) FROM sync_source_sequences")
    protected abstract suspend fun sourceSequenceCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertVaultState(entity: SyncVaultStateEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertOrderState(entity: SyncOrderStateEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAddressOrderState(entity: AddressSyncOrderStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun replaceVaultState(entity: SyncVaultStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun replaceOrderState(entity: SyncOrderStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun replaceAddressOrderState(entity: AddressSyncOrderStateEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertImportedPackage(entity: ImportedSyncPackageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertSourceSequence(entity: SyncSourceSequenceEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertCards(entities: List<CardEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertTombstones(entities: List<SyncTombstoneEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAddresses(entities: List<AddressEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAddressTombstones(
        entities: List<AddressSyncTombstoneEntity>,
    )

    @Query("DELETE FROM cards")
    protected abstract suspend fun deleteAllCards()

    @Query("DELETE FROM sync_tombstones")
    protected abstract suspend fun deleteAllTombstones()

    @Query("DELETE FROM addresses")
    protected abstract suspend fun deleteAllAddresses()

    @Query("DELETE FROM address_sync_tombstones")
    protected abstract suspend fun deleteAllAddressTombstones()

    @Query(
        """
        DELETE FROM imported_sync_packages
        WHERE packageId NOT IN (
            SELECT packageId
            FROM imported_sync_packages
            ORDER BY importedAt DESC, packageId DESC
            LIMIT 1024
        )
        """,
    )
    protected abstract suspend fun trimImportedPackages()

    @Query(
        """
        UPDATE cards
        SET versionVector = :initialVersion
        WHERE length(versionVector) = 0
        """,
    )
    protected abstract suspend fun initializeMissingCardVersions(initialVersion: ByteArray): Int

    @Query(
        """
        UPDATE addresses
        SET versionVector = :initialVersion
        WHERE length(versionVector) = 0
        """,
    )
    protected abstract suspend fun initializeMissingAddressVersions(initialVersion: ByteArray): Int

    @Transaction
    open suspend fun initializeIfNeeded(
        proposedVaultState: SyncVaultStateEntity,
        proposedOrderState: SyncOrderStateEntity,
        proposedAddressOrderState: AddressSyncOrderStateEntity,
    ): SyncVaultStateEntity {
        val rows = getAllVaultStates()
        if (rows.size > 1) throw DatabaseInvariantException()
        val state = rows.singleOrNull() ?: proposedVaultState.also {
            insertVaultState(it)
        }
        val initialVersion = StoredVersionVectors.initial(state.deviceId)
        initializeMissingCardVersions(initialVersion)
        initializeMissingAddressVersions(initialVersion)
        when (val order = getOrderState()) {
            null -> insertOrderState(
                proposedOrderState.copy(
                    versionVector = initialVersion.copyOf(),
                ),
            )
            else -> if (order.versionVector.isEmpty()) {
                replaceOrderState(order.copy(versionVector = initialVersion.copyOf()))
            }
        }
        when (val order = getAddressOrderState()) {
            null -> insertAddressOrderState(
                proposedAddressOrderState.copy(
                    versionVector = initialVersion.copyOf(),
                ),
            )
            else -> if (order.versionVector.isEmpty()) {
                replaceAddressOrderState(order.copy(versionVector = initialVersion.copyOf()))
            }
        }
        return state
    }

    @Transaction
    open suspend fun nextExportSequence(proposedUpdatedAt: Long): SyncVaultStateEntity {
        val state = getVaultState() ?: throw DatabaseInvariantException()
        if (state.exportSequence == Long.MAX_VALUE || state.updatedAt == Long.MAX_VALUE) {
            throw DatabaseInvariantException()
        }
        val updated = state.copy(
            exportSequence = state.exportSequence + 1L,
            updatedAt = maxOf(proposedUpdatedAt.coerceAtLeast(0L), state.updatedAt + 1L),
        )
        replaceVaultState(updated)
        return updated
    }

    @Transaction
    open suspend fun storeInitialSyncKey(
        expectedVaultId: String,
        keyEpoch: Int,
        encryptedSyncKey: ByteArray,
        syncKeyIv: ByteArray,
        syncKeyCryptoVersion: Int,
        proposedUpdatedAt: Long,
    ): SyncVaultStateEntity {
        val state = getVaultState() ?: throw DatabaseInvariantException()
        if (state.vaultId != expectedVaultId) throw DatabaseInvariantException()
        if (state.encryptedSyncKey != null || state.syncKeyIv != null) return state
        if (
            keyEpoch <= 0 ||
            encryptedSyncKey.isEmpty() ||
            syncKeyIv.isEmpty() ||
            syncKeyCryptoVersion <= 0 ||
            state.updatedAt == Long.MAX_VALUE
        ) {
            throw DatabaseInvariantException()
        }
        val updated = state.copy(
            keyEpoch = keyEpoch,
            encryptedSyncKey = encryptedSyncKey.copyOf(),
            syncKeyIv = syncKeyIv.copyOf(),
            syncKeyCryptoVersion = syncKeyCryptoVersion,
            updatedAt = maxOf(proposedUpdatedAt.coerceAtLeast(0L), state.updatedAt + 1L),
        )
        replaceVaultState(updated)
        return updated
    }

    @Transaction
    open suspend fun replaceSnapshotAtomically(
        cards: List<CardEntity>,
        tombstones: List<SyncTombstoneEntity>,
        order: SyncOrderStateEntity,
        addresses: List<AddressEntity>,
        addressTombstones: List<AddressSyncTombstoneEntity>,
        addressOrder: AddressSyncOrderStateEntity,
        vaultState: SyncVaultStateEntity,
        importedPackage: ImportedSyncPackageEntity,
    ) {
        val currentVaultState = getVaultState() ?: throw DatabaseInvariantException()
        cards.requireContinuousOrder()
        addresses.requireContinuousAddressOrder()
        val activeIds = cards.map(CardEntity::id)
        val tombstoneIds = tombstones.map(SyncTombstoneEntity::recordId)
        val activeAddressIds = addresses.map(AddressEntity::id)
        val addressTombstoneIds = addressTombstones.map(AddressSyncTombstoneEntity::recordId)
        if (
            activeIds.toSet().size != activeIds.size ||
            tombstoneIds.toSet().size != tombstoneIds.size ||
            activeIds.toSet().intersect(tombstoneIds.toSet()).isNotEmpty() ||
            cards.any { it.versionVector.isEmpty() } ||
            tombstones.any { it.versionVector.isEmpty() } ||
            order.versionVector.isEmpty() ||
            activeAddressIds.toSet().size != activeAddressIds.size ||
            addressTombstoneIds.toSet().size != addressTombstoneIds.size ||
            activeAddressIds.toSet().intersect(addressTombstoneIds.toSet()).isNotEmpty() ||
            addresses.any { it.versionVector.isEmpty() } ||
            addressTombstones.any { it.versionVector.isEmpty() } ||
            addressOrder.versionVector.isEmpty() ||
            vaultState.deviceId != currentVaultState.deviceId
        ) {
            throw DatabaseInvariantException()
        }
        if (hasImportedPackage(importedPackage.packageId)) {
            throw DuplicateSyncPackageException()
        }
        val highestSequence = maxImportedSequence(importedPackage.sourceDeviceId) ?: 0L
        if (importedPackage.exportSequence <= highestSequence) {
            throw StaleSyncPackageException()
        }
        if (highestSequence == 0L && sourceSequenceCount() >= MAX_SYNC_SOURCES) {
            throw SyncSourceLimitException()
        }

        deleteAllCards()
        deleteAllTombstones()
        deleteAllAddresses()
        deleteAllAddressTombstones()
        if (cards.isNotEmpty()) insertCards(cards)
        if (tombstones.isNotEmpty()) insertTombstones(tombstones)
        if (addresses.isNotEmpty()) insertAddresses(addresses)
        if (addressTombstones.isNotEmpty()) insertAddressTombstones(addressTombstones)
        replaceOrderState(order)
        replaceAddressOrderState(addressOrder)
        replaceVaultState(vaultState)
        insertImportedPackage(importedPackage)
        upsertSourceSequence(
            SyncSourceSequenceEntity(
                sourceDeviceId = importedPackage.sourceDeviceId,
                highestSequence = importedPackage.exportSequence,
            ),
        )
        trimImportedPackages()
    }
}

internal class DuplicateSyncPackageException : IllegalStateException(
    "The synchronization package was already imported.",
)

internal class StaleSyncPackageException : IllegalStateException(
    "The synchronization package is stale.",
)

internal class SyncSourceLimitException : IllegalStateException(
    "The synchronization source limit was reached.",
)

private const val MAX_SYNC_SOURCES = 16

private fun List<CardEntity>.requireContinuousOrder() {
    if (map(CardEntity::sortOrder) != indices.toList()) throw DatabaseInvariantException()
}
