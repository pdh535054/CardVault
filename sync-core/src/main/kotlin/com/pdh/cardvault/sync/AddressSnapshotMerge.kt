package com.pdh.cardvault.sync

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

internal data class AddressMergeResult(
    val snapshot: AddressSyncSnapshot?,
    val conflicts: List<MergeConflict>,
)

/** Deterministic address-collection merge used by [SnapshotMerger]. */
internal object AddressSnapshotMerger {
    fun merge(
        local: AddressSyncSnapshot,
        incoming: AddressSyncSnapshot,
        resolverDeviceId: String,
    ): AddressMergeResult {
        ProtocolValidation.requireUuid(resolverDeviceId)
        val localById = local.records.associateBy(AddressSyncRecord::recordId)
        val incomingById = incoming.records.associateBy(AddressSyncRecord::recordId)
        val occupiedIds = (localById.keys + incomingById.keys).toMutableSet()
        val merged = linkedMapOf<String, AddressSyncRecord>()
        val conflicts = mutableListOf<MergeConflict>()
        val copiesAfter = mutableMapOf<String, MutableList<String>>()

        (localById.keys + incomingById.keys).sorted().forEach { recordId ->
            val localRecord = localById[recordId]
            val incomingRecord = incomingById[recordId]
            when {
                localRecord == null -> merged[recordId] = requireNotNull(incomingRecord)
                incomingRecord == null -> merged[recordId] = localRecord
                else -> mergeRecord(
                    localRecord,
                    incomingRecord,
                    occupiedIds,
                    resolverDeviceId,
                ).forEachIndexed { index, record ->
                    merged[record.recordId] = record
                    if (index > 0) {
                        occupiedIds += record.recordId
                        copiesAfter.getOrPut(recordId, ::mutableListOf) += record.recordId
                        conflicts += MergeConflict(
                            type = if (
                                localRecord.value is AddressSyncRecordValue.Tombstone ||
                                incomingRecord.value is AddressSyncRecordValue.Tombstone
                            ) MergeConflictType.DELETE_UPDATE else MergeConflictType.ACTIVE_ACTIVE,
                            originalRecordId = recordId,
                            preservedCopyRecordId = record.recordId,
                            collection = SyncCollectionKind.ADDRESSES,
                        )
                    }
                }
            }
        }

        if (merged.size > SyncProtocolLimits.ADDRESS_RECORDS) {
            throw SyncProtocolException(SyncErrorCode.LIMIT_EXCEEDED)
        }
        val activeIds = merged.values
            .filter { it.value is AddressSyncRecordValue.Active }
            .map(AddressSyncRecord::recordId)
            .toSet()
        if (activeIds.size > SyncProtocolLimits.ACTIVE_ADDRESSES) {
            throw SyncProtocolException(SyncErrorCode.LIMIT_EXCEEDED)
        }

        val orderChoice = when {
            local.records.isEmpty() && incoming.records.isNotEmpty() ->
                OrderChoice(incoming.order.recordIds, hadConflict = false)
            incoming.records.isEmpty() && local.records.isNotEmpty() ->
                OrderChoice(local.order.recordIds, hadConflict = false)
            else -> chooseOrder(local.order, incoming.order)
        }
        if (orderChoice.hadConflict) {
            conflicts += MergeConflict(
                type = MergeConflictType.ORDER,
                collection = SyncCollectionKind.ADDRESSES,
            )
        }
        val orderedIds = linkedSetOf<String>()
        orderChoice.ids.forEach { recordId ->
            if (recordId in activeIds) orderedIds += recordId
            copiesAfter[recordId].orEmpty().sorted().forEach { copyId ->
                if (copyId in activeIds) orderedIds += copyId
            }
        }
        activeIds.sorted().forEach(orderedIds::add)
        val orderVersion = local.order.version.merge(incoming.order.version).let { mergedVersion ->
            if (orderChoice.hadConflict) resolveVersion(mergedVersion, resolverDeviceId) else mergedVersion
        }
        return AddressMergeResult(
            snapshot = AddressSyncSnapshot(
                records = merged.values.sortedBy(AddressSyncRecord::recordId),
                order = SyncOrder(
                    version = orderVersion,
                    updatedAtEpochMillis = maxOf(
                        local.order.updatedAtEpochMillis,
                        incoming.order.updatedAtEpochMillis,
                    ),
                    recordIds = orderedIds.toList(),
                ),
            ),
            conflicts = conflicts.toList(),
        )
    }

    private fun mergeRecord(
        local: AddressSyncRecord,
        incoming: AddressSyncRecord,
        occupiedIds: Set<String>,
        resolverDeviceId: String,
    ): List<AddressSyncRecord> = when (local.version.compare(incoming.version)) {
        VectorRelation.DOMINATES -> listOf(local)
        VectorRelation.IS_DOMINATED -> listOf(incoming)
        VectorRelation.EQUAL -> {
            if (local.value != incoming.value) {
                throw SyncProtocolException(SyncErrorCode.INVARIANT_VIOLATION)
            }
            listOf(local)
        }
        VectorRelation.CONCURRENT -> mergeConcurrent(
            local,
            incoming,
            occupiedIds,
            resolverDeviceId,
        )
    }

    private fun mergeConcurrent(
        first: AddressSyncRecord,
        second: AddressSyncRecord,
        occupiedIds: Set<String>,
        resolverDeviceId: String,
    ): List<AddressSyncRecord> {
        val mergedVersion = resolveVersion(first.version.merge(second.version), resolverDeviceId)
        if (first.value == second.value) {
            return listOf(first.copy(version = mergedVersion))
        }
        val firstDeleted = first.value is AddressSyncRecordValue.Tombstone
        val secondDeleted = second.value is AddressSyncRecordValue.Tombstone
        if (firstDeleted && secondDeleted) {
            val latestDeletion = maxOf(
                first.value.deletedAtEpochMillis,
                second.value.deletedAtEpochMillis,
            )
            return listOf(
                first.copy(
                    version = mergedVersion,
                    value = AddressSyncRecordValue.Tombstone(latestDeletion),
                ),
            )
        }

        val winner: AddressSyncRecord
        val preserved: AddressSyncRecord
        if (firstDeleted xor secondDeleted) {
            winner = if (firstDeleted) first else second
            preserved = if (firstDeleted) second else first
        } else {
            val comparison = compareUnsigned(canonicalDigest(first.value), canonicalDigest(second.value))
            winner = if (comparison <= 0) first else second
            preserved = if (comparison <= 0) second else first
        }
        val conflictId = conflictCopyId(first.recordId, preserved.value, occupiedIds)
        return listOf(
            winner.copy(version = mergedVersion),
            AddressSyncRecord(conflictId, mergedVersion, preserved.value),
        )
    }

    private data class OrderChoice(val ids: List<String>, val hadConflict: Boolean)

    private fun chooseOrder(first: SyncOrder, second: SyncOrder): OrderChoice =
        when (first.version.compare(second.version)) {
            VectorRelation.DOMINATES -> OrderChoice(first.recordIds, false)
            VectorRelation.IS_DOMINATED -> OrderChoice(second.recordIds, false)
            VectorRelation.EQUAL -> if (first.recordIds == second.recordIds) {
                OrderChoice(first.recordIds, false)
            } else {
                OrderChoice(minOrder(first.recordIds, second.recordIds), true)
            }
            VectorRelation.CONCURRENT -> OrderChoice(minOrder(first.recordIds, second.recordIds), true)
        }

    private fun minOrder(first: List<String>, second: List<String>): List<String> {
        val left = first.joinToString("\u0000")
        val right = second.joinToString("\u0000")
        return if (left <= right) first else second
    }

    private fun conflictCopyId(
        recordId: String,
        value: AddressSyncRecordValue,
        occupiedIds: Set<String>,
    ): String {
        val digest = canonicalDigest(value)
        try {
            repeat(32) { attempt ->
                val sha256 = MessageDigest.getInstance("SHA-256")
                sha256.update("CardVault/AddressConflictCopy/v1".toByteArray(StandardCharsets.US_ASCII))
                sha256.update(recordId.toByteArray(StandardCharsets.US_ASCII))
                sha256.update(digest)
                sha256.update(
                    ByteBuffer.allocate(Int.SIZE_BYTES)
                        .order(ByteOrder.BIG_ENDIAN)
                        .putInt(attempt)
                        .array(),
                )
                val bytes = sha256.digest().copyOf(16)
                bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x50).toByte()
                bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
                val candidate = UUID(buffer.long, buffer.long).toString()
                bytes.fill(0)
                if (candidate !in occupiedIds) return candidate
            }
        } finally {
            digest.fill(0)
        }
        throw SyncProtocolException(SyncErrorCode.LIMIT_EXCEEDED)
    }

    private fun canonicalDigest(value: AddressSyncRecordValue): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        fun addInt(number: Int) = digest.update(
            ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putInt(number).array(),
        )
        fun addLong(number: Long) = digest.update(
            ByteBuffer.allocate(Long.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putLong(number).array(),
        )
        fun addText(text: String) {
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            try {
                addInt(bytes.size)
                digest.update(bytes)
            } finally {
                bytes.fill(0)
            }
        }
        when (value) {
            is AddressSyncRecordValue.Active -> {
                digest.update(1)
                addLong(value.createdAtEpochMillis)
                addLong(value.updatedAtEpochMillis)
                with(value.payload) {
                    addInt(schemaVersion)
                    addText(nickname)
                    addText(detailedAddress)
                    addText(city)
                    addText(other)
                    addText(postalCode)
                    addText(country)
                    addText(cardTemplateId)
                }
            }
            is AddressSyncRecordValue.Tombstone -> {
                digest.update(2)
                addLong(value.deletedAtEpochMillis)
            }
        }
        return digest.digest()
    }

    private fun compareUnsigned(first: ByteArray, second: ByteArray): Int {
        try {
            first.indices.forEach { index ->
                val comparison = (first[index].toInt() and 0xff)
                    .compareTo(second[index].toInt() and 0xff)
                if (comparison != 0) return comparison
            }
            return first.size.compareTo(second.size)
        } finally {
            first.fill(0)
            second.fill(0)
        }
    }

    private fun resolveVersion(
        vector: VersionVector,
        resolverDeviceId: String,
    ): VersionVector {
        if (
            resolverDeviceId !in vector.entries &&
            vector.entries.size == SyncProtocolLimits.DEVICES_PER_VECTOR
        ) {
            throw SyncProtocolException(SyncErrorCode.LIMIT_EXCEEDED)
        }
        return vector.increment(resolverDeviceId)
    }
}
