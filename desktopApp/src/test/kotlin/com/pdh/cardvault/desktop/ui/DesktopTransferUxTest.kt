package com.pdh.cardvault.desktop.ui

import com.pdh.cardvault.desktop.firstDroppedLocalFile
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopTransferUxTest {
    @Test
    fun transferFilesAreRecognisedByExtensionIgnoringCase() {
        assertTrue(isTransferFileName("CardVault-pair-1.cvpair"))
        assertTrue(isTransferFileName("CARDVAULT-SYNC.CVSYNC"))
        assertFalse(isTransferFileName("CardVault-pair.cvpair.txt"))
        assertFalse(isTransferFileName("photo.png"))
        assertFalse(isTransferFileName(null))
    }

    @Test
    fun droppedFileUrisResolveToTheFirstExistingLocalFile() {
        val directory = Files.createTempDirectory("cardvault-drop-uris")
        val file = Files.createFile(directory.resolve("CardVault-pair-1.cvpair"))
        try {
            assertEquals(
                file,
                firstDroppedLocalFile(
                    listOf(
                        "https://example.com/CardVault.cvpair",
                        directory.resolve("missing.cvpair").toUri().toString(),
                        "not a uri at all",
                        directory.toUri().toString(),
                        file.toUri().toString(),
                    ),
                ),
            )
            assertNull(firstDroppedLocalFile(emptyList()))
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun lastSyncLabelIsHumanReadable() {
        val now = 1_800_000_000_000L
        assertEquals("尚未与其他设备同步或导出过加密备份", desktopLastSyncLabel(null, now))
        assertEquals("上次与其他设备同步：今天", desktopLastSyncLabel(now - 1_000, now))
        assertEquals("上次与其他设备同步：12 天前", desktopLastSyncLabel(now - TimeUnit.DAYS.toMillis(12), now))
    }
}
