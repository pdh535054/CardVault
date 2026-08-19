package com.pdh.cardvault.sync

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressSyncProtocolV2Test {
    private val syncSecret = ByteArray(32) { (it + 31).toByte() }
    private val pairingSecret = ByteArray(16) { (it + 9).toByte() }

    @Test
    fun `v2 sync file round trips independent card and address collections`() {
        val addressTombstone = AddressSyncRecord(
            recordId = ADDRESS_B,
            version = VersionVector.of(mapOf(DEVICE_B to 2L)),
            value = AddressSyncRecordValue.Tombstone(1_700_000_300_000L),
        )
        val addresses = addressSnapshotOf(
            records = listOf(activeAddressRecord(), addressTombstone),
            orderIds = listOf(ADDRESS_A),
        )
        val snapshot = snapshotOf(listOf(activeRecord())).copy(addresses = addresses)
        val payload = SyncFilePayload(
            packageId = PACKAGE_A,
            vaultId = VAULT_ID,
            sourceDeviceId = DEVICE_A,
            exportSequence = 5,
            exportedAtEpochMillis = 1_700_000_400_000L,
            keyEpoch = 1,
            snapshot = snapshot,
        )

        val plaintext = PackagePayloadCodec.encodeSync(payload)
        assertEquals(2, plaintext.payloadVersion())
        assertEquals(payload, PackagePayloadCodec.decodeSync(plaintext))

        val encrypted = CardVaultSyncFiles.encodeSync(payload, syncSecret)
        assertEquals(payload, CardVaultSyncFiles.decodeSync(encrypted, syncSecret))
        val raw = encrypted.toString(StandardCharsets.ISO_8859_1)
        assertFalse(raw.contains(fictionalAddressPayload().detailedAddress))
        assertFalse(raw.contains(fictionalAddressPayload().postalCode))
    }

    @Test
    fun `v2 pairing file round trips address collection and secret`() {
        val snapshot = snapshotOf(emptyList()).copy(
            addresses = addressSnapshotOf(listOf(activeAddressRecord())),
        )
        val payload = PairingFilePayload(
            packageId = PACKAGE_A,
            vaultId = VAULT_ID,
            sourceDeviceId = DEVICE_A,
            exportSequence = 1,
            exportedAtEpochMillis = 100L,
            expiresAtEpochMillis = 200L,
            keyEpoch = 1,
            syncSecret = SecretBytes(syncSecret),
            snapshot = snapshot,
        )
        val file = CardVaultSyncFiles.encodePairing(payload, pairingSecret)
        val decoded = CardVaultSyncFiles.decodePairing(file, pairingSecret)
        try {
            assertEquals(snapshot, decoded.snapshot)
            assertNotNull(decoded.snapshot.addresses)
            assertContentEquals(syncSecret, decoded.syncSecret.copyBytes())
        } finally {
            decoded.syncSecret.close()
            payload.syncSecret.close()
        }
    }

    @Test
    fun `legacy v1 card-only file remains readable and absence never erases local addresses`() {
        val legacySnapshot = snapshotOf(listOf(activeRecord()))
        val payload = SyncFilePayload(
            PACKAGE_B,
            VAULT_ID,
            DEVICE_B,
            2,
            1_700_000_500_000L,
            1,
            legacySnapshot,
        )
        val encoded = PackagePayloadCodec.encodeSync(payload)
        assertEquals(1, encoded.payloadVersion())

        val decoded = PackagePayloadCodec.decodeSync(encoded)
        assertNull(decoded.snapshot.addresses)
        assertEquals(legacySnapshot, decoded.snapshot)

        val localAddresses = addressSnapshotOf(listOf(activeAddressRecord()))
        val local = legacySnapshot.copy(addresses = localAddresses)
        val merged = SnapshotMerger.merge(local, decoded.snapshot, DEVICE_C)
        assertEquals(localAddresses, merged.snapshot.addresses)
        assertTrue(merged.conflicts.isEmpty())
    }

    @Test
    fun `published legacy v1 plaintext fixture decodes without an address collection`() {
        val fixture = Base64.getDecoder().decode(
            "Q1ZTWU5DUDEAAAABAAAAJDIwMDAwMDAwLTAwMDAtNDAwMC04MDAwLTAwMDAwMDAwMDAwMgAA" +
                "ACQxMDAwMDAwMC0wMDAwLTQwMDAtODAwMC0wMDAwMDAwMDAwMDEAAAAkMDAwMDAwMDAtMDAw" +
                "MC00MDAwLTgwMDAtMDAwMDAwMDAwMDAyAAAAAAAAAAcAAAGLz+0JIAAAAAEAAAAAAQAAACQw" +
                "MDAwMDAwMC0wMDAwLTQwMDAtODAwMC0wMDAwMDAwMDAwMDIAAAAAAAAAAQAAAYvP6HVAAAAA" +
                "AA==",
        )
        val decoded = PackagePayloadCodec.decodeSync(fixture)
        assertEquals(PACKAGE_B, decoded.packageId)
        assertEquals(VAULT_ID, decoded.vaultId)
        assertEquals(DEVICE_B, decoded.sourceDeviceId)
        assertEquals(7L, decoded.exportSequence)
        assertEquals(1_700_000_500_000L, decoded.exportedAtEpochMillis)
        assertTrue(decoded.snapshot.records.isEmpty())
        assertEquals(VersionVector.of(mapOf(DEVICE_B to 1L)), decoded.snapshot.order.version)
        assertNull(decoded.snapshot.addresses)
    }

    @Test
    fun `v2 decoder rejects truncation trailing data and unsupported payload version`() {
        val payload = SyncFilePayload(
            PACKAGE_A,
            VAULT_ID,
            DEVICE_A,
            1,
            100,
            1,
            snapshotOf(emptyList()).copy(
                addresses = AddressSyncSnapshot.empty(VersionVector.of(mapOf(DEVICE_A to 1L))),
            ),
        )
        val encoded = PackagePayloadCodec.encodeSync(payload)
        assertEquals(2, encoded.payloadVersion())
        listOf(0, 1, encoded.lastIndex).forEach { length ->
            assertFailsWith<SyncProtocolException> {
                PackagePayloadCodec.decodeSync(encoded.copyOf(length))
            }
        }
        assertEquals(
            SyncErrorCode.INVALID_FORMAT,
            assertFailsWith<SyncProtocolException> {
                PackagePayloadCodec.decodeSync(encoded + byteArrayOf(0))
            }.code,
        )
        val unsupported = encoded.copyOf().also { bytes ->
            ByteBuffer.wrap(bytes, 8, Int.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putInt(3)
        }
        assertEquals(
            SyncErrorCode.UNSUPPORTED_VERSION,
            assertFailsWith<SyncProtocolException> {
                PackagePayloadCodec.decodeSync(unsupported)
            }.code,
        )
    }

    @Test
    fun `concurrent address edits preserve both values and identify address conflict`() {
        val leftAddress = activeAddressRecord(
            deviceId = DEVICE_A,
            payload = fictionalAddressPayload("左侧地址", "虚构甲路 1 号"),
        )
        val rightAddress = activeAddressRecord(
            deviceId = DEVICE_B,
            payload = fictionalAddressPayload("右侧地址", "虚构乙路 2 号"),
        )
        val cards = snapshotOf(emptyList())
        val left = cards.copy(
            addresses = addressSnapshotOf(
                listOf(leftAddress),
                orderVector = VersionVector.of(mapOf(DEVICE_A to 1L)),
            ),
        )
        val right = cards.copy(
            addresses = addressSnapshotOf(
                listOf(rightAddress),
                orderVector = VersionVector.of(mapOf(DEVICE_B to 1L)),
            ),
        )

        val forward = SnapshotMerger.merge(left, right, DEVICE_C)
        val reverse = SnapshotMerger.merge(right, left, DEVICE_C)
        assertEquals(forward.snapshot, reverse.snapshot)
        val mergedAddresses = requireNotNull(forward.snapshot.addresses)
        assertEquals(2, mergedAddresses.records.size)
        assertEquals(2, mergedAddresses.order.recordIds.size)
        assertEquals(
            setOf("左侧地址", "右侧地址"),
            mergedAddresses.records.map { record ->
                (record.value as AddressSyncRecordValue.Active).payload.nickname
            }.toSet(),
        )
        assertEquals(
            1,
            forward.conflicts.count {
                it.collection == SyncCollectionKind.ADDRESSES &&
                    it.type == MergeConflictType.ACTIVE_ACTIVE
            },
        )
    }

    @Test
    fun `address delete update and order conflicts resolve independently from cards`() {
        val first = activeAddressRecord(ADDRESS_A, DEVICE_A)
        val second = activeAddressRecord(
            ADDRESS_B,
            DEVICE_A,
            payload = fictionalAddressPayload("第二个地址"),
        )
        val deletedFirst = AddressSyncRecord(
            recordId = ADDRESS_A,
            version = VersionVector.of(mapOf(DEVICE_B to 1L)),
            value = AddressSyncRecordValue.Tombstone(1_700_000_500_000L),
        )
        val cards = snapshotOf(listOf(activeRecord()))
        val local = cards.copy(
            addresses = addressSnapshotOf(
                records = listOf(first, second),
                orderIds = listOf(ADDRESS_A, ADDRESS_B),
                orderVector = VersionVector.of(mapOf(DEVICE_A to 2L)),
            ),
        )
        val incoming = cards.copy(
            addresses = addressSnapshotOf(
                records = listOf(deletedFirst, second),
                orderIds = listOf(ADDRESS_B),
                orderVector = VersionVector.of(mapOf(DEVICE_B to 2L)),
            ),
        )

        val result = SnapshotMerger.merge(local, incoming, DEVICE_C)
        val addresses = requireNotNull(result.snapshot.addresses)
        assertEquals(cards.records, result.snapshot.records)
        assertEquals(3, addresses.records.size)
        assertEquals(2, addresses.order.recordIds.size)
        assertTrue(addresses.records.first { it.recordId == ADDRESS_A }.value is AddressSyncRecordValue.Tombstone)
        assertEquals(
            1,
            result.conflicts.count {
                it.collection == SyncCollectionKind.ADDRESSES &&
                    it.type == MergeConflictType.DELETE_UPDATE
            },
        )
        assertEquals(
            1,
            result.conflicts.count {
                it.collection == SyncCollectionKind.ADDRESSES &&
                    it.type == MergeConflictType.ORDER
            },
        )
    }

    @Test
    fun `empty v2 address collection retains nonempty causal order metadata`() {
        val vector = VersionVector.of(mapOf(DEVICE_A to 1L))
        val empty = AddressSyncSnapshot.empty(vector, 123L)
        assertTrue(empty.records.isEmpty())
        assertEquals(vector, empty.order.version)
        assertEquals(123L, empty.order.updatedAtEpochMillis)
        assertTrue(empty.order.recordIds.isEmpty())
    }

    @Test
    fun `first import into a truly empty device preserves remote card and address order`() {
        val emptyVector = VersionVector.of(mapOf(DEVICE_C to 1L))
        val local = snapshotOf(emptyList(), orderVector = emptyVector).copy(
            addresses = AddressSyncSnapshot.empty(emptyVector),
        )
        val incoming = snapshotOf(
            records = listOf(
                activeRecord(RECORD_A, DEVICE_A),
                activeRecord(
                    RECORD_B,
                    DEVICE_A,
                    payload = fictionalPayload(nickname = "第二张虚构卡"),
                ),
            ),
            orderIds = listOf(RECORD_B, RECORD_A),
            orderVector = VersionVector.of(mapOf(DEVICE_A to 2L)),
        ).copy(
            addresses = addressSnapshotOf(
                records = listOf(
                    activeAddressRecord(ADDRESS_A, DEVICE_A),
                    activeAddressRecord(
                        ADDRESS_B,
                        DEVICE_A,
                        payload = fictionalAddressPayload(nickname = "第二个虚构地址"),
                    ),
                ),
                orderIds = listOf(ADDRESS_B, ADDRESS_A),
                orderVector = VersionVector.of(mapOf(DEVICE_A to 2L)),
            ),
        )

        val result = SnapshotMerger.merge(local, incoming, DEVICE_C)

        assertEquals(listOf(RECORD_B, RECORD_A), result.snapshot.order.recordIds)
        assertEquals(
            listOf(ADDRESS_B, ADDRESS_A),
            requireNotNull(result.snapshot.addresses).order.recordIds,
        )
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun `address textual forms and protocol errors never expose address values`() {
        val payload = fictionalAddressPayload().copy(postalCode = "PST-XY-987")
        val record = activeAddressRecord(payload = payload)
        assertFalse(payload.toString().contains(payload.detailedAddress))
        assertFalse(record.toString().contains(payload.detailedAddress))
        assertFalse(record.toString().contains(payload.postalCode))

        val error = assertFailsWith<IllegalArgumentException> {
            payload.copy(postalCode = "")
        }
        assertFalse(error.message.orEmpty().contains(payload.detailedAddress))
    }

    private fun ByteArray.payloadVersion(): Int = ByteBuffer.wrap(this, 8, Int.SIZE_BYTES)
        .order(ByteOrder.BIG_ENDIAN)
        .int
}
