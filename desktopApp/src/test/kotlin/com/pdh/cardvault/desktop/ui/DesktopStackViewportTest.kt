package com.pdh.cardvault.desktop.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopStackViewportTest {
    @Test
    fun `short stack fills viewport without unnecessary content growth`() {
        assertEquals(
            640f,
            desktopStackContentHeightDp(
                viewportHeightDp = 640f,
                viewportWidthDp = 470f,
                itemCount = 1,
            ),
        )
    }

    @Test
    fun `large stack grows beyond viewport so every card can be scrolled into view`() {
        val contentHeight = desktopStackContentHeightDp(
            viewportHeightDp = 640f,
            viewportWidthDp = 470f,
            itemCount = 20,
        )

        assertTrue(contentHeight > 1_700f)
        assertTrue(contentHeight > 640f)
    }
}
