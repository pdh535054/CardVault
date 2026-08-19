package com.pdh.cardvault.desktop.data

import com.pdh.cardvault.desktop.model.DesktopCard
import com.pdh.cardvault.desktop.model.DesktopAddress
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
) {
    override fun toString(): String =
        "DesktopVaultSnapshot(cardCount=${cards.size}, addressCount=${addresses.size}, revision=$revision)"
}

internal object DesktopVaultCodec {
    private const val VERSION = 3
    private const val LEGACY_VERSION = 2
    private const val MAX_CARDS = 256
    private const val MAX_ADDRESSES = 256
    private const val MAX_PAYLOAD_BYTES = 4 * 1024 * 1024
    private val MAGIC = "CardVault/DesktopSnapshot".toByteArray(StandardCharsets.US_ASCII)

    fun encode(snapshot: DesktopVaultSnapshot): ByteArray {
        require(snapshot.cards.size <= MAX_CARDS) { "卡包数据无效。" }
        require(snapshot.addresses.size <= MAX_ADDRESSES) { "地址数据无效。" }
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
            }
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
                if (formatVersion != LEGACY_VERSION && formatVersion != VERSION) throw InvalidVaultException()
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
                    )
                }
                val addresses = if (formatVersion >= VERSION) {
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
                        )
                    }
                } else {
                    emptyList()
                }
                if (
                    data.available() != 0 ||
                    cards.map(DesktopCard::id).toSet().size != cards.size ||
                    addresses.map(DesktopAddress::id).toSet().size != addresses.size
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
                DesktopVaultSnapshot(
                    cards = cards.sortedBy(DesktopCard::sortOrder),
                    revision = revision,
                    syncState = syncState,
                    addresses = addresses.sortedBy(DesktopAddress::sortOrder),
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
            val addressRecordVectors = if (formatVersion >= VERSION) readVectorMap(1_024) else emptyMap()
            val addressTombstones = if (formatVersion >= VERSION) readTombstoneMap(1_024) else emptyMap()
            val addressOrderVector = if (formatVersion >= VERSION) {
                readVector()
            } else {
                VersionVector(mapOf(deviceId to 1L))
            }
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
