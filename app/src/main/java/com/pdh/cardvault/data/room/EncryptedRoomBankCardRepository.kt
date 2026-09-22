package com.pdh.cardvault.data.room

import com.pdh.cardvault.domain.model.BankCardInput
import com.pdh.cardvault.domain.model.AddressInput
import com.pdh.cardvault.domain.validation.AddressValidator
import com.pdh.cardvault.domain.validation.BankCardValidator
import com.pdh.cardvault.domain.validation.CardNumberTools
import com.pdh.cardvault.domain.validation.CardNetworkDetector
import com.pdh.cardvault.security.crypto.AndroidKeystoreKekManager
import com.pdh.cardvault.security.crypto.AddressPayload
import com.pdh.cardvault.security.crypto.AddressRecordCryptor
import com.pdh.cardvault.security.crypto.CardPayload
import com.pdh.cardvault.security.crypto.CardRecordCryptor
import com.pdh.cardvault.security.crypto.DekGenerator
import com.pdh.cardvault.security.crypto.DekWrapper
import com.pdh.cardvault.security.crypto.EncryptedCardRecord
import com.pdh.cardvault.security.crypto.EncryptedAddressRecord
import com.pdh.cardvault.security.crypto.ForegroundDekSession
import com.pdh.cardvault.security.crypto.EncryptedVaultFolderRecord
import com.pdh.cardvault.security.crypto.VaultFolderCollection
import com.pdh.cardvault.security.crypto.VaultFolderPayload
import com.pdh.cardvault.security.crypto.VaultFolderRecordCryptor
import com.pdh.cardvault.security.crypto.KekManager
import com.pdh.cardvault.security.crypto.VaultCryptoException
import com.pdh.cardvault.security.crypto.VaultKeyUnavailableException
import com.pdh.cardvault.security.crypto.WrappedDek
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class EncryptedRoomBankCardRepository(
    private val cardDao: CardDao,
    private val addressDao: AddressDao,
    private val folderDao: VaultFolderDao,
    private val metadataDao: VaultMetadataDao,
    private val syncStateDao: SyncStateDao,
    private val validator: BankCardValidator,
    private val addressValidator: AddressValidator,
    private val kekManager: KekManager,
    private val dekGenerator: DekGenerator = DekGenerator(),
    private val dekWrapper: DekWrapper = DekWrapper(),
    private val cryptor: CardRecordCryptor = CardRecordCryptor(),
    private val addressCryptor: AddressRecordCryptor = AddressRecordCryptor(),
    private val folderCryptor: VaultFolderRecordCryptor = VaultFolderRecordCryptor(),
    private val dekSession: ForegroundDekSession = ForegroundDekSession(),
    private val clock: () -> Long = System::currentTimeMillis,
) : PersistentBankCardRepository, PersistentAddressRepository, PersistentVaultFolderRepository {
    private val lifecycleLock = Any()
    private var lifecycleGeneration = 0L

    override suspend fun unlockOrCreateVault() = storageOperation {
        val expectedGeneration = currentLifecycleGeneration()
        withContext(Dispatchers.Default) {
            val metadataRows = metadataDao.getAll()
            val dek = when (metadataRows.size) {
                0 -> createInitialVault()
                1 -> unwrapExistingVault(metadataRows.single())
                else -> throw DatabaseInvariantException()
            }
            try {
                synchronized(lifecycleLock) {
                    if (lifecycleGeneration != expectedGeneration) {
                        throw VaultKeyUnavailableException()
                    }
                    dekSession.load(dek)
                }
                initializeSyncStateIfNeeded()
            } finally {
                dek.fill(0)
            }
        }
    }

    override fun lock() {
        synchronized(lifecycleLock) {
            lifecycleGeneration += 1L
            dekSession.clear()
        }
    }

    override fun isUnlocked(): Boolean = dekSession.isLoaded()

    override suspend fun add(input: BankCardInput): UUID = storageOperation {
        withContext(Dispatchers.Default) {
            val normalized = validator.requireValid(input)
            dekSession.useSuspending { dek ->
                val now = clock().coerceAtLeast(0L)
                val localDeviceId = requireLocalDeviceId()
                val recordId = UUID.randomUUID()
                val encrypted = cryptor.encrypt(recordId, normalized.toPayload(), dek)
                val entity = encrypted.toEntity(recordId, sortOrder = 0, now = now)
                cardDao.insertAtFront(
                    entity = entity,
                    localDeviceId = localDeviceId,
                    proposedUpdatedAt = now,
                )
                recordId
            }
        }
    }

    override suspend fun getList(): List<PersistentCardListItem> = storageOperation {
        withContext(Dispatchers.Default) {
            dekSession.useSuspending { dek ->
                val entities = cardDao.getAll()
                entities.requireContinuousOrder()
                entities.map { entity ->
                    entity.decryptPayload(dek).toListItem(entity)
                }
            }
        }
    }

    override suspend fun getMaskedDetail(id: UUID): PersistentCardDetail? = storageOperation {
        withContext(Dispatchers.Default) {
            dekSession.useSuspending { dek ->
                cardDao.getById(id.toString())?.decryptPayload(dek)?.toMaskedDetail()
            }
        }
    }

    override suspend fun getSecretsForAuthenticatedUse(id: UUID): PersistentCardSecrets? =
        storageOperation {
            withContext(Dispatchers.Default) {
                dekSession.useSuspending { dek ->
                    cardDao.getById(id.toString())?.decryptPayload(dek)?.let { payload ->
                        PersistentCardSecrets(
                            cardNumber = payload.cardNumber,
                            expiryMonth = payload.expiryMonth,
                            expiryYear = payload.expiryYear,
                            cvv = payload.cvv,
                        )
                    }
                }
            }
        }

    override suspend fun getEditInputForAuthenticatedUse(id: UUID): BankCardInput? =
        storageOperation {
            withContext(Dispatchers.Default) {
                dekSession.useSuspending { dek ->
                    cardDao.getById(id.toString())?.decryptPayload(dek)?.toInput()
                }
            }
        }

    override suspend fun update(id: UUID, input: BankCardInput): Boolean = storageOperation {
        withContext(Dispatchers.Default) {
            val normalized = validator.requireValid(input)
            dekSession.useSuspending { dek ->
                val localDeviceId = requireLocalDeviceId()
                val existing = cardDao.getById(id.toString()) ?: return@useSuspending false
                val retainedFolderId = normalized.folderId ?: existing.decryptPayload(dek).folderId
                val encrypted = cryptor.encrypt(
                    id,
                    normalized.copy(folderId = retainedFolderId).toPayload(),
                    dek,
                )
                cardDao.updateEncryptedPayloadAtomically(
                    id = id.toString(),
                    ciphertext = encrypted.ciphertextCopy(),
                    recordIv = encrypted.recordIvCopy(),
                    payloadSchemaVersion = encrypted.payloadSchemaVersion,
                    cryptoVersion = encrypted.cryptoVersion,
                    proposedUpdatedAt = clock(),
                    localDeviceId = localDeviceId,
                ) ?: return@useSuspending false
                true
            }
        }
    }

    override suspend fun delete(id: UUID): Boolean = storageOperation {
        withContext(Dispatchers.Default) {
            if (!dekSession.isLoaded()) throw VaultKeyUnavailableException()
            cardDao.deleteAndCompact(
                id = id.toString(),
                localDeviceId = requireLocalDeviceId(),
                proposedDeletedAt = clock(),
            )
        }
    }

    override suspend fun reorder(orderedIds: List<UUID>) = storageOperation {
        withContext(Dispatchers.Default) {
            if (!dekSession.isLoaded()) throw VaultKeyUnavailableException()
            cardDao.reorderAtomically(
                orderedIds = orderedIds.map(UUID::toString),
                localDeviceId = requireLocalDeviceId(),
                proposedUpdatedAt = clock(),
            )
        }
    }

    override suspend fun addAddress(input: AddressInput): UUID = storageOperation {
        withContext(Dispatchers.Default) {
            val normalized = addressValidator.requireValid(input)
            dekSession.useSuspending { dek ->
                val now = clock().coerceAtLeast(0L)
                val recordId = UUID.randomUUID()
                val encrypted = addressCryptor.encrypt(recordId, normalized.toAddressPayload(), dek)
                addressDao.insertAtFront(
                    encrypted.toAddressEntity(recordId, sortOrder = 0, now = now),
                    localDeviceId = requireLocalDeviceId(),
                    proposedUpdatedAt = now,
                )
                recordId
            }
        }
    }

    override suspend fun getAddressList(): List<PersistentAddressListItem> = storageOperation {
        withContext(Dispatchers.Default) {
            dekSession.useSuspending { dek ->
                val entities = addressDao.getAll()
                entities.requireContinuousAddressOrder()
                entities.map { entity -> entity.decryptAddressPayload(dek).toListItem(entity) }
            }
        }
    }

    override suspend fun getAddressDetail(id: UUID): PersistentAddressDetail? = storageOperation {
        withContext(Dispatchers.Default) {
            dekSession.useSuspending { dek ->
                addressDao.getById(id.toString())?.decryptAddressPayload(dek)?.toDetail()
            }
        }
    }

    override suspend fun getAddressEditInput(id: UUID): AddressInput? = storageOperation {
        withContext(Dispatchers.Default) {
            dekSession.useSuspending { dek ->
                addressDao.getById(id.toString())?.decryptAddressPayload(dek)?.toAddressInput()
            }
        }
    }

    override suspend fun updateAddress(id: UUID, input: AddressInput): Boolean = storageOperation {
        withContext(Dispatchers.Default) {
            val normalized = addressValidator.requireValid(input)
            dekSession.useSuspending { dek ->
                val existing = addressDao.getById(id.toString()) ?: return@useSuspending false
                val retainedFolderId = normalized.folderId ?: existing.decryptAddressPayload(dek).folderId
                val encrypted = addressCryptor.encrypt(
                    id,
                    normalized.copy(folderId = retainedFolderId).toAddressPayload(),
                    dek,
                )
                addressDao.updateEncryptedPayloadAtomically(
                    id = id.toString(),
                    ciphertext = encrypted.ciphertextCopy(),
                    recordIv = encrypted.recordIvCopy(),
                    payloadSchemaVersion = encrypted.payloadSchemaVersion,
                    cryptoVersion = encrypted.cryptoVersion,
                    proposedUpdatedAt = clock(),
                    localDeviceId = requireLocalDeviceId(),
                ) ?: return@useSuspending false
                true
            }
        }
    }

    override suspend fun deleteAddress(id: UUID): Boolean = storageOperation {
        withContext(Dispatchers.Default) {
            if (!dekSession.isLoaded()) throw VaultKeyUnavailableException()
            addressDao.deleteAndCompact(
                id = id.toString(),
                localDeviceId = requireLocalDeviceId(),
                proposedDeletedAt = clock(),
            )
        }
    }

    override suspend fun reorderAddresses(orderedIds: List<UUID>) = storageOperation {
        withContext(Dispatchers.Default) {
            if (!dekSession.isLoaded()) throw VaultKeyUnavailableException()
            addressDao.reorderAtomically(
                orderedIds = orderedIds.map(UUID::toString),
                localDeviceId = requireLocalDeviceId(),
                proposedUpdatedAt = clock(),
            )
        }
    }

    override suspend fun getFolders(kind: VaultFolderKind): List<PersistentVaultFolder> = storageOperation {
        withContext(Dispatchers.Default) {
            dekSession.useSuspending { dek ->
                val folders = folderDao.getAll().mapNotNull { entity ->
                    val payload = entity.decryptFolderPayload(dek)
                    val payloadKind = payload.collection.toFolderKind()
                    if (payloadKind == kind) {
                        PersistentVaultFolder(UUID.fromString(entity.id), payload.name, payloadKind)
                    } else null
                }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                val order = normalizedFolderOrder(kind, folders.map(PersistentVaultFolder::id))
                val byId = folders.associateBy(PersistentVaultFolder::id)
                order.mapNotNull { id -> id?.let(byId::get) }
            }
        }
    }

    override suspend fun getFolderOrder(kind: VaultFolderKind): List<UUID?> = storageOperation {
        withContext(Dispatchers.Default) {
            dekSession.useSuspending { dek ->
                val ids = folderDao.getAll().mapNotNull { entity ->
                    val payload = entity.decryptFolderPayload(dek)
                    entity.id.takeIf { payload.collection.toFolderKind() == kind }?.let(UUID::fromString)
                }
                normalizedFolderOrder(kind, ids)
            }
        }
    }

    override suspend fun reorderFolders(kind: VaultFolderKind, orderedIds: List<UUID?>) = storageOperation {
        withContext(Dispatchers.Default) {
            dekSession.useSuspending { dek ->
                val currentIds = folderDao.getAll().mapNotNull { entity ->
                    val payload = entity.decryptFolderPayload(dek)
                    entity.id.takeIf { payload.collection.toFolderKind() == kind }?.let(UUID::fromString)
                }
                requireValidFolderOrder(orderedIds, currentIds)
                folderDao.upsertDisplayOrder(
                    VaultFolderDisplayOrderEntity(kind.name, encodeFolderOrder(orderedIds)),
                )
            }
        }
    }

    override suspend fun createFolder(kind: VaultFolderKind, name: String): UUID = storageOperation {
        withContext(Dispatchers.Default) {
            val normalizedName = requireValidFolderName(name)
            dekSession.useSuspending { dek ->
                val id = UUID.randomUUID()
                val now = clock().coerceAtLeast(0L)
                val encrypted = folderCryptor.encrypt(
                    id,
                    VaultFolderPayload(kind.toFolderCollection(), normalizedName),
                    dek,
                )
                folderDao.insertLocal(
                    VaultFolderEntity(
                        id = id.toString(),
                        ciphertext = encrypted.ciphertextCopy(),
                        recordIv = encrypted.recordIvCopy(),
                        payloadSchemaVersion = encrypted.payloadSchemaVersion,
                        cryptoVersion = encrypted.cryptoVersion,
                        createdAt = now,
                        updatedAt = now,
                        versionVector = ByteArray(0),
                    ),
                    requireLocalDeviceId(),
                )
                id
            }
        }
    }

    override suspend fun renameFolder(id: UUID, name: String): Boolean = storageOperation {
        withContext(Dispatchers.Default) {
            val normalizedName = requireValidFolderName(name)
            dekSession.useSuspending { dek ->
                val existing = folderDao.getById(id.toString()) ?: return@useSuspending false
                val payload = existing.decryptFolderPayload(dek)
                val encrypted = folderCryptor.encrypt(id, payload.copy(name = normalizedName), dek)
                folderDao.updateLocal(
                    id.toString(), encrypted.ciphertextCopy(), encrypted.recordIvCopy(),
                    encrypted.payloadSchemaVersion, encrypted.cryptoVersion, clock(), requireLocalDeviceId(),
                )
            }
        }
    }

    override suspend fun deleteFolder(id: UUID): Boolean = storageOperation {
        withContext(Dispatchers.Default) {
            dekSession.useSuspending { dek ->
                val existing = folderDao.getById(id.toString()) ?: return@useSuspending false
                val kind = existing.decryptFolderPayload(dek).collection
                if (kind == VaultFolderCollection.CARDS) {
                    cardDao.getAll().forEach { entity ->
                        val payload = entity.decryptPayload(dek)
                        if (payload.folderId == id.toString()) updateCardFolder(entity, payload, null, dek)
                    }
                } else {
                    addressDao.getAll().forEach { entity ->
                        val payload = entity.decryptAddressPayload(dek)
                        if (payload.folderId == id.toString()) updateAddressFolder(entity, payload, null, dek)
                    }
                }
                folderDao.deleteLocal(id.toString(), requireLocalDeviceId(), clock())
            }
        }
    }

    override suspend fun moveCardToFolder(cardId: UUID, folderId: UUID?): Boolean = storageOperation {
        withContext(Dispatchers.Default) {
            dekSession.useSuspending { dek ->
                requireFolderKind(folderId, VaultFolderCollection.CARDS, dek)
                val entity = cardDao.getById(cardId.toString()) ?: return@useSuspending false
                updateCardFolder(entity, entity.decryptPayload(dek), folderId, dek)
            }
        }
    }

    override suspend fun moveAddressToFolder(addressId: UUID, folderId: UUID?): Boolean = storageOperation {
        withContext(Dispatchers.Default) {
            dekSession.useSuspending { dek ->
                requireFolderKind(folderId, VaultFolderCollection.ADDRESSES, dek)
                val entity = addressDao.getById(addressId.toString()) ?: return@useSuspending false
                updateAddressFolder(entity, entity.decryptAddressPayload(dek), folderId, dek)
            }
        }
    }

    /**
     * Provides a short-lived copy of the local DEK to the Android sync coordinator.
     *
     * The closure cannot retain the repository-owned key and [ForegroundDekSession] erases the
     * working copy when the call returns or fails.
     */
    internal suspend fun <T> withUnlockedDekForSync(
        block: suspend (ByteArray) -> T,
    ): T = storageOperation {
        withContext(Dispatchers.Default) {
            dekSession.useSuspending(block)
        }
    }

    private suspend fun createInitialVault(): ByteArray {
        if (cardDao.count() != 0 || addressDao.count() != 0 || folderDao.getAll().isNotEmpty()) {
            throw VaultKeyUnavailableException()
        }
        val kek = kekManager.createOrGetForNewVault()
        val dek = dekGenerator.generate()
        return try {
            val wrapped = dekWrapper.wrap(dek, kek)
            val now = clock().coerceAtLeast(0L)
            metadataDao.insertInitial(
                VaultMetadataEntity(
                    kekAliasVersion = wrapped.kekAliasVersion,
                    wrappedDek = wrapped.wrappedDekCopy(),
                    wrappingIv = wrapped.wrappingIvCopy(),
                    wrappingFormatVersion = wrapped.wrappingFormatVersion,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            dek.copyOf()
        } finally {
            dek.fill(0)
        }
    }

    private suspend fun initializeSyncStateIfNeeded() {
        val now = clock().coerceAtLeast(0L)
        val deviceId = UUID.randomUUID().toString()
        val initialVersion = com.pdh.cardvault.sync.android.StoredVersionVectors.initial(deviceId)
        syncStateDao.initializeIfNeeded(
            proposedVaultState = SyncVaultStateEntity(
                vaultId = UUID.randomUUID().toString(),
                deviceId = deviceId,
                keyEpoch = 1,
                encryptedSyncKey = null,
                syncKeyIv = null,
                syncKeyCryptoVersion = 0,
                exportSequence = 0L,
                createdAt = now,
                updatedAt = now,
            ),
            proposedOrderState = SyncOrderStateEntity(
                versionVector = initialVersion,
                updatedAt = now,
            ),
            proposedAddressOrderState = AddressSyncOrderStateEntity(
                versionVector = initialVersion.copyOf(),
                updatedAt = now,
            ),
        )
    }

    private suspend fun requireLocalDeviceId(): String =
        syncStateDao.getVaultState()?.deviceId ?: throw DatabaseInvariantException()

    private fun unwrapExistingVault(metadata: VaultMetadataEntity): ByteArray {
        val wrapped = try {
            WrappedDek(
                wrappingFormatVersion = metadata.wrappingFormatVersion,
                kekAliasVersion = metadata.kekAliasVersion,
                wrappedDek = metadata.wrappedDek,
                wrappingIv = metadata.wrappingIv,
            )
        } catch (_: IllegalArgumentException) {
            throw VaultKeyUnavailableException()
        }
        return dekWrapper.unwrap(wrapped, kekManager.getExisting())
    }

    private fun CardEntity.decryptPayload(dek: ByteArray): CardPayload {
        val recordId = try {
            UUID.fromString(id)
        } catch (_: IllegalArgumentException) {
            throw VaultKeyUnavailableException()
        }
        val encrypted = try {
            EncryptedCardRecord(
                payloadSchemaVersion = payloadSchemaVersion,
                cryptoVersion = cryptoVersion,
                ciphertext = ciphertext,
                recordIv = recordIv,
            )
        } catch (_: IllegalArgumentException) {
            throw VaultKeyUnavailableException()
        }
        return cryptor.decrypt(recordId, encrypted, dek)
    }

    private fun AddressEntity.decryptAddressPayload(dek: ByteArray): AddressPayload {
        val recordId = try {
            UUID.fromString(id)
        } catch (_: IllegalArgumentException) {
            throw VaultKeyUnavailableException()
        }
        val encrypted = try {
            EncryptedAddressRecord(
                payloadSchemaVersion = payloadSchemaVersion,
                cryptoVersion = cryptoVersion,
                ciphertext = ciphertext,
                recordIv = recordIv,
            )
        } catch (_: IllegalArgumentException) {
            throw VaultKeyUnavailableException()
        }
        return addressCryptor.decrypt(recordId, encrypted, dek)
    }

    private fun VaultFolderEntity.decryptFolderPayload(dek: ByteArray): VaultFolderPayload {
        val recordId = try { UUID.fromString(id) } catch (_: IllegalArgumentException) {
            throw VaultKeyUnavailableException()
        }
        val encrypted = try {
            EncryptedVaultFolderRecord(payloadSchemaVersion, cryptoVersion, ciphertext, recordIv)
        } catch (_: IllegalArgumentException) {
            throw VaultKeyUnavailableException()
        }
        return folderCryptor.decrypt(recordId, encrypted, dek)
    }

    private fun BankCardInput.toPayload(): CardPayload = CardPayload(
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

    private fun AddressPayload.toListItem(entity: AddressEntity): PersistentAddressListItem =
        PersistentAddressListItem(
            id = UUID.fromString(entity.id),
            nickname = nickname,
            cardTemplateId = cardTemplateId,
            folderId = folderId?.let(UUID::fromString),
        )

    private fun AddressPayload.toDetail(): PersistentAddressDetail = PersistentAddressDetail(
        nickname = nickname,
        detailedAddress = detailedAddress,
        city = city,
        other = other,
        postalCode = postalCode,
        country = country,
        cardTemplateId = cardTemplateId,
        folderId = folderId?.let(UUID::fromString),
    )

    private fun AddressPayload.toAddressInput(): AddressInput = AddressInput(
        nickname = nickname,
        detailedAddress = detailedAddress,
        city = city,
        other = other,
        postalCode = postalCode,
        country = country,
        cardTemplateId = cardTemplateId,
        folderId = folderId,
    )

    private fun CardPayload.toInput(): BankCardInput = BankCardInput(
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

    private fun CardPayload.toListItem(entity: CardEntity): PersistentCardListItem =
        PersistentCardListItem(
            id = UUID.fromString(entity.id),
            nickname = nickname,
            issuerName = issuerName,
            cardTemplateId = cardTemplateId,
            cardNetwork = CardNetworkDetector.detect(cardNumber),
            folderId = folderId?.let(UUID::fromString),
        )

    private fun CardPayload.toMaskedDetail(): PersistentCardDetail = PersistentCardDetail(
        nickname = nickname,
        issuerName = issuerName,
        maskedCardNumber = maskedCardNumber(),
        cardTemplateId = cardTemplateId,
        notes = notes,
        cvvSaved = saveCvv,
        cardNetwork = CardNetworkDetector.detect(cardNumber),
        folderId = folderId?.let(UUID::fromString),
    )

    private fun CardPayload.maskedCardNumber(): String =
        CardNumberTools.mask(cardNumber) ?: throw DatabaseInvariantException()

    private fun EncryptedCardRecord.toEntity(
        recordId: UUID,
        sortOrder: Int,
        now: Long,
    ): CardEntity = CardEntity(
        id = recordId.toString(),
        ciphertext = ciphertextCopy(),
        recordIv = recordIvCopy(),
        payloadSchemaVersion = payloadSchemaVersion,
        cryptoVersion = cryptoVersion,
        sortOrder = sortOrder,
        createdAt = now,
        updatedAt = now,
    )

    private fun EncryptedAddressRecord.toAddressEntity(
        recordId: UUID,
        sortOrder: Int,
        now: Long,
    ): AddressEntity = AddressEntity(
        id = recordId.toString(),
        ciphertext = ciphertextCopy(),
        recordIv = recordIvCopy(),
        payloadSchemaVersion = payloadSchemaVersion,
        cryptoVersion = cryptoVersion,
        sortOrder = sortOrder,
        createdAt = now,
        updatedAt = now,
    )

    private fun List<CardEntity>.requireContinuousOrder() {
        if (map(CardEntity::sortOrder) != indices.toList()) throw DatabaseInvariantException()
    }

    private fun List<AddressEntity>.requireContinuousAddressOrder() {
        if (map(AddressEntity::sortOrder) != indices.toList()) {
            throw DatabaseInvariantException()
        }
    }

    private suspend fun requireFolderKind(
        folderId: UUID?,
        expected: VaultFolderCollection,
        dek: ByteArray,
    ) {
        if (folderId == null) return
        val folder = folderDao.getById(folderId.toString()) ?: throw IllegalArgumentException("Unknown folder.")
        if (folder.decryptFolderPayload(dek).collection != expected) {
            throw IllegalArgumentException("Wrong folder type.")
        }
    }

    private suspend fun updateCardFolder(
        entity: CardEntity,
        payload: CardPayload,
        folderId: UUID?,
        dek: ByteArray,
    ): Boolean {
        if (payload.folderId == folderId?.toString()) return true
        val id = UUID.fromString(entity.id)
        val encrypted = cryptor.encrypt(id, payload.copy(folderId = folderId?.toString()), dek)
        return cardDao.updateEncryptedPayloadAtomically(
            entity.id, encrypted.ciphertextCopy(), encrypted.recordIvCopy(),
            encrypted.payloadSchemaVersion, encrypted.cryptoVersion, clock(), requireLocalDeviceId(),
        ) != null
    }

    private suspend fun updateAddressFolder(
        entity: AddressEntity,
        payload: AddressPayload,
        folderId: UUID?,
        dek: ByteArray,
    ): Boolean {
        if (payload.folderId == folderId?.toString()) return true
        val id = UUID.fromString(entity.id)
        val encrypted = addressCryptor.encrypt(id, payload.copy(folderId = folderId?.toString()), dek)
        return addressDao.updateEncryptedPayloadAtomically(
            entity.id, encrypted.ciphertextCopy(), encrypted.recordIvCopy(),
            encrypted.payloadSchemaVersion, encrypted.cryptoVersion, clock(), requireLocalDeviceId(),
        ) != null
    }

    private fun requireValidFolderName(value: String): String {
        val normalized = value.trim()
        require(normalized.codePointCount(0, normalized.length) in 1..50) { "Invalid folder name." }
        return normalized
    }

    private fun VaultFolderKind.toFolderCollection(): VaultFolderCollection = when (this) {
        VaultFolderKind.CARDS -> VaultFolderCollection.CARDS
        VaultFolderKind.ADDRESSES -> VaultFolderCollection.ADDRESSES
    }

    private suspend fun normalizedFolderOrder(
        kind: VaultFolderKind,
        folderIds: List<UUID>,
    ): List<UUID?> {
        val validIds = folderIds.toSet()
        val stored = folderDao.getDisplayOrder(kind.name)
            ?.orderedEntries
            ?.let(::decodeFolderOrder)
            .orEmpty()
        val normalized = buildList<UUID?> {
            val seen = mutableSetOf<UUID?>()
            stored.forEach { id ->
                if ((id == null || id in validIds) && seen.add(id)) add(id)
            }
            if (seen.add(null)) add(0, null)
            folderIds.forEach { id -> if (seen.add(id)) add(id) }
        }
        if (stored != normalized) {
            folderDao.upsertDisplayOrder(
                VaultFolderDisplayOrderEntity(kind.name, encodeFolderOrder(normalized)),
            )
        }
        return normalized
    }

    private fun requireValidFolderOrder(orderedIds: List<UUID?>, folderIds: List<UUID>) {
        require(orderedIds.size == folderIds.size + 1) { "Invalid folder order." }
        require(orderedIds.count { it == null } == 1) { "Invalid folder order." }
        require(orderedIds.filterNotNull().toSet() == folderIds.toSet()) { "Invalid folder order." }
        require(orderedIds.filterNotNull().distinct().size == folderIds.size) { "Invalid folder order." }
    }

    private fun encodeFolderOrder(orderedIds: List<UUID?>): String =
        orderedIds.joinToString(FOLDER_ORDER_SEPARATOR) { it?.toString() ?: UNFILED_ORDER_TOKEN }

    private fun decodeFolderOrder(encoded: String): List<UUID?> =
        encoded.split(FOLDER_ORDER_SEPARATOR).mapNotNull { token ->
            if (token == UNFILED_ORDER_TOKEN) FolderOrderToken.Unfiled
            else runCatching { FolderOrderToken.Folder(UUID.fromString(token)) }.getOrNull()
        }.map { token ->
            when (token) {
                FolderOrderToken.Unfiled -> null
                is FolderOrderToken.Folder -> token.id
            }
        }

    private sealed interface FolderOrderToken {
        data object Unfiled : FolderOrderToken
        data class Folder(val id: UUID) : FolderOrderToken
    }

    private fun VaultFolderCollection.toFolderKind(): VaultFolderKind = when (this) {
        VaultFolderCollection.CARDS -> VaultFolderKind.CARDS
        VaultFolderCollection.ADDRESSES -> VaultFolderKind.ADDRESSES
    }

    private fun currentLifecycleGeneration(): Long = synchronized(lifecycleLock) {
        lifecycleGeneration
    }

    override fun toString(): String = "EncryptedRoomBankCardRepository(contents=redacted)"

    companion object {
        fun create(
            database: CardVaultDatabase,
            validator: BankCardValidator,
            addressValidator: AddressValidator,
            context: android.content.Context,
        ): EncryptedRoomBankCardRepository = EncryptedRoomBankCardRepository(
            cardDao = database.cardDao(),
            addressDao = database.addressDao(),
            folderDao = database.vaultFolderDao(),
            metadataDao = database.vaultMetadataDao(),
            syncStateDao = database.syncStateDao(),
            validator = validator,
            addressValidator = addressValidator,
            kekManager = AndroidKeystoreKekManager(context.applicationContext),
        )
    }
}

internal class VaultStorageException : Exception(
    "The encrypted vault storage operation failed.",
)

private suspend fun <T> storageOperation(block: suspend () -> T): T = try {
    block()
} catch (exception: VaultCryptoException) {
    throw exception
} catch (exception: DatabaseInvariantException) {
    throw exception
} catch (_: RuntimeException) {
    throw VaultStorageException()
}

private const val FOLDER_ORDER_SEPARATOR = "\n"
private const val UNFILED_ORDER_TOKEN = "@unfiled"
