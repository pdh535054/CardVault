package com.pdh.cardvault.wallet

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupReminderPolicyTest {
    private val now = 1_800_000_000_000L
    private fun daysAgo(days: Long) = now - TimeUnit.DAYS.toMillis(days)

    @Test
    fun emptyVaultNeverNeedsAReminder() {
        assertNull(BackupReminderPolicy.evaluate(false, null, null, now))
        assertNull(BackupReminderPolicy.evaluate(false, daysAgo(400), null, now))
    }

    @Test
    fun vaultThatNeverLeftTheDeviceIsFlagged() {
        assertEquals(BackupReminder.NeverSynced, BackupReminderPolicy.evaluate(true, null, null, now))
    }

    @Test
    fun reminderAppearsOnlyAfterTheInterval() {
        assertNull(BackupReminderPolicy.evaluate(true, daysAgo(0), null, now))
        assertNull(BackupReminderPolicy.evaluate(true, daysAgo(29), null, now))
        assertEquals(BackupReminder.Overdue(30), BackupReminderPolicy.evaluate(true, daysAgo(30), null, now))
        assertEquals(BackupReminder.Overdue(95), BackupReminderPolicy.evaluate(true, daysAgo(95), null, now))
    }

    @Test
    fun snoozeHidesTheReminderForSevenDays() {
        val snoozedUntil = BackupReminderPolicy.snoozeUntil(now)
        assertEquals(now + TimeUnit.DAYS.toMillis(7), snoozedUntil)
        assertNull(BackupReminderPolicy.evaluate(true, null, snoozedUntil, now))
        assertNull(BackupReminderPolicy.evaluate(true, null, snoozedUntil, snoozedUntil - 1))
        assertEquals(
            BackupReminder.NeverSynced,
            BackupReminderPolicy.evaluate(true, null, snoozedUntil, snoozedUntil),
        )
    }

    @Test
    fun clockGoingBackwardsNeverProducesNegativeDays() {
        assertEquals(0, BackupReminderPolicy.daysBetween(now, now - 10_000))
        assertNull(BackupReminderPolicy.evaluate(true, now + TimeUnit.DAYS.toMillis(5), null, now))
    }
}
