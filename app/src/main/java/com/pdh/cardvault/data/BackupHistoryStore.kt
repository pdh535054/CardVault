package com.pdh.cardvault.data

import android.content.Context
import androidx.core.content.edit

/**
 * Non-sensitive timestamps used for the "back up your vault" reminder. Only epoch
 * millisecond values are stored; no vault content, file names or pairing codes.
 */
interface BackupHistoryStore {
    fun lastSyncAtEpochMillis(): Long?

    fun recordSync(atEpochMillis: Long)

    fun reminderSnoozedUntilEpochMillis(): Long?

    fun snoozeReminder(untilEpochMillis: Long)
}

class SharedPreferencesBackupHistoryStore(
    context: Context,
) : BackupHistoryStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        NON_SENSITIVE_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun lastSyncAtEpochMillis(): Long? =
        preferences.getLong(KEY_LAST_SYNC_AT, 0L).takeIf { it > 0L }

    override fun recordSync(atEpochMillis: Long) {
        preferences.edit {
            putLong(KEY_LAST_SYNC_AT, atEpochMillis)
            remove(KEY_REMINDER_SNOOZED_UNTIL)
        }
    }

    override fun reminderSnoozedUntilEpochMillis(): Long? =
        preferences.getLong(KEY_REMINDER_SNOOZED_UNTIL, 0L).takeIf { it > 0L }

    override fun snoozeReminder(untilEpochMillis: Long) {
        preferences.edit { putLong(KEY_REMINDER_SNOOZED_UNTIL, untilEpochMillis) }
    }

    private companion object {
        const val KEY_LAST_SYNC_AT = "backup_last_sync_at"
        const val KEY_REMINDER_SNOOZED_UNTIL = "backup_reminder_snoozed_until"
    }
}

internal class InMemoryBackupHistoryStore : BackupHistoryStore {
    private var lastSync: Long? = null
    private var snoozedUntil: Long? = null

    override fun lastSyncAtEpochMillis(): Long? = lastSync

    override fun recordSync(atEpochMillis: Long) {
        lastSync = atEpochMillis
        snoozedUntil = null
    }

    override fun reminderSnoozedUntilEpochMillis(): Long? = snoozedUntil

    override fun snoozeReminder(untilEpochMillis: Long) {
        snoozedUntil = untilEpochMillis
    }
}
