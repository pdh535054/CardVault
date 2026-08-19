package com.pdh.cardvault.security.crypto

import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRecordCryptorTest {
    private val cryptor = CardRecordCryptor()
    private val generator = DekGenerator()

    @Test
    fun recordEncryptionRoundTrips() {
        val recordId = UUID.randomUUID()
        val dek = generator.generate()
        try {
            val encrypted = cryptor.encrypt(recordId, syntheticCardPayload(), dek)

            assertEquals(syntheticCardPayload(), cryptor.decrypt(recordId, encrypted, dek))
            assertEquals(12, encrypted.recordIvCopy().size)
        } finally {
            dek.fill(0)
        }
    }

    @Test
    fun samePayloadAndAadProduceDifferentIvAndCiphertext() {
        val recordId = UUID.randomUUID()
        val payload = syntheticCardPayload()
        val dek = generator.generate()
        try {
            val first = cryptor.encrypt(recordId, payload, dek)
            val second = cryptor.encrypt(recordId, payload, dek)

            assertFalse(first.recordIvCopy().contentEquals(second.recordIvCopy()))
            assertFalse(first.ciphertextCopy().contentEquals(second.ciphertextCopy()))
        } finally {
            dek.fill(0)
        }
    }

    @Test
    fun modifiedCiphertextFailsAuthentication() {
        assertTamperingFails { encrypted ->
            val ciphertext = encrypted.ciphertextCopy().also { it[it.lastIndex] = it.last().inc() }
            EncryptedCardRecord(
                encrypted.payloadSchemaVersion,
                encrypted.cryptoVersion,
                ciphertext,
                encrypted.recordIvCopy(),
            )
        }
    }

    @Test
    fun modifiedIvFailsAuthentication() {
        assertTamperingFails { encrypted ->
            val iv = encrypted.recordIvCopy().also { it[0] = it[0].inc() }
            EncryptedCardRecord(
                encrypted.payloadSchemaVersion,
                encrypted.cryptoVersion,
                encrypted.ciphertextCopy(),
                iv,
            )
        }
    }

    @Test
    fun modifiedRecordIdFailsAuthentication() {
        val originalRecordId = UUID.randomUUID()
        val dek = generator.generate()
        try {
            val encrypted = cryptor.encrypt(originalRecordId, syntheticCardPayload(), dek)

            assertThrows(EncryptedDataAuthenticationException::class.java) {
                cryptor.decrypt(UUID.randomUUID(), encrypted, dek)
            }
        } finally {
            dek.fill(0)
        }
    }

    @Test
    fun modifiedSchemaVersionFailsClosed() {
        val recordId = UUID.randomUUID()
        val dek = generator.generate()
        try {
            val encrypted = cryptor.encrypt(recordId, syntheticCardPayload(), dek)
            val modified = EncryptedCardRecord(
                payloadSchemaVersion = encrypted.payloadSchemaVersion + 1,
                cryptoVersion = encrypted.cryptoVersion,
                ciphertext = encrypted.ciphertextCopy(),
                recordIv = encrypted.recordIvCopy(),
            )

            assertThrows(UnsupportedCryptoVersionException::class.java) {
                cryptor.decrypt(recordId, modified, dek)
            }
        } finally {
            dek.fill(0)
        }
    }

    @Test
    fun directlyModifiedAadFailsAuthentication() {
        val dek = generator.generate()
        val plaintext = "synthetic cryptographic marker".toByteArray(StandardCharsets.UTF_8)
        val originalAad = "CardVault/test-aad".toByteArray(StandardCharsets.UTF_8)
        val modifiedAad = originalAad.copyOf().also { it[it.lastIndex] = it.last().inc() }
        try {
            val cipher = AesGcmCipher()
            val envelope = cipher.encrypt(plaintext, SecretKeySpec(dek, "AES"), originalAad)

            assertThrows(EncryptedDataAuthenticationException::class.java) {
                cipher.decrypt(envelope, SecretKeySpec(dek, "AES"), modifiedAad)
            }
        } finally {
            dek.fill(0)
            plaintext.fill(0)
            originalAad.fill(0)
            modifiedAad.fill(0)
        }
    }

    @Test
    fun wrongDekFailsAuthentication() {
        val recordId = UUID.randomUUID()
        val correctDek = generator.generate()
        val wrongDek = generator.generate()
        try {
            val encrypted = cryptor.encrypt(recordId, syntheticCardPayload(), correctDek)

            assertThrows(EncryptedDataAuthenticationException::class.java) {
                cryptor.decrypt(recordId, encrypted, wrongDek)
            }
        } finally {
            correctDek.fill(0)
            wrongDek.fill(0)
        }
    }

    @Test
    fun unknownCryptoVersionFailsClosed() {
        val recordId = UUID.randomUUID()
        val dek = generator.generate()
        try {
            val encrypted = cryptor.encrypt(recordId, syntheticCardPayload(), dek)
            val unknownVersion = EncryptedCardRecord(
                encrypted.payloadSchemaVersion,
                encrypted.cryptoVersion + 1,
                encrypted.ciphertextCopy(),
                encrypted.recordIvCopy(),
            )

            assertThrows(UnsupportedCryptoVersionException::class.java) {
                cryptor.decrypt(recordId, unknownVersion, dek)
            }
        } finally {
            dek.fill(0)
        }
    }

    @Test
    fun cryptographicErrorsAndModelsDoNotExposeSensitiveInputs() {
        val recordId = UUID.randomUUID()
        val payload = syntheticCardPayload()
        val dek = generator.generate()
        val wrongDek = generator.generate()
        try {
            val encrypted = cryptor.encrypt(recordId, payload, dek)
            val exception = assertThrows(EncryptedDataAuthenticationException::class.java) {
                cryptor.decrypt(recordId, encrypted, wrongDek)
            }
            val output = listOf(exception.toString(), encrypted.toString()).joinToString()

            assertFalse(output.contains(payload.nickname))
            assertFalse(output.contains(payload.cardNumber))
            assertFalse(output.contains(requireNotNull(payload.cvv)))
            assertFalse(output.contains(recordId.toString()))
            assertTrue(exception.cause == null)
            assertNotEquals(encrypted.ciphertextCopy().contentToString(), encrypted.toString())
        } finally {
            dek.fill(0)
            wrongDek.fill(0)
        }
    }

    private fun assertTamperingFails(
        transform: (EncryptedCardRecord) -> EncryptedCardRecord,
    ) {
        val recordId = UUID.randomUUID()
        val dek = generator.generate()
        try {
            val encrypted = cryptor.encrypt(recordId, syntheticCardPayload(), dek)

            assertThrows(EncryptedDataAuthenticationException::class.java) {
                cryptor.decrypt(recordId, transform(encrypted), dek)
            }
        } finally {
            dek.fill(0)
        }
    }
}
