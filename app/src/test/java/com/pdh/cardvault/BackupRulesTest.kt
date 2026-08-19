package com.pdh.cardvault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class BackupRulesTest {
    private val expectedDomains = setOf(
        "root",
        "file",
        "database",
        "sharedpref",
        "external",
        "device_root",
        "device_file",
        "device_database",
        "device_sharedpref",
    )

    @Test
    fun legacyBackupRulesExcludeEveryDataDomain() {
        val document = parseSecureXml(TestProjectFiles.resolve("src/main/res/xml/backup_rules.xml"))

        assertEquals("full-backup-content", document.documentElement.tagName)
        assertEquals(0, document.elements("include").size)
        assertExcludesEveryDomain(document.elements("exclude"))
    }

    @Test
    fun modernRulesExcludeCloudAndDeviceTransfer() {
        val document = parseSecureXml(
            TestProjectFiles.resolve("src/main/res/xml/data_extraction_rules.xml"),
        )

        assertEquals("data-extraction-rules", document.documentElement.tagName)
        assertEquals(0, document.elements("include").size)
        assertEquals(0, document.elements("cross-platform-transfer").size)

        val cloudBackup = document.elements("cloud-backup").single()
        assertEquals("true", cloudBackup.getAttribute("disableIfNoEncryptionCapabilities"))
        assertExcludesEveryDomain(cloudBackup.descendantElements("exclude"))

        val deviceTransfer = document.elements("device-transfer").single()
        assertExcludesEveryDomain(deviceTransfer.descendantElements("exclude"))
    }

    private fun assertExcludesEveryDomain(excludes: List<Element>) {
        assertEquals(expectedDomains.size, excludes.size)
        assertEquals(expectedDomains, excludes.map { element -> element.getAttribute("domain") }.toSet())
        assertTrue(excludes.all { element -> element.getAttribute("path") == "." })
    }

    private fun Element.descendantElements(tagName: String): List<Element> {
        val nodes = getElementsByTagName(tagName)
        return (0 until nodes.length).map { index -> nodes.item(index) as Element }
    }
}
