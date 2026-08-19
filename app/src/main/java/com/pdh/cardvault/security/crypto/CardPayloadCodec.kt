package com.pdh.cardvault.security.crypto

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class CardPayload(
    val nickname: String,
    val issuerName: String,
    val cardNumber: String,
    val expiryMonth: Int,
    val expiryYear: Int,
    val saveCvv: Boolean,
    val cvv: String?,
    val cardTemplateId: String,
    val notes: String,
) {
    init {
        require(nickname == nickname.trim() && nickname.codePointLength() in 1..50) {
            "The card payload is invalid."
        }
        require(issuerName == issuerName.trim() && issuerName.codePointLength() in 1..80) {
            "The card payload is invalid."
        }
        require(cardNumber.length in 12..19 && cardNumber.all(Char::isAsciiDigit)) {
            "The card payload is invalid."
        }
        require(expiryMonth in 1..12 && expiryYear in 1000..9999) {
            "The card payload is invalid."
        }
        require(
            if (saveCvv) {
                cvv?.length in 3..4 && cvv?.all(Char::isAsciiDigit) == true
            } else {
                cvv == null
            },
        ) {
            "The card payload is invalid."
        }
        require(cardTemplateId.isNotBlank() && cardTemplateId.length <= 100) {
            "The card payload is invalid."
        }
        require(notes.codePointLength() <= 1000) {
            "The card payload is invalid."
        }
    }

    override fun toString(): String = "CardPayload(sensitiveFields=redacted)"
}

class CardPayloadCodec {
    val currentSchemaVersion: Int = CURRENT_SCHEMA_VERSION

    fun encode(payload: CardPayload): ByteArray {
        val nickname = payload.nickname.encodedUtf8()
        val issuer = payload.issuerName.encodedUtf8()
        val cardNumber = payload.cardNumber.encodedUtf8()
        val cvv = payload.cvv?.encodedUtf8()
        val templateId = payload.cardTemplateId.encodedUtf8()
        val notes = payload.notes.encodedUtf8()
        val sensitiveBuffers = listOfNotNull(
            nickname,
            issuer,
            cardNumber,
            cvv,
            templateId,
            notes,
        )

        return try {
            val optionalCvvBytes = if (payload.saveCvv) {
                Int.SIZE_BYTES + requireNotNull(cvv).size
            } else {
                0
            }
            val totalSize =
                Int.SIZE_BYTES + MAGIC.size +
                    Int.SIZE_BYTES +
                    encodedStringSize(nickname) +
                    encodedStringSize(issuer) +
                    encodedStringSize(cardNumber) +
                    Int.SIZE_BYTES * 2 +
                    Byte.SIZE_BYTES +
                    optionalCvvBytes +
                    encodedStringSize(templateId) +
                    encodedStringSize(notes)
            require(totalSize <= MAX_PAYLOAD_BYTES) { "The card payload is invalid." }

            ByteBuffer.allocate(totalSize)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(MAGIC.size)
                .put(MAGIC)
                .putInt(CURRENT_SCHEMA_VERSION)
                .putEncodedString(nickname)
                .putEncodedString(issuer)
                .putEncodedString(cardNumber)
                .putInt(payload.expiryMonth)
                .putInt(payload.expiryYear)
                .put(if (payload.saveCvv) 1.toByte() else 0.toByte())
                .apply {
                    if (payload.saveCvv) putEncodedString(requireNotNull(cvv))
                }
                .putEncodedString(templateId)
                .putEncodedString(notes)
                .array()
        } finally {
            sensitiveBuffers.forEach { bytes -> bytes.fill(0) }
        }
    }

    fun decode(encodedPayload: ByteArray, expectedSchemaVersion: Int): CardPayload {
        if (expectedSchemaVersion != CURRENT_SCHEMA_VERSION) {
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

            val encodedSchemaVersion = buffer.int
            if (encodedSchemaVersion != expectedSchemaVersion) {
                throw UnsupportedCryptoVersionException()
            }

            val nickname = buffer.readString(MAX_NICKNAME_BYTES)
            val issuerName = buffer.readString(MAX_ISSUER_BYTES)
            val cardNumber = buffer.readString(MAX_CARD_NUMBER_BYTES)
            val expiryMonth = buffer.int
            val expiryYear = buffer.int
            val saveCvv = buffer.readBoolean()
            val cvv = if (saveCvv) buffer.readString(MAX_CVV_BYTES) else null
            val cardTemplateId = buffer.readString(MAX_TEMPLATE_ID_BYTES)
            val notes = buffer.readString(MAX_NOTES_BYTES)
            if (buffer.hasRemaining()) throw InvalidEncryptedPayloadException()

            CardPayload(
                nickname = nickname,
                issuerName = issuerName,
                cardNumber = cardNumber,
                expiryMonth = expiryMonth,
                expiryYear = expiryYear,
                saveCvv = saveCvv,
                cvv = cvv,
                cardTemplateId = cardTemplateId,
                notes = notes,
            )
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

    private fun ByteBuffer.readBoolean(): Boolean = when (val encoded = get().toInt()) {
        0 -> false
        1 -> true
        else -> throw InvalidEncryptedPayloadException()
    }

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_PAYLOAD_BYTES = 8 * 1024
        const val MIN_PAYLOAD_BYTES = 40
        const val MAX_MAGIC_BYTES = 64
        const val MAX_NICKNAME_BYTES = 200
        const val MAX_ISSUER_BYTES = 320
        const val MAX_CARD_NUMBER_BYTES = 19
        const val MAX_CVV_BYTES = 4
        const val MAX_TEMPLATE_ID_BYTES = 400
        const val MAX_NOTES_BYTES = 4000
        val MAGIC = "CardVault/Payload".toByteArray(StandardCharsets.UTF_8)

        fun encodedStringSize(bytes: ByteArray): Int = Int.SIZE_BYTES + bytes.size

        fun String.encodedUtf8(): ByteArray = toByteArray(StandardCharsets.UTF_8)

        fun ByteBuffer.putEncodedString(bytes: ByteArray): ByteBuffer =
            putInt(bytes.size).put(bytes)
    }
}

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

private fun String.codePointLength(): Int = codePointCount(0, length)
