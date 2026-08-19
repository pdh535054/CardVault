package com.pdh.cardvault.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossPlatformConvergenceTest {
    @Test
    fun `concurrent update delete edit and reorder converge after repeated bidirectional merges`() {
        val baseFirst = activeRecord(RECORD_A, DEVICE_A, payload = fictionalPayload("基础卡 A"))
        val baseSecond = activeRecord(RECORD_B, DEVICE_A, payload = fictionalPayload("基础卡 B"))
        val onDeviceA = snapshotOf(
            records = listOf(
                baseFirst.copy(
                    version = VersionVector.of(mapOf(DEVICE_A to 2L)),
                    value = (baseFirst.value as SyncRecordValue.Active).copy(
                        payload = fictionalPayload("设备 A 已编辑"),
                        updatedAtEpochMillis = 1_700_000_300_000L,
                    ),
                ),
                baseSecond,
            ),
            orderIds = listOf(RECORD_B, RECORD_A),
            orderVector = VersionVector.of(mapOf(DEVICE_A to 2L)),
        )
        val onDeviceB = snapshotOf(
            records = listOf(
                SyncRecord(
                    recordId = RECORD_A,
                    version = VersionVector.of(mapOf(DEVICE_A to 1L, DEVICE_B to 1L)),
                    value = SyncRecordValue.Tombstone(1_700_000_400_000L),
                ),
                baseSecond.copy(
                    version = VersionVector.of(mapOf(DEVICE_A to 1L, DEVICE_B to 1L)),
                    value = (baseSecond.value as SyncRecordValue.Active).copy(
                        payload = fictionalPayload("设备 B 已编辑"),
                        updatedAtEpochMillis = 1_700_000_350_000L,
                    ),
                ),
            ),
            orderIds = listOf(RECORD_B),
            orderVector = VersionVector.of(mapOf(DEVICE_A to 1L, DEVICE_B to 1L)),
        )

        val resolved = SnapshotMerger.merge(onDeviceA, onDeviceB, DEVICE_C)
        assertEquals(2, resolved.conflicts.size)
        assertTrue(resolved.snapshot.records.first { it.recordId == RECORD_A }.value is SyncRecordValue.Tombstone)
        assertEquals(2, resolved.snapshot.order.recordIds.size)

        val finalOnA = SnapshotMerger.merge(onDeviceA, resolved.snapshot, DEVICE_A).snapshot
        val finalOnB = SnapshotMerger.merge(onDeviceB, resolved.snapshot, DEVICE_B).snapshot
        assertEquals(resolved.snapshot, finalOnA)
        assertEquals(resolved.snapshot, finalOnB)
        assertEquals(resolved.snapshot, SnapshotMerger.merge(finalOnA, finalOnB, DEVICE_C).snapshot)
    }

    @Test
    fun `per-device replay sequences remain monotonic across interleaved phone and desktop files`() {
        val firstPhone = SyncPackageDescriptor(PACKAGE_A, VAULT_ID, DEVICE_A, 7, 1)
        val firstDesktop = SyncPackageDescriptor(PACKAGE_B, VAULT_ID, DEVICE_B, 11, 1)
        val afterPhone = ReplayProtector.accept(ReplayMetadata.empty(), firstPhone, VAULT_ID, 1)
        val afterBoth = ReplayProtector.accept(afterPhone, firstDesktop, VAULT_ID, 1)

        val replay = assertFailsWith<SyncProtocolException> {
            ReplayProtector.accept(afterBoth, firstPhone, VAULT_ID, 1)
        }
        assertEquals(SyncErrorCode.REPLAYED_PACKAGE, replay.code)
        val stale = assertFailsWith<SyncProtocolException> {
            ReplayProtector.accept(
                afterBoth,
                SyncPackageDescriptor(
                    "20000000-0000-4000-8000-000000000003",
                    VAULT_ID,
                    DEVICE_B,
                    10,
                    1,
                ),
                VAULT_ID,
                1,
            )
        }
        assertEquals(SyncErrorCode.STALE_PACKAGE, stale.code)

        val advanced = ReplayProtector.accept(
            afterBoth,
            SyncPackageDescriptor(
                "20000000-0000-4000-8000-000000000004",
                VAULT_ID,
                DEVICE_A,
                8,
                1,
            ),
            VAULT_ID,
            1,
        )
        assertEquals(8L, advanced.highestSequenceByDevice[DEVICE_A])
        assertEquals(11L, advanced.highestSequenceByDevice[DEVICE_B])
    }
}
