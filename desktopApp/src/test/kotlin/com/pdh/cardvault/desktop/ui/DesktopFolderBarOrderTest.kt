package com.pdh.cardvault.desktop.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopFolderBarOrderTest {
    @Test
    fun `unfiled can move between custom folders`() {
        val order = listOf<String?>(null, "one", "two")

        assertEquals(listOf("one", null, "two"), moveDesktopFolderItem(order, 0, 1))
        assertEquals(listOf("one", "two", null), moveDesktopFolderItem(order, 0, 2))
    }

    @Test
    fun `normalization keeps one movable unfiled item and appends new folders`() {
        assertEquals(
            listOf("two", null, "one", "three"),
            normalizeDesktopFolderOrder(listOf("two", null, "missing", null, "one"), listOf("one", "two", "three")),
        )
    }

    @Test
    fun `drag target is clamped to available positions`() {
        assertEquals(0, desktopFolderDragTargetIndex(1, -500f, 140f, 3))
        assertEquals(2, desktopFolderDragTargetIndex(1, 500f, 140f, 3))
    }
}
