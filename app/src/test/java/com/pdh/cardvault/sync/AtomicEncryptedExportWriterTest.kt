package com.pdh.cardvault.sync

import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AtomicEncryptedExportWriterTest {
    @Test
    fun completedFileReplacesOlderExportWithoutLeavingTemporaryFiles() {
        val directory = Files.createTempDirectory("cardvault-export-test").toFile()
        try {
            val destination = directory.resolve("CardVault-sync-1.cvsync")
            Files.write(destination.toPath(), byteArrayOf(1, 2, 3))
            val encryptedBytes = ByteArray(4_097) { index -> (index % 251).toByte() }

            AtomicEncryptedExportWriter.write(destination, encryptedBytes)

            assertArrayEquals(encryptedBytes, Files.readAllBytes(destination.toPath()))
            assertEquals(listOf(destination.name), directory.listFiles()?.map { it.name })
        } finally {
            assertTrue(directory.deleteRecursively())
        }
    }
}
