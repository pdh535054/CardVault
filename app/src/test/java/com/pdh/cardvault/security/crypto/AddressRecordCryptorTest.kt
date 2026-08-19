package com.pdh.cardvault.security.crypto

import java.util.UUID
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AddressRecordCryptorTest {
    private val cryptor = AddressRecordCryptor()

    @Test
    fun encryptedAddressRoundTripsAndUsesFreshIv() {
        val id = UUID.randomUUID()
        val dek = DekGenerator().generate()
        val payload = syntheticPayload()
        try {
            val first = cryptor.encrypt(id, payload, dek)
            val second = cryptor.encrypt(id, payload, dek)

            assertEquals(payload, cryptor.decrypt(id, first, dek))
            assertFalse(first.recordIvCopy().contentEquals(second.recordIvCopy()))
            assertFalse(first.ciphertextCopy().contentEquals(second.ciphertextCopy()))
        } finally {
            dek.fill(0)
        }
    }

    @Test
    fun changedRecordIdOrCiphertextFailsAuthentication() {
        val id = UUID.randomUUID()
        val dek = DekGenerator().generate()
        try {
            val encrypted = cryptor.encrypt(id, syntheticPayload(), dek)
            assertThrows(EncryptedDataAuthenticationException::class.java) {
                cryptor.decrypt(UUID.randomUUID(), encrypted, dek)
            }
            val changedCiphertext = encrypted.ciphertextCopy().also {
                it[it.lastIndex] = it.last().inc()
            }
            assertThrows(EncryptedDataAuthenticationException::class.java) {
                cryptor.decrypt(
                    id,
                    EncryptedAddressRecord(
                        encrypted.payloadSchemaVersion,
                        encrypted.cryptoVersion,
                        changedCiphertext,
                        encrypted.recordIvCopy(),
                    ),
                    dek,
                )
            }
        } finally {
            dek.fill(0)
        }
    }

    @Test
    fun modelsDoNotPrintSensitiveAddressFields() {
        val id = UUID.randomUUID()
        val dek = DekGenerator().generate()
        val payload = syntheticPayload()
        try {
            val encrypted = cryptor.encrypt(id, payload, dek)
            val output = listOf(payload, encrypted).joinToString()
            assertFalse(output.contains(payload.detailedAddress))
            assertFalse(output.contains(payload.postalCode))
        } finally {
            dek.fill(0)
        }
    }

    @Test
    fun legacyVersionOnePayloadRemainsReadableWithEmptyCountry() {
        val values = listOf(
            "Legacy address",
            "Fictional avenue block 7",
            "Example City",
            "Imaginary floor",
            "TEST-007",
            "custom:v2:obsidian:contours:modern:auto",
        ).map { it.toByteArray(StandardCharsets.UTF_8) }
        val magic = "CardVault/AddressPayload".toByteArray(StandardCharsets.UTF_8)
        val encoded = ByteBuffer.allocate(
            Int.SIZE_BYTES + magic.size + Int.SIZE_BYTES +
                values.sumOf { Int.SIZE_BYTES + it.size },
        ).order(ByteOrder.BIG_ENDIAN)
            .putInt(magic.size)
            .put(magic)
            .putInt(1)
            .apply { values.forEach { value -> putInt(value.size).put(value) } }
            .array()

        val decoded = AddressPayloadCodec().decode(encoded, expectedSchemaVersion = 1)

        assertEquals("", decoded.country)
        assertEquals("Legacy address", decoded.nickname)
    }

    private fun syntheticPayload(): AddressPayload = AddressPayload(
        nickname = "Synthetic address",
        detailedAddress = "Fictional avenue block 42",
        city = "Example City",
        other = "Imaginary floor",
        postalCode = "TEST-042",
        country = "Example Country",
        cardTemplateId = "custom:v2:obsidian:contours:modern:auto",
    )
}
