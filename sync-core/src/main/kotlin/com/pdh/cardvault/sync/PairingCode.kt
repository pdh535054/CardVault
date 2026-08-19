package com.pdh.cardvault.sync

import java.security.SecureRandom
import java.util.Locale

data class PairingMaterial(
    val displayCode: String,
    val secret: SecretBytes,
) : AutoCloseable {
    override fun close() = secret.close()

    override fun toString(): String = "PairingMaterial(code=redacted, secret=redacted)"
}

object PairingCode {
    private const val PREFIX = "CVP1-"
    private const val SECRET_BYTES = 16
    private const val ENCODED_CHARS = 26
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun generate(random: SecureRandom = SecureRandom()): PairingMaterial {
        val secret = ByteArray(SECRET_BYTES)
        random.nextBytes(secret)
        return try {
            PairingMaterial(encode(secret), SecretBytes(secret))
        } finally {
            secret.fill(0)
        }
    }

    fun encode(secret: ByteArray): String {
        require(secret.size == SECRET_BYTES) { "Invalid pairing secret." }
        var accumulator = 0
        var bitCount = 0
        val encoded = StringBuilder(ENCODED_CHARS)
        secret.forEach { byte ->
            accumulator = (accumulator shl 8) or (byte.toInt() and 0xff)
            bitCount += 8
            while (bitCount >= 5) {
                bitCount -= 5
                encoded.append(ALPHABET[(accumulator ushr bitCount) and 31])
                accumulator = accumulator and ((1 shl bitCount) - 1)
            }
        }
        if (bitCount > 0) encoded.append(ALPHABET[(accumulator shl (5 - bitCount)) and 31])
        check(encoded.length == ENCODED_CHARS)
        return PREFIX + listOf(
            encoded.substring(0, 5),
            encoded.substring(5, 10),
            encoded.substring(10, 15),
            encoded.substring(15, 20),
            encoded.substring(20, 26),
        ).joinToString("-")
    }

    @Throws(SyncProtocolException::class)
    fun decode(displayCode: String): ByteArray {
        val normalized = displayCode.trim().uppercase(Locale.ROOT)
        if (!normalized.startsWith(PREFIX)) invalidPairingCode()
        val body = normalized.substring(PREFIX.length)
        if (body.any { it != '-' && !it.isWhitespace() && it !in ALPHABET }) invalidPairingCode()
        val compact = body.filterNot { it == '-' || it.isWhitespace() }
        if (compact.length != ENCODED_CHARS) invalidPairingCode()

        var accumulator = 0
        var bitCount = 0
        val decoded = ByteArray(SECRET_BYTES)
        var outputIndex = 0
        compact.forEach { character ->
            val value = ALPHABET.indexOf(character)
            if (value < 0) invalidPairingCode()
            accumulator = (accumulator shl 5) or value
            bitCount += 5
            if (bitCount >= 8) {
                bitCount -= 8
                if (outputIndex >= decoded.size) invalidPairingCode()
                decoded[outputIndex++] = ((accumulator ushr bitCount) and 0xff).toByte()
                accumulator = accumulator and ((1 shl bitCount) - 1)
            }
        }
        if (outputIndex != SECRET_BYTES || bitCount != 2 || accumulator != 0) {
            decoded.fill(0)
            invalidPairingCode()
        }
        return decoded
    }

    private fun invalidPairingCode(): Nothing =
        throw SyncProtocolException(SyncErrorCode.INVALID_PAIRING_CODE)
}
