package com.pdh.cardvault.desktop

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Converts the file URIs delivered by a Compose drag-and-drop event (for example
 * "file:/C:/Users/me/Downloads/CardVault-pair.cvpair") into the first existing local file.
 * Non-file URIs and unreadable entries are ignored; the file itself is read later by the
 * normal size-limited import flow.
 */
internal fun firstDroppedLocalFile(uris: List<String>): Path? = uris.asSequence()
    .mapNotNull { value ->
        runCatching {
            val uri = URI(value)
            if (uri.scheme.equals("file", ignoreCase = true)) Paths.get(uri) else null
        }.getOrNull()
    }
    .firstOrNull { path -> Files.isRegularFile(path) }
