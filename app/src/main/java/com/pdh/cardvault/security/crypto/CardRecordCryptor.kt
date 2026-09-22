package com.pdh.cardvault.security.crypto

import java.util.UUID
import javax.crypto.spec.SecretKeySpec

class CardRecordCryptor internal constructor(
    private val payloadCodec: CardPayloadCodec,
    private val cipher: AesGcmCipher,
) {
    constructor() : this(CardPayloadCodec(), AesGcmCipher())

    fun encrypt(
        recordId: UUID,
        payload: CardPayload,
        dek: ByteArray,
    ): EncryptedCardRecord {
        val keyBytes = validatedDekCopy(dek)
        val plaintext = payloadCodec.encode(payload)
        val aad = AadEncoder.forCard(recordId, payloadCodec.currentSchemaVersion)
        return try {
            val envelope = cipher.encrypt(
                plaintext = plaintext,
                key = SecretKeySpec(keyBytes, AES_ALGORITHM),
                aad = aad,
            )
            EncryptedCardRecord(
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
        encryptedRecord: EncryptedCardRecord,
        dek: ByteArray,
    ): CardPayload {
        if (
            encryptedRecord.cryptoVersion != CURRENT_CRYPTO_VERSION ||
            !payloadCodec.isSupportedSchemaVersion(encryptedRecord.payloadSchemaVersion)
        ) {
            throw UnsupportedCryptoVersionException()
        }

        val keyBytes = validatedDekCopy(dek)
        val aad = AadEncoder.forCard(recordId, encryptedRecord.payloadSchemaVersion)
        val envelope = try {
            AeadEnvelope(
                ciphertext = encryptedRecord.ciphertextCopy(),
                iv = encryptedRecord.recordIvCopy(),
            )
        } catch (_: IllegalArgumentException) {
            keyBytes.fill(0)
            aad.fill(0)
            throw InvalidEncryptedPayloadException()
        }
        var plaintext: ByteArray? = null
        return try {
            plaintext = cipher.decrypt(
                envelope = envelope,
                key = SecretKeySpec(keyBytes, AES_ALGORITHM),
                aad = aad,
            )
            payloadCodec.decode(
                encodedPayload = plaintext,
                expectedSchemaVersion = encryptedRecord.payloadSchemaVersion,
            )
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
