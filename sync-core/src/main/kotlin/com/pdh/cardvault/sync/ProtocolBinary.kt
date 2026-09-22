package com.pdh.cardvault.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

object VersionVectorCodec {
    private val magic = "CVVVEC1\n".toByteArray(StandardCharsets.US_ASCII)

    fun encode(vector: VersionVector): ByteArray = BinaryWriter().use { writer ->
        writer.writeRaw(magic)
        writer.writeVersionVector(vector)
        writer.toByteArray()
    }

    @Throws(SyncProtocolException::class)
    fun decode(encoded: ByteArray): VersionVector {
        if (encoded.size !in (magic.size + 1)..2048) {
            throw SyncProtocolException(SyncErrorCode.INVALID_FORMAT)
        }
        return decodeSafely {
            BinaryReader(encoded).use { reader ->
                if (!reader.readRaw(magic.size).contentEquals(magic)) invalidFormat()
                reader.readVersionVector().also { reader.requireEnd() }
            }
        }
    }
}

internal object PackagePayloadCodec {
    private const val LEGACY_PAYLOAD_VERSION = 1
    private const val ADDRESS_PAYLOAD_VERSION = 2
    private const val CURRENT_PAYLOAD_VERSION = 3
    private val pairMagic = "CVPAIRP1".toByteArray(StandardCharsets.US_ASCII)
    private val syncMagic = "CVSYNCP1".toByteArray(StandardCharsets.US_ASCII)

    fun encodePairing(payload: PairingFilePayload): ByteArray = BinaryWriter().use { writer ->
        writer.writeRaw(pairMagic)
        writer.writeInt(payload.snapshot.payloadVersion())
        writer.writeUuid(payload.packageId)
        writer.writeUuid(payload.vaultId)
        writer.writeUuid(payload.sourceDeviceId)
        writer.writeLong(payload.exportSequence)
        writer.writeLong(payload.exportedAtEpochMillis)
        writer.writeLong(payload.expiresAtEpochMillis)
        writer.writeInt(payload.keyEpoch)
        val secret = payload.syncSecret.copyBytes()
        try {
            writer.writeSizedBytes(secret, 32)
        } finally {
            secret.fill(0)
        }
        writer.writeVersionedSnapshot(payload.snapshot)
        writer.toByteArray().also(::requirePlaintextSize)
    }

    fun decodePairing(encoded: ByteArray): PairingFilePayload = decodeSafely {
        requirePlaintextSize(encoded)
        BinaryReader(encoded).use { reader ->
            if (!reader.readRaw(pairMagic.size).contentEquals(pairMagic)) invalidFormat()
            val payloadVersion = reader.readPayloadVersion()
            val packageId = reader.readUuid()
            val vaultId = reader.readUuid()
            val sourceDeviceId = reader.readUuid()
            val exportSequence = reader.readLong()
            val exportedAt = reader.readLong()
            val expiresAt = reader.readLong()
            val keyEpoch = reader.readInt()
            val syncSecretBytes = reader.readSizedBytes(32)
            val syncSecret = SecretBytes(syncSecretBytes)
            syncSecretBytes.fill(0)
            try {
                val snapshot = reader.readVersionedSnapshot(payloadVersion)
                reader.requireEnd()
                PairingFilePayload(
                    packageId = packageId,
                    vaultId = vaultId,
                    sourceDeviceId = sourceDeviceId,
                    exportSequence = exportSequence,
                    exportedAtEpochMillis = exportedAt,
                    expiresAtEpochMillis = expiresAt,
                    keyEpoch = keyEpoch,
                    syncSecret = syncSecret,
                    snapshot = snapshot,
                )
            } catch (error: Throwable) {
                syncSecret.close()
                throw error
            }
        }
    }

    fun encodeSync(payload: SyncFilePayload): ByteArray = BinaryWriter().use { writer ->
        writer.writeRaw(syncMagic)
        writer.writeInt(payload.snapshot.payloadVersion())
        writer.writeUuid(payload.packageId)
        writer.writeUuid(payload.vaultId)
        writer.writeUuid(payload.sourceDeviceId)
        writer.writeLong(payload.exportSequence)
        writer.writeLong(payload.exportedAtEpochMillis)
        writer.writeInt(payload.keyEpoch)
        writer.writeVersionedSnapshot(payload.snapshot)
        writer.toByteArray().also(::requirePlaintextSize)
    }

    fun decodeSync(encoded: ByteArray): SyncFilePayload = decodeSafely {
        requirePlaintextSize(encoded)
        BinaryReader(encoded).use { reader ->
            if (!reader.readRaw(syncMagic.size).contentEquals(syncMagic)) invalidFormat()
            val payloadVersion = reader.readPayloadVersion()
            val result = SyncFilePayload(
                packageId = reader.readUuid(),
                vaultId = reader.readUuid(),
                sourceDeviceId = reader.readUuid(),
                exportSequence = reader.readLong(),
                exportedAtEpochMillis = reader.readLong(),
                keyEpoch = reader.readInt(),
                snapshot = reader.readVersionedSnapshot(payloadVersion),
            )
            reader.requireEnd()
            result
        }
    }

    private fun SyncSnapshot.payloadVersion(): Int = when {
        folders != null -> CURRENT_PAYLOAD_VERSION
        addresses != null -> ADDRESS_PAYLOAD_VERSION
        else -> LEGACY_PAYLOAD_VERSION
    }

    private fun BinaryReader.readPayloadVersion(): Int = readInt().also { version ->
        if (version !in LEGACY_PAYLOAD_VERSION..CURRENT_PAYLOAD_VERSION) {
            throw SyncProtocolException(SyncErrorCode.UNSUPPORTED_VERSION)
        }
    }

    private fun BinaryWriter.writeVersionedSnapshot(snapshot: SyncSnapshot) {
        val payloadVersion = snapshot.payloadVersion()
        writeSnapshot(snapshot, includeFolderMembership = payloadVersion >= CURRENT_PAYLOAD_VERSION)
        snapshot.addresses?.let { addresses ->
            writeAddressSnapshot(
                addresses,
                includeFolderMembership = payloadVersion >= CURRENT_PAYLOAD_VERSION,
            )
        }
        snapshot.folders?.let(::writeFolderSnapshot)
    }

    private fun BinaryReader.readVersionedSnapshot(payloadVersion: Int): SyncSnapshot {
        val cards = readSnapshot(includeFolderMembership = payloadVersion >= CURRENT_PAYLOAD_VERSION)
        val withAddresses = if (payloadVersion >= ADDRESS_PAYLOAD_VERSION) {
            cards.copy(addresses = readAddressSnapshot(includeFolderMembership = payloadVersion >= CURRENT_PAYLOAD_VERSION))
        } else cards
        return if (payloadVersion >= CURRENT_PAYLOAD_VERSION) {
            withAddresses.copy(folders = readFolderSnapshot())
        } else withAddresses
    }
}

private class BinaryWriter : AutoCloseable {
    private val bytes = ByteArrayOutputStream()
    private val output = DataOutputStream(bytes)

    fun writeRaw(value: ByteArray) {
        output.write(value)
    }

    fun writeByte(value: Int) {
        output.writeByte(value)
    }

    fun writeBoolean(value: Boolean) {
        output.writeByte(if (value) 1 else 0)
    }

    fun writeInt(value: Int) {
        output.writeInt(value)
    }

    fun writeLong(value: Long) {
        output.writeLong(value)
    }

    fun writeUtf8(value: String, maxBytes: Int) {
        val encoded = value.toByteArray(StandardCharsets.UTF_8)
        try {
            require(encoded.size <= maxBytes) { "Exchange value exceeds a safety limit." }
            output.writeInt(encoded.size)
            output.write(encoded)
        } finally {
            encoded.fill(0)
        }
    }

    fun writeUuid(value: String) {
        ProtocolValidation.requireUuid(value)
        writeUtf8(value, UUID_BYTES)
    }

    fun writeNullableUuid(value: String?) {
        writeBoolean(value != null)
        value?.let(::writeUuid)
    }

    fun writeSizedBytes(value: ByteArray, exactBytes: Int) {
        require(value.size == exactBytes) { "Invalid exchange binary value." }
        output.writeInt(value.size)
        output.write(value)
    }

    fun writeVersionVector(vector: VersionVector) {
        require(vector.entries.size <= SyncProtocolLimits.DEVICES_PER_VECTOR) {
            "Version vector exceeds a safety limit."
        }
        output.writeByte(vector.entries.size)
        vector.entries.forEach { (deviceId, counter) ->
            writeUuid(deviceId)
            output.writeLong(counter)
        }
    }

    fun writeCardPayload(payload: CardSyncPayload, includeFolderMembership: Boolean) {
        val nested = BinaryWriter().use { card ->
            card.writeInt(if (includeFolderMembership) 2 else 1)
            card.writeUtf8(payload.nickname, MAX_NICKNAME_BYTES)
            card.writeUtf8(payload.issuerName, MAX_ISSUER_BYTES)
            card.writeUtf8(payload.cardNumber, MAX_CARD_NUMBER_BYTES)
            card.writeInt(payload.expiryMonth)
            card.writeInt(payload.expiryYear)
            card.writeBoolean(payload.saveCvv)
            if (payload.saveCvv) card.writeUtf8(requireNotNull(payload.cvv), MAX_CVV_BYTES)
            card.writeUtf8(payload.cardTemplateId, MAX_TEMPLATE_ID_BYTES)
            card.writeUtf8(payload.notes, MAX_NOTES_BYTES)
            if (includeFolderMembership) card.writeNullableUuid(payload.folderId)
            card.toByteArray()
        }
        try {
            require(nested.size <= SyncProtocolLimits.CARD_PAYLOAD_BYTES) {
                "Card exchange payload exceeds a safety limit."
            }
            output.writeInt(nested.size)
            output.write(nested)
        } finally {
            nested.fill(0)
        }
    }

    fun writeAddressPayload(payload: AddressSyncPayload, includeFolderMembership: Boolean) {
        val nested = BinaryWriter().use { address ->
            address.writeInt(if (includeFolderMembership) 2 else 1)
            address.writeUtf8(payload.nickname, MAX_NICKNAME_BYTES)
            address.writeUtf8(payload.detailedAddress, MAX_ADDRESS_BYTES)
            address.writeUtf8(payload.city, MAX_CITY_BYTES)
            address.writeUtf8(payload.other, MAX_OTHER_BYTES)
            address.writeUtf8(payload.postalCode, MAX_POSTAL_CODE_BYTES)
            address.writeUtf8(payload.country, MAX_COUNTRY_BYTES)
            address.writeUtf8(payload.cardTemplateId, MAX_TEMPLATE_ID_BYTES)
            if (includeFolderMembership) address.writeNullableUuid(payload.folderId)
            address.toByteArray()
        }
        try {
            require(nested.size <= SyncProtocolLimits.ADDRESS_PAYLOAD_BYTES) {
                "Address exchange payload exceeds a safety limit."
            }
            output.writeInt(nested.size)
            output.write(nested)
        } finally {
            nested.fill(0)
        }
    }

    fun writeSnapshot(snapshot: SyncSnapshot, includeFolderMembership: Boolean) {
        // Revalidate at the trust boundary even if a caller retained and mutated a source list.
        val checked = SyncSnapshot(snapshot.records.toList(), snapshot.order.copy(recordIds = snapshot.order.recordIds.toList()))
        output.writeInt(checked.records.size)
        checked.records.sortedBy(SyncRecord::recordId).forEach { record ->
            writeUuid(record.recordId)
            writeVersionVector(record.version)
            when (val value = record.value) {
                is SyncRecordValue.Active -> {
                    writeByte(ACTIVE_RECORD)
                    writeLong(value.createdAtEpochMillis)
                    writeLong(value.updatedAtEpochMillis)
                    writeCardPayload(value.payload, includeFolderMembership)
                }
                is SyncRecordValue.Tombstone -> {
                    writeByte(TOMBSTONE_RECORD)
                    writeLong(value.deletedAtEpochMillis)
                }
            }
        }
        writeVersionVector(checked.order.version)
        writeLong(checked.order.updatedAtEpochMillis)
        output.writeInt(checked.order.recordIds.size)
        checked.order.recordIds.forEach(::writeUuid)
    }

    fun writeAddressSnapshot(
        snapshot: AddressSyncSnapshot,
        includeFolderMembership: Boolean,
    ) {
        val checked = AddressSyncSnapshot(
            records = snapshot.records.toList(),
            order = snapshot.order.copy(recordIds = snapshot.order.recordIds.toList()),
        )
        output.writeInt(checked.records.size)
        checked.records.sortedBy(AddressSyncRecord::recordId).forEach { record ->
            writeUuid(record.recordId)
            writeVersionVector(record.version)
            when (val value = record.value) {
                is AddressSyncRecordValue.Active -> {
                    writeByte(ACTIVE_RECORD)
                    writeLong(value.createdAtEpochMillis)
                    writeLong(value.updatedAtEpochMillis)
                    writeAddressPayload(value.payload, includeFolderMembership)
                }
                is AddressSyncRecordValue.Tombstone -> {
                    writeByte(TOMBSTONE_RECORD)
                    writeLong(value.deletedAtEpochMillis)
                }
            }
        }
        writeVersionVector(checked.order.version)
        writeLong(checked.order.updatedAtEpochMillis)
        output.writeInt(checked.order.recordIds.size)
        checked.order.recordIds.forEach(::writeUuid)
    }

    fun writeFolderSnapshot(snapshot: FolderSyncSnapshot) {
        val checked = FolderSyncSnapshot(snapshot.records.toList())
        output.writeInt(checked.records.size)
        checked.records.sortedBy(FolderSyncRecord::recordId).forEach { record ->
            writeUuid(record.recordId)
            writeVersionVector(record.version)
            when (val value = record.value) {
                is FolderSyncRecordValue.Active -> {
                    writeByte(ACTIVE_RECORD)
                    writeLong(value.createdAtEpochMillis)
                    writeLong(value.updatedAtEpochMillis)
                    val nested = BinaryWriter().use { folder ->
                        folder.writeInt(value.payload.schemaVersion)
                        folder.writeByte(value.payload.collection.ordinal)
                        folder.writeUtf8(value.payload.name, MAX_FOLDER_NAME_BYTES)
                        folder.toByteArray()
                    }
                    try {
                        require(nested.size <= SyncProtocolLimits.FOLDER_PAYLOAD_BYTES)
                        output.writeInt(nested.size)
                        output.write(nested)
                    } finally {
                        nested.fill(0)
                    }
                }
                is FolderSyncRecordValue.Tombstone -> {
                    writeByte(TOMBSTONE_RECORD)
                    writeLong(value.deletedAtEpochMillis)
                }
            }
        }
    }

    fun toByteArray(): ByteArray {
        output.flush()
        return bytes.toByteArray()
    }

    override fun close() {
        output.close()
    }
}

private class BinaryReader(encoded: ByteArray) : AutoCloseable {
    private val input = DataInputStream(ByteArrayInputStream(encoded))

    fun readRaw(count: Int): ByteArray = ByteArray(count).also(input::readFully)

    fun readByte(): Int = input.readUnsignedByte()

    fun readBoolean(): Boolean = when (val value = readByte()) {
        0 -> false
        1 -> true
        else -> invalidFormat()
    }

    fun readInt(): Int = input.readInt()

    fun readLong(): Long = input.readLong()

    fun readUtf8(maxBytes: Int): String {
        val encoded = readLengthBoundedBytes(maxBytes)
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(encoded))
                .toString()
        } finally {
            encoded.fill(0)
        }
    }

    fun readUuid(): String = readUtf8(UUID_BYTES).also(ProtocolValidation::requireUuid)

    fun readSizedBytes(exactBytes: Int): ByteArray {
        val result = readLengthBoundedBytes(exactBytes)
        if (result.size != exactBytes) invalidFormat()
        return result
    }

    fun readVersionVector(): VersionVector {
        val count = readByte()
        if (count > SyncProtocolLimits.DEVICES_PER_VECTOR) limitExceeded()
        val entries = linkedMapOf<String, Long>()
        repeat(count) {
            val deviceId = readUuid()
            val counter = readLong()
            if (counter <= 0L || entries.put(deviceId, counter) != null) invalidFormat()
        }
        return VersionVector.of(entries)
    }

    fun readSnapshot(includeFolderMembership: Boolean): SyncSnapshot {
        val count = readInt()
        if (count !in 0..SyncProtocolLimits.RECORDS) limitExceeded()
        val records = ArrayList<SyncRecord>(count)
        repeat(count) {
            val recordId = readUuid()
            val version = readVersionVector()
            val value = when (readByte()) {
                ACTIVE_RECORD -> {
                    val createdAt = readLong()
                    val updatedAt = readLong()
                    val payload = readCardPayloadCorrectly(includeFolderMembership)
                    SyncRecordValue.Active(payload, createdAt, updatedAt)
                }
                TOMBSTONE_RECORD -> SyncRecordValue.Tombstone(readLong())
                else -> invalidFormat()
            }
            records += SyncRecord(recordId, version, value)
        }
        val orderVersion = readVersionVector()
        val orderUpdatedAt = readLong()
        val orderCount = readInt()
        if (orderCount !in 0..SyncProtocolLimits.ACTIVE_CARDS) limitExceeded()
        val orderIds = List(orderCount) { readUuid() }
        return SyncSnapshot(records, SyncOrder(orderVersion, orderUpdatedAt, orderIds))
    }

    fun readAddressSnapshot(includeFolderMembership: Boolean): AddressSyncSnapshot {
        val count = readInt()
        if (count !in 0..SyncProtocolLimits.ADDRESS_RECORDS) limitExceeded()
        val records = ArrayList<AddressSyncRecord>(count)
        repeat(count) {
            val recordId = readUuid()
            val version = readVersionVector()
            val value = when (readByte()) {
                ACTIVE_RECORD -> {
                    val createdAt = readLong()
                    val updatedAt = readLong()
                    val payload = readAddressPayload(includeFolderMembership)
                    AddressSyncRecordValue.Active(payload, createdAt, updatedAt)
                }
                TOMBSTONE_RECORD -> AddressSyncRecordValue.Tombstone(readLong())
                else -> invalidFormat()
            }
            records += AddressSyncRecord(recordId, version, value)
        }
        val orderVersion = readVersionVector()
        val orderUpdatedAt = readLong()
        val orderCount = readInt()
        if (orderCount !in 0..SyncProtocolLimits.ACTIVE_ADDRESSES) limitExceeded()
        val orderIds = List(orderCount) { readUuid() }
        return AddressSyncSnapshot(records, SyncOrder(orderVersion, orderUpdatedAt, orderIds))
    }

    private fun readCardPayloadCorrectly(includeFolderMembership: Boolean): CardSyncPayload {
        val encoded = readLengthBoundedBytes(SyncProtocolLimits.CARD_PAYLOAD_BYTES)
        return try {
            BinaryReader(encoded).use { card ->
                val schemaVersion = card.readInt()
                if (schemaVersion !in 1..2) throw SyncProtocolException(SyncErrorCode.UNSUPPORTED_VERSION)
                val nickname = card.readUtf8(MAX_NICKNAME_BYTES)
                val issuerName = card.readUtf8(MAX_ISSUER_BYTES)
                val cardNumber = card.readUtf8(MAX_CARD_NUMBER_BYTES)
                val expiryMonth = card.readInt()
                val expiryYear = card.readInt()
                val saveCvv = card.readBoolean()
                val cvv = if (saveCvv) card.readUtf8(MAX_CVV_BYTES) else null
                val templateId = card.readUtf8(MAX_TEMPLATE_ID_BYTES)
                val notes = card.readUtf8(MAX_NOTES_BYTES)
                val folderId = if (includeFolderMembership && schemaVersion >= 2) {
                    card.readNullableUuid()
                } else null
                card.requireEnd()
                CardSyncPayload(
                    schemaVersion = schemaVersion,
                    nickname = nickname,
                    issuerName = issuerName,
                    cardNumber = cardNumber,
                    expiryMonth = expiryMonth,
                    expiryYear = expiryYear,
                    saveCvv = saveCvv,
                    cvv = cvv,
                    cardTemplateId = templateId,
                    notes = notes,
                    folderId = folderId,
                )
            }
        } finally {
            encoded.fill(0)
        }
    }

    private fun readAddressPayload(includeFolderMembership: Boolean): AddressSyncPayload {
        val encoded = readLengthBoundedBytes(SyncProtocolLimits.ADDRESS_PAYLOAD_BYTES)
        return try {
            BinaryReader(encoded).use { address ->
                val schemaVersion = address.readInt()
                if (schemaVersion !in 1..2) {
                    throw SyncProtocolException(SyncErrorCode.UNSUPPORTED_VERSION)
                }
                val payload = AddressSyncPayload(
                    schemaVersion = schemaVersion,
                    nickname = address.readUtf8(MAX_NICKNAME_BYTES),
                    detailedAddress = address.readUtf8(MAX_ADDRESS_BYTES),
                    city = address.readUtf8(MAX_CITY_BYTES),
                    other = address.readUtf8(MAX_OTHER_BYTES),
                    postalCode = address.readUtf8(MAX_POSTAL_CODE_BYTES),
                    country = address.readUtf8(MAX_COUNTRY_BYTES),
                    cardTemplateId = address.readUtf8(MAX_TEMPLATE_ID_BYTES),
                    folderId = if (includeFolderMembership && schemaVersion >= 2) {
                        address.readNullableUuid()
                    } else null,
                )
                address.requireEnd()
                payload
            }
        } finally {
            encoded.fill(0)
        }
    }

    fun readFolderSnapshot(): FolderSyncSnapshot {
        val count = readInt()
        if (count !in 0..SyncProtocolLimits.FOLDER_RECORDS) limitExceeded()
        val records = ArrayList<FolderSyncRecord>(count)
        repeat(count) {
            val recordId = readUuid()
            val version = readVersionVector()
            val value = when (readByte()) {
                ACTIVE_RECORD -> {
                    val createdAt = readLong()
                    val updatedAt = readLong()
                    val encoded = readLengthBoundedBytes(SyncProtocolLimits.FOLDER_PAYLOAD_BYTES)
                    val payload = try {
                        BinaryReader(encoded).use { folder ->
                            val schemaVersion = folder.readInt()
                            if (schemaVersion != 1) {
                                throw SyncProtocolException(SyncErrorCode.UNSUPPORTED_VERSION)
                            }
                            val collectionOrdinal = folder.readByte()
                            val collection = FolderCollectionKind.entries.getOrNull(collectionOrdinal)
                                ?: invalidFormat()
                            FolderSyncPayload(
                                schemaVersion = schemaVersion,
                                collection = collection,
                                name = folder.readUtf8(MAX_FOLDER_NAME_BYTES),
                            ).also { folder.requireEnd() }
                        }
                    } finally {
                        encoded.fill(0)
                    }
                    FolderSyncRecordValue.Active(payload, createdAt, updatedAt)
                }
                TOMBSTONE_RECORD -> FolderSyncRecordValue.Tombstone(readLong())
                else -> invalidFormat()
            }
            records += FolderSyncRecord(recordId, version, value)
        }
        return FolderSyncSnapshot(records)
    }

    private fun readNullableUuid(): String? = if (readBoolean()) readUuid() else null

    private fun readLengthBoundedBytes(maxBytes: Int): ByteArray {
        val length = readInt()
        if (length < 0) invalidFormat()
        if (length > maxBytes) limitExceeded()
        if (length > input.available()) invalidFormat()
        return ByteArray(length).also(input::readFully)
    }

    fun requireEnd() {
        if (input.available() != 0) invalidFormat()
    }

    override fun close() {
        input.close()
    }
}

private inline fun <T> decodeSafely(block: () -> T): T = try {
    block()
} catch (error: SyncProtocolException) {
    throw error
} catch (error: EOFException) {
    throw SyncProtocolException(SyncErrorCode.INVALID_FORMAT, error)
} catch (error: CharacterCodingException) {
    throw SyncProtocolException(SyncErrorCode.INVALID_FORMAT, error)
} catch (error: IllegalArgumentException) {
    throw SyncProtocolException(SyncErrorCode.INVALID_FORMAT, error)
} catch (error: NegativeArraySizeException) {
    throw SyncProtocolException(SyncErrorCode.INVALID_FORMAT, error)
}

private fun requirePlaintextSize(encoded: ByteArray) {
    if (encoded.isEmpty()) invalidFormat()
    if (encoded.size > MAX_PLAINTEXT_BYTES) limitExceeded()
}

private fun invalidFormat(): Nothing = throw SyncProtocolException(SyncErrorCode.INVALID_FORMAT)

private fun limitExceeded(): Nothing = throw SyncProtocolException(SyncErrorCode.LIMIT_EXCEEDED)

private const val MAX_PLAINTEXT_BYTES = SyncProtocolLimits.FILE_BYTES - 64 - 16
private const val UUID_BYTES = 36
private const val MAX_NICKNAME_BYTES = 200
private const val MAX_ISSUER_BYTES = 320
private const val MAX_CARD_NUMBER_BYTES = 19
private const val MAX_CVV_BYTES = 4
private const val MAX_TEMPLATE_ID_BYTES = 400
private const val MAX_NOTES_BYTES = 4000
private const val MAX_ADDRESS_BYTES = 2000
private const val MAX_CITY_BYTES = 400
private const val MAX_OTHER_BYTES = 800
private const val MAX_POSTAL_CODE_BYTES = 80
private const val MAX_COUNTRY_BYTES = 400
private const val MAX_FOLDER_NAME_BYTES = 200
private const val ACTIVE_RECORD = 1
private const val TOMBSTONE_RECORD = 2
