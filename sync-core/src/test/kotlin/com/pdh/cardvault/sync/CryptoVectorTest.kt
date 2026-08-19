package com.pdh.cardvault.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class CryptoVectorTest {
    @Test
    fun `published HKDF and AES GCM vector is exact`() {
        val rootKey = "000102030405060708090a0b0c0d0e0f".hexBytes()
        val salt = "101112131415161718191a1b1c1d1e1f".hexBytes()
        val iv = "202122232425262728292a2b".hexBytes()
        val plaintext = ByteArray(16) { it.toByte() }

        val fileKey = FileCrypto.deriveFileKey(rootKey, salt, SyncFileKind.PAIRING, 1)
        assertEquals(
            "8db8c1ac8e37300d7d54f03fc6b2cceedd4ab53f2bcc1df86b43cbc5d507a328",
            fileKey.hex(),
        )

        val encrypted = FileCrypto.encryptWithParameters(
            kind = SyncFileKind.PAIRING,
            keyEpoch = 1,
            rootKey = rootKey,
            plaintext = plaintext,
            salt = salt,
            iv = iv,
        )
        assertEquals(
            "43564c5442494e0a01010000004000000000000101010000" +
                "101112131415161718191a1b1c1d1e1f" +
                "202122232425262728292a2b000000200000000000000000",
            encrypted.copyOfRange(0, 64).hex(),
        )
        assertEquals("577e3434258e884ba1d4b6e53a1e1391", encrypted.copyOfRange(64, 80).hex())
        assertEquals("bbb5a0c5e4559960d3ff8acb4c727396", encrypted.copyOfRange(80, 96).hex())

        val opened = FileCrypto.decrypt(SyncFileKind.PAIRING, rootKey, encrypted)
        assertEquals(1, opened.keyEpoch)
        assertContentEquals(plaintext, opened.plaintext)
    }

    @Test
    fun `strict inspection rejects truncation reserved fields and unsupported versions`() {
        val key = ByteArray(16) { it.toByte() }
        val file = FileCrypto.encryptWithParameters(
            SyncFileKind.PAIRING,
            1,
            key,
            "payload".encodeToByteArray(),
            ByteArray(16) { (it + 16).toByte() },
            ByteArray(12) { (it + 32).toByte() },
        )
        assertEquals(SyncFileKind.PAIRING, CardVaultSyncFiles.inspectKind(file))

        listOf(0, 1, 63, 64, file.lastIndex).forEach { length ->
            val error = assertFailsWith<SyncProtocolException> {
                CardVaultSyncFiles.inspectKind(file.copyOf(length))
            }
            assertEquals(SyncErrorCode.INVALID_FORMAT, error.code)
        }

        val reserved = file.copyOf().also { it[11] = 1 }
        assertEquals(
            SyncErrorCode.INVALID_FORMAT,
            assertFailsWith<SyncProtocolException> { CardVaultSyncFiles.inspectKind(reserved) }.code,
        )

        val unsupported = file.copyOf().also { it[9] = 2 }
        assertEquals(
            SyncErrorCode.UNSUPPORTED_VERSION,
            assertFailsWith<SyncProtocolException> { CardVaultSyncFiles.inspectKind(unsupported) }.code,
        )
    }

    @Test
    fun `salt ciphertext tag and wrong key changes all fail authentication`() {
        val key = ByteArray(32) { it.toByte() }
        val file = FileCrypto.encryptWithParameters(
            SyncFileKind.SYNC,
            1,
            key,
            "authenticated content".encodeToByteArray(),
            ByteArray(16) { (it + 20).toByte() },
            ByteArray(12) { (it + 40).toByte() },
        )
        val mutations = listOf(24, 63, 64, file.lastIndex)
        mutations.forEach { offset ->
            val changed = file.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() }
            val error = assertFailsWith<SyncProtocolException> {
                FileCrypto.decrypt(SyncFileKind.SYNC, key, changed)
            }
            if (offset == 63) {
                assertEquals(SyncErrorCode.INVALID_FORMAT, error.code)
            } else {
                assertEquals(SyncErrorCode.AUTHENTICATION_FAILED, error.code)
            }
        }
        val wrongKey = key.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertEquals(
            SyncErrorCode.AUTHENTICATION_FAILED,
            assertFailsWith<SyncProtocolException> {
                FileCrypto.decrypt(SyncFileKind.SYNC, wrongKey, file)
            }.code,
        )
    }
}
