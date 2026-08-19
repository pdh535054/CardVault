package com.pdh.cardvault.sync

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CardVaultSyncFiles {
    const val PAIRING_EXTENSION: String = "cvpair"
    const val SYNC_EXTENSION: String = "cvsync"

    fun generateSyncSecret(random: SecureRandom = SecureRandom()): SecretBytes =
        ByteArray(SYNC_SECRET_BYTES).let { bytes ->
            random.nextBytes(bytes)
            try {
                SecretBytes(bytes)
            } finally {
                bytes.fill(0)
            }
        }

    /** Strictly inspects the public header without trusting a filename or MIME type. */
    @Throws(SyncProtocolException::class)
    fun inspectKind(fileBytes: ByteArray): SyncFileKind = FileCrypto.inspectKind(fileBytes)

    fun encodePairing(
        payload: PairingFilePayload,
        pairingSecret: ByteArray,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        require(pairingSecret.size == PAIRING_SECRET_BYTES) { "Invalid pairing secret." }
        val plaintext = PackagePayloadCodec.encodePairing(payload)
        return try {
            FileCrypto.encrypt(SyncFileKind.PAIRING, payload.keyEpoch, pairingSecret, plaintext, random)
        } finally {
            plaintext.fill(0)
        }
    }

    @Throws(SyncProtocolException::class)
    fun decodePairing(fileBytes: ByteArray, pairingSecret: ByteArray): PairingFilePayload {
        if (pairingSecret.size != PAIRING_SECRET_BYTES) {
            throw SyncProtocolException(SyncErrorCode.INVALID_PAIRING_CODE)
        }
        val opened = FileCrypto.decrypt(SyncFileKind.PAIRING, pairingSecret, fileBytes)
        return try {
            PackagePayloadCodec.decodePairing(opened.plaintext).also { payload ->
                if (payload.keyEpoch != opened.keyEpoch) {
                    payload.syncSecret.close()
                    throw SyncProtocolException(SyncErrorCode.INVALID_FORMAT)
                }
            }
        } finally {
            opened.plaintext.fill(0)
        }
    }

    fun requirePairingUsableAt(payload: PairingFilePayload, nowEpochMillis: Long) {
        if (nowEpochMillis < payload.exportedAtEpochMillis || nowEpochMillis > payload.expiresAtEpochMillis) {
            throw SyncProtocolException(SyncErrorCode.STALE_PACKAGE)
        }
    }

    fun encodeSync(
        payload: SyncFilePayload,
        syncSecret: ByteArray,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        require(syncSecret.size == SYNC_SECRET_BYTES) { "Invalid sync secret." }
        val plaintext = PackagePayloadCodec.encodeSync(payload)
        return try {
            FileCrypto.encrypt(SyncFileKind.SYNC, payload.keyEpoch, syncSecret, plaintext, random)
        } finally {
            plaintext.fill(0)
        }
    }

    @Throws(SyncProtocolException::class)
    fun decodeSync(fileBytes: ByteArray, syncSecret: ByteArray): SyncFilePayload {
        if (syncSecret.size != SYNC_SECRET_BYTES) {
            throw SyncProtocolException(SyncErrorCode.AUTHENTICATION_FAILED)
        }
        val opened = FileCrypto.decrypt(SyncFileKind.SYNC, syncSecret, fileBytes)
        return try {
            PackagePayloadCodec.decodeSync(opened.plaintext).also { payload ->
                if (payload.keyEpoch != opened.keyEpoch) {
                    throw SyncProtocolException(SyncErrorCode.INVALID_FORMAT)
                }
            }
        } finally {
            opened.plaintext.fill(0)
        }
    }

    private const val PAIRING_SECRET_BYTES = 16
    private const val SYNC_SECRET_BYTES = 32
}

enum class SyncFileKind(internal val id: Int) {
    PAIRING(1),
    SYNC(2),
    ;

    companion object {
        internal fun fromId(id: Int): SyncFileKind = entries.firstOrNull { it.id == id }
            ?: throw SyncProtocolException(SyncErrorCode.INVALID_FORMAT)
    }
}

internal data class OpenedFile(val keyEpoch: Int, val plaintext: ByteArray)

internal object FileCrypto {
    private const val HEADER_BYTES = 64
    private const val TAG_BYTES = 16
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BYTES = 32
    private const val MAJOR_VERSION = 1
    private const val MINOR_VERSION = 0
    private const val KDF_HKDF_SHA256 = 1
    private const val AEAD_AES_256_GCM = 1
    private val magic = "CVLTBIN\n".toByteArray(StandardCharsets.US_ASCII)
    private val hkdfLabel = "CardVault/FileKey/v1".toByteArray(StandardCharsets.US_ASCII)

    fun encrypt(
        kind: SyncFileKind,
        keyEpoch: Int,
        rootKey: ByteArray,
        plaintext: ByteArray,
        random: SecureRandom,
    ): ByteArray {
        val salt = ByteArray(SALT_BYTES)
        val iv = ByteArray(IV_BYTES)
        random.nextBytes(salt)
        random.nextBytes(iv)
        return try {
            encryptWithParameters(kind, keyEpoch, rootKey, plaintext, salt, iv)
        } finally {
            salt.fill(0)
            iv.fill(0)
        }
    }

    fun encryptWithParameters(
        kind: SyncFileKind,
        keyEpoch: Int,
        rootKey: ByteArray,
        plaintext: ByteArray,
        salt: ByteArray,
        iv: ByteArray,
    ): ByteArray {
        require(keyEpoch > 0) { "Invalid key metadata." }
        require(rootKey.size in setOf(16, 32)) { "Invalid root key." }
        require(salt.size == SALT_BYTES && iv.size == IV_BYTES) { "Invalid encryption parameters." }
        val ciphertextLength = plaintext.size.toLong() + TAG_BYTES
        require(ciphertextLength in TAG_BYTES.toLong()..(SyncProtocolLimits.FILE_BYTES - HEADER_BYTES).toLong()) {
            "Exchange payload exceeds a safety limit."
        }
        val header = encodeHeader(kind, keyEpoch, salt, iv, ciphertextLength.toInt())
        val fileKey = deriveFileKey(rootKey, salt, kind, keyEpoch)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(fileKey, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(header)
            val ciphertext = cipher.doFinal(plaintext)
            check(ciphertext.size == ciphertextLength.toInt())
            header + ciphertext
        } catch (error: GeneralSecurityException) {
            throw IllegalStateException("CardVault cryptography is unavailable.", error)
        } finally {
            fileKey.fill(0)
        }
    }

    fun inspectKind(fileBytes: ByteArray): SyncFileKind {
        validateFileSize(fileBytes)
        val headerBytes = fileBytes.copyOfRange(0, HEADER_BYTES)
        val header = decodeHeader(headerBytes, fileBytes.size)
        return try {
            header.kind
        } finally {
            header.salt.fill(0)
            header.iv.fill(0)
            headerBytes.fill(0)
        }
    }

    fun decrypt(expectedKind: SyncFileKind, rootKey: ByteArray, fileBytes: ByteArray): OpenedFile {
        validateFileSize(fileBytes)
        val headerBytes = fileBytes.copyOfRange(0, HEADER_BYTES)
        val header = decodeHeader(headerBytes, fileBytes.size)
        if (header.kind != expectedKind) {
            header.salt.fill(0)
            header.iv.fill(0)
            headerBytes.fill(0)
            throw SyncProtocolException(SyncErrorCode.INVALID_FORMAT)
        }
        val fileKey = deriveFileKey(rootKey, header.salt, header.kind, header.keyEpoch)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(fileKey, "AES"), GCMParameterSpec(128, header.iv))
            cipher.updateAAD(headerBytes)
            val plaintext = cipher.doFinal(fileBytes, HEADER_BYTES, header.ciphertextLength)
            OpenedFile(header.keyEpoch, plaintext)
        } catch (error: AEADBadTagException) {
            throw SyncProtocolException(SyncErrorCode.AUTHENTICATION_FAILED, error)
        } catch (error: GeneralSecurityException) {
            throw SyncProtocolException(SyncErrorCode.AUTHENTICATION_FAILED, error)
        } finally {
            fileKey.fill(0)
            header.salt.fill(0)
            header.iv.fill(0)
            headerBytes.fill(0)
        }
    }

    private fun validateFileSize(fileBytes: ByteArray) {
        if (fileBytes.size !in (HEADER_BYTES + TAG_BYTES)..SyncProtocolLimits.FILE_BYTES) {
            throw SyncProtocolException(
                if (fileBytes.size > SyncProtocolLimits.FILE_BYTES) SyncErrorCode.LIMIT_EXCEEDED else SyncErrorCode.INVALID_FORMAT,
            )
        }
    }

    fun deriveFileKey(
        rootKey: ByteArray,
        salt: ByteArray,
        kind: SyncFileKind,
        keyEpoch: Int,
    ): ByteArray {
        require(rootKey.isNotEmpty() && salt.size == SALT_BYTES && keyEpoch > 0)
        val info = ByteBuffer.allocate(hkdfLabel.size + 7)
            .order(ByteOrder.BIG_ENDIAN)
            .put(hkdfLabel)
            .put(kind.id.toByte())
            .put(MAJOR_VERSION.toByte())
            .put(MINOR_VERSION.toByte())
            .putInt(keyEpoch)
            .array()
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(salt, "HmacSHA256"))
        val pseudoRandomKey = extract.doFinal(rootKey)
        return try {
            val expand = Mac.getInstance("HmacSHA256")
            expand.init(SecretKeySpec(pseudoRandomKey, "HmacSHA256"))
            expand.update(info)
            expand.doFinal(byteArrayOf(1)).copyOf(KEY_BYTES)
        } finally {
            pseudoRandomKey.fill(0)
            info.fill(0)
        }
    }

    private fun encodeHeader(
        kind: SyncFileKind,
        keyEpoch: Int,
        salt: ByteArray,
        iv: ByteArray,
        ciphertextLength: Int,
    ): ByteArray = ByteBuffer.allocate(HEADER_BYTES)
        .order(ByteOrder.BIG_ENDIAN)
        .put(magic)
        .put(kind.id.toByte())
        .put(MAJOR_VERSION.toByte())
        .put(MINOR_VERSION.toByte())
        .put(0.toByte())
        .putShort(HEADER_BYTES.toShort())
        .putShort(0.toShort())
        .putInt(keyEpoch)
        .put(KDF_HKDF_SHA256.toByte())
        .put(AEAD_AES_256_GCM.toByte())
        .putShort(0.toShort())
        .put(salt)
        .put(iv)
        .putInt(ciphertextLength)
        .putLong(0L)
        .array()

    private fun decodeHeader(encoded: ByteArray, completeFileSize: Int): FileHeader {
        try {
            val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
            val actualMagic = ByteArray(magic.size).also(buffer::get)
            if (!actualMagic.contentEquals(magic)) invalidHeader()
            val kind = SyncFileKind.fromId(buffer.get().toInt() and 0xff)
            val major = buffer.get().toInt() and 0xff
            val minor = buffer.get().toInt() and 0xff
            val flags = buffer.get().toInt() and 0xff
            val headerLength = buffer.short.toInt() and 0xffff
            val reservedOne = buffer.short.toInt() and 0xffff
            val keyEpoch = buffer.int
            val kdf = buffer.get().toInt() and 0xff
            val aead = buffer.get().toInt() and 0xff
            val reservedTwo = buffer.short.toInt() and 0xffff
            val salt = ByteArray(SALT_BYTES).also(buffer::get)
            val iv = ByteArray(IV_BYTES).also(buffer::get)
            val ciphertextLength = buffer.int
            val reservedThree = buffer.long
            if (major != MAJOR_VERSION || minor != MINOR_VERSION) {
                salt.fill(0)
                iv.fill(0)
                throw SyncProtocolException(SyncErrorCode.UNSUPPORTED_VERSION)
            }
            if (
                flags != 0 || headerLength != HEADER_BYTES || reservedOne != 0 || keyEpoch <= 0 ||
                kdf != KDF_HKDF_SHA256 || aead != AEAD_AES_256_GCM || reservedTwo != 0 ||
                ciphertextLength < TAG_BYTES || ciphertextLength != completeFileSize - HEADER_BYTES || reservedThree != 0L
            ) {
                salt.fill(0)
                iv.fill(0)
                invalidHeader()
            }
            return FileHeader(kind, keyEpoch, salt, iv, ciphertextLength)
        } catch (error: SyncProtocolException) {
            throw error
        } catch (error: RuntimeException) {
            throw SyncProtocolException(SyncErrorCode.INVALID_FORMAT, error)
        }
    }

    private fun invalidHeader(): Nothing = throw SyncProtocolException(SyncErrorCode.INVALID_FORMAT)

    private data class FileHeader(
        val kind: SyncFileKind,
        val keyEpoch: Int,
        val salt: ByteArray,
        val iv: ByteArray,
        val ciphertextLength: Int,
    )
}
