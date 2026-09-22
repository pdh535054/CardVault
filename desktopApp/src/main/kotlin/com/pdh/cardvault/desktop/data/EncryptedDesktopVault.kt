package com.pdh.cardvault.desktop.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptedDesktopVault(
    private val directory: Path = defaultDirectory(),
    private val protector: LocalKeyProtector = WindowsDpapiKeyProtector(),
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    private val keyFile = directory.resolve("vault.key")
    private val dataFile = directory.resolve("vault.data")

    @Synchronized
    fun load(): DesktopVaultSnapshot {
        Files.createDirectories(directory)
        if (!Files.exists(dataFile)) {
            // Verify DPAPI and persist the protected local key before the UI becomes usable.
            // Previously an empty vault skipped this check, so a packaged-runtime DPAPI
            // problem was discovered only after a fully verified import tried to save.
            loadOrCreateKey().fill(0)
            return DesktopVaultSnapshot(emptyList(), revision = 0)
        }
        val key = loadOrCreateKey()
        val cleartext = try {
            decrypt(Files.readAllBytes(dataFile), key)
        } finally {
            key.fill(0)
        }
        return try {
            DesktopVaultCodec.decode(cleartext)
        } finally {
            cleartext.fill(0)
        }
    }

    @Synchronized
    fun save(snapshot: DesktopVaultSnapshot) {
        Files.createDirectories(directory)
        val key = loadOrCreateKey()
        val cleartext = DesktopVaultCodec.encode(snapshot)
        val encrypted = try {
            encrypt(cleartext, key)
        } finally {
            cleartext.fill(0)
            key.fill(0)
        }
        try {
            writeAtomically(dataFile, encrypted)
        } finally {
            encrypted.fill(0)
        }
    }

    private fun loadOrCreateKey(): ByteArray {
        if (Files.exists(keyFile)) {
            val protectedKey = decodeKeyFile(Files.readAllBytes(keyFile))
            return try {
                protector.unprotect(protectedKey).also { plain ->
                    if (plain.size != KEY_BYTES) {
                        plain.fill(0)
                        throw LocalKeyUnavailableException()
                    }
                }
            } finally {
                protectedKey.fill(0)
            }
        }
        val key = ByteArray(KEY_BYTES).also(secureRandom::nextBytes)
        val protectedKey = try {
            protector.protect(key)
        } catch (error: Throwable) {
            key.fill(0)
            throw error
        }
        try {
            writeAtomically(keyFile, encodeKeyFile(protectedKey))
        } finally {
            protectedKey.fill(0)
        }
        return key
    }

    private fun encrypt(cleartext: ByteArray, key: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(DATA_AAD)
        val ciphertext = cipher.doFinal(cleartext)
        return ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { data ->
                data.write(DATA_MAGIC)
                data.writeInt(DATA_VERSION)
                data.writeByte(iv.size)
                data.write(iv)
                data.writeInt(ciphertext.size)
                data.write(ciphertext)
            }
            iv.fill(0)
            ciphertext.fill(0)
        }.toByteArray()
    }

    private fun decrypt(encoded: ByteArray, key: ByteArray): ByteArray = try {
        DataInputStream(ByteArrayInputStream(encoded)).use { data ->
            val magic = ByteArray(DATA_MAGIC.size).also(data::readFully)
            if (!magic.contentEquals(DATA_MAGIC) || data.readInt() != DATA_VERSION) {
                throw InvalidVaultException()
            }
            val ivLength = data.readUnsignedByte()
            if (ivLength != IV_BYTES) throw InvalidVaultException()
            val iv = ByteArray(ivLength).also(data::readFully)
            val cipherLength = data.readInt()
            if (cipherLength !in 16..MAX_FILE_BYTES || cipherLength != data.available()) {
                throw InvalidVaultException()
            }
            val ciphertext = ByteArray(cipherLength).also(data::readFully)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
                cipher.updateAAD(DATA_AAD)
                cipher.doFinal(ciphertext)
            } finally {
                iv.fill(0)
                ciphertext.fill(0)
            }
        }
    } catch (_: AEADBadTagException) {
        throw InvalidVaultException()
    } catch (_: InvalidVaultException) {
        throw InvalidVaultException()
    } catch (_: Exception) {
        throw InvalidVaultException()
    }

    private fun encodeKeyFile(protectedKey: ByteArray): ByteArray =
        ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { data ->
                data.write(KEY_MAGIC)
                data.writeInt(KEY_VERSION)
                data.writeInt(protectedKey.size)
                data.write(protectedKey)
            }
        }.toByteArray()

    private fun decodeKeyFile(encoded: ByteArray): ByteArray = try {
        DataInputStream(ByteArrayInputStream(encoded)).use { data ->
            val magic = ByteArray(KEY_MAGIC.size).also(data::readFully)
            if (!magic.contentEquals(KEY_MAGIC) || data.readInt() != KEY_VERSION) {
                throw LocalKeyUnavailableException()
            }
            val size = data.readInt()
            if (size !in 16..64 * 1024 || size != data.available()) throw LocalKeyUnavailableException()
            ByteArray(size).also(data::readFully)
        }
    } catch (_: Exception) {
        throw LocalKeyUnavailableException()
    }

    private fun writeAtomically(target: Path, bytes: ByteArray) {
        val temp = target.resolveSibling(".${target.fileName}.${System.nanoTime()}.tmp")
        try {
            FileChannel.open(
                temp,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
            ).use { channel ->
                val buffer = java.nio.ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    companion object {
        private const val KEY_BYTES = 32
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val KEY_VERSION = 1
        private const val DATA_VERSION = 1
        private const val MAX_FILE_BYTES = 4 * 1024 * 1024
        private val KEY_MAGIC = "CVKEY01\n".toByteArray(Charsets.US_ASCII)
        private val DATA_MAGIC = "CVDATA01".toByteArray(Charsets.US_ASCII)
        private val DATA_AAD = "CardVault/DesktopVault/v1".toByteArray(Charsets.US_ASCII)

        fun defaultDirectory(): Path {
            val localAppData = System.getenv("LOCALAPPDATA")
                ?.takeIf(String::isNotBlank)
                ?: Path.of(System.getProperty("user.home"), "AppData", "Local").toString()
            return Path.of(localAppData, "CardVault")
        }
    }
}
