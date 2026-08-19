package com.pdh.cardvault.security.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import java.util.UUID

interface SensitiveClipboardController {
    /** Writes one normalized card number without retaining it in controller state. */
    fun copyCardNumber(normalizedCardNumber: String): Boolean

    /** Writes a bounded sensitive text value such as one complete postal address. */
    fun copySensitiveText(value: String): Boolean = false

    /** Clears the clipboard only while it is still owned by the last CardVault write. */
    fun clearCardNumberIfOwned()
}

internal sealed interface ClipboardOwnershipObservation {
    data class Present(val ownerToken: String?) : ClipboardOwnershipObservation

    data object Unavailable : ClipboardOwnershipObservation
}

internal interface CardNumberClipboardGateway {
    fun writeCardNumber(normalizedCardNumber: String, ownerToken: String): Boolean

    fun observeOwnership(): ClipboardOwnershipObservation

    fun clear(): Boolean
}

class AndroidSensitiveClipboardController internal constructor(
    private val clipboardGateway: CardNumberClipboardGateway,
    private val handler: Handler,
    private val ownerTokenFactory: () -> String,
    private val clearDelayMillis: Long,
    private val clearRetryDelayMillis: Long,
) : SensitiveClipboardController {
    constructor(context: Context) : this(
        clipboardGateway = AndroidCardNumberClipboardGateway(
            context.applicationContext.getSystemService(ClipboardManager::class.java),
        ),
        handler = Handler(Looper.getMainLooper()),
        ownerTokenFactory = { UUID.randomUUID().toString() },
        clearDelayMillis = CARD_NUMBER_CLIPBOARD_LIFETIME_MILLIS,
        clearRetryDelayMillis = CLIPBOARD_CLEAR_RETRY_DELAY_MILLIS,
    )

    private val stateLock = Any()
    private var activeOwnerToken: String? = null
    private var remainingClearRetries = 0
    private val scheduledClear = Runnable {
        synchronized(stateLock) {
            attemptClearCardNumberIfOwnedLocked()
        }
    }

    override fun copyCardNumber(normalizedCardNumber: String): Boolean {
        if (!normalizedCardNumber.isNormalizedCardNumber()) return false

        return copyOwnedSensitiveText(normalizedCardNumber)
    }

    override fun copySensitiveText(value: String): Boolean {
        if (value.isBlank() || value.codePointCount(0, value.length) > MAX_SENSITIVE_TEXT_LENGTH) {
            return false
        }
        return copyOwnedSensitiveText(value)
    }

    private fun copyOwnedSensitiveText(value: String): Boolean {
        return synchronized(stateLock) {
            handler.removeCallbacks(scheduledClear)
            remainingClearRetries = MAX_CLEAR_RETRIES
            attemptClearCardNumberIfOwnedLocked()

            val ownerToken = ownerTokenFactory()
            if (!clipboardGateway.writeCardNumber(value, ownerToken)) {
                return@synchronized false
            }

            handler.removeCallbacks(scheduledClear)
            activeOwnerToken = ownerToken
            remainingClearRetries = MAX_CLEAR_RETRIES
            if (handler.postDelayed(scheduledClear, clearDelayMillis)) {
                true
            } else {
                attemptClearCardNumberIfOwnedLocked()
                false
            }
        }
    }

    override fun clearCardNumberIfOwned() {
        synchronized(stateLock) {
            handler.removeCallbacks(scheduledClear)
            remainingClearRetries = MAX_CLEAR_RETRIES
            attemptClearCardNumberIfOwnedLocked()
        }
    }

    private fun attemptClearCardNumberIfOwnedLocked() {
        val ownerToken = activeOwnerToken ?: return

        when (val observation = clipboardGateway.observeOwnership()) {
            ClipboardOwnershipObservation.Unavailable -> scheduleClearRetryLocked()
            is ClipboardOwnershipObservation.Present -> {
                if (observation.ownerToken != ownerToken) {
                    activeOwnerToken = null
                    remainingClearRetries = 0
                    return
                }

                if (clipboardGateway.clear()) {
                    activeOwnerToken = null
                    remainingClearRetries = 0
                } else {
                    scheduleClearRetryLocked()
                }
            }
        }
    }

    private fun scheduleClearRetryLocked() {
        if (remainingClearRetries <= 0) return
        remainingClearRetries -= 1
        handler.removeCallbacks(scheduledClear)
        handler.postDelayed(scheduledClear, clearRetryDelayMillis)
    }

    override fun toString(): String = "AndroidSensitiveClipboardController(contents=redacted)"

    private companion object {
        const val CARD_NUMBER_CLIPBOARD_LIFETIME_MILLIS = 30_000L
        const val CLIPBOARD_CLEAR_RETRY_DELAY_MILLIS = 1_000L
        const val MAX_CLEAR_RETRIES = 3
        const val MAX_SENSITIVE_TEXT_LENGTH = 2_000
    }
}

private class AndroidCardNumberClipboardGateway(
    private val clipboardManager: ClipboardManager,
) : CardNumberClipboardGateway {
    override fun writeCardNumber(normalizedCardNumber: String, ownerToken: String): Boolean =
        try {
            val clip = ClipData.newPlainText(CLIP_LABEL, normalizedCardNumber).apply {
                description.extras = PersistableBundle().apply {
                    putBoolean(SENSITIVE_CLIP_EXTRA, true)
                    putString(OWNER_TOKEN_EXTRA, ownerToken)
                }
            }
            clipboardManager.setPrimaryClip(clip)
            true
        } catch (_: RuntimeException) {
            false
        }

    override fun observeOwnership(): ClipboardOwnershipObservation =
        try {
            val description = clipboardManager.primaryClipDescription
                ?: return ClipboardOwnershipObservation.Unavailable
            ClipboardOwnershipObservation.Present(
                description.extras?.getString(OWNER_TOKEN_EXTRA),
            )
        } catch (_: RuntimeException) {
            ClipboardOwnershipObservation.Unavailable
        }

    override fun clear(): Boolean =
        try {
            clipboardManager.clearPrimaryClip()
            true
        } catch (_: RuntimeException) {
            false
        }

    private companion object {
        const val SENSITIVE_CLIP_EXTRA = "android.content.extra.IS_SENSITIVE"
        const val OWNER_TOKEN_EXTRA = "com.pdh.cardvault.extra.CLIP_OWNER"
        const val CLIP_LABEL = "CardVault sensitive value"
    }
}

private fun String.isNormalizedCardNumber(): Boolean =
    length in 12..19 && all { character -> character in '0'..'9' }
