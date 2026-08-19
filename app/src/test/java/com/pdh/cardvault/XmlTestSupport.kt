package com.pdh.cardvault

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

internal object TestProjectFiles {
    private val appProjectDirectory: File by lazy {
        val configuredPath = requireNotNull(System.getProperty("cardvault.appProjectDir")) {
            "The cardvault.appProjectDir test system property is required."
        }
        File(configuredPath).canonicalFile
    }

    fun resolve(relativePath: String): File =
        File(appProjectDirectory, relativePath).canonicalFile.also { file ->
            require(file.toPath().startsWith(appProjectDirectory.toPath())) {
                "Test path must stay inside the app project directory."
            }
            require(file.isFile) { "Required test file does not exist: $file" }
        }
}

internal fun parseSecureXml(file: File): Document {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isXIncludeAware = false
        isExpandEntityReferences = false
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
        setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
    }
    return factory.newDocumentBuilder().parse(file).also { document ->
        document.documentElement.normalize()
    }
}

internal fun Document.elements(tagName: String): List<Element> {
    val nodes = getElementsByTagName(tagName)
    return (0 until nodes.length).map { index -> nodes.item(index) as Element }
}
