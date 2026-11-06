package com.pdh.cardvault.desktop.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import com.pdh.cardvault.desktop.model.DesktopFolder
import com.pdh.cardvault.desktop.model.DesktopFolderKind

class DesktopFolderBarOrderTest {
    @Test fun `folder drag accepts siblings but blocks self descendants missing and wrong collection`() {
        val source = DesktopFolder("50000000-0000-4000-8000-000000000001", "Synthetic source", DesktopFolderKind.CARDS, 1, 1)
        val child = DesktopFolder("50000000-0000-4000-8000-000000000002", "Synthetic child", DesktopFolderKind.CARDS, 1, 1, source.id)
        val grandchild = DesktopFolder("50000000-0000-4000-8000-000000000003", "Synthetic grandchild", DesktopFolderKind.CARDS, 1, 1, child.id)
        val target = DesktopFolder("50000000-0000-4000-8000-000000000004", "Synthetic target", DesktopFolderKind.CARDS, 1, 1)
        val wrongKind = DesktopFolder("50000000-0000-4000-8000-000000000005", "Synthetic addresses", DesktopFolderKind.ADDRESSES, 1, 1)
        val folders = listOf(source, child, grandchild, target, wrongKind)
        val entry = DesktopWalletEntry(source.id, true)
        assertTrue(canDropDesktopWalletEntry(entry, target.id, folders))
        for (invalid in listOf(source.id, child.id, grandchild.id, wrongKind.id, "missing")) {
            assertFalse(canDropDesktopWalletEntry(entry, invalid, folders))
        }
        assertFalse(canDropDesktopWalletEntry(DesktopWalletEntry("missing", true), target.id, folders))
        assertTrue(canDropDesktopWalletEntry(DesktopWalletEntry("record", false), target.id, folders))
    }
    @Test fun `move selection permits root and existing destinations but rejects same and deleted folder`() {
        val parent = DesktopFolder("50000000-0000-4000-8000-000000000001", "Synthetic parent", DesktopFolderKind.CARDS, 1, 1)
        val child = DesktopFolder("50000000-0000-4000-8000-000000000002", "Synthetic child", DesktopFolderKind.CARDS, 1, 1, parent.id)
        val folders = listOf(parent, child)
        assertTrue(canMoveDesktopRecord(child.id, null, folders))
        assertTrue(canMoveDesktopRecord(child.id, parent.id, folders))
        assertTrue(canMoveDesktopRecord(null, child.id, folders))
        assertFalse(canMoveDesktopRecord(child.id, child.id, folders))
        assertFalse(canMoveDesktopRecord(null, null, folders))
        assertFalse(canMoveDesktopRecord(child.id, "50000000-0000-4000-8000-000000000099", folders))
    }
    @Test
    fun `folder count includes descendants`() {
        val root = DesktopFolder("50000000-0000-4000-8000-000000000001", "Synthetic root", DesktopFolderKind.CARDS, 1, 1)
        val child = DesktopFolder("50000000-0000-4000-8000-000000000002", "Synthetic child", DesktopFolderKind.CARDS, 1, 1, root.id)
        assertEquals(2, countDesktopFolderTreeItems(listOf(root, child), root.id, listOf(root.id, child.id, null)))
    }
    @Test
    fun `reordering siblings preserves folders at other levels`() {
        assertEquals(
            listOf<String?>(null, "root", "child-b", "other-root", "child-a"),
            mergeDesktopFolderOrder(
                listOf(null, "root", "child-a", "other-root", "child-b"),
                listOf("child-b", "child-a"),
            ),
        )
    }

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
