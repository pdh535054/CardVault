package com.pdh.cardvault.desktop.data

import com.pdh.cardvault.desktop.model.DesktopCard
import com.pdh.cardvault.desktop.model.DesktopAddress
import com.pdh.cardvault.desktop.model.DesktopFolder
import com.pdh.cardvault.desktop.model.DesktopFolderKind
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class DesktopVaultSnapshot(
    val cards: List<DesktopCard>,
    val revision: Long,
    val syncState: DesktopSyncState = DesktopSyncState.create(),
    val addresses: List<DesktopAddress> = emptyList(),
    val folders: List<DesktopFolder> = emptyList(),
    val cardFolderOrder: List<String?> = listOf(null),
    val addressFolderOrder: List<String?> = listOf(null),
) {
    override fun toString(): String =
        "DesktopVaultSnapshot(cardCount=${cards.size}, addressCount=${addresses.size}, folderCount=${folders.size}, revision=$revision)"
}

internal object DesktopVaultCodec {
    private const val VERSION = 5
    private const val FOLDER_VERSION = 4
    private const val ADDRESS_VERSION = 3
    private const val LEGACY_VERSION = 2
    private const val MAX_CARDS = 256
    private const val MAX_ADDRESSES = 256
    private const val MAX_FOLDERS = 128
    private const val MAX_PAYLOAD_BYTES = 4 * 1024 * 1024
    private val MAGIC = "CardVault/DesktopSnapshot".toByteArray(StandardCharsets.US_ASCII)

    fun encode(snapshot: DesktopVaultSnapshot): ByteArray {
        require(snapshot.cards.size <= MAX_CARDS) { "卡包数据无效。" }
        require(snapshot.addresses.size <= MAX_ADDRESSES) { "地址数据无效。" }
        require(snapshot.folders.size <= MAX_FOLDERS) { "文件夹数据无效。" }
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeByte(MAGIC.size)
            data.write(MAGIC)
            data.writeInt(VERSION)
            data.writeLong(snapshot.revision)
            data.writeSyncState(snapshot.syncState)
            data.writeInt(snapshot.cards.size)
            snapshot.cards.sortedBy(DesktopCard::sortOrder).forEach { card ->
                data.writeString(card.id, 64)
                data.writeString(card.nickname, 200)
                data.writeString(card.issuerName, 320)
                data.writeString(card.cardNumber, 19)
                data.writeInt(card.expiryMonth)
                data.writeInt(card.expiryYear)
                data.writeBoolean(card.cvv != null)
                card.cvv?.let { data.writeString(it, 4) }
                data.writeString(card.notes, 4_000)
                data.writeString(card.cardTemplateId, 400)
                data.writeInt(card.sortOrder)
                data.writeLong(card.createdAtEpochMillis)
                data.writeLong(card.updatedAtEpochMillis)
                data.writeNullableString(card.folderId, 64)
            }
            data.writeInt(snapshot.addresses.size)
            snapshot.addresses.sortedBy(DesktopAddress::sortOrder).forEach { address ->
                data.writeString(address.id, 64)
                data.writeString(address.nickname, 200)
                data.writeString(address.detailedAddress, 2_000)
                data.writeString(address.city, 400)
                data.writeString(address.other, 800)
                data.writeString(address.postalCode, 80)
                data.writeString(address.country, 400)
                data.writeString(address.cardTemplateId, 400)
                data.writeInt(address.sortOrder)
                data.writeLong(address.createdAtEpochMillis)
                data.writeLong(address.updatedAtEpochMillis)
                data.writeNullableString(address.folderId, 64)
            }
            data.writeInt(snapshot.folders.size)
            snapshot.folders.sortedBy(DesktopFolder::createdAtEpochMillis).forEach { folder ->
                data.writeString(folder.id, 64)
                data.writeString(folder.name, 200)
                data.writeInt(folder.kind.ordinal)
                data.writeLong(folder.createdAtEpochMillis)
                data.writeLong(folder.updatedAtEpochMillis)
            }
            data.writeFolderOrder(snapshot.cardFolderOrder)
            data.writeFolderOrder(snapshot.addressFolderOrder)
        }
        return output.toByteArray().also { bytes ->
            require(bytes.size <= MAX_PAYLOAD_BYTES) { "卡包数据过大。" }
        }
    }

    fun decode(encoded: ByteArray): DesktopVaultSnapshot {
        if (encoded.size !in 1..MAX_PAYLOAD_BYTES) throw InvalidVaultException()
        var decodedSyncState: DesktopSyncState? = null
        return try {
            DataInputStream(ByteArrayInputStream(encoded)).use { data ->
                val magicLength = data.readUnsignedByte()
                if (magicLength != MAGIC.size) throw InvalidVaultException()
                val magic = ByteArray(magicLength).also(data::readFully)
                if (!magic.contentEquals(MAGIC)) throw InvalidVaultException()
                val formatVersion = data.readInt()
                if (formatVersion !in LEGACY_VERSION..VERSION) throw InvalidVaultException()
                val revision = data.readLong().takeIf { it >= 0 } ?: throw InvalidVaultException()
                val syncState = data.readSyncState(formatVersion).also { decodedSyncState = it }
                val count = data.readInt().takeIf { it in 0..MAX_CARDS } ?: throw InvalidVaultException()
                val cards = List(count) {
                    DesktopCard(
                        id = data.readString(64),
                        nickname = data.readString(200),
                        issuerName = data.readString(320),
                        cardNumber = data.readString(19),
                        expiryMonth = data.readInt(),
                        expiryYear = data.readInt(),
                        cvv = if (data.readBoolean()) data.readString(4) else null,
                        notes = data.readString(4_000),
                        cardTemplateId = data.readString(400),
                        sortOrder = data.readInt(),
                        createdAtEpochMillis = data.readLong(),
                        updatedAtEpochMillis = data.readLong(),
                        folderId = if (formatVersion >= FOLDER_VERSION) data.readNullableString(64) else null,
                    )
                }
                val addresses = if (formatVersion >= ADDRESS_VERSION) {
                    val addressCount = data.readInt().takeIf { it in 0..MAX_ADDRESSES }
                        ?: throw InvalidVaultException()
                    List(addressCount) {
                        DesktopAddress(
                            id = data.readString(64),
                            nickname = data.readString(200),
                            detailedAddress = data.readString(2_000),
                            city = data.readString(400),
                            other = data.readString(800),
                            postalCode = data.readString(80),
                            country = data.readString(400),
                            cardTemplateId = data.readString(400),
                            sortOrder = data.readInt(),
                            createdAtEpochMillis = data.readLong(),
                            updatedAtEpochMillis = data.readLong(),
                            folderId = if (formatVersion >= FOLDER_VERSION) data.readNullableString(64) else null,
                        )
                    }
                } else {
                    emptyList()
                }
                val folders = if (formatVersion >= FOLDER_VERSION) {
                    val folderCount = data.readInt().takeIf { it in 0..MAX_FOLDERS }
                        ?: throw InvalidVaultException()
                    List(folderCount) {
                        DesktopFolder(
                            id = data.readString(64),
                            name = data.readString(200),
                            kind = DesktopFolderKind.entries.getOrNull(data.readInt())
                                ?: throw InvalidVaultException(),
                            createdAtEpochMillis = data.readLong(),
                            updatedAtEpochMillis = data.readLong(),
                        )
                    }
                } else emptyList()
                val cardFolderOrder = if (formatVersion >= VERSION) {
                    data.readFolderOrder()
                } else {
                    listOf(null) + folders.filter { it.kind == DesktopFolderKind.CARDS }
                        .sortedBy(DesktopFolder::createdAtEpochMillis).map(DesktopFolder::id)
                }
                val addressFolderOrder = if (formatVersion >= VERSION) {
                    data.readFolderOrder()
                } else {
                    listOf(null) + folders.filter { it.kind == DesktopFolderKind.ADDRESSES }
                        .sortedBy(DesktopFolder::createdAtEpochMillis).map(DesktopFolder::id)
                }
                if (
                    data.available() != 0 ||
                    cards.map(DesktopCard::id).toSet().size != cards.size ||
                    addresses.map(DesktopAddress::id).toSet().size != addresses.size ||
                    folders.map(DesktopFolder::id).toSet().size != folders.size
                ) {
                    throw InvalidVaultException()
                }
                if (cards.map(DesktopCard::sortOrder).sorted() != cards.indices.toList()) {
                    throw InvalidVaultException()
                }
                if (syncState.recordVectors.keys.any { id -> cards.none { it.id == id } }) {
                    throw InvalidVaultException()
                }
                if (addresses.map(DesktopAddress::sortOrder).sorted() != addresses.indices.toList()) {
                    throw InvalidVaultException()
                }
                if (syncState.addressRecordVectors.keys.any { id -> addresses.none { it.id == id } }) {
                    throw InvalidVaultException()
                }
                if (syncState.folderRecordVectors.keys.any { id -> folders.none { it.id == id } }) {
                    throw InvalidVaultException()
                }
                requireValidFolderOrder(
                    cardFolderOrder,
                    folders.filter { it.kind == DesktopFolderKind.CARDS }.map(DesktopFolder::id),
                )
                requireValidFolderOrder(
                    addressFolderOrder,
                    folders.filter { it.kind == DesktopFolderKind.ADDRESSES }.map(DesktopFolder::id),
                )
                DesktopVaultSnapshot(
                    cards = cards.sortedBy(DesktopCard::sortOrder),
                    revision = revision,
                    syncState = syncState,
                    addresses = addresses.sortedBy(DesktopAddress::sortOrder),
                    folders = folders,
                    cardFolderOrder = cardFolderOrder,
                    addressFolderOrder = addressFolderOrder,
                ).also {
                    decodedSyncState = null
                }
            }
        } catch (_: InvalidVaultException) {
            decodedSyncState?.sharedSyncKey?.close()
            throw InvalidVaultException()
        } catch (_: Exception) {
            decodedSyncState?.sharedSyncKey?.close()
            throw InvalidVaultException()
        }
    }

    private fun DataOutputStream.writeSyncState(state: DesktopSyncState) {
        writeString(state.vaultId, 64)
        writeString(state.deviceId, 64)
        writeLong(state.keyEpoch)
        writeBoolean(state.sharedSyncKey != null)
        state.sharedSyncKey?.copyBytes()?.let { key ->
            try {
                writeInt(key.size)
                write(key)
            } finally {
                key.fill(0)
            }
        }
        writeLong(state.exportSequence)
        writeInt(state.recordVectors.size)
        state.recordVectors.toSortedMap().forEach { (recordId, vector) ->
            writeString(recordId, 64)
            writeVector(vector)
        }
        writeInt(state.tombstones.size)
        state.tombstones.toSortedMap().forEach { (recordId, tombstone) ->
            writeString(recordId, 64)
            writeLong(tombstone.deletedAtEpochMillis)
            writeVector(tombstone.vector)
        }
        writeVector(state.orderVector)
        writeInt(state.recentPackageIds.size)
        state.recentPackageIds.forEach { writeString(it, 64) }
        writeInt(state.replaySequences.size)
        state.replaySequences.toSortedMap().forEach { (deviceId, sequence) ->
            writeString(deviceId, 64)
            writeLong(sequence)
        }
        writeInt(state.addressRecordVectors.size)
        state.addressRecordVectors.toSortedMap().forEach { (recordId, vector) ->
            writeString(recordId, 64)
            writeVector(vector)
        }
        writeInt(state.addressTombstones.size)
        state.addressTombstones.toSortedMap().forEach { (recordId, tombstone) ->
            writeString(recordId, 64)
            writeLong(tombstone.deletedAtEpochMillis)
            writeVector(tombstone.vector)
        }
        writeVector(state.addressOrderVector)
        writeInt(state.folderRecordVectors.size)
        state.folderRecordVectors.toSortedMap().forEach { (recordId, vector) ->
            writeString(recordId, 64)
            writeVector(vector)
        }
        writeInt(state.folderTombstones.size)
        state.folderTombstones.toSortedMap().forEach { (recordId, tombstone) ->
            writeString(recordId, 64)
            writeLong(tombstone.deletedAtEpochMillis)
            writeVector(tombstone.vector)
        }
    }

    private fun DataInputStream.readSyncState(formatVersion: Int): DesktopSyncState {
        val vaultId = readString(64)
        val deviceId = readString(64)
        val keyEpoch = readLong()
        var sharedKey = if (readBoolean()) {
            if (readInt() != 32 || available() < 32) throw InvalidVaultException()
            val bytes = ByteArray(32).also(::readFully)
            try { SecretBytes.of(bytes) } finally { bytes.fill(0) }
        } else null
        try {
            val exportSequence = readLong()
            val vectorCount = readInt().takeIf { it in 0..1_024 } ?: throw InvalidVaultException()
            val recordVectors = buildMap {
                repeat(vectorCount) {
                    val id = readString(64)
                    if (put(id, readVector()) != null) throw InvalidVaultException()
                }
            }
            val tombstoneCount = readInt().takeIf { it in 0..1_024 } ?: throw InvalidVaultException()
            val tombstones = buildMap {
                repeat(tombstoneCount) {
                    val id = readString(64)
                    val value = DesktopTombstone(id, readLong(), readVector())
                    if (put(id, value) != null) throw InvalidVaultException()
                }
            }
            val orderVector = readVector()
            val recentCount = readInt().takeIf { it in 0..64 } ?: throw InvalidVaultException()
            val recent = List(recentCount) { readString(64) }
            if (recent.toSet().size != recent.size) throw InvalidVaultException()
            val replayCount = readInt().takeIf { it in 0..16 } ?: throw InvalidVaultException()
            val replay = buildMap {
                repeat(replayCount) {
                    val id = readString(64)
                    val sequence = readLong()
                    if (put(id, sequence) != null) throw InvalidVaultException()
                }
            }
            val addressRecordVectors = if (formatVersion >= ADDRESS_VERSION) readVectorMap(1_024) else emptyMap()
            val addressTombstones = if (formatVersion >= ADDRESS_VERSION) readTombstoneMap(1_024) else emptyMap()
            val addressOrderVector = if (formatVersion >= ADDRESS_VERSION) {
                readVector()
            } else {
                VersionVector(mapOf(deviceId to 1L))
            }
            val folderRecordVectors = if (formatVersion >= FOLDER_VERSION) readVectorMap(512) else emptyMap()
            val folderTombstones = if (formatVersion >= FOLDER_VERSION) readTombstoneMap(512) else emptyMap()
            return DesktopSyncState(
                vaultId = vaultId,
                deviceId = deviceId,
                keyEpoch = keyEpoch,
                sharedSyncKey = sharedKey,
                exportSequence = exportSequence,
                recordVectors = recordVectors,
                tombstones = tombstones,
                orderVector = orderVector,
                addressRecordVectors = addressRecordVectors,
                addressTombstones = addressTombstones,
                addressOrderVector = addressOrderVector,
                folderRecordVectors = folderRecordVectors,
                folderTombstones = folderTombstones,
                recentPackageIds = recent,
                replaySequences = replay,
            ).also { sharedKey = null }
        } finally {
            sharedKey?.close()
        }
    }

    private fun DataInputStream.readVectorMap(limit: Int): Map<String, VersionVector> {
        val count = readInt().takeIf { it in 0..limit } ?: throw InvalidVaultException()
        return buildMap {
            repeat(count) {
                val id = readString(64)
                if (put(id, readVector()) != null) throw InvalidVaultException()
            }
        }
    }

    private fun DataInputStream.readTombstoneMap(limit: Int): Map<String, DesktopTombstone> {
        val count = readInt().takeIf { it in 0..limit } ?: throw InvalidVaultException()
        return buildMap {
            repeat(count) {
                val id = readString(64)
                val value = DesktopTombstone(id, readLong(), readVector())
                if (put(id, value) != null) throw InvalidVaultException()
            }
        }
    }

    private fun DataOutputStream.writeVector(vector: VersionVector) {
        writeInt(vector.entries.size)
        vector.entries.toSortedMap().forEach { (deviceId, sequence) ->
            writeString(deviceId, 64)
            writeLong(sequence)
        }
    }

    private fun DataInputStream.readVector(): VersionVector {
        val count = readInt().takeIf { it in 0..16 } ?: throw InvalidVaultException()
        return VersionVector(buildMap {
            repeat(count) {
                val id = readString(64)
                val sequence = readLong()
                if (put(id, sequence) != null) throw InvalidVaultException()
            }
        })
    }

    private fun DataOutputStream.writeString(value: String, maxBytes: Int) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        try {
            require(bytes.size <= maxBytes) { "卡包数据无效。" }
            writeInt(bytes.size)
            write(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun DataOutputStream.writeNullableString(value: String?, maxBytes: Int) {
        writeBoolean(value != null)
        if (value != null) writeString(value, maxBytes)
    }

    private fun DataInputStream.readNullableString(maxBytes: Int): String? =
        if (readBoolean()) readString(maxBytes) else null

    private fun DataOutputStream.writeFolderOrder(order: List<String?>) {
        writeInt(order.size)
        order.forEach { writeNullableString(it, 64) }
    }

    private fun DataInputStream.readFolderOrder(): List<String?> {
        val count = readInt().takeIf { it in 1..MAX_FOLDERS + 1 } ?: throw InvalidVaultException()
        return List(count) { readNullableString(64) }
    }

    private fun requireValidFolderOrder(order: List<String?>, folderIds: List<String>) {
        if (
            order.size != folderIds.size + 1 ||
            order.count { it == null } != 1 ||
            order.filterNotNull().distinct().size != folderIds.size ||
            order.filterNotNull().toSet() != folderIds.toSet()
        ) {
            throw InvalidVaultException()
        }
    }

    private fun DataInputStream.readString(maxBytes: Int): String {
        val length = readInt()
        if (length !in 0..maxBytes || length > available()) throw InvalidVaultException()
        val bytes = ByteArray(length).also(::readFully)
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        } finally {
            bytes.fill(0)
        }
    }
}

class InvalidVaultException : IllegalStateException("无法安全读取本地卡包。")
