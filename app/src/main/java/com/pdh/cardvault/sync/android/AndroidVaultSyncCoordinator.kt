package com.pdh.cardvault.sync.android

import com.pdh.cardvault.data.room.CardDao
import com.pdh.cardvault.data.room.CardEntity
import com.pdh.cardvault.data.room.AddressDao
import com.pdh.cardvault.data.room.AddressEntity
import com.pdh.cardvault.data.room.AddressSyncOrderStateEntity
import com.pdh.cardvault.data.room.AddressSyncTombstoneEntity
import com.pdh.cardvault.data.room.DatabaseInvariantException
import com.pdh.cardvault.data.room.DuplicateSyncPackageException
import com.pdh.cardvault.data.room.EncryptedRoomBankCardRepository
import com.pdh.cardvault.data.room.ImportedSyncPackageEntity
import com.pdh.cardvault.data.room.StaleSyncPackageException
import com.pdh.cardvault.data.room.SyncOrderStateEntity
import com.pdh.cardvault.data.room.SyncSourceLimitException
import com.pdh.cardvault.data.room.SyncStateDao
import com.pdh.cardvault.data.room.SyncTombstoneEntity
import com.pdh.cardvault.data.room.SyncVaultStateEntity
import com.pdh.cardvault.data.room.VaultFolderDao
import com.pdh.cardvault.data.room.VaultFolderEntity
import com.pdh.cardvault.data.room.FolderSyncTombstoneEntity
import com.pdh.cardvault.domain.model.BankCardInput
import com.pdh.cardvault.domain.model.AddressInput
import com.pdh.cardvault.domain.validation.BankCardValidator
import com.pdh.cardvault.domain.validation.AddressValidationError
import com.pdh.cardvault.domain.validation.AddressValidator
import com.pdh.cardvault.security.crypto.AddressPayload
import com.pdh.cardvault.security.crypto.AddressRecordCryptor
import com.pdh.cardvault.security.crypto.EncryptedAddressRecord
import com.pdh.cardvault.security.crypto.CardPayload
import com.pdh.cardvault.security.crypto.CardRecordCryptor
import com.pdh.cardvault.security.crypto.EncryptedCardRecord
import com.pdh.cardvault.security.crypto.EncryptedVaultFolderRecord
import com.pdh.cardvault.security.crypto.VaultFolderCollection
import com.pdh.cardvault.security.crypto.VaultFolderPayload
import com.pdh.cardvault.security.crypto.VaultFolderRecordCryptor
import com.pdh.cardvault.sync.CardSyncPayload
import com.pdh.cardvault.sync.AddressSyncPayload
import com.pdh.cardvault.sync.AddressSyncRecord
import com.pdh.cardvault.sync.AddressSyncRecordValue
import com.pdh.cardvault.sync.AddressSyncSnapshot
import com.pdh.cardvault.sync.CardVaultSyncFiles
import com.pdh.cardvault.sync.MergeConflict
import com.pdh.cardvault.sync.PairingCode
import com.pdh.cardvault.sync.PairingFilePayload
import com.pdh.cardvault.sync.ReplayMetadata
import com.pdh.cardvault.sync.ReplayProtector
import com.pdh.cardvault.sync.SecretBytes
import com.pdh.cardvault.sync.SnapshotMerger
import com.pdh.cardvault.sync.SyncErrorCode
import com.pdh.cardvault.sync.SyncFilePayload
import com.pdh.cardvault.sync.SyncFileKind
import com.pdh.cardvault.sync.SyncOrder
import com.pdh.cardvault.sync.SyncProtocolException
import com.pdh.cardvault.sync.SyncRecord
import com.pdh.cardvault.sync.SyncRecordValue
import com.pdh.cardvault.sync.SyncSnapshot
import com.pdh.cardvault.sync.FolderCollectionKind
import com.pdh.cardvault.sync.FolderSyncPayload
import com.pdh.cardvault.sync.FolderSyncRecord
import com.pdh.cardvault.sync.FolderSyncRecordValue
import com.pdh.cardvault.sync.FolderSyncSnapshot
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class SyncPairingStatus(
    val isPaired: Boolean,
    val keyEpoch: Int?,
)

internal class PairingExport(
    val fileName: String,
    val displayCode: String,
    fileBytes: ByteArray,
) {
    private val encryptedFile = fileBytes.copyOf()

    fun fileBytesCopy(): ByteArray = encryptedFile.copyOf()

    override fun toString(): String = "PairingExport(contents=redacted)"
}

internal class SyncExport(
    val fileName: String,
    fileBytes: ByteArray,
) {
    private val encryptedFile = fileBytes.copyOf()

    fun fileBytesCopy(): ByteArray = encryptedFile.copyOf()

    override fun toString(): String = "SyncExport(contents=redacted)"
}

internal data class SyncImportSummary(
    val isPairingFile: Boolean,
    val activeCards: Int,
    val tombstones: Int,
    val activeAddresses: Int = 0,
    val addressTombstones: Int = 0,
    val exportedAtEpochMillis: Long,
)

internal data class SyncImportResult(
    val activeCards: Int,
    val tombstones: Int,
    val conflicts: List<MergeConflict>,
    val activeAddresses: Int = 0,
    val addressTombstones: Int = 0,
)

internal class SyncNotPairedException : IllegalStateException(
    "This CardVault installation has not been paired.",
)

/**
 * Android adapter for the platform-neutral encrypted exchange protocol.
 *
 * All methods that touch card plaintext execute inside [EncryptedRoomBankCardRepository]'s
 * foreground DEK closure. Only encrypted .cvpair/.cvsync bytes cross this boundary.
 */
internal class AndroidVaultSyncCoordinator(
    private val cardDao: CardDao,
    private val addressDao: AddressDao,
    private val folderDao: VaultFolderDao,
    private val syncStateDao: SyncStateDao,
    private val repository: EncryptedRoomBankCardRepository,
    private val validator: BankCardValidator,
    private val addressValidator: AddressValidator,
    private val recordCryptor: CardRecordCryptor = CardRecordCryptor(),
    private val addressRecordCryptor: AddressRecordCryptor = AddressRecordCryptor(),
    private val folderRecordCryptor: VaultFolderRecordCryptor = VaultFolderRecordCryptor(),
    private val syncKeyCryptor: SyncKeyCryptor = SyncKeyCryptor(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val operationMutex = Mutex()

    /** Reads only the authenticated-file public header; no card payload is decrypted here. */
    fun inspectKind(encryptedFile: ByteArray): SyncFileKind =
        CardVaultSyncFiles.inspectKind(encryptedFile)

    suspend fun pairingStatus(): SyncPairingStatus = operationMutex.withLock {
        val state = requireVaultState()
        val paired = state.encryptedSyncKey != null && state.syncKeyIv != null
        SyncPairingStatus(
            isPaired = paired,
            keyEpoch = state.keyEpoch.takeIf { paired },
        )
    }

    suspend fun createPairingExport(
        validityMillis: Long = DEFAULT_PAIRING_VALIDITY_MILLIS,
    ): PairingExport = operationMutex.withLock {
        requirePairingValidity(validityMillis)
        repository.withUnlockedDekForSync { localDek ->
            val syncKey = getOrCreateSyncKey(localDek)
            try {
                createPairingExport(localDek, syncKey, validityMillis)
            } finally {
                syncKey.fill(0)
            }
        }
    }

    /**
     * Revokes the current synchronization relationship, advances the key epoch, and returns a
     * fresh pairing package. Card/address ciphertext and saved CVV values are not modified.
     */
    suspend fun rotateSyncKeyAndCreatePairingExport(
        validityMillis: Long = DEFAULT_PAIRING_VALIDITY_MILLIS,
    ): PairingExport = operationMutex.withLock {
        requirePairingValidity(validityMillis)
        repository.withUnlockedDekForSync { localDek ->
            val current = requirePairedState()
            if (current.keyEpoch == Int.MAX_VALUE) throw DatabaseInvariantException()
            CardVaultSyncFiles.generateSyncSecret().use { generated ->
                val newSyncKey = generated.copyBytes()
                try {
                    val encrypted = syncKeyCryptor.encrypt(
                        syncKey = newSyncKey,
                        localDek = localDek,
                        vaultId = current.vaultId,
                        keyEpoch = current.keyEpoch + 1,
                    )
                    val ciphertext = encrypted.ciphertextCopy()
                    try {
                        syncStateDao.rotateSyncKey(
                            expectedVaultId = current.vaultId,
                            expectedKeyEpoch = current.keyEpoch,
                            encryptedSyncKey = ciphertext,
                            syncKeyIv = encrypted.ivCopy(),
                            syncKeyCryptoVersion = encrypted.cryptoVersion,
                            proposedUpdatedAt = now(),
                        )
                    } finally {
                        ciphertext.fill(0)
                    }
                    createPairingExport(localDek, newSyncKey, validityMillis)
                } finally {
                    newSyncKey.fill(0)
                }
            }
        }
    }

    suspend fun exportSync(): SyncExport = operationMutex.withLock {
        repository.withUnlockedDekForSync { localDek ->
            val stateBefore = requirePairedState()
            val syncKey = decryptStoredSyncKey(stateBefore, localDek)
            try {
                val exportedAt = now()
                val state = syncStateDao.nextExportSequence(exportedAt)
                val payload = SyncFilePayload(
                    packageId = UUID.randomUUID().toString(),
                    vaultId = state.vaultId,
                    sourceDeviceId = state.deviceId,
                    exportSequence = state.exportSequence,
                    exportedAtEpochMillis = exportedAt,
                    keyEpoch = state.keyEpoch,
                    snapshot = buildLocalSnapshot(localDek),
                )
                SyncExport(
                    fileName = "CardVault-sync-" + state.exportSequence + "." +
                        CardVaultSyncFiles.SYNC_EXTENSION,
                    fileBytes = CardVaultSyncFiles.encodeSync(payload, syncKey),
                )
            } finally {
                syncKey.fill(0)
            }
        }
    }

    suspend fun inspectPairing(
        encryptedFile: ByteArray,
        displayCode: String,
    ): SyncImportSummary = operationMutex.withLock {
        repository.withUnlockedDekForSync {
            val pairingSecret = PairingCode.decode(displayCode)
            try {
                CardVaultSyncFiles.decodePairing(encryptedFile, pairingSecret).useSecret { payload ->
                    CardVaultSyncFiles.requirePairingUsableAt(payload, now())
                    validateDescriptor(
                        descriptor = ReplayProtector.descriptor(payload),
                        state = requireVaultState(),
                        allowUnpairedVaultChange = true,
                        allowNewerKeyEpoch = true,
                    )
                    payload.snapshot.toSummary(
                        pairing = true,
                        exportedAt = payload.exportedAtEpochMillis,
                    )
                }
            } finally {
                pairingSecret.fill(0)
            }
        }
    }

    suspend fun inspectSync(encryptedFile: ByteArray): SyncImportSummary =
        operationMutex.withLock {
            repository.withUnlockedDekForSync { localDek ->
                val state = requirePairedState()
                val syncKey = decryptStoredSyncKey(state, localDek)
                try {
                    val payload = CardVaultSyncFiles.decodeSync(encryptedFile, syncKey)
                    validateDescriptor(
                        descriptor = ReplayProtector.descriptor(payload),
                        state = state,
                        allowUnpairedVaultChange = false,
                    )
                    payload.snapshot.toSummary(
                        pairing = false,
                        exportedAt = payload.exportedAtEpochMillis,
                    )
                } finally {
                    syncKey.fill(0)
                }
            }
        }

    suspend fun importPairing(
        encryptedFile: ByteArray,
        displayCode: String,
    ): SyncImportResult = operationMutex.withLock {
        repository.withUnlockedDekForSync { localDek ->
            val pairingSecret = PairingCode.decode(displayCode)
            try {
                CardVaultSyncFiles.decodePairing(encryptedFile, pairingSecret).useSecret { payload ->
                    CardVaultSyncFiles.requirePairingUsableAt(payload, now())
                    val currentState = requireVaultState()
                    validateDescriptor(
                        descriptor = ReplayProtector.descriptor(payload),
                        state = currentState,
                        allowUnpairedVaultChange = true,
                        allowNewerKeyEpoch = true,
                    )
                    val merged = SnapshotMerger.merge(
                        local = buildLocalSnapshot(localDek),
                        incoming = payload.snapshot,
                        resolverDeviceId = currentState.deviceId,
                    )
                    val importedSyncKey = payload.syncSecret.copyBytes()
                    try {
                        val encryptedKey = syncKeyCryptor.encrypt(
                            syncKey = importedSyncKey,
                            localDek = localDek,
                            vaultId = payload.vaultId,
                            keyEpoch = payload.keyEpoch,
                        )
                        val updatedState = currentState.copy(
                            vaultId = payload.vaultId,
                            keyEpoch = payload.keyEpoch,
                            encryptedSyncKey = encryptedKey.ciphertextCopy(),
                            syncKeyIv = encryptedKey.ivCopy(),
                            syncKeyCryptoVersion = encryptedKey.cryptoVersion,
                            updatedAt = maxOf(now(), currentState.updatedAt),
                        )
                        persistMergedSnapshot(
                            snapshot = merged.snapshot,
                            localDek = localDek,
                            vaultState = updatedState,
                            packageId = payload.packageId,
                            sourceDeviceId = payload.sourceDeviceId,
                            exportSequence = payload.exportSequence,
                            resetReplayHistory = currentState.vaultId != payload.vaultId,
                        )
                    } finally {
                        importedSyncKey.fill(0)
                    }
                    merged.toImportResult()
                }
            } finally {
                pairingSecret.fill(0)
            }
        }
    }

    suspend fun importSync(encryptedFile: ByteArray): SyncImportResult =
        operationMutex.withLock {
            repository.withUnlockedDekForSync { localDek ->
                val state = requirePairedState()
                val syncKey = decryptStoredSyncKey(state, localDek)
                try {
                    val payload = CardVaultSyncFiles.decodeSync(encryptedFile, syncKey)
                    validateDescriptor(
                        descriptor = ReplayProtector.descriptor(payload),
                        state = state,
                        allowUnpairedVaultChange = false,
                    )
                    val merged = SnapshotMerger.merge(
                        local = buildLocalSnapshot(localDek),
                        incoming = payload.snapshot,
                        resolverDeviceId = state.deviceId,
                    )
                    persistMergedSnapshot(
                        snapshot = merged.snapshot,
                        localDek = localDek,
                        vaultState = state.copy(updatedAt = maxOf(now(), state.updatedAt)),
                        packageId = payload.packageId,
                        sourceDeviceId = payload.sourceDeviceId,
                        exportSequence = payload.exportSequence,
                    )
                    merged.toImportResult()
                } finally {
                    syncKey.fill(0)
                }
            }
        }

    private suspend fun getOrCreateSyncKey(localDek: ByteArray): ByteArray {
        val current = requireVaultState()
        if (current.encryptedSyncKey != null || current.syncKeyIv != null) {
            return decryptStoredSyncKey(requirePairedState(), localDek)
        }
        CardVaultSyncFiles.generateSyncSecret().use { generated ->
            val syncKey = generated.copyBytes()
            try {
                val encrypted = syncKeyCryptor.encrypt(
                    syncKey = syncKey,
                    localDek = localDek,
                    vaultId = current.vaultId,
                    keyEpoch = current.keyEpoch,
                )
                val encryptedBytes = encrypted.ciphertextCopy()
                val stored = try {
                    syncStateDao.storeInitialSyncKey(
                        expectedVaultId = current.vaultId,
                        keyEpoch = current.keyEpoch,
                        encryptedSyncKey = encryptedBytes,
                        syncKeyIv = encrypted.ivCopy(),
                        syncKeyCryptoVersion = encrypted.cryptoVersion,
                        proposedUpdatedAt = now(),
                    )
                } finally {
                    encryptedBytes.fill(0)
                }
                return if (stored.encryptedSyncKey?.contentEquals(encrypted.ciphertextCopy()) == true) {
                    syncKey.copyOf()
                } else {
                    decryptStoredSyncKey(stored, localDek)
                }
            } finally {
                syncKey.fill(0)
            }
        }
    }

    private suspend fun createPairingExport(
        localDek: ByteArray,
        syncKey: ByteArray,
        validityMillis: Long,
    ): PairingExport {
        val exportedAt = now()
        val expiresAt = safeAdd(exportedAt, validityMillis)
        val state = syncStateDao.nextExportSequence(exportedAt)
        val snapshot = buildLocalSnapshot(localDek)
        PairingCode.generate().use { pairing ->
            val pairingSecret = pairing.secret.copyBytes()
            val payloadSecret = SecretBytes(syncKey)
            try {
                val payload = PairingFilePayload(
                    packageId = UUID.randomUUID().toString(),
                    vaultId = state.vaultId,
                    sourceDeviceId = state.deviceId,
                    exportSequence = state.exportSequence,
                    exportedAtEpochMillis = exportedAt,
                    expiresAtEpochMillis = expiresAt,
                    keyEpoch = state.keyEpoch,
                    syncSecret = payloadSecret,
                    snapshot = snapshot,
                )
                return PairingExport(
                    fileName = "CardVault-pair-" + state.exportSequence + "." +
                        CardVaultSyncFiles.PAIRING_EXTENSION,
                    displayCode = pairing.displayCode,
                    fileBytes = CardVaultSyncFiles.encodePairing(payload, pairingSecret),
                )
            } finally {
                payloadSecret.close()
                pairingSecret.fill(0)
            }
        }
    }

    private suspend fun buildLocalSnapshot(localDek: ByteArray): SyncSnapshot {
        val cards = cardDao.getAll()
        if (cards.map(CardEntity::sortOrder) != cards.indices.toList()) {
            throw DatabaseInvariantException()
        }
        val active = cards.map { entity ->
            val id = canonicalUuid(entity.id)
            val payload = decryptPayload(entity, id, localDek).toSyncPayload()
            SyncRecord(
                recordId = entity.id,
                version = StoredVersionVectors.decode(entity.versionVector),
                value = SyncRecordValue.Active(
                    payload = payload,
                    createdAtEpochMillis = entity.createdAt,
                    updatedAtEpochMillis = entity.updatedAt,
                ),
            )
        }
        val tombstones = syncStateDao.getTombstones().map { entity ->
            canonicalUuid(entity.recordId)
            SyncRecord(
                recordId = entity.recordId,
                version = StoredVersionVectors.decode(entity.versionVector),
                value = SyncRecordValue.Tombstone(entity.deletedAt),
            )
        }
        val order = syncStateDao.getOrderState() ?: throw DatabaseInvariantException()
        val addresses = addressDao.getAll()
        if (addresses.map(AddressEntity::sortOrder) != addresses.indices.toList()) {
            throw DatabaseInvariantException()
        }
        val activeAddresses = addresses.map { entity ->
            val id = canonicalUuid(entity.id)
            val payload = decryptAddressPayload(entity, id, localDek).toSyncPayload()
            AddressSyncRecord(
                recordId = entity.id,
                version = StoredVersionVectors.decode(entity.versionVector),
                value = AddressSyncRecordValue.Active(
                    payload = payload,
                    createdAtEpochMillis = entity.createdAt,
                    updatedAtEpochMillis = entity.updatedAt,
                ),
            )
        }
        val addressTombstones = syncStateDao.getAddressTombstones().map { entity ->
            canonicalUuid(entity.recordId)
            AddressSyncRecord(
                recordId = entity.recordId,
                version = StoredVersionVectors.decode(entity.versionVector),
                value = AddressSyncRecordValue.Tombstone(entity.deletedAt),
            )
        }
        val addressOrder = syncStateDao.getAddressOrderState()
            ?: throw DatabaseInvariantException()
        val folders = folderDao.getAll().map { entity ->
            val id = canonicalUuid(entity.id)
            FolderSyncRecord(
                recordId = entity.id,
                version = StoredVersionVectors.decode(entity.versionVector),
                value = FolderSyncRecordValue.Active(
                    payload = decryptFolderPayload(entity, id, localDek).toSyncPayload(),
                    createdAtEpochMillis = entity.createdAt,
                    updatedAtEpochMillis = entity.updatedAt,
                ),
            )
        }
        val folderTombstones = syncStateDao.getFolderTombstones().map { entity ->
            canonicalUuid(entity.recordId)
            FolderSyncRecord(
                recordId = entity.recordId,
                version = StoredVersionVectors.decode(entity.versionVector),
                value = FolderSyncRecordValue.Tombstone(entity.deletedAt),
            )
        }
        return SyncSnapshot(
            records = active + tombstones,
            order = SyncOrder(
                version = StoredVersionVectors.decode(order.versionVector),
                updatedAtEpochMillis = order.updatedAt,
                recordIds = cards.map(CardEntity::id),
            ),
            addresses = AddressSyncSnapshot(
                records = activeAddresses + addressTombstones,
                order = SyncOrder(
                    version = StoredVersionVectors.decode(addressOrder.versionVector),
                    updatedAtEpochMillis = addressOrder.updatedAt,
                    recordIds = addresses.map(AddressEntity::id),
                ),
            ),
            folders = FolderSyncSnapshot(folders + folderTombstones),
        )
    }

    private suspend fun persistMergedSnapshot(
        snapshot: SyncSnapshot,
        localDek: ByteArray,
        vaultState: SyncVaultStateEntity,
        packageId: String,
        sourceDeviceId: String,
        exportSequence: Long,
        resetReplayHistory: Boolean = false,
    ) {
        val recordsById = snapshot.records.associateBy(SyncRecord::recordId)
        val cards = snapshot.order.recordIds.mapIndexed { sortOrder, recordId ->
            val record = recordsById[recordId] ?: throw DatabaseInvariantException()
            val value = record.value as? SyncRecordValue.Active ?: throw DatabaseInvariantException()
            val normalized = validator.requireValid(value.payload.toInput())
            val cardPayload = normalized.toCardPayload()
            val id = canonicalUuid(record.recordId)
            val encrypted = recordCryptor.encrypt(id, cardPayload, localDek)
            CardEntity(
                id = record.recordId,
                ciphertext = encrypted.ciphertextCopy(),
                recordIv = encrypted.recordIvCopy(),
                payloadSchemaVersion = encrypted.payloadSchemaVersion,
                cryptoVersion = encrypted.cryptoVersion,
                sortOrder = sortOrder,
                createdAt = value.createdAtEpochMillis,
                updatedAt = value.updatedAtEpochMillis,
                versionVector = StoredVersionVectors.encode(record.version),
            )
        }
        val tombstones = snapshot.records.mapNotNull { record ->
            val value = record.value as? SyncRecordValue.Tombstone ?: return@mapNotNull null
            SyncTombstoneEntity(
                recordId = record.recordId,
                versionVector = StoredVersionVectors.encode(record.version),
                deletedAt = value.deletedAtEpochMillis,
            )
        }
        val addressSnapshot = snapshot.addresses ?: throw DatabaseInvariantException()
        val addressRecordsById = addressSnapshot.records.associateBy(AddressSyncRecord::recordId)
        val addresses = addressSnapshot.order.recordIds.mapIndexed { sortOrder, recordId ->
            val record = addressRecordsById[recordId] ?: throw DatabaseInvariantException()
            val value = record.value as? AddressSyncRecordValue.Active
                ?: throw DatabaseInvariantException()
            val normalized = validateStoredAddress(value.payload.toInput())
            val id = canonicalUuid(record.recordId)
            val encrypted = addressRecordCryptor.encrypt(id, normalized.toAddressPayload(), localDek)
            AddressEntity(
                id = record.recordId,
                ciphertext = encrypted.ciphertextCopy(),
                recordIv = encrypted.recordIvCopy(),
                payloadSchemaVersion = encrypted.payloadSchemaVersion,
                cryptoVersion = encrypted.cryptoVersion,
                sortOrder = sortOrder,
                createdAt = value.createdAtEpochMillis,
                updatedAt = value.updatedAtEpochMillis,
                versionVector = StoredVersionVectors.encode(record.version),
            )
        }
        val addressTombstones = addressSnapshot.records.mapNotNull { record ->
            val value = record.value as? AddressSyncRecordValue.Tombstone
                ?: return@mapNotNull null
            AddressSyncTombstoneEntity(
                recordId = record.recordId,
                versionVector = StoredVersionVectors.encode(record.version),
                deletedAt = value.deletedAtEpochMillis,
            )
        }
        val folderSnapshot = snapshot.folders ?: FolderSyncSnapshot(emptyList())
        val folders = folderSnapshot.records.mapNotNull { record ->
            val value = record.value as? FolderSyncRecordValue.Active ?: return@mapNotNull null
            val id = canonicalUuid(record.recordId)
            val encrypted = folderRecordCryptor.encrypt(id, value.payload.toLocalPayload(), localDek)
            VaultFolderEntity(
                id = record.recordId,
                ciphertext = encrypted.ciphertextCopy(),
                recordIv = encrypted.recordIvCopy(),
                payloadSchemaVersion = encrypted.payloadSchemaVersion,
                cryptoVersion = encrypted.cryptoVersion,
                createdAt = value.createdAtEpochMillis,
                updatedAt = value.updatedAtEpochMillis,
                versionVector = StoredVersionVectors.encode(record.version),
            )
        }
        val folderTombstones = folderSnapshot.records.mapNotNull { record ->
            val value = record.value as? FolderSyncRecordValue.Tombstone ?: return@mapNotNull null
            FolderSyncTombstoneEntity(
                recordId = record.recordId,
                versionVector = StoredVersionVectors.encode(record.version),
                deletedAt = value.deletedAtEpochMillis,
            )
        }
        try {
            syncStateDao.replaceSnapshotAtomically(
                cards = cards,
                tombstones = tombstones,
                order = SyncOrderStateEntity(
                    versionVector = StoredVersionVectors.encode(snapshot.order.version),
                    updatedAt = snapshot.order.updatedAtEpochMillis,
                ),
                addresses = addresses,
                addressTombstones = addressTombstones,
                addressOrder = AddressSyncOrderStateEntity(
                    versionVector = StoredVersionVectors.encode(addressSnapshot.order.version),
                    updatedAt = addressSnapshot.order.updatedAtEpochMillis,
                ),
                folders = folders,
                folderTombstones = folderTombstones,
                vaultState = vaultState,
                importedPackage = ImportedSyncPackageEntity(
                    packageId = packageId,
                    sourceDeviceId = sourceDeviceId,
                    exportSequence = exportSequence,
                    importedAt = now(),
                ),
                resetReplayHistory = resetReplayHistory,
            )
        } catch (_: DuplicateSyncPackageException) {
            throw SyncProtocolException(SyncErrorCode.REPLAYED_PACKAGE)
        } catch (_: StaleSyncPackageException) {
            throw SyncProtocolException(SyncErrorCode.STALE_PACKAGE)
        } catch (_: SyncSourceLimitException) {
            throw SyncProtocolException(SyncErrorCode.LIMIT_EXCEEDED)
        }
    }

    private suspend fun validateDescriptor(
        descriptor: com.pdh.cardvault.sync.SyncPackageDescriptor,
        state: SyncVaultStateEntity,
        allowUnpairedVaultChange: Boolean,
        allowNewerKeyEpoch: Boolean = false,
    ) {
        if (descriptor.sourceDeviceId == state.deviceId) {
            throw SyncProtocolException(SyncErrorCode.REPLAYED_PACKAGE)
        }
        val paired = state.encryptedSyncKey != null && state.syncKeyIv != null
        val sameVault = descriptor.vaultId == state.vaultId
        if (paired && sameVault && allowNewerKeyEpoch && descriptor.keyEpoch < state.keyEpoch) {
            throw SyncProtocolException(SyncErrorCode.STALE_PACKAGE)
        }
        val switchesVault = allowUnpairedVaultChange && !sameVault
        val recent = if (switchesVault) emptyList() else syncStateDao.getRecentImportedPackages()
        val highest = if (switchesVault) null else syncStateDao.maxImportedSequence(descriptor.sourceDeviceId)
        ReplayProtector.accept(
            metadata = ReplayMetadata(
                recentPackageIds = recent.map(ImportedSyncPackageEntity::packageId),
                highestSequenceByDevice = highest?.let {
                    mapOf(descriptor.sourceDeviceId to it)
                }.orEmpty(),
            ),
            descriptor = descriptor,
            expectedVaultId = state.vaultId.takeIf { !allowUnpairedVaultChange },
            expectedKeyEpoch = state.keyEpoch.takeIf {
                !allowUnpairedVaultChange &&
                    !(allowNewerKeyEpoch && paired && descriptor.keyEpoch > state.keyEpoch)
            },
        )
    }

    private fun requirePairingValidity(validityMillis: Long) {
        require(validityMillis in 1L..MAX_PAIRING_VALIDITY_MILLIS) {
            "Invalid pairing validity."
        }
    }

    private fun decryptStoredSyncKey(
        state: SyncVaultStateEntity,
        localDek: ByteArray,
    ): ByteArray {
        val ciphertext = state.encryptedSyncKey ?: throw SyncNotPairedException()
        val iv = state.syncKeyIv ?: throw SyncNotPairedException()
        return syncKeyCryptor.decrypt(
            encrypted = EncryptedSyncKey(
                cryptoVersion = state.syncKeyCryptoVersion,
                ciphertext = ciphertext,
                iv = iv,
            ),
            localDek = localDek,
            vaultId = state.vaultId,
            keyEpoch = state.keyEpoch,
        )
    }

    private suspend fun requireVaultState(): SyncVaultStateEntity =
        syncStateDao.getVaultState() ?: throw DatabaseInvariantException()

    private suspend fun requirePairedState(): SyncVaultStateEntity {
        val state = requireVaultState()
        if (
            state.encryptedSyncKey == null ||
            state.syncKeyIv == null ||
            state.syncKeyCryptoVersion <= 0
        ) {
            throw SyncNotPairedException()
        }
        return state
    }

    private fun decryptPayload(
        entity: CardEntity,
        recordId: UUID,
        localDek: ByteArray,
    ): CardPayload = recordCryptor.decrypt(
        recordId = recordId,
        encryptedRecord = EncryptedCardRecord(
            payloadSchemaVersion = entity.payloadSchemaVersion,
            cryptoVersion = entity.cryptoVersion,
            ciphertext = entity.ciphertext,
            recordIv = entity.recordIv,
        ),
        dek = localDek,
    )

    private fun decryptAddressPayload(
        entity: AddressEntity,
        recordId: UUID,
        localDek: ByteArray,
    ): AddressPayload = addressRecordCryptor.decrypt(
        recordId = recordId,
        encryptedRecord = EncryptedAddressRecord(
            payloadSchemaVersion = entity.payloadSchemaVersion,
            cryptoVersion = entity.cryptoVersion,
            ciphertext = entity.ciphertext,
            recordIv = entity.recordIv,
        ),
        dek = localDek,
    )

    private fun decryptFolderPayload(
        entity: VaultFolderEntity,
        recordId: UUID,
        localDek: ByteArray,
    ): VaultFolderPayload = folderRecordCryptor.decrypt(
        recordId,
        EncryptedVaultFolderRecord(
            entity.payloadSchemaVersion,
            entity.cryptoVersion,
            entity.ciphertext,
            entity.recordIv,
        ),
        localDek,
    )

    private fun CardPayload.toSyncPayload(): CardSyncPayload = CardSyncPayload(
        schemaVersion = 2,
        nickname = nickname,
        issuerName = issuerName,
        cardNumber = cardNumber,
        expiryMonth = expiryMonth,
        expiryYear = expiryYear,
        saveCvv = saveCvv,
        cvv = cvv.takeIf { saveCvv },
        cardTemplateId = cardTemplateId,
        notes = notes,
        folderId = folderId,
    )

    private fun CardSyncPayload.toInput(): BankCardInput = BankCardInput(
        nickname = nickname,
        issuerName = issuerName,
        cardNumber = cardNumber,
        expiryMonth = expiryMonth,
        expiryYear = expiryYear,
        saveCvv = saveCvv,
        cvv = cvv.takeIf { saveCvv },
        cardTemplateId = cardTemplateId,
        notes = notes,
        folderId = folderId,
    )

    private fun BankCardInput.toCardPayload(): CardPayload = CardPayload(
        nickname = nickname,
        issuerName = issuerName,
        cardNumber = cardNumber,
        expiryMonth = expiryMonth,
        expiryYear = expiryYear,
        saveCvv = saveCvv,
        cvv = cvv.takeIf { saveCvv },
        cardTemplateId = cardTemplateId,
        notes = notes,
        folderId = folderId,
    )

    private fun AddressPayload.toSyncPayload(): AddressSyncPayload = AddressSyncPayload(
        schemaVersion = 2,
        nickname = nickname,
        detailedAddress = detailedAddress,
        city = city,
        other = other,
        postalCode = postalCode,
        country = country,
        cardTemplateId = cardTemplateId,
        folderId = folderId,
    )

    private fun AddressSyncPayload.toInput(): AddressInput = AddressInput(
        nickname = nickname,
        detailedAddress = detailedAddress,
        city = city,
        other = other,
        postalCode = postalCode,
        country = country,
        cardTemplateId = cardTemplateId,
        folderId = folderId,
    )

    /**
     * Legacy on-device address payloads predate the country field. Keep that one absence
     * intact during synchronization while enforcing every other current validation rule.
     */
    private fun validateStoredAddress(input: AddressInput): AddressInput {
        val validation = addressValidator.validate(input)
        val allowedLegacyErrors = if (input.country.trim().isEmpty()) {
            setOf(AddressValidationError.Country)
        } else {
            emptySet()
        }
        if (validation.errors != allowedLegacyErrors) throw DatabaseInvariantException()
        return validation.normalizedInput
    }

    private fun AddressInput.toAddressPayload(): AddressPayload = AddressPayload(
        nickname = nickname,
        detailedAddress = detailedAddress,
        city = city,
        other = other,
        postalCode = postalCode,
        country = country,
        cardTemplateId = cardTemplateId,
        folderId = folderId,
    )

    private fun VaultFolderPayload.toSyncPayload(): FolderSyncPayload = FolderSyncPayload(
        collection = when (collection) {
            VaultFolderCollection.CARDS -> FolderCollectionKind.CARDS
            VaultFolderCollection.ADDRESSES -> FolderCollectionKind.ADDRESSES
        },
        name = name,
    )

    private fun FolderSyncPayload.toLocalPayload(): VaultFolderPayload = VaultFolderPayload(
        collection = when (collection) {
            FolderCollectionKind.CARDS -> VaultFolderCollection.CARDS
            FolderCollectionKind.ADDRESSES -> VaultFolderCollection.ADDRESSES
        },
        name = name,
    )

    private fun SyncSnapshot.toSummary(
        pairing: Boolean,
        exportedAt: Long,
    ): SyncImportSummary = SyncImportSummary(
        isPairingFile = pairing,
        activeCards = order.recordIds.size,
        tombstones = records.count { it.value is SyncRecordValue.Tombstone },
        activeAddresses = addresses?.order?.recordIds?.size ?: 0,
        addressTombstones = addresses?.records?.count {
            it.value is AddressSyncRecordValue.Tombstone
        } ?: 0,
        exportedAtEpochMillis = exportedAt,
    )

    private fun com.pdh.cardvault.sync.MergeResult.toImportResult(): SyncImportResult =
        SyncImportResult(
            activeCards = snapshot.order.recordIds.size,
            tombstones = snapshot.records.count { it.value is SyncRecordValue.Tombstone },
            conflicts = conflicts,
            activeAddresses = snapshot.addresses?.order?.recordIds?.size ?: 0,
            addressTombstones = snapshot.addresses?.records?.count {
                it.value is AddressSyncRecordValue.Tombstone
            } ?: 0,
        )

    private inline fun <T> PairingFilePayload.useSecret(
        block: (PairingFilePayload) -> T,
    ): T = try {
        block(this)
    } finally {
        syncSecret.close()
    }

    private fun canonicalUuid(value: String): UUID {
        val id = try {
            UUID.fromString(value)
        } catch (_: IllegalArgumentException) {
            throw DatabaseInvariantException()
        }
        if (id.toString() != value) throw DatabaseInvariantException()
        return id
    }

    private fun now(): Long = clock().coerceAtLeast(0L)

    private fun safeAdd(left: Long, right: Long): Long = try {
        Math.addExact(left, right)
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("Invalid pairing validity.")
    }

    override fun toString(): String = "AndroidVaultSyncCoordinator(contents=redacted)"

    private companion object {
        const val DEFAULT_PAIRING_VALIDITY_MILLIS = 7L * 24L * 60L * 60L * 1000L
        const val MAX_PAIRING_VALIDITY_MILLIS = 7L * 24L * 60L * 60L * 1000L
    }
}
