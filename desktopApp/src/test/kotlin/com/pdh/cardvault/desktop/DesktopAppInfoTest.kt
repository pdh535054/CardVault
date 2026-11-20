package com.pdh.cardvault.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopAppInfoTest {
    @Test
    fun packagedVersionMatchesGradleProjectVersion() {
        val expected = requireNotNull(System.getProperty("cardvault.expectedDesktopVersion"))
        assertEquals(expected, DesktopAppInfo.version)
        assertEquals("CardVault $expected", DesktopAppInfo.windowTitle)
        assertEquals("CardVault $expected  ·  Windows", DesktopAppInfo.settingsLabel)
    }

    @Test
    fun missingOrBlankVersionResourceFallsBackSafely() {
        assertEquals("dev", DesktopAppInfo.readVersion { null })
        assertEquals("dev", DesktopAppInfo.readVersion { "version=   \n".byteInputStream() })
        assertEquals("dev", DesktopAppInfo.readVersion { error("unreadable") })
        assertEquals("9.9.9", DesktopAppInfo.readVersion { "version=9.9.9\n".byteInputStream() })
    }
}
