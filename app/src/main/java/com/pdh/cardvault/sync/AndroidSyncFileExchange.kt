package com.pdh.cardvault.sync

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
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

        val exportDirectory = File(context.cacheDir, EXPORT_DIRECTORY).apply {
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

    fun createShareIntent(file: SharedEncryptedFile): Intent = Intent(Intent.ACTION_SEND).apply {
        type = MIME_TYPE
        putExtra(Intent.EXTRA_STREAM, file.uri)
        clipData = ClipData.newUri(context.contentResolver, file.fileName, file.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    data class SharedEncryptedFile(
        val uri: Uri,
        val fileName: String,
    ) {
        override fun toString(): String = "SharedEncryptedFile(uri=redacted)"
    }

    companion object {
        const val MIME_TYPE = "application/vnd.cardvault.transfer"
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
