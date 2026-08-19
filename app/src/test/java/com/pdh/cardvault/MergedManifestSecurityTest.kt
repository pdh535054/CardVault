package com.pdh.cardvault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File

class MergedManifestSecurityTest {
    private val androidNamespace = "http://schemas.android.com/apk/res/android"
    private val applicationId = "com.pdh.cardvault"
    private val dynamicReceiverPermission =
        "$applicationId.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
    private val allowedRequestedPermissions = setOf(
        dynamicReceiverPermission,
        "android.permission.USE_BIOMETRIC",
        "android.permission.USE_FINGERPRINT",
    )

    @Test
    fun debugMergedManifestPreservesSecurityPolicy() {
        verifyMergedManifest(buildType = "debug", release = false)
    }

    @Test
    fun releaseMergedManifestPreservesSecurityPolicy() {
        verifyMergedManifest(buildType = "release", release = true)
    }

    private fun verifyMergedManifest(buildType: String, release: Boolean) {
        val document = parseSecureXml(mergedManifestFile(buildType))

        verifyPermissions(document)
        verifyApplicationPolicy(document, release)
        verifyExportedComponents(document)
        verifyLauncher(document)
    }

    private fun verifyPermissions(document: Document) {
        val requestedPermissions = document.elements("*")
            .filter { element -> element.tagName.startsWith("uses-permission") }
            .map { element -> element.androidAttribute("name") }
            .filter(String::isNotBlank)
            .distinct()

        assertTrue(
            "Merged manifests may request only audited biometric and AndroidX permissions.",
            requestedPermissions.all { permission -> permission in allowedRequestedPermissions },
        )

        val declaredPermissions = document.elements("permission")
        assertTrue(
            "Merged manifests may declare only AndroidX's app-signature receiver permission.",
            declaredPermissions.all { element ->
                element.androidAttribute("name") == dynamicReceiverPermission
            },
        )

        val dynamicDeclarations = declaredPermissions.filter { element ->
            element.androidAttribute("name") == dynamicReceiverPermission
        }
        assertEquals(
            "The dynamic receiver permission must be requested and declared together.",
            dynamicReceiverPermission in requestedPermissions,
            dynamicDeclarations.isNotEmpty(),
        )
        dynamicDeclarations.forEach { declaration ->
            assertEquals("signature", declaration.androidAttribute("protectionLevel"))
        }
    }

    private fun verifyApplicationPolicy(document: Document, release: Boolean) {
        val application = document.elements("application").single()

        assertEquals("false", application.androidAttribute("allowBackup"))
        assertEquals("false", application.androidAttribute("usesCleartextTraffic"))
        assertEquals(
            "@xml/data_extraction_rules",
            application.androidAttribute("dataExtractionRules"),
        )
        assertEquals("@xml/backup_rules", application.androidAttribute("fullBackupContent"))
        assertNotEquals("true", application.androidAttribute("testOnly"))

        if (release) {
            assertNotEquals("true", application.androidAttribute("debuggable"))
        }
    }

    private fun verifyExportedComponents(document: Document) {
        val componentTags = setOf("activity", "activity-alias", "service", "receiver", "provider")
        val components = document.elements("*").filter { element ->
            element.tagName in componentTags
        }
        val exportedComponents = components.filter { element ->
            element.androidAttribute("exported") == "true"
        }

        assertEquals(1, exportedComponents.size)
        val exported = exportedComponents.single()
        assertEquals("activity", exported.tagName)
        assertEquals("$applicationId.MainActivity", resolveComponentName(exported))

        components.filterNot { element -> element === exported }.forEach { element ->
            assertFalse(
                "Unexpected exported component: ${element.tagName} ${resolveComponentName(element)}",
                element.androidAttribute("exported") == "true",
            )
        }
    }

    private fun verifyLauncher(document: Document) {
        val mainActivity = document.elements("activity").single { element ->
            resolveComponentName(element) == "$applicationId.MainActivity"
        }
        val actions = mainActivity.descendants("action")
            .map { element -> element.androidAttribute("name") }
        val categories = mainActivity.descendants("category")
            .map { element -> element.androidAttribute("name") }

        assertTrue("android.intent.action.MAIN" in actions)
        assertTrue("android.intent.category.LAUNCHER" in categories)
    }

    private fun mergedManifestFile(buildType: String): File {
        val configuredBuildDirectory = requireNotNull(System.getProperty("cardvault.appBuildDir")) {
            "The cardvault.appBuildDir test system property is required."
        }
        val buildDirectory = File(configuredBuildDirectory).canonicalFile
        val taskName = "process${buildType.replaceFirstChar(Char::uppercase)}MainManifest"
        val manifest = File(
            buildDirectory,
            "intermediates/merged_manifest/$buildType/$taskName/AndroidManifest.xml",
        ).canonicalFile

        require(manifest.toPath().startsWith(buildDirectory.toPath())) {
            "Merged manifest path must remain inside the app build directory."
        }
        require(manifest.isFile) { "Merged manifest does not exist: $manifest" }
        return manifest
    }

    private fun resolveComponentName(element: Element): String {
        val rawName = element.androidAttribute("name")
        return when {
            rawName.startsWith(".") -> applicationId + rawName
            "." !in rawName -> "$applicationId.$rawName"
            else -> rawName
        }
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(androidNamespace, name)

    private fun Element.descendants(tagName: String): List<Element> {
        val nodes = getElementsByTagName(tagName)
        return (0 until nodes.length).map { index -> nodes.item(index) as Element }
    }
}
