package com.pdh.cardvault.security.crypto

internal class AeadEnvelope(
    ciphertext: ByteArray,
    iv: ByteArray,
) {
    private val ciphertextBytes = ciphertext.copyOf()
    private val ivBytes = iv.copyOf()

    init {
        require(ciphertextBytes.size >= GCM_TAG_LENGTH_BYTES) {
            "The authenticated ciphertext has an invalid length."
        }
        require(ivBytes.size == GCM_IV_LENGTH_BYTES) {
            "The authenticated ciphertext IV has an invalid length."
        }
    }

    fun ciphertextCopy(): ByteArray = ciphertextBytes.copyOf()

    fun ivCopy(): ByteArray = ivBytes.copyOf()

    override fun toString(): String = "AeadEnvelope(redacted)"
}

class EncryptedCardRecord(
    val payloadSchemaVersion: Int,
    val cryptoVersion: Int,
    ciphertext: ByteArray,
    recordIv: ByteArray,
) {
    private val ciphertextBytes = ciphertext.copyOf()
    private val recordIvBytes = recordIv.copyOf()

    init {
        require(ciphertextBytes.size >= GCM_TAG_LENGTH_BYTES) {
            "The encrypted record has an invalid length."
        }
        require(recordIvBytes.size == GCM_IV_LENGTH_BYTES) {
            "The encrypted record IV has an invalid length."
        }
    }

    fun ciphertextCopy(): ByteArray = ciphertextBytes.copyOf()

    fun recordIvCopy(): ByteArray = recordIvBytes.copyOf()

    override fun toString(): String = "EncryptedCardRecord(redacted)"
}

class WrappedDek(
    val wrappingFormatVersion: Int,
    val kekAliasVersion: Int,
    wrappedDek: ByteArray,
    wrappingIv: ByteArray,
) {
    private val wrappedDekBytes = wrappedDek.copyOf()
    private val wrappingIvBytes = wrappingIv.copyOf()

    init {
        require(wrappedDekBytes.size >= DEK_LENGTH_BYTES + GCM_TAG_LENGTH_BYTES) {
            "The wrapped vault key has an invalid length."
        }
        require(wrappingIvBytes.size == GCM_IV_LENGTH_BYTES) {
            "The wrapped vault key IV has an invalid length."
        }
    }

    fun wrappedDekCopy(): ByteArray = wrappedDekBytes.copyOf()

    fun wrappingIvCopy(): ByteArray = wrappingIvBytes.copyOf()

    override fun toString(): String = "WrappedDek(redacted)"
}

internal const val DEK_LENGTH_BYTES = 32
internal const val GCM_IV_LENGTH_BYTES = 12
internal const val GCM_TAG_LENGTH_BITS = 128
internal const val GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / Byte.SIZE_BITS
