package com.pdh.cardvault.desktop.sync

import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

object DesktopTransferFiles {
    const val MAX_IMPORT_BYTES: Long = 4L * 1024 * 1024

    fun writeExport(directory: Path, packageFile: DesktopExportPackage): Path {
        Files.createDirectories(directory)
        val safeName = packageFile.suggestedFileName.takeIf(::isSafeFileName)
            ?: throw IllegalArgumentException("导出文件名无效。")
        val normalizedDirectory = directory.toAbsolutePath().normalize()
        val target = normalizedDirectory.resolve(safeName).normalize()
        if (target.parent != normalizedDirectory) throw IllegalArgumentException("导出位置无效。")
        val temporary = target.resolveSibling(".${target.fileName}.${System.nanoTime()}.tmp")
        try {
            FileChannel.open(
                temporary,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
            ).use { channel ->
                val buffer = java.nio.ByteBuffer.wrap(packageFile.bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
            return target
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun readImport(path: Path): ByteArray {
        if (!Files.isRegularFile(path)) throw IllegalArgumentException("请选择有效的 CardVault 文件。")
        val size = Files.size(path)
        if (size !in 1..MAX_IMPORT_BYTES) throw IllegalArgumentException("同步文件大小无效。")
        return Files.readAllBytes(path)
    }

    private fun isSafeFileName(value: String): Boolean =
        value.length in 1..120 &&
            value.none { it in "\\/:*?\"<>|" } &&
            (value.endsWith(".cvsync") || value.endsWith(".cvpair"))
}
