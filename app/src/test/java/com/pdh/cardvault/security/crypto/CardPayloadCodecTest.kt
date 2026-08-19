package com.pdh.cardvault.security.crypto

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CardPayloadCodecTest {
    private val codec = CardPayloadCodec()

    @Test
    fun versionedPayloadRoundTripsDeterministically() {
        val payload = syntheticCardPayload()

        val first = codec.encode(payload)
        val second = codec.encode(payload)

        assertArrayEquals(first, second)
        assertEquals(payload, codec.decode(first, codec.currentSchemaVersion))
    }

    @Test
    fun unicodeBusinessFieldLimitsUseCharactersRatherThanUtf16CodeUnits() {
        val payload = syntheticCardPayload().copy(
            nickname = "🔐".repeat(50),
            issuerName = "🏦".repeat(80),
            notes = "🗒️".repeat(500),
        )

        val encoded = codec.encode(payload)

        assertEquals(payload, codec.decode(encoded, codec.currentSchemaVersion))
    }

    @Test
    fun disabledCvvIsAbsentRatherThanSerializedAsHiddenData() {
        val withCvv = codec.encode(syntheticCardPayload(saveCvv = true))
        val withoutCvv = codec.encode(syntheticCardPayload(saveCvv = false))
        val cvvBytes = "8".repeat(3).toByteArray(StandardCharsets.UTF_8)

        assertEquals(Int.SIZE_BYTES + cvvBytes.size, withCvv.size - withoutCvv.size)
        assertFalse(withoutCvv.containsSequence(cvvBytes))
        assertEquals(null, codec.decode(withoutCvv, codec.currentSchemaVersion).cvv)
    }

    @Test
    fun modelRejectsCvvWhenSaveCvvIsDisabled() {
        val valid = syntheticCardPayload(saveCvv = false)

        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(cvv = "8".repeat(3))
        }
    }

    @Test
    fun unknownExpectedSchemaVersionFailsClosed() {
        val encoded = codec.encode(syntheticCardPayload())

        assertThrows(UnsupportedCryptoVersionException::class.java) {
            codec.decode(encoded, expectedSchemaVersion = 2)
        }
    }

    @Test
    fun unknownEmbeddedSchemaVersionFailsClosed() {
        val encoded = codec.encode(syntheticCardPayload())
        val schemaOffset = Int.SIZE_BYTES + "CardVault/Payload".toByteArray().size
        ByteBuffer.wrap(encoded)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(schemaOffset, 2)

        assertThrows(UnsupportedCryptoVersionException::class.java) {
            codec.decode(encoded, expectedSchemaVersion = codec.currentSchemaVersion)
        }
    }

    @Test
    fun trailingOrMalformedPayloadDataIsRejected() {
        val encoded = codec.encode(syntheticCardPayload())
        val withTrailingByte = encoded + 1.toByte()

        assertThrows(InvalidEncryptedPayloadException::class.java) {
            codec.decode(withTrailingByte, codec.currentSchemaVersion)
        }
        assertThrows(InvalidEncryptedPayloadException::class.java) {
            codec.decode(encoded.copyOf(12), codec.currentSchemaVersion)
        }
    }

    @Test
    fun payloadStringRepresentationRedactsEveryBusinessField() {
        val payload = syntheticCardPayload()
        val rendered = payload.toString()

        assertFalse(rendered.contains(payload.nickname))
        assertFalse(rendered.contains(payload.issuerName))
        assertFalse(rendered.contains(payload.cardNumber))
        assertFalse(rendered.contains(requireNotNull(payload.cvv)))
        assertTrue(rendered.contains("redacted"))
    }
}

private fun ByteArray.containsSequence(sequence: ByteArray): Boolean {
    if (sequence.isEmpty()) return true
    return indices.any { start ->
        start + sequence.size <= size &&
            sequence.indices.all { offset -> this[start + offset] == sequence[offset] }
    }
}
