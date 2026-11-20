package com.pdh.cardvault.wallet

import java.util.concurrent.TimeUnit

/**
 * Shared Android/Windows policy for the "back up your vault" reminder. CardVault keeps data
 * only on the device, so a vault that never left it is one lost or reset device away from
 * being gone. Only timestamps are involved; no vault content.
 */
object BackupReminderPolicy {
    const val INTERVAL_DAYS = 30
    const val SNOOZE_DAYS = 7

    fun daysBetween(fromEpochMillis: Long, toEpochMillis: Long): Int =
        TimeUnit.MILLISECONDS.toDays((toEpochMillis - fromEpochMillis).coerceAtLeast(0L))
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

    fun snoozeUntil(nowEpochMillis: Long): Long =
        nowEpochMillis + TimeUnit.DAYS.toMillis(SNOOZE_DAYS.toLong())

    fun evaluate(
        hasVaultData: Boolean,
        lastSyncAtEpochMillis: Long?,
        snoozedUntilEpochMillis: Long?,
        nowEpochMillis: Long,
    ): BackupReminder? {
        if (!hasVaultData) return null
        if (snoozedUntilEpochMillis != null && nowEpochMillis < snoozedUntilEpochMillis) return null
        if (lastSyncAtEpochMillis == null) return BackupReminder.NeverSynced
        val days = daysBetween(lastSyncAtEpochMillis, nowEpochMillis)
        return if (days >= INTERVAL_DAYS) BackupReminder.Overdue(days) else null
    }
}

sealed interface BackupReminder {
    /** The vault has data but was never exported to, or imported from, another device. */
    data object NeverSynced : BackupReminder

    /** The last export/import is at least [BackupReminderPolicy.INTERVAL_DAYS] old. */
    data class Overdue(val daysSinceSync: Int) : BackupReminder
}
