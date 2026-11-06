package com.pdh.cardvault.ui.screen

import com.pdh.cardvault.presentation.VaultFolderUiModel
import com.pdh.cardvault.ui.card.WalletStackEntry
import com.pdh.cardvault.ui.card.orderedWalletEntries
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MixedWalletNavigationTest {
    @Test fun folderDragAllowsSiblingDestinationButRejectsSelfDescendantsAndMissingFolders() {
        val source = UUID.randomUUID()
        val child = UUID.randomUUID()
        val grandchild = UUID.randomUUID()
        val target = UUID.randomUUID()
        val folders = listOf(VaultFolderUiModel(source, "Synthetic source", 0),
            VaultFolderUiModel(child, "Synthetic child", 0, source),
            VaultFolderUiModel(grandchild, "Synthetic grandchild", 0, child),
            VaultFolderUiModel(target, "Synthetic target", 0))
        val entry = WalletStackEntry(source, true)
        assertTrue(canDropWalletEntry(entry, target, folders))
        assertFalse(canDropWalletEntry(entry, source, folders))
        assertFalse(canDropWalletEntry(entry, child, folders))
        assertFalse(canDropWalletEntry(entry, grandchild, folders))
        assertFalse(canDropWalletEntry(entry, UUID.randomUUID(), folders))
        assertFalse(canDropWalletEntry(WalletStackEntry(UUID.randomUUID(), true), target, folders))
        assertTrue(canDropWalletEntry(WalletStackEntry(UUID.randomUUID(), false), target, folders))
    }
    @Test fun moveRequiresDifferentExistingDestinationAndAllowsRoot() {
        val parent = UUID.randomUUID()
        val child = UUID.randomUUID()
        val folders = listOf(VaultFolderUiModel(parent, "Synthetic parent", 0),
            VaultFolderUiModel(child, "Synthetic child", 0, parent))
        assertTrue(canMoveRecordToFolder(child, parent, folders))
        assertTrue(canMoveRecordToFolder(child, null, folders))
        assertTrue(canMoveRecordToFolder(null, child, folders))
        assertFalse(canMoveRecordToFolder(child, child, folders))
        assertFalse(canMoveRecordToFolder(null, null, folders))
        assertFalse(canMoveRecordToFolder(child, UUID.randomUUID(), folders))
    }
    @Test fun backFromNestedFolderReturnsImmediateParentThenRoot() {
        val parent = UUID.randomUUID()
        val child = UUID.randomUUID()
        val folders = listOf(VaultFolderUiModel(parent, "Synthetic parent", 0),
            VaultFolderUiModel(child, "Synthetic child", 0, parent))
        assertEquals(parent, parentFolderForBack(folders, child))
        assertNull(parentFolderForBack(folders, parent))
    }
    @Test fun foldersAndCardsCanRemainInterleaved() {
        val folder = WalletStackEntry(UUID.randomUUID(), true)
        val first = WalletStackEntry(UUID.randomUUID(), false)
        val last = WalletStackEntry(UUID.randomUUID(), false)
        assertEquals(listOf(first, folder, last), orderedWalletEntries(listOf(first.key, folder.key, last.key), listOf(folder, first, last)))
    }
}
