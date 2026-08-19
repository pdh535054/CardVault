package com.pdh.cardvault.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressFormLayoutTest {
    @Test
    fun previewKeepsAUsableEditorAreaAcrossCompactAndTallScreens() {
        assertEquals(AddressFormLayout(4f, 127f), addressFormLayout(300f))
        assertEquals(AddressFormLayout(10f, 240f), addressFormLayout(800f))
    }

    @Test
    fun previewHeightNeverBecomesNegative() {
        assertEquals(AddressFormLayout(4f, 0f), addressFormLayout(0f))
        assertEquals(AddressFormLayout(4f, 0f), addressFormLayout(-100f))
    }
}
