package com.pdh.cardvault.desktop.sync

import com.pdh.cardvault.desktop.data.DesktopSyncState
import com.pdh.cardvault.desktop.data.DesktopVaultSnapshot
import com.pdh.cardvault.desktop.data.SecretBytes as DesktopSecret
import com.pdh.cardvault.desktop.data.VersionVector
import com.pdh.cardvault.desktop.model.AndroidTemplateStyleCodec
import com.pdh.cardvault.desktop.model.DesktopCard
import com.pdh.cardvault.sync.CardSyncPayload
import com.pdh.cardvault.sync.CardVaultSyncFiles
import com.pdh.cardvault.sync.PairingCode
import com.pdh.cardvault.sync.PairingFilePayload
import com.pdh.cardvault.sync.SecretBytes as CoreSecret
import com.pdh.cardvault.sync.SyncOrder
import com.pdh.cardvault.sync.SyncRecord
import com.pdh.cardvault.sync.SyncRecordValue
import com.pdh.cardvault.sync.SyncSnapshot
import com.pdh.cardvault.sync.VersionVector as CoreVector
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SyncCoreDesktopInteroperabilityTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-17T08:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `a desktop device rejects its own pairing package`() {
        val gateway = SyncCoreDesktopGateway(clock, SecureRandom())
        val pairing = gateway.export(snapshotWithCard(), newPairing = true)
        val code = assertNotNull(pairing.pairingCode)

        try {
            assertFailsWith<DesktopSyncException> {
                gateway.import(pairing.bytes, code, pairing.snapshotAfterExport)
            }
        } finally {
            pairing.bytes.fill(0)
        }
    }

    @Test
    fun `an already paired desktop accepts a pairing package from a newer key epoch`() {
        val vaultId = "10000000-0000-4000-8000-000000000077"
        val sourceDevice = "00000000-0000-4000-8000-000000000077"
        val targetDevice = "00000000-0000-4000-8000-000000000078"
        val sourceState = pairedState(vaultId, sourceDevice, keyEpoch = 2, keyByte = 0x22)
        val targetState = pairedState(vaultId, targetDevice, keyEpoch = 1, keyByte = 0x11)
        val pairing = SyncCoreDesktopGateway(clock, SecureRandom()).export(
            DesktopVaultSnapshot(emptyList(), 0, sourceState),
            newPairing = true,
        )

        try {
            val imported = SyncCoreDesktopGateway(clock, SecureRandom()).import(
                pairing.bytes,
                assertNotNull(pairing.pairingCode),
                DesktopVaultSnapshot(emptyList(), 0, targetState),
            )
            assertEquals(2L, imported.snapshot.syncState.keyEpoch)
        } finally {
            pairing.bytes.fill(0)
        }
    }

    @Test
    fun `an already paired desktop rejects a pairing package from an older key epoch`() {
        val vaultId = "10000000-0000-4000-8000-000000000079"
        val sourceState = pairedState(
            vaultId,
            "00000000-0000-4000-8000-000000000079",
            keyEpoch = 1,
            keyByte = 0x11,
        )
        val targetState = pairedState(
            vaultId,
            "00000000-0000-4000-8000-000000000080",
            keyEpoch = 2,
            keyByte = 0x22,
        )
        val pairing = SyncCoreDesktopGateway(clock, SecureRandom()).export(
            DesktopVaultSnapshot(emptyList(), 0, sourceState),
            newPairing = true,
        )

        try {
            assertFailsWith<DesktopSyncException> {
                SyncCoreDesktopGateway(clock, SecureRandom()).import(
                    pairing.bytes,
                    assertNotNull(pairing.pairingCode),
                    DesktopVaultSnapshot(emptyList(), 0, targetState),
                )
            }
        } finally {
            pairing.bytes.fill(0)
        }
    }

    @Test
    fun `custom v3 cover identifier survives pairing and sync without an edit`() {
        val templateId = "custom:v3:rgb-13C400:continuous:orbit:rgb-F0A1C2"
        val source = snapshotWithCard(templateId)
        val sourceGateway = SyncCoreDesktopGateway(clock, SecureRandom())
        val targetGateway = SyncCoreDesktopGateway(clock, SecureRandom())
        val pairing = sourceGateway.export(source, newPairing = true)
        val imported = try {
            targetGateway.import(
                pairing.bytes,
                assertNotNull(pairing.pairingCode),
                DesktopVaultSnapshot(emptyList(), 0, DesktopSyncState.create()),
            )
        } finally {
            pairing.bytes.fill(0)
        }
        assertEquals(templateId, imported.snapshot.cards.single().cardTemplateId)

        val sync = targetGateway.export(imported.snapshot, newPairing = false)
        val mergedBack = try {
            sourceGateway.import(sync.bytes, null, pairing.snapshotAfterExport)
        } finally {
            sync.bytes.fill(0)
        }
        assertEquals(templateId, mergedBack.snapshot.cards.single().cardTemplateId)
    }

    @Test
    fun `a protocol-valid zero timestamp remains lossless through desktop import and export`() {
        val sourceDevice = "00000000-0000-4000-8000-000000000091"
        val recordId = "30000000-0000-4000-8000-000000000091"
        val pairing = PairingCode.generate(SecureRandom())
        val pairingCode = pairing.displayCode
        val pairingSecret = pairing.secret.copyBytes()
        val syncKey = ByteArray(32) { 0x5A }
        val payloadSecret = CoreSecret(syncKey)
        val coreSnapshot = SyncSnapshot(
            records = listOf(
                SyncRecord(
                    recordId = recordId,
                    version = CoreVector.of(mapOf(sourceDevice to 1L)),
                    value = SyncRecordValue.Active(
                        payload = CardSyncPayload(
                            nickname = "零时刻虚构卡",
                            issuerName = "虚构发行方",
                            cardNumber = "8".repeat(19),
                            expiryMonth = 11,
                            expiryYear = 2098,
                            saveCvv = true,
                            cvv = "8".repeat(3),
                            cardTemplateId = "custom:v3:rgb-13C400:continuous:orbit:rgb-F0A1C2",
                            notes = "虚构测试",
                        ),
                        createdAtEpochMillis = 0,
                        updatedAtEpochMillis = 0,
                    ),
                ),
            ),
            order = SyncOrder(
                version = CoreVector.of(mapOf(sourceDevice to 1L)),
                updatedAtEpochMillis = 0,
                recordIds = listOf(recordId),
            ),
        )
        val now = clock.millis()
        val payload = PairingFilePayload(
            packageId = "20000000-0000-4000-8000-000000000091",
            vaultId = "10000000-0000-4000-8000-000000000091",
            sourceDeviceId = sourceDevice,
            exportSequence = 1,
            exportedAtEpochMillis = now,
            expiresAtEpochMillis = now + 60_000,
            keyEpoch = 1,
            syncSecret = payloadSecret,
            snapshot = coreSnapshot,
        )
        val pairingBytes = try {
            CardVaultSyncFiles.encodePairing(payload, pairingSecret, SecureRandom())
        } finally {
            payloadSecret.close()
            pairingSecret.fill(0)
            syncKey.fill(0)
            pairing.close()
        }

        val gateway = SyncCoreDesktopGateway(clock, SecureRandom())
        val imported = try {
            gateway.import(
                pairingBytes,
                pairingCode,
                DesktopVaultSnapshot(emptyList(), 0, DesktopSyncState.create()),
            )
        } finally {
            pairingBytes.fill(0)
        }
        val importedCreatedAt = imported.snapshot.cards.single().createdAtEpochMillis
        val exported = gateway.export(imported.snapshot, newPairing = false)
        val storedSyncKey = assertNotNull(imported.snapshot.syncState.sharedSyncKey).copyBytes()
        val reexportedCreatedAt = try {
            val decoded = CardVaultSyncFiles.decodeSync(exported.bytes, storedSyncKey)
            (decoded.snapshot.records.single().value as SyncRecordValue.Active).createdAtEpochMillis
        } finally {
            storedSyncKey.fill(0)
            exported.bytes.fill(0)
        }

        assertEquals(0L, importedCreatedAt)
        assertEquals(0L, reexportedCreatedAt)
    }

    private fun pairedState(
        vaultId: String,
        deviceId: String,
        keyEpoch: Long,
        keyByte: Byte,
    ): DesktopSyncState {
        val key = ByteArray(32) { keyByte }
        return try {
            DesktopSyncState(
                vaultId = vaultId,
                deviceId = deviceId,
                keyEpoch = keyEpoch,
                sharedSyncKey = DesktopSecret.of(key),
                orderVector = VersionVector(mapOf(deviceId to 1L)),
            )
        } finally {
            key.fill(0)
        }
    }

    private fun snapshotWithCard(templateId: String? = null): DesktopVaultSnapshot {
        val state = DesktopSyncState.create()
        val style = templateId?.let(AndroidTemplateStyleCodec::decode)
            ?: com.pdh.cardvault.desktop.model.CardCoverStyle.Default
        val card = DesktopCard.create(
            nickname = "同步实验卡",
            issuerName = "虚构发行方",
            cardNumber = "8".repeat(19),
            expiryMonth = 11,
            expiryYear = 2098,
            cvv = "8".repeat(3),
            notes = "虚构测试",
            style = style,
            sortOrder = 0,
            clock = clock,
        )
        return DesktopVaultSnapshot(
            cards = listOf(card),
            revision = 1,
            syncState = state.copy(
                recordVectors = mapOf(card.id to VersionVector(mapOf(state.deviceId to 1L))),
            ),
        )
    }
}
