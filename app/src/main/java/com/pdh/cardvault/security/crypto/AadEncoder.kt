package com.pdh.cardvault.security.crypto

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID

internal object AadEncoder {
    private val cardDomain = "CardVault/Card".toByteArray(StandardCharsets.UTF_8)
    private val addressDomain = "CardVault/Address".toByteArray(StandardCharsets.UTF_8)
    private val folderDomain = "CardVault/Folder".toByteArray(StandardCharsets.UTF_8)
    private val dekDomain = "CardVault/DEK".toByteArray(StandardCharsets.UTF_8)

    fun forCard(recordId: UUID, payloadSchemaVersion: Int): ByteArray =
        ByteBuffer.allocate(Int.SIZE_BYTES + cardDomain.size + UUID_BYTES + Int.SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(cardDomain.size)
            .put(cardDomain)
            .putLong(recordId.mostSignificantBits)
            .putLong(recordId.leastSignificantBits)
            .putInt(payloadSchemaVersion)
            .array()

    fun forAddress(recordId: UUID, payloadSchemaVersion: Int): ByteArray =
        ByteBuffer.allocate(Int.SIZE_BYTES + addressDomain.size + UUID_BYTES + Int.SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(addressDomain.size)
            .put(addressDomain)
            .putLong(recordId.mostSignificantBits)
            .putLong(recordId.leastSignificantBits)
            .putInt(payloadSchemaVersion)
            .array()

    fun forFolder(recordId: UUID, payloadSchemaVersion: Int): ByteArray =
        ByteBuffer.allocate(Int.SIZE_BYTES + folderDomain.size + UUID_BYTES + Int.SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(folderDomain.size)
            .put(folderDomain)
            .putLong(recordId.mostSignificantBits)
            .putLong(recordId.leastSignificantBits)
            .putInt(payloadSchemaVersion)
            .array()

    fun forWrappedDek(wrappingFormatVersion: Int, kekAliasVersion: Int): ByteArray =
        ByteBuffer.allocate(Int.SIZE_BYTES + dekDomain.size + Int.SIZE_BYTES * 2)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(dekDomain.size)
            .put(dekDomain)
            .putInt(wrappingFormatVersion)
            .putInt(kekAliasVersion)
            .array()

    private const val UUID_BYTES = Long.SIZE_BYTES * 2
}
