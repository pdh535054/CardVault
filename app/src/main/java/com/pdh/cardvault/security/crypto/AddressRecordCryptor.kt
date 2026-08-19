package com.pdh.cardvault.security.crypto

import java.util.UUID
import javax.crypto.spec.SecretKeySpec

class EncryptedAddressRecord(
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

    override fun toString(): String = "EncryptedAddressRecord(redacted)"
}

class AddressRecordCryptor internal constructor(
    private val payloadCodec: AddressPayloadCodec,
    private val cipher: AesGcmCipher,
) {
    constructor() : this(AddressPayloadCodec(), AesGcmCipher())

    fun encrypt(recordId: UUID, payload: AddressPayload, dek: ByteArray): EncryptedAddressRecord {
        val keyBytes = validatedDekCopy(dek)
        val plaintext = payloadCodec.encode(payload)
        val aad = AadEncoder.forAddress(recordId, payloadCodec.currentSchemaVersion)
        return try {
            val envelope = cipher.encrypt(plaintext, SecretKeySpec(keyBytes, AES_ALGORITHM), aad)
            EncryptedAddressRecord(
                payloadSchemaVersion = payloadCodec.currentSchemaVersion,
                cryptoVersion = CURRENT_CRYPTO_VERSION,
                ciphertext = envelope.ciphertextCopy(),
                recordIv = envelope.ivCopy(),
            )
        } finally {
            keyBytes.fill(0)
            plaintext.fill(0)
            aad.fill(0)
        }
    }

    fun decrypt(
        recordId: UUID,
        encryptedRecord: EncryptedAddressRecord,
        dek: ByteArray,
    ): AddressPayload {
        if (
            encryptedRecord.cryptoVersion != CURRENT_CRYPTO_VERSION ||
            !payloadCodec.isSupportedSchemaVersion(encryptedRecord.payloadSchemaVersion)
        ) {
            throw UnsupportedCryptoVersionException()
        }
        val keyBytes = validatedDekCopy(dek)
        val aad = AadEncoder.forAddress(recordId, encryptedRecord.payloadSchemaVersion)
        val envelope = try {
            AeadEnvelope(encryptedRecord.ciphertextCopy(), encryptedRecord.recordIvCopy())
        } catch (_: IllegalArgumentException) {
            keyBytes.fill(0)
            aad.fill(0)
            throw InvalidEncryptedPayloadException()
        }
        var plaintext: ByteArray? = null
        return try {
            plaintext = cipher.decrypt(envelope, SecretKeySpec(keyBytes, AES_ALGORITHM), aad)
            payloadCodec.decode(plaintext, encryptedRecord.payloadSchemaVersion)
        } finally {
            keyBytes.fill(0)
            aad.fill(0)
            plaintext?.fill(0)
        }
    }

    private fun validatedDekCopy(dek: ByteArray): ByteArray {
        if (dek.size != DEK_LENGTH_BYTES) throw VaultKeyUnavailableException()
        return dek.copyOf()
    }

    companion object {
        const val CURRENT_CRYPTO_VERSION = 1
        private const val AES_ALGORITHM = "AES"
    }
}
