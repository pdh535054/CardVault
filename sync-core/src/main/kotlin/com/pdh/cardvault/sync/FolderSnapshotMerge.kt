package com.pdh.cardvault.sync

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

internal data class FolderMergeResult(
    val snapshot: FolderSyncSnapshot?,
    val conflicts: List<MergeConflict>,
)

internal object FolderSnapshotMerger {
    fun merge(
        local: FolderSyncSnapshot,
        incoming: FolderSyncSnapshot,
        resolverDeviceId: String,
    ): FolderMergeResult {
        ProtocolValidation.requireUuid(resolverDeviceId)
        val localById = local.records.associateBy(FolderSyncRecord::recordId)
        val incomingById = incoming.records.associateBy(FolderSyncRecord::recordId)
        val occupiedIds = (localById.keys + incomingById.keys).toMutableSet()
        val merged = linkedMapOf<String, FolderSyncRecord>()
        val conflicts = mutableListOf<MergeConflict>()
        (localById.keys + incomingById.keys).sorted().forEach { recordId ->
            val first = localById[recordId]
            val second = incomingById[recordId]
            when {
                first == null -> merged[recordId] = requireNotNull(second)
                second == null -> merged[recordId] = first
                else -> mergeRecord(first, second, occupiedIds, resolverDeviceId)
                    .forEachIndexed { index, record ->
                        merged[record.recordId] = record
                        if (index > 0) {
                            occupiedIds += record.recordId
                            conflicts += MergeConflict(
                                type = if (
                                    first.value is FolderSyncRecordValue.Tombstone ||
                                    second.value is FolderSyncRecordValue.Tombstone
                                ) MergeConflictType.DELETE_UPDATE else MergeConflictType.ACTIVE_ACTIVE,
                                originalRecordId = recordId,
                                preservedCopyRecordId = record.recordId,
                                collection = SyncCollectionKind.FOLDERS,
                            )
                        }
                    }
            }
        }
        return FolderMergeResult(FolderSyncSnapshot(merged.values.toList()), conflicts)
    }

    private fun mergeRecord(
        first: FolderSyncRecord,
        second: FolderSyncRecord,
        occupiedIds: Set<String>,
        resolverDeviceId: String,
    ): List<FolderSyncRecord> = when (first.version.compare(second.version)) {
        VectorRelation.DOMINATES -> listOf(first)
        VectorRelation.IS_DOMINATED -> listOf(second)
        VectorRelation.EQUAL -> {
            if (first.value != second.value) {
                throw SyncProtocolException(SyncErrorCode.INVARIANT_VIOLATION)
            }
            listOf(first)
        }
        VectorRelation.CONCURRENT -> mergeConcurrent(first, second, occupiedIds, resolverDeviceId)
    }

    private fun mergeConcurrent(
        first: FolderSyncRecord,
        second: FolderSyncRecord,
        occupiedIds: Set<String>,
        resolverDeviceId: String,
    ): List<FolderSyncRecord> {
        val mergedVersion = resolveVersion(first.version.merge(second.version), resolverDeviceId)
        if (first.value == second.value) return listOf(first.copy(version = mergedVersion))
        val firstDeleted = first.value is FolderSyncRecordValue.Tombstone
        val secondDeleted = second.value is FolderSyncRecordValue.Tombstone
        if (firstDeleted && secondDeleted) {
            val deletedAt = maxOf(
                first.value.deletedAtEpochMillis,
                second.value.deletedAtEpochMillis,
            )
            return listOf(first.copy(version = mergedVersion, value = FolderSyncRecordValue.Tombstone(deletedAt)))
        }
        val winner: FolderSyncRecord
        val preserved: FolderSyncRecord
        if (firstDeleted xor secondDeleted) {
            winner = if (firstDeleted) first else second
            preserved = if (firstDeleted) second else first
        } else {
            val comparison = compareUnsigned(digest(first.value), digest(second.value))
            winner = if (comparison <= 0) first else second
            preserved = if (comparison <= 0) second else first
        }
        val copyId = conflictCopyId(first.recordId, preserved.value, occupiedIds)
        return listOf(
            winner.copy(version = mergedVersion),
            FolderSyncRecord(copyId, mergedVersion, preserved.value),
        )
    }

    private fun digest(value: FolderSyncRecordValue): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        when (value) {
            is FolderSyncRecordValue.Active -> {
                digest.update(1)
                digest.update(value.payload.collection.ordinal.toByte())
                digest.update(value.payload.name.toByteArray(StandardCharsets.UTF_8))
                digest.update(ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
                    .putLong(value.createdAtEpochMillis).putLong(value.updatedAtEpochMillis).array())
            }
            is FolderSyncRecordValue.Tombstone -> {
                digest.update(2)
                digest.update(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                    .putLong(value.deletedAtEpochMillis).array())
            }
        }
        return digest.digest()
    }

    private fun conflictCopyId(
        recordId: String,
        value: FolderSyncRecordValue,
        occupiedIds: Set<String>,
    ): String {
        val valueDigest = digest(value)
        try {
            repeat(32) { attempt ->
                val sha = MessageDigest.getInstance("SHA-256")
                sha.update("CardVault/FolderConflictCopy/v1".toByteArray(StandardCharsets.US_ASCII))
                sha.update(recordId.toByteArray(StandardCharsets.US_ASCII))
                sha.update(valueDigest)
                sha.update(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(attempt).array())
                val bytes = sha.digest().copyOf(16)
                bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x50).toByte()
                bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
                val candidate = UUID(buffer.long, buffer.long).toString()
                bytes.fill(0)
                if (candidate !in occupiedIds) return candidate
            }
        } finally {
            valueDigest.fill(0)
        }
        throw SyncProtocolException(SyncErrorCode.LIMIT_EXCEEDED)
    }

    private fun compareUnsigned(first: ByteArray, second: ByteArray): Int {
        try {
            first.indices.forEach { index ->
                val compared = (first[index].toInt() and 0xff).compareTo(second[index].toInt() and 0xff)
                if (compared != 0) return compared
            }
            return first.size.compareTo(second.size)
        } finally {
            first.fill(0)
            second.fill(0)
        }
    }

    private fun resolveVersion(vector: VersionVector, resolverDeviceId: String): VersionVector {
        if (
            resolverDeviceId !in vector.entries &&
            vector.entries.size == SyncProtocolLimits.DEVICES_PER_VECTOR
        ) throw SyncProtocolException(SyncErrorCode.LIMIT_EXCEEDED)
        return vector.increment(resolverDeviceId)
    }
}
