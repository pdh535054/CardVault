package com.pdh.cardvault.sync.android

import com.pdh.cardvault.security.crypto.AeadEnvelope
import com.pdh.cardvault.security.crypto.AesGcmCipher
import com.pdh.cardvault.security.crypto.VaultKeyUnavailableException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

internal class EncryptedSyncKey(
    val cryptoVersion: Int,
    ciphertext: ByteArray,
    iv: ByteArray,
) {
    private val ciphertextBytes = ciphertext.copyOf()
    private val ivBytes = iv.copyOf()

    fun ciphertextCopy(): ByteArray = ciphertextBytes.copyOf()

    fun ivCopy(): ByteArray = ivBytes.copyOf()

    override fun toString(): String = "EncryptedSyncKey(keyMaterial=redacted)"
}

/**
 * Encrypts the cross-device sync secret under this installation's foreground-only local DEK.
 *
 * The vault id and key epoch are authenticated as AAD so a database row cannot be transplanted
 * between vaults or key generations without detection.
 */
internal class SyncKeyCryptor(
    private val cipher: AesGcmCipher = AesGcmCipher(),
) {
    fun encrypt(
        syncKey: ByteArray,
        localDek: ByteArray,
        vaultId: String,
        keyEpoch: Int,
    ): EncryptedSyncKey {
        require(syncKey.size == SYNC_KEY_BYTES) { "Invalid synchronization key." }
        val key = requireLocalDekCopy(localDek)
        val aad = aad(vaultId, keyEpoch)
        return try {
            val envelope = cipher.encrypt(
                plaintext = syncKey,
                key = SecretKeySpec(key, AES),
                aad = aad,
            )
            EncryptedSyncKey(
                cryptoVersion = CRYPTO_VERSION,
                ciphertext = envelope.ciphertextCopy(),
                iv = envelope.ivCopy(),
            )
        } finally {
            key.fill(0)
            aad.fill(0)
        }
    }

    fun decrypt(
        encrypted: EncryptedSyncKey,
        localDek: ByteArray,
        vaultId: String,
        keyEpoch: Int,
    ): ByteArray {
        if (encrypted.cryptoVersion != CRYPTO_VERSION) {
            throw InvalidStoredSyncMetadataException()
        }
        val key = requireLocalDekCopy(localDek)
        val aad = aad(vaultId, keyEpoch)
        return try {
            val plaintext = cipher.decrypt(
                envelope = AeadEnvelope(
                    ciphertext = encrypted.ciphertextCopy(),
                    iv = encrypted.ivCopy(),
                ),
                key = SecretKeySpec(key, AES),
                aad = aad,
            )
            if (plaintext.size != SYNC_KEY_BYTES) {
                plaintext.fill(0)
                throw InvalidStoredSyncMetadataException()
            }
            plaintext
        } catch (_: IllegalArgumentException) {
            throw InvalidStoredSyncMetadataException()
        } finally {
            key.fill(0)
            aad.fill(0)
        }
    }

    private fun aad(vaultId: String, keyEpoch: Int): ByteArray {
        val vault = UUID.fromString(vaultId)
        require(vault.toString() == vaultId && keyEpoch > 0) {
            "Invalid synchronization metadata."
        }
        return ByteBuffer.allocate(AAD_MAGIC.size + 16 + Int.SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .put(AAD_MAGIC)
            .putLong(vault.mostSignificantBits)
            .putLong(vault.leastSignificantBits)
            .putInt(keyEpoch)
            .array()
    }

    private fun requireLocalDekCopy(localDek: ByteArray): ByteArray {
        if (localDek.size != LOCAL_DEK_BYTES) throw VaultKeyUnavailableException()
        return localDek.copyOf()
    }

    private companion object {
        const val CRYPTO_VERSION = 1
        const val SYNC_KEY_BYTES = 32
        const val LOCAL_DEK_BYTES = 32
        const val AES = "AES"
        val AAD_MAGIC = "CardVault/SyncKey/v1".toByteArray(StandardCharsets.US_ASCII)
    }
}
