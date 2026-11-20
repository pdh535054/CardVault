package com.pdh.cardvault.ui.screen

import com.pdh.cardvault.sync.isSupportedTransferUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferFilePickerTest {
    @Test
    fun eachPickerFallsBackToTheOther() {
        assertEquals(
            listOf(TransferPicker.SystemChooser, TransferPicker.DocumentPicker),
            pickerFallbackOrder(TransferPicker.SystemChooser),
        )
        assertEquals(
            listOf(TransferPicker.DocumentPicker, TransferPicker.SystemChooser),
            pickerFallbackOrder(TransferPicker.DocumentPicker),
        )
    }

    @Test
    fun failedPrimaryPickerFallsBackWithoutPropagatingTheFailure() {
        val attempts = mutableListOf<TransferPicker>()

        val launched = launchFirstAvailablePicker(
            pickerFallbackOrder(TransferPicker.SystemChooser),
        ) { picker ->
            attempts += picker
            if (picker == TransferPicker.SystemChooser) {
                throw IllegalStateException("Simulated ActivityNotFoundException")
            }
        }

        assertTrue(launched)
        assertEquals(listOf(TransferPicker.SystemChooser, TransferPicker.DocumentPicker), attempts)
    }

    @Test
    fun reportsFailureWhenNoPickerCanStart() {
        val attempts = mutableListOf<TransferPicker>()

        val launched = launchFirstAvailablePicker(
            pickerFallbackOrder(TransferPicker.DocumentPicker),
        ) { picker ->
            attempts += picker
            throw SecurityException("Simulated disabled picker")
        }

        assertFalse(launched)
        assertEquals(listOf(TransferPicker.DocumentPicker, TransferPicker.SystemChooser), attempts)
    }

    @Test
    fun onlyContentUrisWithAnAuthorityAreAccepted() {
        assertTrue(isSupportedTransferUri("content", "com.tencent.mm.external.fileprovider"))
        assertTrue(isSupportedTransferUri("content", "com.android.providers.downloads.documents"))
        assertFalse(isSupportedTransferUri("file", ""))
        assertFalse(isSupportedTransferUri("https", "example.com"))
        assertFalse(isSupportedTransferUri("content", null))
        assertFalse(isSupportedTransferUri("content", "  "))
        assertFalse(isSupportedTransferUri(null, "authority"))
    }
}
