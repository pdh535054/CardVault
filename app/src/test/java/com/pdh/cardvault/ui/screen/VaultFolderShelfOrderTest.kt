package com.pdh.cardvault.ui.screen

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultFolderShelfOrderTest {
    @Test
    fun unfiledEntryCanMoveBetweenCustomFolders() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val initial = listOf<UUID?>(null, first, second)

        assertEquals(
            listOf(first, second, null),
            moveFolderShelfItem(initial, fromIndex = 0, toIndex = 2),
        )
    }

    @Test
    fun normalizationRemovesUnknownAndDuplicateEntriesWithoutLosingUnfiled() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val unknown = UUID.randomUUID()

        assertEquals(
            listOf(second, null, first),
            normalizeFolderOrder(
                requestedOrder = listOf(second, unknown, second, null),
                folderIds = listOf(first, second),
            ),
        )
    }

    @Test
    fun horizontalDragClampsToShelfBounds() {
        assertEquals(0, folderDragTargetIndex(0, -400f, 100f, 4))
        assertEquals(2, folderDragTargetIndex(1, 149f, 100f, 4))
        assertEquals(3, folderDragTargetIndex(1, 900f, 100f, 4))
    }
}
