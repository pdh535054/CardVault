package com.pdh.cardvault.sync.android

import com.pdh.cardvault.security.crypto.EncryptedDataAuthenticationException
import com.pdh.cardvault.sync.VersionVector
import java.security.SecureRandom
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncKeyCryptorTest {
    @Test
    fun syncKeyRoundTripsOnlyWithSameLocalDekVaultAndEpoch() {
        val cryptor = SyncKeyCryptor()
        val localDek = randomBytes(32)
        val syncKey = randomBytes(32)
        val vaultId = UUID.randomUUID().toString()

        val encrypted = cryptor.encrypt(syncKey, localDek, vaultId, 1)
        val decrypted = cryptor.decrypt(encrypted, localDek, vaultId, 1)

        try {
            assertArrayEquals(syncKey, decrypted)
            assertFalse(encrypted.ciphertextCopy().contentEquals(syncKey))
            assertFalse(encrypted.toString().contains(syncKey.toHex()))
        } finally {
            localDek.fill(0)
            syncKey.fill(0)
            decrypted.fill(0)
        }
    }

    @Test
    fun sameSyncKeyUsesFreshIvAndCiphertext() {
        val cryptor = SyncKeyCryptor()
        val localDek = randomBytes(32)
        val syncKey = randomBytes(32)
        val vaultId = UUID.randomUUID().toString()

        val first = cryptor.encrypt(syncKey, localDek, vaultId, 1)
        val second = cryptor.encrypt(syncKey, localDek, vaultId, 1)

        try {
            assertNotEquals(first.ivCopy().toList(), second.ivCopy().toList())
            assertNotEquals(first.ciphertextCopy().toList(), second.ciphertextCopy().toList())
        } finally {
            localDek.fill(0)
            syncKey.fill(0)
        }
    }

    @Test
    fun changingVaultOrEpochFailsAuthenticatedDecryption() {
        val cryptor = SyncKeyCryptor()
        val localDek = randomBytes(32)
        val syncKey = randomBytes(32)
        val vaultId = UUID.randomUUID().toString()
        val encrypted = cryptor.encrypt(syncKey, localDek, vaultId, 1)

        try {
            val wrongVault = runCatching {
                cryptor.decrypt(encrypted, localDek, UUID.randomUUID().toString(), 1)
            }.exceptionOrNull()
            val wrongEpoch = runCatching {
                cryptor.decrypt(encrypted, localDek, vaultId, 2)
            }.exceptionOrNull()

            assertTrue(wrongVault is EncryptedDataAuthenticationException)
            assertTrue(wrongEpoch is EncryptedDataAuthenticationException)
        } finally {
            localDek.fill(0)
            syncKey.fill(0)
        }
    }

    @Test
    fun storedVersionVectorRoundTripsAndIncrementsDeterministically() {
        val firstDevice = UUID.randomUUID().toString()
        val secondDevice = UUID.randomUUID().toString()
        val initial = StoredVersionVectors.initial(firstDevice)
        val incremented = StoredVersionVectors.increment(initial, secondDevice)
        val decoded = StoredVersionVectors.decode(incremented)

        assertEqualsVector(
            VersionVector.of(mapOf(firstDevice to 1L, secondDevice to 1L)),
            decoded,
        )
        assertArrayEquals(incremented, StoredVersionVectors.encode(decoded))
    }

    private fun assertEqualsVector(expected: VersionVector, actual: VersionVector) {
        assertTrue(expected == actual)
        assertTrue(actual.toString().contains("entries=2"))
        assertFalse(actual.toString().contains(actual.entries.keys.first()))
    }

    private fun randomBytes(size: Int): ByteArray =
        ByteArray(size).also(SecureRandom()::nextBytes)
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    (byte.toInt() and 0xff).toString(16).padStart(2, '0')
}
