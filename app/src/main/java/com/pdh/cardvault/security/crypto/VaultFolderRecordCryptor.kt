package com.pdh.cardvault.security.crypto

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

enum class VaultFolderCollection {
    CARDS,
    ADDRESSES,
}

data class VaultFolderPayload(
    val collection: VaultFolderCollection,
    val name: String,
) {
    init {
        require(name == name.trim() && name.codePointCount(0, name.length) in 1..50) {
            "The folder payload is invalid."
        }
    }

    override fun toString(): String = "VaultFolderPayload(name=redacted, collection=$collection)"
}

class EncryptedVaultFolderRecord(
    val payloadSchemaVersion: Int,
    val cryptoVersion: Int,
    ciphertext: ByteArray,
    recordIv: ByteArray,
) {
    private val ciphertextBytes = ciphertext.copyOf()
    private val recordIvBytes = recordIv.copyOf()

    init {
        require(ciphertextBytes.size >= GCM_TAG_LENGTH_BYTES)
        require(recordIvBytes.size == GCM_IV_LENGTH_BYTES)
    }

    fun ciphertextCopy(): ByteArray = ciphertextBytes.copyOf()
    fun recordIvCopy(): ByteArray = recordIvBytes.copyOf()
    override fun toString(): String = "EncryptedVaultFolderRecord(redacted)"
}

class VaultFolderRecordCryptor internal constructor(
    private val cipher: AesGcmCipher,
) {
    constructor() : this(AesGcmCipher())

    fun encrypt(recordId: UUID, payload: VaultFolderPayload, dek: ByteArray): EncryptedVaultFolderRecord {
        val keyBytes = validatedDekCopy(dek)
        val plaintext = encode(payload)
        val aad = AadEncoder.forFolder(recordId, SCHEMA_VERSION)
        return try {
            val envelope = cipher.encrypt(plaintext, SecretKeySpec(keyBytes, AES_ALGORITHM), aad)
            EncryptedVaultFolderRecord(
                payloadSchemaVersion = SCHEMA_VERSION,
                cryptoVersion = CRYPTO_VERSION,
                ciphertext = envelope.ciphertextCopy(),
                recordIv = envelope.ivCopy(),
            )
        } finally {
            keyBytes.fill(0)
            plaintext.fill(0)
            aad.fill(0)
        }
    }

    fun decrypt(recordId: UUID, encrypted: EncryptedVaultFolderRecord, dek: ByteArray): VaultFolderPayload {
        if (encrypted.cryptoVersion != CRYPTO_VERSION || encrypted.payloadSchemaVersion != SCHEMA_VERSION) {
            throw UnsupportedCryptoVersionException()
        }
        val keyBytes = validatedDekCopy(dek)
        val aad = AadEncoder.forFolder(recordId, encrypted.payloadSchemaVersion)
        val envelope = try {
            AeadEnvelope(encrypted.ciphertextCopy(), encrypted.recordIvCopy())
        } catch (_: IllegalArgumentException) {
            keyBytes.fill(0)
            aad.fill(0)
            throw InvalidEncryptedPayloadException()
        }
        var plaintext: ByteArray? = null
        return try {
            plaintext = cipher.decrypt(envelope, SecretKeySpec(keyBytes, AES_ALGORITHM), aad)
            decode(plaintext)
        } finally {
            keyBytes.fill(0)
            aad.fill(0)
            plaintext?.fill(0)
        }
    }

    private fun encode(payload: VaultFolderPayload): ByteArray {
        val name = payload.name.toByteArray(StandardCharsets.UTF_8)
        return try {
            require(name.size <= MAX_NAME_BYTES)
            ByteBuffer.allocate(Int.SIZE_BYTES * 4 + MAGIC.size + name.size)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(MAGIC.size)
                .put(MAGIC)
                .putInt(SCHEMA_VERSION)
                .putInt(payload.collection.ordinal)
                .putInt(name.size)
                .put(name)
                .array()
        } finally {
            name.fill(0)
        }
    }

    private fun decode(encoded: ByteArray): VaultFolderPayload = try {
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        val magicLength = buffer.int
        if (magicLength != MAGIC.size || magicLength > buffer.remaining()) throw InvalidEncryptedPayloadException()
        val magic = ByteArray(magicLength).also(buffer::get)
        try {
            if (!magic.contentEquals(MAGIC)) throw InvalidEncryptedPayloadException()
        } finally {
            magic.fill(0)
        }
        if (buffer.int != SCHEMA_VERSION) throw UnsupportedCryptoVersionException()
        val collection = VaultFolderCollection.entries.getOrNull(buffer.int)
            ?: throw InvalidEncryptedPayloadException()
        val nameLength = buffer.int
        if (nameLength !in 1..MAX_NAME_BYTES || nameLength > buffer.remaining()) {
            throw InvalidEncryptedPayloadException()
        }
        val bytes = ByteArray(nameLength).also(buffer::get)
        val name = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } finally {
            bytes.fill(0)
        }
        if (buffer.hasRemaining()) throw InvalidEncryptedPayloadException()
        VaultFolderPayload(collection, name)
    } catch (exception: UnsupportedCryptoVersionException) {
        throw exception
    } catch (exception: InvalidEncryptedPayloadException) {
        throw exception
    } catch (_: BufferUnderflowException) {
        throw InvalidEncryptedPayloadException()
    } catch (_: IllegalArgumentException) {
        throw InvalidEncryptedPayloadException()
    }

    private fun validatedDekCopy(dek: ByteArray): ByteArray {
        if (dek.size != DEK_LENGTH_BYTES) throw VaultKeyUnavailableException()
        return dek.copyOf()
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val CRYPTO_VERSION = 1
        const val MAX_NAME_BYTES = 200
        const val AES_ALGORITHM = "AES"
        val MAGIC = "CardVault/FolderPayload".toByteArray(StandardCharsets.UTF_8)
    }
}
