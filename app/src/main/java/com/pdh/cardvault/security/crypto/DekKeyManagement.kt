package com.pdh.cardvault.security.crypto

import java.security.SecureRandom
import javax.crypto.SecretKey

class DekGenerator(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun generate(): ByteArray = ByteArray(DEK_LENGTH_BYTES).also(secureRandom::nextBytes)
}

internal class DekWrapper(
    private val cipher: AesGcmCipher,
) {
    constructor() : this(AesGcmCipher())

    fun wrap(dek: ByteArray, kek: SecretKey): WrappedDek {
        if (dek.size != DEK_LENGTH_BYTES) throw VaultKeyUnavailableException()
        val aad = AadEncoder.forWrappedDek(
            CURRENT_WRAPPING_FORMAT_VERSION,
            CURRENT_KEK_ALIAS_VERSION,
        )
        return try {
            val envelope = cipher.encrypt(dek, kek, aad)
            WrappedDek(
                wrappingFormatVersion = CURRENT_WRAPPING_FORMAT_VERSION,
                kekAliasVersion = CURRENT_KEK_ALIAS_VERSION,
                wrappedDek = envelope.ciphertextCopy(),
                wrappingIv = envelope.ivCopy(),
            )
        } finally {
            aad.fill(0)
        }
    }

    fun unwrap(wrappedDek: WrappedDek, kek: SecretKey): ByteArray {
        if (
            wrappedDek.wrappingFormatVersion != CURRENT_WRAPPING_FORMAT_VERSION ||
            wrappedDek.kekAliasVersion != CURRENT_KEK_ALIAS_VERSION
        ) {
            throw UnsupportedCryptoVersionException()
        }

        val aad = AadEncoder.forWrappedDek(
            wrappedDek.wrappingFormatVersion,
            wrappedDek.kekAliasVersion,
        )
        val envelope = try {
            AeadEnvelope(
                ciphertext = wrappedDek.wrappedDekCopy(),
                iv = wrappedDek.wrappingIvCopy(),
            )
        } catch (_: IllegalArgumentException) {
            aad.fill(0)
            throw InvalidEncryptedPayloadException()
        }
        val unwrapped = try {
            cipher.decrypt(envelope, kek, aad)
        } finally {
            aad.fill(0)
        }
        if (unwrapped.size != DEK_LENGTH_BYTES) {
            unwrapped.fill(0)
            throw InvalidEncryptedPayloadException()
        }
        return unwrapped
    }

    companion object {
        const val CURRENT_WRAPPING_FORMAT_VERSION = 1
        const val CURRENT_KEK_ALIAS_VERSION = 1
    }
}

internal class ForegroundDekSession : AutoCloseable {
    private var activeDek: ByteArray? = null

    @Synchronized
    fun load(dek: ByteArray) {
        if (dek.size != DEK_LENGTH_BYTES) throw VaultKeyUnavailableException()
        clearLocked()
        activeDek = dek.copyOf()
    }

    fun <T> use(block: (ByteArray) -> T): T {
        val workingCopy = synchronized(this) {
            activeDek?.copyOf() ?: throw VaultKeyUnavailableException()
        }
        return try {
            block(workingCopy)
        } finally {
            workingCopy.fill(0)
        }
    }

    suspend fun <T> useSuspending(block: suspend (ByteArray) -> T): T {
        val workingCopy = synchronized(this) {
            activeDek?.copyOf() ?: throw VaultKeyUnavailableException()
        }
        return try {
            block(workingCopy)
        } finally {
            workingCopy.fill(0)
        }
    }

    @Synchronized
    fun isLoaded(): Boolean = activeDek != null

    @Synchronized
    fun clear() {
        clearLocked()
    }

    override fun close() = clear()

    private fun clearLocked() {
        activeDek?.fill(0)
        activeDek = null
    }

    override fun toString(): String = "ForegroundDekSession(keyMaterial=redacted)"
}
