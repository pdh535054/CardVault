package com.pdh.cardvault.desktop.sync

import com.pdh.cardvault.desktop.data.DesktopSyncState
import com.pdh.cardvault.desktop.data.DesktopTombstone
import com.pdh.cardvault.desktop.data.DesktopVaultSnapshot
import com.pdh.cardvault.desktop.data.SecretBytes as DesktopSecret
import com.pdh.cardvault.desktop.data.VersionVector as DesktopVector
import com.pdh.cardvault.desktop.model.DesktopCard
import com.pdh.cardvault.desktop.model.DesktopAddress
import com.pdh.cardvault.desktop.model.DesktopFolder
import com.pdh.cardvault.desktop.model.DesktopFolderKind
import com.pdh.cardvault.sync.AddressSyncPayload
import com.pdh.cardvault.sync.AddressSyncRecord
import com.pdh.cardvault.sync.AddressSyncRecordValue
import com.pdh.cardvault.sync.AddressSyncSnapshot
import com.pdh.cardvault.sync.CardSyncPayload
import com.pdh.cardvault.sync.CardVaultSyncFiles
import com.pdh.cardvault.sync.PairingCode
import com.pdh.cardvault.sync.PairingFilePayload
import com.pdh.cardvault.sync.ReplayMetadata
import com.pdh.cardvault.sync.ReplayProtector
import com.pdh.cardvault.sync.SecretBytes as CoreSecret
import com.pdh.cardvault.sync.SnapshotMerger
import com.pdh.cardvault.sync.SyncErrorCode
import com.pdh.cardvault.sync.SyncFileKind
import com.pdh.cardvault.sync.SyncFilePayload
import com.pdh.cardvault.sync.SyncOrder
import com.pdh.cardvault.sync.SyncProtocolException
import com.pdh.cardvault.sync.SyncRecord
import com.pdh.cardvault.sync.SyncRecordValue
import com.pdh.cardvault.sync.SyncSnapshot
import com.pdh.cardvault.sync.VersionVector as CoreVector
import com.pdh.cardvault.sync.FolderCollectionKind
import com.pdh.cardvault.sync.FolderSyncPayload
import com.pdh.cardvault.sync.FolderSyncRecord
import com.pdh.cardvault.sync.FolderSyncRecordValue
import com.pdh.cardvault.sync.FolderSyncSnapshot
import java.security.SecureRandom
import java.time.Clock
import java.util.UUID

class SyncCoreDesktopGateway(
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
) : DesktopSyncGateway {
    override val isReady: Boolean = true

    override fun export(snapshot: DesktopVaultSnapshot, newPairing: Boolean): DesktopExportPackage {
        return if (newPairing) exportPairing(snapshot) else exportSync(snapshot)
    }

    override fun rotateSyncKey(snapshot: DesktopVaultSnapshot): DesktopVaultSnapshot {
        val state = snapshot.syncState
        if (state.sharedSyncKey == null || state.keyEpoch !in 1 until Int.MAX_VALUE.toLong()) {
            throw IllegalStateException("请先完成设备配对。")
        }
        CardVaultSyncFiles.generateSyncSecret(random).use { generated ->
            val newKey = generated.copyBytes()
            return try {
                snapshot.copy(
                    syncState = state.copy(
                        keyEpoch = state.keyEpoch + 1L,
                        sharedSyncKey = DesktopSecret.of(newKey),
                    ),
                    revision = snapshot.revision + 1L,
                )
            } finally {
                newKey.fill(0)
            }
        }
    }

    override fun import(
        bytes: ByteArray,
        pairingCode: String?,
        current: DesktopVaultSnapshot,
    ): DesktopImportResult = try {
        when (CardVaultSyncFiles.inspectKind(bytes)) {
            SyncFileKind.PAIRING -> importPairing(bytes, pairingCode, clock.millis(), current)
            SyncFileKind.SYNC -> importSync(bytes, current)
        }
    } catch (error: SyncProtocolException) {
        throw DesktopSyncException(error.code)
    } catch (error: IllegalArgumentException) {
        throw DesktopSyncException(SyncErrorCode.INVALID_FORMAT)
    }

    private fun exportPairing(snapshot: DesktopVaultSnapshot): DesktopExportPackage {
        val oldState = snapshot.syncState
        val generatedSecret = if (oldState.sharedSyncKey == null) CardVaultSyncFiles.generateSyncSecret(random) else null
        val syncKey = oldState.sharedSyncKey?.copyBytes() ?: generatedSecret!!.copyBytes()
        generatedSecret?.close()
        val keyEpoch = if (oldState.keyEpoch == 0L) 1 else oldState.keyEpoch.toInt()
        val sequence = nextSequence(oldState.exportSequence)
        val packageId = UUID.randomUUID().toString()
        val state = oldState.copy(
            keyEpoch = keyEpoch.toLong(),
            sharedSyncKey = DesktopSecret.of(syncKey),
            exportSequence = sequence,
        )
        val snapshotAfter = snapshot.copy(syncState = state, revision = snapshot.revision + 1)
        val pairing = PairingCode.generate(random)
        val pairingSecret = pairing.secret.copyBytes()
        val payloadSecret = CoreSecret(syncKey)
        return try {
            val now = clock.millis()
            val payload = PairingFilePayload(
                packageId = packageId,
                vaultId = state.vaultId,
                sourceDeviceId = state.deviceId,
                exportSequence = sequence,
                exportedAtEpochMillis = now,
                expiresAtEpochMillis = Math.addExact(now, PAIRING_LIFETIME_MILLIS),
                keyEpoch = keyEpoch,
                syncSecret = payloadSecret,
                snapshot = snapshotAfter.toCoreSnapshot(),
            )
            DesktopExportPackage(
                suggestedFileName = "CardVault-pair-$sequence.cvpair",
                bytes = CardVaultSyncFiles.encodePairing(payload, pairingSecret, random),
                pairingCode = pairing.displayCode,
                snapshotAfterExport = snapshotAfter,
            )
        } finally {
            payloadSecret.close()
            pairingSecret.fill(0)
            pairing.close()
            syncKey.fill(0)
        }
    }

    private fun exportSync(snapshot: DesktopVaultSnapshot): DesktopExportPackage {
        val state = snapshot.syncState
        val key = state.sharedSyncKey?.copyBytes()
            ?: throw IllegalStateException("请先为新设备创建配对文件。")
        if (state.keyEpoch !in 1..Int.MAX_VALUE.toLong()) throw IllegalStateException("同步密钥状态无效。")
        val sequence = nextSequence(state.exportSequence)
        val stateAfter = state.copy(exportSequence = sequence)
        val snapshotAfter = snapshot.copy(syncState = stateAfter, revision = snapshot.revision + 1)
        return try {
            val payload = SyncFilePayload(
                packageId = UUID.randomUUID().toString(),
                vaultId = state.vaultId,
                sourceDeviceId = state.deviceId,
                exportSequence = sequence,
                exportedAtEpochMillis = clock.millis(),
                keyEpoch = state.keyEpoch.toInt(),
                snapshot = snapshotAfter.toCoreSnapshot(),
            )
            DesktopExportPackage(
                suggestedFileName = "CardVault-sync-$sequence.cvsync",
                bytes = CardVaultSyncFiles.encodeSync(payload, key, random),
                pairingCode = null,
                snapshotAfterExport = snapshotAfter,
            )
        } finally {
            key.fill(0)
        }
    }

    private fun importPairing(
        bytes: ByteArray,
        code: String?,
        now: Long,
        current: DesktopVaultSnapshot,
    ): DesktopImportResult {
        val displayCode = code?.takeIf(String::isNotBlank)
            ?: throw DesktopSyncException(SyncErrorCode.INVALID_PAIRING_CODE)
        val pairingSecret = PairingCode.decode(displayCode)
        val payload = try {
            CardVaultSyncFiles.decodePairing(bytes, pairingSecret)
        } finally {
            pairingSecret.fill(0)
        }
        return try {
            CardVaultSyncFiles.requirePairingUsableAt(payload, now)
            importOpenedPairing(payload, current)
        } finally {
            payload.syncSecret.close()
        }
    }

    private fun importOpenedPairing(
        payload: PairingFilePayload,
        current: DesktopVaultSnapshot,
    ): DesktopImportResult {
        val currentState = current.syncState
        if (payload.sourceDeviceId == currentState.deviceId) {
            throw DesktopSyncException(SyncErrorCode.REPLAYED_PACKAGE)
        }
        val sameVault = currentState.vaultId == payload.vaultId
        if (currentState.sharedSyncKey != null && sameVault && payload.keyEpoch < currentState.keyEpoch) {
            throw DesktopSyncException(SyncErrorCode.STALE_PACKAGE)
        }
        val acceptsNewerKeyEpoch = currentState.sharedSyncKey != null &&
            payload.keyEpoch > currentState.keyEpoch
        val replay = ReplayProtector.accept(
            metadata = if (sameVault) {
                currentState.toReplayMetadata()
            } else {
                ReplayMetadata(emptyList(), emptyMap())
            },
            descriptor = ReplayProtector.descriptor(payload),
            expectedVaultId = null,
            expectedKeyEpoch = currentState.keyEpoch.toInt().takeIf {
                currentState.sharedSyncKey != null && sameVault && !acceptsNewerKeyEpoch
            },
        )
        val merged = SnapshotMerger.merge(current.toCoreSnapshot(), payload.snapshot, currentState.deviceId)
        val sharedKey = payload.syncSecret.copyBytes()
        val state = try {
            currentState.copy(
                vaultId = payload.vaultId,
                keyEpoch = payload.keyEpoch.toLong(),
                sharedSyncKey = DesktopSecret.of(sharedKey),
            ).withReplay(replay)
        } finally {
            sharedKey.fill(0)
        }
        return mergeResult(current, merged.snapshot, state, merged.conflicts.size)
    }

    private fun importSync(bytes: ByteArray, current: DesktopVaultSnapshot): DesktopImportResult {
        val state = current.syncState
        val key = state.sharedSyncKey?.copyBytes()
            ?: throw IllegalStateException("请先导入配对文件。")
        val payload = try {
            CardVaultSyncFiles.decodeSync(bytes, key)
        } finally {
            key.fill(0)
        }
        if (payload.sourceDeviceId == state.deviceId) {
            throw DesktopSyncException(SyncErrorCode.REPLAYED_PACKAGE)
        }
        val replay = ReplayProtector.accept(
            metadata = state.toReplayMetadata(),
            descriptor = ReplayProtector.descriptor(payload),
            expectedVaultId = state.vaultId,
            expectedKeyEpoch = state.keyEpoch.toInt(),
        )
        val merged = SnapshotMerger.merge(current.toCoreSnapshot(), payload.snapshot, state.deviceId)
        return mergeResult(current, merged.snapshot, state.withReplay(replay), merged.conflicts.size)
    }

    private fun mergeResult(
        before: DesktopVaultSnapshot,
        merged: SyncSnapshot,
        state: DesktopSyncState,
        conflicts: Int,
    ): DesktopImportResult {
        val after = merged.toDesktopSnapshot(before.revision + 1, state)
        val beforeIds = before.cards.map(DesktopCard::id).toSet()
        val afterIds = after.cards.map(DesktopCard::id).toSet()
        val added = (afterIds - beforeIds).size
        val updated = (afterIds intersect beforeIds).count { id ->
            before.cards.first { it.id == id } != after.cards.first { it.id == id }
        }
        val beforeAddressIds = before.addresses.map(DesktopAddress::id).toSet()
        val afterAddressIds = after.addresses.map(DesktopAddress::id).toSet()
        val addedAddresses = (afterAddressIds - beforeAddressIds).size
        val updatedAddresses = (afterAddressIds intersect beforeAddressIds).count { id ->
            before.addresses.first { it.id == id } != after.addresses.first { it.id == id }
        }
        val message = buildString {
            append("导入完成：银行卡新增 $added，更新 $updated")
            append("；地址新增 $addedAddresses，更新 $updatedAddresses")
            if (conflicts > 0) append("，保留 $conflicts 个冲突副本")
        }
        return DesktopImportResult(
            snapshot = after,
            added = added + addedAddresses,
            updated = updated + updatedAddresses,
            conflicts = conflicts,
            message = message,
        )
    }

    private fun nextSequence(value: Long): Long = if (value == Long.MAX_VALUE) {
        throw IllegalStateException("同步导出次数已达到上限。")
    } else value + 1

    companion object {
        private const val PAIRING_LIFETIME_MILLIS = 7L * 24L * 60 * 60 * 1000
    }
}

private fun DesktopVaultSnapshot.toCoreSnapshot(): SyncSnapshot {
    val active = cards.map { card ->
        val vector = syncState.recordVectors[card.id]
            ?: DesktopVector().increment(syncState.deviceId)
        SyncRecord(
            recordId = card.id,
            version = vector.toCore(),
            value = SyncRecordValue.Active(
                payload = CardSyncPayload(
                    schemaVersion = 2,
                    nickname = card.nickname,
                    issuerName = card.issuerName,
                    cardNumber = card.cardNumber,
                    expiryMonth = card.expiryMonth,
                    expiryYear = card.expiryYear,
                    saveCvv = card.cvv != null,
                    cvv = card.cvv,
                    cardTemplateId = card.cardTemplateId,
                    notes = card.notes,
                    folderId = card.folderId,
                ),
                createdAtEpochMillis = card.createdAtEpochMillis,
                updatedAtEpochMillis = card.updatedAtEpochMillis,
            ),
        )
    }
    val deleted = syncState.tombstones.values.map { tombstone ->
        SyncRecord(
            tombstone.recordId,
            tombstone.vector.toCore(),
            SyncRecordValue.Tombstone(tombstone.deletedAtEpochMillis),
        )
    }
    val activeAddresses = addresses.map { address ->
        val vector = syncState.addressRecordVectors[address.id]
            ?: DesktopVector().increment(syncState.deviceId)
        AddressSyncRecord(
            recordId = address.id,
            version = vector.toCore(),
            value = AddressSyncRecordValue.Active(
                payload = AddressSyncPayload(
                    schemaVersion = 2,
                    nickname = address.nickname,
                    detailedAddress = address.detailedAddress,
                    city = address.city,
                    other = address.other,
                    postalCode = address.postalCode,
                    country = address.country,
                    cardTemplateId = address.cardTemplateId,
                    folderId = address.folderId,
                ),
                createdAtEpochMillis = address.createdAtEpochMillis,
                updatedAtEpochMillis = address.updatedAtEpochMillis,
            ),
        )
    }
    val deletedAddresses = syncState.addressTombstones.values.map { tombstone ->
        AddressSyncRecord(
            recordId = tombstone.recordId,
            version = tombstone.vector.toCore(),
            value = AddressSyncRecordValue.Tombstone(tombstone.deletedAtEpochMillis),
        )
    }
    val activeFolders = folders.map { folder ->
        val vector = syncState.folderRecordVectors[folder.id]
            ?: DesktopVector().increment(syncState.deviceId)
        FolderSyncRecord(
            recordId = folder.id,
            version = vector.toCore(),
            value = FolderSyncRecordValue.Active(
                payload = FolderSyncPayload(
                    collection = when (folder.kind) {
                        DesktopFolderKind.CARDS -> FolderCollectionKind.CARDS
                        DesktopFolderKind.ADDRESSES -> FolderCollectionKind.ADDRESSES
                    },
                    name = folder.name,
                ),
                createdAtEpochMillis = folder.createdAtEpochMillis,
                updatedAtEpochMillis = folder.updatedAtEpochMillis,
            ),
        )
    }
    val deletedFolders = syncState.folderTombstones.values.map { tombstone ->
        FolderSyncRecord(
            recordId = tombstone.recordId,
            version = tombstone.vector.toCore(),
            value = FolderSyncRecordValue.Tombstone(tombstone.deletedAtEpochMillis),
        )
    }
    return SyncSnapshot(
        records = (active + deleted).sortedBy(SyncRecord::recordId),
        order = SyncOrder(
            version = syncState.orderVector.toCore(),
            updatedAtEpochMillis = cards.maxOfOrNull(DesktopCard::updatedAtEpochMillis) ?: 0L,
            recordIds = cards.sortedBy(DesktopCard::sortOrder).map(DesktopCard::id),
        ),
        addresses = AddressSyncSnapshot(
            records = (activeAddresses + deletedAddresses).sortedBy(AddressSyncRecord::recordId),
            order = SyncOrder(
                version = syncState.addressOrderVector.toCore(),
                updatedAtEpochMillis = addresses.maxOfOrNull(DesktopAddress::updatedAtEpochMillis) ?: 0L,
                recordIds = addresses.sortedBy(DesktopAddress::sortOrder).map(DesktopAddress::id),
            ),
        ),
        folders = FolderSyncSnapshot((activeFolders + deletedFolders).sortedBy(FolderSyncRecord::recordId)),
    )
}

private fun SyncSnapshot.toDesktopSnapshot(revision: Long, base: DesktopSyncState): DesktopVaultSnapshot {
    val activeById = records.mapNotNull { record ->
        val value = record.value as? SyncRecordValue.Active ?: return@mapNotNull null
        record.recordId to DesktopCard(
            id = record.recordId,
            nickname = value.payload.nickname,
            issuerName = value.payload.issuerName,
            cardNumber = value.payload.cardNumber,
            expiryMonth = value.payload.expiryMonth,
            expiryYear = value.payload.expiryYear,
            cvv = value.payload.cvv,
            notes = value.payload.notes,
            cardTemplateId = value.payload.cardTemplateId,
            sortOrder = 0,
            createdAtEpochMillis = value.createdAtEpochMillis,
            updatedAtEpochMillis = value.updatedAtEpochMillis,
            folderId = value.payload.folderId,
        )
    }.toMap()
    val cards = order.recordIds.mapIndexed { index, id -> requireNotNull(activeById[id]).copy(sortOrder = index) }
    val vectors = records.filter { it.value is SyncRecordValue.Active }
        .associate { it.recordId to it.version.toDesktop() }
    val tombstones = records.mapNotNull { record ->
        val value = record.value as? SyncRecordValue.Tombstone ?: return@mapNotNull null
        record.recordId to DesktopTombstone(record.recordId, value.deletedAtEpochMillis, record.version.toDesktop())
    }.toMap()
    val addressSnapshot = addresses
    val activeAddressesById = addressSnapshot?.records.orEmpty().mapNotNull { record ->
        val value = record.value as? AddressSyncRecordValue.Active ?: return@mapNotNull null
        record.recordId to DesktopAddress(
            id = record.recordId,
            nickname = value.payload.nickname,
            detailedAddress = value.payload.detailedAddress,
            city = value.payload.city,
            other = value.payload.other,
            postalCode = value.payload.postalCode,
            country = value.payload.country,
            cardTemplateId = value.payload.cardTemplateId,
            sortOrder = 0,
            createdAtEpochMillis = value.createdAtEpochMillis,
            updatedAtEpochMillis = value.updatedAtEpochMillis,
            folderId = value.payload.folderId,
        )
    }.toMap()
    val desktopAddresses = addressSnapshot?.order?.recordIds.orEmpty().mapIndexed { index, id ->
        requireNotNull(activeAddressesById[id]).copy(sortOrder = index)
    }
    val addressVectors = addressSnapshot?.records.orEmpty()
        .filter { it.value is AddressSyncRecordValue.Active }
        .associate { it.recordId to it.version.toDesktop() }
    val addressTombstones = addressSnapshot?.records.orEmpty().mapNotNull { record ->
        val value = record.value as? AddressSyncRecordValue.Tombstone ?: return@mapNotNull null
        record.recordId to DesktopTombstone(
            recordId = record.recordId,
            deletedAtEpochMillis = value.deletedAtEpochMillis,
            vector = record.version.toDesktop(),
        )
    }.toMap()
    val folderSnapshot = folders
    val desktopFolders = folderSnapshot?.records.orEmpty().mapNotNull { record ->
        val value = record.value as? FolderSyncRecordValue.Active ?: return@mapNotNull null
        DesktopFolder(
            id = record.recordId,
            name = value.payload.name,
            kind = when (value.payload.collection) {
                FolderCollectionKind.CARDS -> DesktopFolderKind.CARDS
                FolderCollectionKind.ADDRESSES -> DesktopFolderKind.ADDRESSES
            },
            createdAtEpochMillis = value.createdAtEpochMillis,
            updatedAtEpochMillis = value.updatedAtEpochMillis,
        )
    }
    val folderVectors = folderSnapshot?.records.orEmpty()
        .filter { it.value is FolderSyncRecordValue.Active }
        .associate { it.recordId to it.version.toDesktop() }
    val folderTombstones = folderSnapshot?.records.orEmpty().mapNotNull { record ->
        val value = record.value as? FolderSyncRecordValue.Tombstone ?: return@mapNotNull null
        record.recordId to DesktopTombstone(
            recordId = record.recordId,
            deletedAtEpochMillis = value.deletedAtEpochMillis,
            vector = record.version.toDesktop(),
        )
    }.toMap()
    return DesktopVaultSnapshot(
        cards = cards,
        revision = revision,
        syncState = base.copy(
            recordVectors = vectors,
            tombstones = tombstones,
            orderVector = order.version.toDesktop(),
            addressRecordVectors = addressVectors,
            addressTombstones = addressTombstones,
            addressOrderVector = addressSnapshot?.order?.version?.toDesktop() ?: base.addressOrderVector,
            folderRecordVectors = folderVectors,
            folderTombstones = folderTombstones,
        ),
        addresses = desktopAddresses,
        folders = desktopFolders,
    )
}

private fun DesktopVector.toCore(): CoreVector = CoreVector.of(entries)
private fun CoreVector.toDesktop(): DesktopVector = DesktopVector(entries)

private fun DesktopSyncState.toReplayMetadata(): ReplayMetadata = ReplayMetadata(
    recentPackageIds = recentPackageIds,
    highestSequenceByDevice = replaySequences,
)

private fun DesktopSyncState.withReplay(replay: ReplayMetadata): DesktopSyncState = copy(
    recentPackageIds = replay.recentPackageIds.takeLast(64),
    replaySequences = replay.highestSequenceByDevice,
)

class DesktopSyncException(val code: SyncErrorCode) : IllegalStateException(
    when (code) {
        SyncErrorCode.INVALID_PAIRING_CODE -> "配对码无效。"
        SyncErrorCode.AUTHENTICATION_FAILED -> "无法验证该加密文件。"
        SyncErrorCode.REPLAYED_PACKAGE -> "这个同步文件已经导入过。"
        SyncErrorCode.STALE_PACKAGE -> "同步文件已过期或早于已导入版本。"
        SyncErrorCode.VAULT_MISMATCH -> "该文件属于另一个 CardVault 卡包。"
        SyncErrorCode.LIMIT_EXCEEDED -> "同步文件超过安全限制。"
        SyncErrorCode.UNSUPPORTED_VERSION -> "同步文件版本不受支持。"
        SyncErrorCode.INVARIANT_VIOLATION -> "同步数据存在冲突，已停止导入以保护本地数据。"
        SyncErrorCode.INVALID_FORMAT -> "请选择有效的 CardVault 加密文件。"
    },
)
