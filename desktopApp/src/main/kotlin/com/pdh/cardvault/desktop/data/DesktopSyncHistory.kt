package com.pdh.cardvault.desktop.data

import java.util.prefs.Preferences

/**
 * Non-sensitive timestamps behind the desktop "back up your vault" reminder. Only epoch
 * millisecond values are stored (in the current user's Java preferences); no vault content.
 */
interface DesktopSyncHistory {
    fun lastSyncAtEpochMillis(): Long?

    fun recordSync(atEpochMillis: Long)

    fun reminderSnoozedUntilEpochMillis(): Long?

    fun snoozeReminder(untilEpochMillis: Long)
}

class PreferencesDesktopSyncHistory(
    private val preferences: Preferences = Preferences.userRoot().node("com/pdh/cardvault/desktop"),
) : DesktopSyncHistory {
    override fun lastSyncAtEpochMillis(): Long? =
        preferences.getLong(KEY_LAST_SYNC_AT, 0L).takeIf { it > 0L }

    override fun recordSync(atEpochMillis: Long) {
        preferences.putLong(KEY_LAST_SYNC_AT, atEpochMillis)
        preferences.remove(KEY_REMINDER_SNOOZED_UNTIL)
    }

    override fun reminderSnoozedUntilEpochMillis(): Long? =
        preferences.getLong(KEY_REMINDER_SNOOZED_UNTIL, 0L).takeIf { it > 0L }

    override fun snoozeReminder(untilEpochMillis: Long) {
        preferences.putLong(KEY_REMINDER_SNOOZED_UNTIL, untilEpochMillis)
    }

    private companion object {
        const val KEY_LAST_SYNC_AT = "backupLastSyncAt"
        const val KEY_REMINDER_SNOOZED_UNTIL = "backupReminderSnoozedUntil"
    }
}

class InMemoryDesktopSyncHistory : DesktopSyncHistory {
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
