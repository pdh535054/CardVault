package com.pdh.cardvault.sync

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only content:// URIs granted by another app (or the system picker) are ever read. */
internal fun isSupportedTransferUri(scheme: String?, authority: String?): Boolean =
    scheme == ContentResolver.SCHEME_CONTENT && !authority.isNullOrBlank()

/**
 * Extracts the transfer file from an inbound "Open with" (VIEW) or "Share" (SEND) intent.
 * Returns null for anything that is not a readable content:// URI.
 */
internal fun extractInboundTransferUri(intent: Intent?): Uri? {
    if (intent == null) return null
    val candidate = when (intent.action) {
        Intent.ACTION_VIEW -> intent.data ?: firstClipUri(intent.clipData)
        Intent.ACTION_SEND ->
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?: firstClipUri(intent.clipData)
        else -> null
    }
    return candidate?.takeIf { uri -> isSupportedTransferUri(uri.scheme, uri.authority) }
}

/** Picker results usually carry the URI in data; a few OEM pickers only fill clipData. */
internal fun extractPickerResultUri(resultData: Intent?): Uri? {
    if (resultData == null) return null
    val candidate = resultData.data ?: firstClipUri(resultData.clipData)
    return candidate?.takeIf { uri -> isSupportedTransferUri(uri.scheme, uri.authority) }
}

private fun firstClipUri(clipData: ClipData?): Uri? =
    clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri

internal class AndroidSyncFileExchange(
    private val context: Context,
) {
    suspend fun publishEncryptedExport(
        fileName: String,
        bytes: ByteArray,
    ): SharedEncryptedFile = withContext(Dispatchers.IO) {
        require(bytes.isNotEmpty() && bytes.size <= MAX_TRANSFER_BYTES) {
            "The encrypted transfer file size is invalid."
        }
        require(FILE_NAME.matches(fileName)) {
            "The encrypted transfer file name is invalid."
        }

        val exportDirectory = exportDirectory().apply {
            if (!exists() && !mkdirs()) throw IOException("Unable to prepare encrypted export.")
        }
        val destination = File(exportDirectory, fileName)
        exportDirectory.listFiles()?.forEach { existing ->
            if (existing.isFile && existing != destination) existing.delete()
        }
        AtomicEncryptedExportWriter.write(destination, bytes)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.sync-files",
            destination,
        )
        SharedEncryptedFile(uri = uri, fileName = fileName)
    }

    suspend fun readEncryptedImport(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        if (!isSupportedTransferUri(uri.scheme, uri.authority)) {
            throw SecurityException("Unsupported encrypted transfer URI.")
        }
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open the selected encrypted file.")
        input.use { source ->
            val result = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_TRANSFER_BYTES) {
                    throw IOException("The selected encrypted file is too large.")
                }
                result.write(buffer, 0, count)
            }
            result.toByteArray().also { bytes ->
                if (bytes.isEmpty()) throw IOException("The selected encrypted file is empty.")
            }
        }
    }

    /** Copies the already-encrypted export to a location the user picked with CreateDocument. */
    suspend fun saveEncryptedExport(
        file: SharedEncryptedFile,
        destination: Uri,
    ) = withContext(Dispatchers.IO) {
        if (!isSupportedTransferUri(destination.scheme, destination.authority)) {
            throw SecurityException("Unsupported destination URI.")
        }
        require(FILE_NAME.matches(file.fileName)) {
            "The encrypted transfer file name is invalid."
        }
        val directory = exportDirectory().canonicalFile
        val source = File(directory, file.fileName).canonicalFile
        if (!source.toPath().startsWith(directory.toPath()) || !source.isFile) {
            throw IOException("The prepared encrypted export is no longer available.")
        }
        val output = runCatching { context.contentResolver.openOutputStream(destination, "wt") }
            .getOrNull()
            ?: context.contentResolver.openOutputStream(destination)
            ?: throw IOException("Unable to open the destination file.")
        output.use { target ->
            source.inputStream().use { input -> input.copyTo(target) }
            target.flush()
        }
    }

    fun createShareIntent(file: SharedEncryptedFile): Intent = Intent(Intent.ACTION_SEND).apply {
        // A generic binary type is accepted by WeChat, QQ, mail and file managers; the custom
        // vendor type used before 1.6.6 was rejected or hidden by several of them.
        type = SHARE_MIME_TYPE
        putExtra(Intent.EXTRA_STREAM, file.uri)
        clipData = ClipData.newUri(context.contentResolver, file.fileName, file.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun exportDirectory(): File = File(context.cacheDir, EXPORT_DIRECTORY)

    data class SharedEncryptedFile(
        val uri: Uri,
        val fileName: String,
    ) {
        override fun toString(): String = "SharedEncryptedFile(uri=redacted)"
    }

    companion object {
        const val SHARE_MIME_TYPE = "application/octet-stream"
        const val MAX_TRANSFER_BYTES = 4 * 1024 * 1024
        private const val EXPORT_DIRECTORY = "sync_exports"
        private val FILE_NAME = Regex("CardVault-[A-Za-z0-9_-]{1,80}\\.(cvpair|cvsync)")
    }
}

internal object AtomicEncryptedExportWriter {
    fun write(destination: File, bytes: ByteArray) {
        val directory = destination.parentFile
            ?: throw IOException("Unable to prepare encrypted export.")
        val temporary = File.createTempFile(".${destination.name}.", ".tmp", directory)
        var moved = false
        try {
            FileOutputStream(temporary).channel.use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) {
                    channel.write(buffer)
                }
                channel.force(true)
            }
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            moved = true
        } finally {
            if (!moved) temporary.delete()
        }
    }
}
