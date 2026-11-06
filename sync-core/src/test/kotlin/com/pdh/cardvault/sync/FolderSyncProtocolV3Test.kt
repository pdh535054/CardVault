package com.pdh.cardvault.sync

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderSyncProtocolV3Test {
    private val cardFolderId = "50000000-0000-4000-8000-000000000001"
    private val addressFolderId = "50000000-0000-4000-8000-000000000002"

    @Test fun `moving a whole folder syncs its parent without changing descendants or card address membership`() {
        for (kind in FolderCollectionKind.entries) {
            val childId = "50000000-0000-4000-8000-000000000003"
            val source = folderRecord(cardFolderId, kind, "Synthetic source")
            val destination = folderRecord(addressFolderId, kind, "Synthetic destination")
            val child = folderRecord(childId, kind, "Synthetic child").let { record ->
                val value = record.value as FolderSyncRecordValue.Active
                record.copy(value = value.copy(payload = value.payload.copy(parentFolderId = cardFolderId)))
            }
            val local = snapshotOf(if (kind == FolderCollectionKind.CARDS) listOf(activeRecord(
                payload = fictionalPayload().copy(schemaVersion = 2, folderId = childId))) else emptyList()).copy(
                addresses = addressSnapshotOf(if (kind == FolderCollectionKind.ADDRESSES) listOf(activeAddressRecord(
                    payload = fictionalAddressPayload().copy(schemaVersion = 2, folderId = childId))) else emptyList()),
                folders = FolderSyncSnapshot(listOf(source, child, destination)),
            )
            val sourceValue = source.value as FolderSyncRecordValue.Active
            val moved = source.copy(version = source.version.increment(DEVICE_A), value = sourceValue.copy(
                payload = sourceValue.payload.copy(parentFolderId = addressFolderId, colorRgb = 0x27AE93)))
            val incoming = local.copy(folders = FolderSyncSnapshot(listOf(moved, child, destination)))
            val merged = SnapshotMerger.merge(local, incoming, DEVICE_C)
            assertTrue(merged.conflicts.isEmpty())
            val encoded = PackagePayloadCodec.encodeSync(SyncFilePayload(PACKAGE_A, VAULT_ID, DEVICE_A, 1, 100, 1, merged.snapshot))
            val decoded = PackagePayloadCodec.decodeSync(encoded).snapshot
            val folders = requireNotNull(decoded.folders).records.associateBy { it.recordId }
            val parent = (folders.getValue(cardFolderId).value as FolderSyncRecordValue.Active).payload
            assertEquals(addressFolderId, parent.parentFolderId)
            assertEquals(0x27AE93, parent.colorRgb)
            assertEquals(cardFolderId, (folders.getValue(childId).value as FolderSyncRecordValue.Active).payload.parentFolderId)
            if (kind == FolderCollectionKind.CARDS) assertEquals(childId, (decoded.records.single().value as SyncRecordValue.Active).payload.folderId)
            else assertEquals(childId, (requireNotNull(decoded.addresses).records.single().value as AddressSyncRecordValue.Active).payload.folderId)
        }
    }

    @Test
    fun `nested parent survives binary round trip and legacy folder stays at root`() {
        val childId = "50000000-0000-4000-8000-000000000003"
        val parent = folderRecord(cardFolderId, FolderCollectionKind.CARDS, "Synthetic parent")
        val child = folderRecord(childId, FolderCollectionKind.CARDS, "Synthetic child").let { record ->
            val value = record.value as FolderSyncRecordValue.Active
            record.copy(value = value.copy(payload = value.payload.copy(parentFolderId = cardFolderId, colorRgb = 0x17ABCD)))
        }
        val old = folderRecord(addressFolderId, FolderCollectionKind.ADDRESSES, "Synthetic legacy").let { record ->
            val value = record.value as FolderSyncRecordValue.Active
            record.copy(value = value.copy(payload = value.payload.copy(schemaVersion = 1)))
        }
        val snapshot = snapshotOf(emptyList()).copy(
            addresses = addressSnapshotOf(emptyList()),
            folders = FolderSyncSnapshot(listOf(parent, child, old)),
        )
        val encoded = PackagePayloadCodec.encodeSync(SyncFilePayload(PACKAGE_A, VAULT_ID, DEVICE_A, 1, 100, 1, snapshot))
        val decoded = PackagePayloadCodec.decodeSync(encoded)
        val folders = requireNotNull(decoded.snapshot.folders).records.associateBy(FolderSyncRecord::recordId)
        assertEquals(cardFolderId, (folders.getValue(childId).value as FolderSyncRecordValue.Active).payload.parentFolderId)
        assertEquals(0x17ABCD, (folders.getValue(childId).value as FolderSyncRecordValue.Active).payload.colorRgb)
        assertNull((folders.getValue(addressFolderId).value as FolderSyncRecordValue.Active).payload.parentFolderId)
    }

    @Test fun `legacy and current default folder schemas merge without a false conflict`() {
        val current = folderRecord(cardFolderId, FolderCollectionKind.CARDS, "Synthetic legacy")
        val value = current.value as FolderSyncRecordValue.Active
        val legacy = current.copy(value = value.copy(payload = value.payload.copy(schemaVersion = 2)))
        val base = snapshotOf(emptyList()).copy(addresses = addressSnapshotOf(emptyList()))
        val result = SnapshotMerger.merge(base.copy(folders = FolderSyncSnapshot(listOf(current))),
            base.copy(folders = FolderSyncSnapshot(listOf(legacy))), DEVICE_C)
        assertTrue(result.conflicts.isEmpty())
        assertEquals(1, result.snapshot.folders?.records?.size)
    }

    @Test
    fun `merge safely flattens cyclic folder parents`() {
        val first = folderRecord(cardFolderId, FolderCollectionKind.CARDS, "Synthetic first").let { record ->
            val value = record.value as FolderSyncRecordValue.Active
            record.copy(value = value.copy(payload = value.payload.copy(parentFolderId = addressFolderId)))
        }
        val second = folderRecord(addressFolderId, FolderCollectionKind.CARDS, "Synthetic second").let { record ->
            val value = record.value as FolderSyncRecordValue.Active
            record.copy(value = value.copy(payload = value.payload.copy(parentFolderId = cardFolderId)))
        }
        val base = snapshotOf(emptyList()).copy(addresses = addressSnapshotOf(emptyList()))
        val merged = SnapshotMerger.merge(
            base,
            base.copy(folders = FolderSyncSnapshot(listOf(first, second))),
            DEVICE_C,
        ).snapshot
        requireNotNull(merged.folders).records.forEach { record ->
            assertNull((record.value as FolderSyncRecordValue.Active).payload.parentFolderId)
        }
    }

    @Test
    fun `v3 round trip carries encrypted folders and memberships`() {
        val folders = FolderSyncSnapshot(
            listOf(
                folderRecord(cardFolderId, FolderCollectionKind.CARDS, "旅行卡"),
                folderRecord(addressFolderId, FolderCollectionKind.ADDRESSES, "常用地址"),
            ),
        )
        val card = activeRecord(
            payload = fictionalPayload().copy(schemaVersion = 2, folderId = cardFolderId),
        )
        val address = activeAddressRecord(
            payload = fictionalAddressPayload().copy(schemaVersion = 2, folderId = addressFolderId),
        )
        val snapshot = snapshotOf(listOf(card)).copy(
            addresses = addressSnapshotOf(listOf(address)),
            folders = folders,
        )
        val payload = SyncFilePayload(PACKAGE_A, VAULT_ID, DEVICE_A, 1, 100, 1, snapshot)
        val encoded = PackagePayloadCodec.encodeSync(payload)

        assertEquals(3, ByteBuffer.wrap(encoded, 8, 4).order(ByteOrder.BIG_ENDIAN).int)
        assertEquals(payload, PackagePayloadCodec.decodeSync(encoded))
    }

    @Test
    fun `legacy v2 snapshot does not erase local folders or memberships`() {
        val local = snapshotOf(
            listOf(activeRecord(payload = fictionalPayload().copy(schemaVersion = 2, folderId = cardFolderId))),
        ).copy(
            addresses = addressSnapshotOf(emptyList()),
            folders = FolderSyncSnapshot(listOf(folderRecord(cardFolderId, FolderCollectionKind.CARDS, "本地文件夹"))),
        )
        val legacyV2 = snapshotOf(listOf(activeRecord())).copy(
            addresses = addressSnapshotOf(emptyList()),
        )

        assertNull(legacyV2.folders)
        val merged = SnapshotMerger.merge(local, legacyV2, DEVICE_C).snapshot
        assertEquals(local.folders, merged.folders)
        val active = merged.records.first { it.recordId == RECORD_A }.value as SyncRecordValue.Active
        assertEquals(cardFolderId, active.payload.folderId)
    }

    @Test
    fun `concurrent folder names preserve a conflict copy without leaking name in text`() {
        val left = FolderSyncSnapshot(listOf(folderRecord(cardFolderId, FolderCollectionKind.CARDS, "日常", DEVICE_A)))
        val right = FolderSyncSnapshot(listOf(folderRecord(cardFolderId, FolderCollectionKind.CARDS, "出行", DEVICE_B)))
        val cards = snapshotOf(emptyList()).copy(addresses = addressSnapshotOf(emptyList()))
        val result = SnapshotMerger.merge(cards.copy(folders = left), cards.copy(folders = right), DEVICE_C)

        assertEquals(2, requireNotNull(result.snapshot.folders).records.size)
        assertTrue(result.conflicts.any { it.collection == SyncCollectionKind.FOLDERS })
        requireNotNull(result.snapshot.folders).records.forEach { record ->
            assertTrue(!record.toString().contains("日常") && !record.toString().contains("出行"))
        }
    }

    @Test
    fun `deleted folder returns member card to the unfiled root`() {
        val card = activeRecord(
            payload = fictionalPayload().copy(schemaVersion = 2, folderId = cardFolderId),
        )
        val local = snapshotOf(listOf(card)).copy(
            addresses = addressSnapshotOf(emptyList()),
            folders = FolderSyncSnapshot(
                listOf(folderRecord(cardFolderId, FolderCollectionKind.CARDS, "本地文件夹")),
            ),
        )
        val incoming = local.copy(
            folders = FolderSyncSnapshot(
                listOf(
                    FolderSyncRecord(
                        recordId = cardFolderId,
                        version = VersionVector.of(mapOf(DEVICE_A to 2L)),
                        value = FolderSyncRecordValue.Tombstone(1_700_000_200_000L),
                    ),
                ),
            ),
        )

        val merged = SnapshotMerger.merge(local, incoming, DEVICE_C).snapshot

        val active = merged.records.single().value as SyncRecordValue.Active
        assertNull(active.payload.folderId)
        assertTrue(requireNotNull(merged.folders).records.single().value is FolderSyncRecordValue.Tombstone)
    }

    private fun folderRecord(
        id: String,
        kind: FolderCollectionKind,
        name: String,
        deviceId: String = DEVICE_A,
    ): FolderSyncRecord = FolderSyncRecord(
        recordId = id,
        version = VersionVector.of(mapOf(deviceId to 1L)),
        value = FolderSyncRecordValue.Active(
            FolderSyncPayload(collection = kind, name = name),
            createdAtEpochMillis = 1_700_000_000_000L,
            updatedAtEpochMillis = 1_700_000_100_000L,
        ),
    )
}
