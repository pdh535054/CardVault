package com.pdh.cardvault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class SourceManifestSecurityTest {
    private val androidNamespace = "http://schemas.android.com/apk/res/android"
    private val manifest by lazy {
        parseSecureXml(TestProjectFiles.resolve("src/main/AndroidManifest.xml"))
    }

    @Test
    fun sourceManifestDeclaresNoPermissions() {
        val permissionElements = manifest.elements("*").filter { element ->
            element.tagName.startsWith("uses-permission") || element.tagName == "permission"
        }

        assertTrue(
            "The source manifest must not request or declare permissions.",
            permissionElements.isEmpty(),
        )
    }

    @Test
    fun sourceManifestDisablesBackupAndCleartextTraffic() {
        val application = manifest.elements("application").single()

        assertEquals("false", application.androidAttribute("allowBackup"))
        assertEquals("false", application.androidAttribute("usesCleartextTraffic"))
        assertEquals(
            "@xml/data_extraction_rules",
            application.androidAttribute("dataExtractionRules"),
        )
        assertEquals("@xml/backup_rules", application.androidAttribute("fullBackupContent"))
    }

    @Test
    fun onlyMainActivityIsExported() {
        val componentTags = setOf("activity", "activity-alias", "service", "receiver", "provider")
        val components = manifest.elements("*").filter { element ->
            element.tagName in componentTags
        }
        val exportedComponents = components.filter { element ->
            element.androidAttribute("exported") == "true"
        }

        assertEquals(2, components.size)
        assertEquals(1, exportedComponents.size)
        assertEquals("activity", exportedComponents.single().tagName)
        assertEquals(".MainActivity", exportedComponents.single().androidAttribute("name"))
        assertEquals("singleTask", exportedComponents.single().androidAttribute("launchMode"))

        val provider = components.single { element -> element.tagName == "provider" }
        assertEquals("androidx.core.content.FileProvider", provider.androidAttribute("name"))
        assertEquals("false", provider.androidAttribute("exported"))
        assertEquals("true", provider.androidAttribute("grantUriPermissions"))
        assertEquals("${'$'}{applicationId}.sync-files", provider.androidAttribute("authorities"))
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(androidNamespace, name)
}
