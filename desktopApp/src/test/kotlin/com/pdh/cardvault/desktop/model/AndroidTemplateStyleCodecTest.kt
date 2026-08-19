package com.pdh.cardvault.desktop.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidTemplateStyleCodecTest {
    @Test
    fun `preset template identifier round trips exactly`() {
        val id = "custom:v2:midnight:orbits:editorial:gold"
        assertEquals(id, AndroidTemplateStyleCodec.encode(AndroidTemplateStyleCodec.decode(id)))
    }

    @Test
    fun `custom RGB template identifier round trips exactly`() {
        val id = "custom:v3:rgb-13C400:continuous:orbit:rgb-F0A1C2"
        val decoded = AndroidTemplateStyleCodec.decode(id)
        assertEquals(id, AndroidTemplateStyleCodec.encode(decoded))
        assertEquals(CardPattern.Continuous, decoded.pattern)
        assertEquals(NicknameTypography.Orbit, decoded.typography)
    }

    @Test
    fun `unknown legacy identifier falls back but is retained as source`() {
        val decoded = AndroidTemplateStyleCodec.decode("legacy-unknown")
        assertEquals(CardCoverStyle.Default.startArgb, decoded.startArgb)
        assertEquals("legacy-unknown", decoded.sourceTemplateId)
        assertTrue(AndroidTemplateStyleCodec.encode(decoded).startsWith("custom:"))
    }
}
