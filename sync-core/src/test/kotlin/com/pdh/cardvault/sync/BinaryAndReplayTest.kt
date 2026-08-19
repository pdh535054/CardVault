package com.pdh.cardvault.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BinaryAndReplayTest {
    @Test
    fun `version vector encoding is deterministic sorted and round trips`() {
        val first = VersionVector.of(linkedMapOf(DEVICE_C to 7L, DEVICE_A to 2L, DEVICE_B to 4L))
        val second = VersionVector.of(linkedMapOf(DEVICE_B to 4L, DEVICE_C to 7L, DEVICE_A to 2L))
        val firstBytes = VersionVectorCodec.encode(first)
        val secondBytes = VersionVectorCodec.encode(second)
        assertTrue(firstBytes.contentEquals(secondBytes))
        assertEquals(first, VersionVectorCodec.decode(firstBytes))
        assertEquals(listOf(DEVICE_A, DEVICE_B, DEVICE_C), first.entries.keys.toList())
    }

    @Test
    fun `version vector relations merge and increment are correct`() {
        val a1 = VersionVector.of(mapOf(DEVICE_A to 1L))
        val a2 = a1.increment(DEVICE_A)
        val b1 = VersionVector.of(mapOf(DEVICE_B to 1L))
        assertEquals(VectorRelation.DOMINATES, a2.compare(a1))
        assertEquals(VectorRelation.IS_DOMINATED, a1.compare(a2))
        assertEquals(VectorRelation.CONCURRENT, a1.compare(b1))
        assertEquals(VectorRelation.EQUAL, a2.compare(a2))
        assertEquals(mapOf(DEVICE_A to 2L, DEVICE_B to 1L), a2.merge(b1).entries)
    }

    @Test
    fun `duplicate package stale sequence wrong vault and wrong epoch are rejected`() {
        val first = SyncPackageDescriptor(PACKAGE_A, VAULT_ID, DEVICE_A, 5, 1)
        val accepted = ReplayProtector.accept(
            ReplayMetadata.empty(),
            first,
            expectedVaultId = VAULT_ID,
            expectedKeyEpoch = 1,
        )
        assertEquals(5L, accepted.highestSequenceByDevice[DEVICE_A])

        assertEquals(
            SyncErrorCode.REPLAYED_PACKAGE,
            assertFailsWith<SyncProtocolException> {
                ReplayProtector.accept(accepted, first, VAULT_ID, 1)
            }.code,
        )
        assertEquals(
            SyncErrorCode.STALE_PACKAGE,
            assertFailsWith<SyncProtocolException> {
                ReplayProtector.accept(
                    accepted,
                    SyncPackageDescriptor(PACKAGE_B, VAULT_ID, DEVICE_A, 4, 1),
                    VAULT_ID,
                    1,
                )
            }.code,
        )
        assertEquals(
            SyncErrorCode.VAULT_MISMATCH,
            assertFailsWith<SyncProtocolException> {
                ReplayProtector.accept(
                    accepted,
                    SyncPackageDescriptor(PACKAGE_B, "10000000-0000-4000-8000-000000000099", DEVICE_B, 1, 1),
                    VAULT_ID,
                    1,
                )
            }.code,
        )
        assertEquals(
            SyncErrorCode.STALE_PACKAGE,
            assertFailsWith<SyncProtocolException> {
                ReplayProtector.accept(
                    accepted,
                    SyncPackageDescriptor(PACKAGE_B, VAULT_ID, DEVICE_B, 1, 2),
                    VAULT_ID,
                    1,
                )
            }.code,
        )
    }

    @Test
    fun `replay descriptor adapters retain only non-sensitive metadata`() {
        val sync = SyncFilePayload(PACKAGE_B, VAULT_ID, DEVICE_B, 9, 100, 1, snapshotOf(listOf(activeRecord())))
        assertEquals(
            SyncPackageDescriptor(PACKAGE_B, VAULT_ID, DEVICE_B, 9, 1),
            ReplayProtector.descriptor(sync),
        )
    }
}
