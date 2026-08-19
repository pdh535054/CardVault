package com.pdh.cardvault.security.crypto

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class AddressPayload(
    val nickname: String,
    val detailedAddress: String,
    val city: String,
    val other: String,
    val postalCode: String,
    val country: String,
    val cardTemplateId: String,
) {
    init {
        require(nickname == nickname.trim() && nickname.codePointLength() in 1..50)
        require(
            detailedAddress == detailedAddress.trim() &&
                detailedAddress.codePointLength() in 1..500,
        )
        require(city == city.trim() && city.codePointLength() in 1..100)
        require(other == other.trim() && other.codePointLength() <= 200)
        require(postalCode == postalCode.trim() && postalCode.codePointLength() in 1..20)
        // Empty is accepted only when decoding a legacy version-1 payload created before
        // CardVault supported the country field. Newly submitted records require a country.
        require(country == country.trim() && country.codePointLength() <= 100)
        require(cardTemplateId.isNotBlank() && cardTemplateId.length <= 400)
    }

    override fun toString(): String = "AddressPayload(sensitiveFields=redacted)"
}

class AddressPayloadCodec {
    val currentSchemaVersion: Int = CURRENT_SCHEMA_VERSION

    fun encode(payload: AddressPayload): ByteArray {
        val values = listOf(
            payload.nickname,
            payload.detailedAddress,
            payload.city,
            payload.other,
            payload.postalCode,
            payload.country,
            payload.cardTemplateId,
        ).map { value -> value.encodedUtf8() }
        return try {
            val totalSize =
                Int.SIZE_BYTES + MAGIC.size + Int.SIZE_BYTES +
                    values.sumOf(::encodedStringSize)
            require(totalSize <= MAX_PAYLOAD_BYTES)
            ByteBuffer.allocate(totalSize)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(MAGIC.size)
                .put(MAGIC)
                .putInt(CURRENT_SCHEMA_VERSION)
                .apply { values.forEach { bytes -> putEncodedString(bytes) } }
                .array()
        } finally {
            values.forEach { bytes -> bytes.fill(0) }
        }
    }

    fun decode(encodedPayload: ByteArray, expectedSchemaVersion: Int): AddressPayload {
        if (!isSupportedSchemaVersion(expectedSchemaVersion)) {
            throw UnsupportedCryptoVersionException()
        }
        if (encodedPayload.size !in MIN_PAYLOAD_BYTES..MAX_PAYLOAD_BYTES) {
            throw InvalidEncryptedPayloadException()
        }
        return try {
            val buffer = ByteBuffer.wrap(encodedPayload).order(ByteOrder.BIG_ENDIAN)
            val magic = buffer.readBytes(MAX_MAGIC_BYTES)
            try {
                if (!magic.contentEquals(MAGIC)) throw InvalidEncryptedPayloadException()
            } finally {
                magic.fill(0)
            }
            if (buffer.int != expectedSchemaVersion) throw UnsupportedCryptoVersionException()
            val nickname = buffer.readString(MAX_NICKNAME_BYTES)
            val detailedAddress = buffer.readString(MAX_ADDRESS_BYTES)
            val city = buffer.readString(MAX_CITY_BYTES)
            val other = buffer.readString(MAX_OTHER_BYTES)
            val postalCode = buffer.readString(MAX_POSTAL_CODE_BYTES)
            val country = if (expectedSchemaVersion >= COUNTRY_SCHEMA_VERSION) {
                buffer.readString(MAX_COUNTRY_BYTES)
            } else {
                ""
            }
            val payload = AddressPayload(
                nickname = nickname,
                detailedAddress = detailedAddress,
                city = city,
                other = other,
                postalCode = postalCode,
                country = country,
                cardTemplateId = buffer.readString(MAX_TEMPLATE_ID_BYTES),
            )
            if (buffer.hasRemaining()) throw InvalidEncryptedPayloadException()
            payload
        } catch (exception: UnsupportedCryptoVersionException) {
            throw exception
        } catch (exception: InvalidEncryptedPayloadException) {
            throw exception
        } catch (_: BufferUnderflowException) {
            throw InvalidEncryptedPayloadException()
        } catch (_: CharacterCodingException) {
            throw InvalidEncryptedPayloadException()
        } catch (_: IllegalArgumentException) {
            throw InvalidEncryptedPayloadException()
        }
    }

    fun isSupportedSchemaVersion(version: Int): Boolean =
        version in LEGACY_SCHEMA_VERSION..CURRENT_SCHEMA_VERSION

    private fun ByteBuffer.readString(maxBytes: Int): String {
        val bytes = readBytes(maxBytes)
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } finally {
            bytes.fill(0)
        }
    }

    private fun ByteBuffer.readBytes(maxBytes: Int): ByteArray {
        val length = int
        if (length !in 0..maxBytes || length > remaining()) {
            throw InvalidEncryptedPayloadException()
        }
        return ByteArray(length).also(::get)
    }

    private companion object {
        const val LEGACY_SCHEMA_VERSION = 1
        const val COUNTRY_SCHEMA_VERSION = 2
        const val CURRENT_SCHEMA_VERSION = COUNTRY_SCHEMA_VERSION
        const val MAX_PAYLOAD_BYTES = 16 * 1024
        const val MIN_PAYLOAD_BYTES = 48
        const val MAX_MAGIC_BYTES = 64
        const val MAX_NICKNAME_BYTES = 200
        const val MAX_ADDRESS_BYTES = 2_000
        const val MAX_CITY_BYTES = 400
        const val MAX_OTHER_BYTES = 800
        const val MAX_POSTAL_CODE_BYTES = 80
        const val MAX_COUNTRY_BYTES = 400
        const val MAX_TEMPLATE_ID_BYTES = 400
        val MAGIC = "CardVault/AddressPayload".toByteArray(StandardCharsets.UTF_8)

        fun encodedStringSize(bytes: ByteArray): Int = Int.SIZE_BYTES + bytes.size

        fun String.encodedUtf8(): ByteArray = toByteArray(StandardCharsets.UTF_8)

        fun ByteBuffer.putEncodedString(bytes: ByteArray): ByteBuffer =
            putInt(bytes.size).put(bytes)
    }
}

private fun String.codePointLength(): Int = codePointCount(0, length)
