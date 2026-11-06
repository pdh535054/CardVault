package com.pdh.cardvault.wallet

import org.junit.Assert.*
import org.junit.Test

class FolderDropIntentTest {
    @Test fun quickCrossingReordersInsteadOfMoving() {
        val intent = FolderDropIntent()
        intent.update("folder", 100f, 20f, 0)
        assertFalse(intent.update("folder", 100f, 20f, 799).armed)
        assertNull(intent.release("folder"))
    }
    @Test fun stationaryDwellArmsAndReleaseMovesExactlyOnce() {
        val intent = FolderDropIntent()
        intent.update("folder", 100f, 20f, 0)
        assertTrue(intent.update("folder", 100f, 20f, 800).armed)
        assertEquals("folder", intent.release("folder"))
        assertNull(intent.release("folder"))
    }
    @Test fun smallHandJitterDoesNotResetDwell() {
        val intent = FolderDropIntent()
        intent.update("folder", 0f, 0f, 10)
        assertTrue(intent.update("folder", 6f, 6f, 810).armed)
    }
    @Test fun continuousMovementWithinFolderRestartsDwell() {
        val intent = FolderDropIntent()
        intent.update("folder", 0f, 0f, 0)
        assertEquals(0f, intent.update("folder", 20f, 0f, 700).progress)
        assertFalse(intent.update("folder", 20f, 0f, 800).armed)
        assertNull(intent.release("folder"))
    }
    @Test fun movingAwayDisarmsImmediately() {
        val intent = FolderDropIntent()
        intent.update("folder", 1f, 1f, 0)
        intent.update("folder", 1f, 1f, 800)
        assertEquals(FolderDropHover(), intent.update(null, 1f, 1f, 801))
        assertNull(intent.release(null))
    }
    @Test fun switchingFoldersRequiresFreshDwell() {
        val intent = FolderDropIntent()
        intent.update("first", 1f, 1f, 0)
        intent.update("first", 1f, 1f, 800)
        assertFalse(intent.update("second", 1f, 1f, 801).armed)
        assertNull(intent.release("second"))
    }
    @Test fun leavingAndReturningRequiresFreshDwell() {
        val intent = FolderDropIntent()
        intent.update("folder", 0f, 0f, 0)
        intent.update(null, 0f, 0f, 750)
        assertEquals(0f, intent.update("folder", 0f, 0f, 800).progress)
        assertNull(intent.release("folder"))
    }
    @Test fun cancelledDragCannotMove() {
        val intent = FolderDropIntent()
        intent.update("folder", 0f, 0f, 0)
        intent.update("folder", 0f, 0f, 800)
        intent.cancel()
        assertNull(intent.release("folder"))
    }
    @Test fun deletedOrDifferentTargetAtReleaseCannotMove() {
        for (target in listOf(null, "other")) {
            val intent = FolderDropIntent()
            intent.update("folder", 0f, 0f, 0)
            intent.update("folder", 0f, 0f, 800)
            assertNull(intent.release(target))
        }
    }
    @Test fun releaseDoesNotArmWithoutDisplayedFeedback() {
        val intent = FolderDropIntent()
        intent.update("folder", 0f, 0f, 0)
        assertNull(intent.release("folder"))
    }
    @Test fun clockRollbackRestartsDwell() {
        val intent = FolderDropIntent()
        intent.update("folder", 0f, 0f, 1000)
        intent.update("folder", 0f, 0f, 1800)
        assertFalse(intent.update("folder", 0f, 0f, 100).armed)
    }
    @Test fun invalidCoordinatesDisarm() {
        val intent = FolderDropIntent()
        intent.update("folder", 0f, 0f, 0)
        intent.update("folder", 0f, 0f, 800)
        assertFalse(intent.update("folder", Float.NaN, 0f, 801).armed)
        assertNull(intent.release("folder"))
    }
}
