package com.pdh.cardvault.desktop.sync

import com.pdh.cardvault.desktop.data.DesktopSyncState
import com.pdh.cardvault.desktop.data.DesktopVaultSnapshot
import com.pdh.cardvault.desktop.data.VersionVector
import com.pdh.cardvault.desktop.model.CardCoverStyle
import com.pdh.cardvault.desktop.model.DesktopCard
import com.pdh.cardvault.desktop.model.DesktopAddress
import com.pdh.cardvault.sync.CardVaultSyncFiles
import com.pdh.cardvault.sync.PairingCode
import com.pdh.cardvault.sync.SecretBytes
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncCoreDesktopGatewayTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-17T08:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `pairing then sync preserves card address and template data`() {
        val source = snapshotWithCard()
        val sourceGateway = SyncCoreDesktopGateway(clock, SecureRandom())
        val pairing = sourceGateway.export(source, newPairing = true)
        val code = assertNotNull(pairing.pairingCode)

        val target = DesktopVaultSnapshot(emptyList(), 0, DesktopSyncState.create())
        val targetGateway = SyncCoreDesktopGateway(clock, SecureRandom())
        val imported = targetGateway.import(pairing.bytes, code, target)
        pairing.bytes.fill(0)

        assertEquals(source.cards.single().cardNumber, imported.snapshot.cards.single().cardNumber)
        assertEquals(source.cards.single().cardTemplateId, imported.snapshot.cards.single().cardTemplateId)
        assertEquals(source.addresses.single(), imported.snapshot.addresses.single())
        assertEquals(pairing.snapshotAfterExport.syncState.vaultId, imported.snapshot.syncState.vaultId)

        val syncFile = targetGateway.export(imported.snapshot, newPairing = false)
        val mergedBack = sourceGateway.import(syncFile.bytes, null, pairing.snapshotAfterExport)
        syncFile.bytes.fill(0)
        assertEquals(1, mergedBack.snapshot.cards.size)
        assertEquals(1, mergedBack.snapshot.addresses.size)
        assertTrue(mergedBack.conflicts >= 0)
    }

    @Test
    fun `legacy card-only pairing preserves a desktop local address`() {
        val sourceGateway = SyncCoreDesktopGateway(clock, SecureRandom())
        val pairing = sourceGateway.export(snapshotWithCard(), newPairing = true)
        val code = assertNotNull(pairing.pairingCode)
        val pairingSecret = PairingCode.decode(code)
        val opened = CardVaultSyncFiles.decodePairing(pairing.bytes, pairingSecret)
        val sharedSecret = opened.syncSecret.copyBytes()
        val legacyPayload = opened.copy(
            syncSecret = SecretBytes(sharedSecret),
            snapshot = opened.snapshot.copy(addresses = null),
        )
        val legacyBytes = try {
            CardVaultSyncFiles.encodePairing(legacyPayload, pairingSecret, SecureRandom())
        } finally {
            legacyPayload.syncSecret.close()
            opened.syncSecret.close()
            sharedSecret.fill(0)
            pairingSecret.fill(0)
            pairing.bytes.fill(0)
        }
        val target = snapshotWithCard()
        val retainedAddress = target.addresses.single()

        val imported = try {
            SyncCoreDesktopGateway(clock, SecureRandom()).import(legacyBytes, code, target)
        } finally {
            legacyBytes.fill(0)
        }

        assertEquals(listOf(retainedAddress), imported.snapshot.addresses)
    }

    private fun snapshotWithCard(): DesktopVaultSnapshot {
        val state = DesktopSyncState.create()
        val card = DesktopCard.create(
            nickname = "同步实验卡",
            issuerName = "虚构发行方",
            cardNumber = "8".repeat(19),
            expiryMonth = 11,
            expiryYear = 2098,
            cvv = "8".repeat(3),
            notes = "测试",
            style = CardCoverStyle.Default,
            sortOrder = 0,
            clock = clock,
        )
        val address = DesktopAddress.create(
            nickname = "同步实验地址",
            detailedAddress = "虚构大道 18 号",
            city = "虚构城",
            other = "虚构楼层",
            postalCode = "000000",
            country = "虚构国",
            style = CardCoverStyle.Default,
            sortOrder = 0,
            clock = clock,
        )
        return DesktopVaultSnapshot(
            cards = listOf(card),
            revision = 1,
            syncState = state.copy(
                recordVectors = mapOf(card.id to VersionVector(mapOf(state.deviceId to 1L))),
                addressRecordVectors = mapOf(address.id to VersionVector(mapOf(state.deviceId to 1L))),
            ),
            addresses = listOf(address),
        )
    }
}
