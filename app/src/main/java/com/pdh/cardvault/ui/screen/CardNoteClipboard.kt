package com.pdh.cardvault.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Card notes are free text written by the user (for example a reference code), so they are
 * copied as ordinary plain text: no sensitive-content flag and no automatic clipboard wipe.
 * Card number, expiry and CVV keep their separate, stricter clipboard handling.
 */
internal const val CARD_NOTE_CLIP_LABEL = "CardVault note"
private const val COPIED_FEEDBACK_MILLIS = 2_000L

internal fun canCopyCardNote(notes: String): Boolean = notes.isNotBlank()

internal fun writeCardNoteClip(
    notes: String,
    write: (label: String, text: String) -> Unit,
): Boolean {
    if (!canCopyCardNote(notes)) return false
    write(CARD_NOTE_CLIP_LABEL, notes)
    return true
}

@Stable
internal class CardNoteCopier(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    var recentlyCopied by mutableStateOf(false)
        private set

    private var resetJob: Job? = null

    fun copy(notes: String): Boolean {
        val copied = runCatching {
            writeCardNoteClip(notes) { label, text ->
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                    ?: error("Clipboard service unavailable")
                clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            }
        }.getOrDefault(false)
        if (copied) {
            recentlyCopied = true
            resetJob?.cancel()
            resetJob = scope.launch {
                delay(COPIED_FEEDBACK_MILLIS)
                recentlyCopied = false
            }
        }
        return copied
    }
}

@Composable
internal fun rememberCardNoteCopier(): CardNoteCopier {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember(context, scope) { CardNoteCopier(context, scope) }
}
