package com.pdh.cardvault.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PairingCodeTest {
    @Test
    fun `RFC 4648 code is stable and round trips`() {
        val secret = ByteArray(16) { it.toByte() }
        val code = PairingCode.encode(secret)
        assertEquals("CVP1-AAAQE-AYEAU-DAOCA-JBIFQ-YDIOB4", code)
        assertContentEquals(secret, PairingCode.decode(code))
        assertContentEquals(secret, PairingCode.decode("  cvp1-aaaqe ayeau-daoca-jbifq-ydiob4  "))
    }

    @Test
    fun `invalid alphabet length prefix and nonzero padding are rejected safely`() {
        listOf(
            "",
            "CVP2-AAAQE-AYEAU-DAOCA-JBIFQ-YDIOB4",
            "CVP1-AAAQE-AYEAU-DAOCA-JBIFQ-YDIOB",
            "CVP1-AAAQE-AYEAU-DAOCA-JBIFQ-YDIOB!",
            "CVP1-AAAQE-AYEAU-DAOCA-JBIFQ-YDIOB5",
        ).forEach { candidate ->
            val error = assertFailsWith<SyncProtocolException> { PairingCode.decode(candidate) }
            assertEquals(SyncErrorCode.INVALID_PAIRING_CODE, error.code)
            if (candidate.isNotEmpty()) assertFalse(error.message.orEmpty().contains(candidate))
        }
    }

    @Test
    fun `generated material never prints its code or secret`() {
        PairingCode.generate().use { material ->
            assertFalse(material.toString().contains(material.displayCode))
            assertEquals(16, material.secret.size)
        }
    }

    @Test
    fun `pairing code is found inside pasted chat text and canonicalised`() {
        val canonical = "CVP1-AAAQE-AYEAU-DAOCA-JBIFQ-YDIOB4"
        assertEquals(canonical, PairingCode.findIn(canonical))
        assertEquals(canonical, PairingCode.findIn("配对码：cvp1-aaaqe ayeau-daoca-jbifq-ydiob4，请勿转发"))
        assertEquals(canonical, PairingCode.findIn("CVP1AAAQEAYEAUDAOCAJBIFQYDIOB4"))
        assertContentEquals(ByteArray(16) { it.toByte() }, PairingCode.decode(PairingCode.findIn(" $canonical\n")!!))
    }

    @Test
    fun `text without a complete pairing code yields null`() {
        listOf(
            null,
            "",
            "   ",
            "没有配对码",
            "CVP1-AAAQE-AYEAU-DAOCA-JBIFQ-YDIOB",
            "CVP1-AAAQE-AYEAU-DAOCA-JBIFQ-YDIOB4X",
            "CVP2-AAAQE-AYEAU-DAOCA-JBIFQ-YDIOB4",
        ).forEach { candidate -> assertEquals(null, PairingCode.findIn(candidate)) }
    }
}
