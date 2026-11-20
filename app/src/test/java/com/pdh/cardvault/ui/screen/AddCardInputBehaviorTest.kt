package com.pdh.cardvault.ui.screen

import com.pdh.cardvault.TestProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddCardInputBehaviorTest {
    @Test
    fun expiryFocusAdvancesOnlyWhenTheFourthDigitIsNewlyCompleted() {
        assertFalse(shouldAdvanceExpiryFocusToCvv(previousDigits = "06", normalizedDigits = "062"))
        assertTrue(shouldAdvanceExpiryFocusToCvv(previousDigits = "062", normalizedDigits = "0629"))
        assertFalse(shouldAdvanceExpiryFocusToCvv(previousDigits = "0629", normalizedDigits = "0629"))
        assertFalse(shouldAdvanceExpiryFocusToCvv(previousDigits = "0629", normalizedDigits = "062"))
    }

    @Test
    fun cardNoteClipWriterCopiesExactNoteOnlyWhenNonBlank() {
        var capturedClip: Pair<String, String>? = null

        assertFalse(canCopyCardNote(""))
        assertFalse(canCopyCardNote("   \n\t "))
        assertFalse(
            writeCardNoteClip("   ") { label, text -> capturedClip = label to text },
        )
        assertNull(capturedClip)

        assertTrue(canCopyCardNote("25fuxxuefuxfuffu"))
        assertTrue(
            writeCardNoteClip("25fuxxuefuxfuffu") { label, text -> capturedClip = label to text },
        )
        assertEquals(CARD_NOTE_CLIP_LABEL to "25fuxxuefuxfuffu", capturedClip)
    }

    @Test
    fun clipboardBlockingIsScopedToPaymentCredentialFieldsOnly() {
        // Regression guard for the 1.6.5 bug: wrapping the whole form in ClipboardBlocked
        // silently swallowed "select all -> copy" in the notes field.
        val source = TestProjectFiles.resolve(
            "src/main/java/com/pdh/cardvault/ui/screen/AddCardScreen.kt",
        ).readText().replace("\r\n", "\n")
        val wholeFormWrapper = Regex("""ClipboardBlocked\s*\{\s*Scaffold\(""")

        assertFalse(wholeFormWrapper.containsMatchIn(source))
        assertEquals(2, Regex("""ClipboardBlocked\s*\{""").findAll(source).count())

        val notesFieldStart = source.indexOf("value = form.notes,")
        assertTrue(notesFieldStart > 0)
        val lastBlockStartBeforeNotes = source.lastIndexOf("ClipboardBlocked {", notesFieldStart)
        val textBetween = source.substring(lastBlockStartBeforeNotes, notesFieldStart)
        val opened = textBetween.count { it == '{' }
        val closed = textBetween.count { it == '}' }
        assertTrue(
            "The notes field must not be nested inside a ClipboardBlocked scope.",
            closed >= opened,
        )
    }
}
