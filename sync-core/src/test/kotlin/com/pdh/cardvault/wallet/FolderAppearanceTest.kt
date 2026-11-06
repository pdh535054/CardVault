package com.pdh.cardvault.wallet

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderAppearanceTest {
    @Test fun defaultAndPresetsAreOpaqueRgbValues() {
        assertEquals(12, FolderAppearance.presetColors.distinct().size)
        FolderAppearance.presetColors.forEach(FolderAppearance::requireValidColor)
        FolderAppearance.requireValidColor(0)
        FolderAppearance.requireValidColor(0xFFFFFF)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidNegativeRgbIsRejected() {
        FolderAppearance.requireValidColor(-1)
    }
}
