package com.pdh.cardvault.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotMergerTest {
    @Test
    fun `dominating active update replaces older value without a conflict`() {
        val old = activeRecord(payload = fictionalPayload("旧版本"))
        val updated = old.copy(
            version = VersionVector.of(mapOf(DEVICE_A to 2L)),
            value = (old.value as SyncRecordValue.Active).copy(payload = fictionalPayload("新版本")),
        )
        val local = snapshotOf(listOf(old))
        val incoming = snapshotOf(
            listOf(updated),
            orderVector = VersionVector.of(mapOf(DEVICE_A to 2L)),
        )

        val result = SnapshotMerger.merge(local, incoming, DEVICE_B)
        assertTrue(result.conflicts.isEmpty())
        assertEquals(listOf(updated), result.snapshot.records)
        assertEquals(listOf(RECORD_A), result.snapshot.order.recordIds)
    }

    @Test
    fun `concurrent active edits deterministically preserve both variants`() {
        val left = activeRecord(
            deviceId = DEVICE_A,
            payload = fictionalPayload("左侧虚构版本", number = "9876543210987654321", cvv = "987"),
        )
        val right = activeRecord(
            deviceId = DEVICE_B,
            payload = fictionalPayload("右侧虚构版本", number = "8765432109876543210", cvv = "876"),
        )
        val leftSnapshot = snapshotOf(listOf(left), orderVector = VersionVector.of(mapOf(DEVICE_A to 1L)))
        val rightSnapshot = snapshotOf(listOf(right), orderVector = VersionVector.of(mapOf(DEVICE_B to 1L)))

        val forward = SnapshotMerger.merge(leftSnapshot, rightSnapshot, DEVICE_C)
        val reverse = SnapshotMerger.merge(rightSnapshot, leftSnapshot, DEVICE_C)

        assertEquals(forward.snapshot, reverse.snapshot)
        assertEquals(2, forward.snapshot.records.size)
        assertEquals(2, forward.snapshot.order.recordIds.size)
        assertEquals(1, forward.conflicts.count { it.type == MergeConflictType.ACTIVE_ACTIVE })
        val nicknames = forward.snapshot.records.mapNotNull { record ->
            (record.value as? SyncRecordValue.Active)?.payload?.nickname
        }.toSet()
        assertEquals(setOf("左侧虚构版本", "右侧虚构版本"), nicknames)
        assertNotEquals(RECORD_A, forward.conflicts.first { it.type == MergeConflictType.ACTIVE_ACTIVE }.preservedCopyRecordId)
        forward.snapshot.records.forEach { record ->
            assertEquals(1L, record.version[DEVICE_C])
        }
    }

    @Test
    fun `concurrent deletion wins original ID while update survives as conflict copy`() {
        val active = activeRecord(deviceId = DEVICE_A, payload = fictionalPayload("保留的数据"))
        val deleted = SyncRecord(
            recordId = RECORD_A,
            version = VersionVector.of(mapOf(DEVICE_B to 1L)),
            value = SyncRecordValue.Tombstone(1_700_000_400_000L),
        )
        val activeSnapshot = snapshotOf(
            listOf(active),
            orderVector = VersionVector.of(mapOf(DEVICE_A to 1L)),
        )
        val deletedSnapshot = snapshotOf(
            listOf(deleted),
            orderIds = emptyList(),
            orderVector = VersionVector.of(mapOf(DEVICE_B to 1L)),
        )

        val result = SnapshotMerger.merge(activeSnapshot, deletedSnapshot, DEVICE_C)
        assertTrue(result.snapshot.records.first { it.recordId == RECORD_A }.value is SyncRecordValue.Tombstone)
        val surviving = result.snapshot.records.single { it.recordId != RECORD_A }
        assertEquals("保留的数据", (surviving.value as SyncRecordValue.Active).payload.nickname)
        assertEquals(listOf(surviving.recordId), result.snapshot.order.recordIds)
        assertEquals(1, result.conflicts.count { it.type == MergeConflictType.DELETE_UPDATE })
    }

    @Test
    fun `concurrent order changes are deterministic continuous and causally resolved`() {
        val first = activeRecord(RECORD_A, DEVICE_A)
        val second = activeRecord(RECORD_B, DEVICE_A, payload = fictionalPayload("第二张虚构卡"))
        val left = snapshotOf(
            listOf(first, second),
            orderIds = listOf(RECORD_A, RECORD_B),
            orderVector = VersionVector.of(mapOf(DEVICE_A to 2L)),
        )
        val right = snapshotOf(
            listOf(first, second),
            orderIds = listOf(RECORD_B, RECORD_A),
            orderVector = VersionVector.of(mapOf(DEVICE_B to 4L)),
        )

        val forward = SnapshotMerger.merge(left, right, DEVICE_C)
        val reverse = SnapshotMerger.merge(right, left, DEVICE_C)
        assertEquals(forward.snapshot.order, reverse.snapshot.order)
        assertEquals(2, forward.snapshot.order.recordIds.distinct().size)
        assertEquals(setOf(RECORD_A, RECORD_B), forward.snapshot.order.recordIds.toSet())
        assertEquals(1L, forward.snapshot.order.version[DEVICE_C])
        assertEquals(1, forward.conflicts.count { it.type == MergeConflictType.ORDER })
        assertEquals(2, forward.snapshot.records.size)
    }

    @Test
    fun `independent resolution on two devices converges through a third device`() {
        val a = snapshotOf(
            listOf(activeRecord(deviceId = DEVICE_A, payload = fictionalPayload("设备 A 版本"))),
            orderVector = VersionVector.of(mapOf(DEVICE_A to 1L)),
        )
        val b = snapshotOf(
            listOf(activeRecord(deviceId = DEVICE_B, payload = fictionalPayload("设备 B 版本"))),
            orderVector = VersionVector.of(mapOf(DEVICE_B to 1L)),
        )
        val resolvedByA = SnapshotMerger.merge(a, b, DEVICE_A).snapshot
        val resolvedByB = SnapshotMerger.merge(b, a, DEVICE_B).snapshot

        val throughCForward = SnapshotMerger.merge(resolvedByA, resolvedByB, DEVICE_C).snapshot
        val throughCReverse = SnapshotMerger.merge(resolvedByB, resolvedByA, DEVICE_C).snapshot
        assertEquals(throughCForward, throughCReverse)

        val finalOnA = SnapshotMerger.merge(resolvedByA, throughCForward, DEVICE_A).snapshot
        val finalOnB = SnapshotMerger.merge(resolvedByB, throughCForward, DEVICE_B).snapshot
        assertEquals(throughCForward, finalOnA)
        assertEquals(throughCForward, finalOnB)
        assertEquals(2, throughCForward.records.size)
    }

    @Test
    fun `equal version with different value is rejected as an invariant violation`() {
        val vector = VersionVector.of(mapOf(DEVICE_A to 1L))
        val left = activeRecord(payload = fictionalPayload("值一")).copy(version = vector)
        val right = activeRecord(payload = fictionalPayload("值二")).copy(version = vector)
        val error = assertFailsWith<SyncProtocolException> {
            SnapshotMerger.merge(snapshotOf(listOf(left)), snapshotOf(listOf(right)), DEVICE_B)
        }
        assertEquals(SyncErrorCode.INVARIANT_VIOLATION, error.code)
    }

    @Test
    fun `merge never drops unrelated records or invents conflicts`() {
        val first = activeRecord(RECORD_A, DEVICE_A)
        val second = activeRecord(RECORD_B, DEVICE_B, payload = fictionalPayload("另一张虚构卡"))
        val local = snapshotOf(listOf(first), orderVector = VersionVector.of(mapOf(DEVICE_A to 1L)))
        val incoming = snapshotOf(listOf(second), orderVector = VersionVector.of(mapOf(DEVICE_B to 1L)))

        val result = SnapshotMerger.merge(local, incoming, DEVICE_C)
        assertEquals(setOf(RECORD_A, RECORD_B), result.snapshot.records.map(SyncRecord::recordId).toSet())
        assertEquals(setOf(RECORD_A, RECORD_B), result.snapshot.order.recordIds.toSet())
        assertTrue(result.conflicts.none { it.type != MergeConflictType.ORDER })
    }

    @Test
    fun `a seventeenth resolver device is rejected by the vector safety bound`() {
        val devices = (1..16).map { index -> "00000000-0000-4000-8000-${index.toString().padStart(12, '0')}" }
        val leftEntries = devices.associateWith { 1L }.toMutableMap().also { it[devices[0]] = 2L }
        val rightEntries = devices.associateWith { 1L }.toMutableMap().also { it[devices[1]] = 2L }
        val left = activeRecord(payload = fictionalPayload("上限左侧")).copy(version = VersionVector.of(leftEntries))
        val right = activeRecord(payload = fictionalPayload("上限右侧")).copy(version = VersionVector.of(rightEntries))
        val seventeenth = "00000000-0000-4000-8000-000000000017"

        val error = assertFailsWith<SyncProtocolException> {
            SnapshotMerger.merge(
                snapshotOf(listOf(left), orderVector = VersionVector.of(leftEntries)),
                snapshotOf(listOf(right), orderVector = VersionVector.of(rightEntries)),
                seventeenth,
            )
        }
        assertEquals(SyncErrorCode.LIMIT_EXCEEDED, error.code)
    }
}
