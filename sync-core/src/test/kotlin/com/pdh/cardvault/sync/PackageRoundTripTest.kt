package com.pdh.cardvault.sync

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageRoundTripTest {
    private val pairingSecret = ByteArray(16) { it.toByte() }
    private val syncSecret = ByteArray(32) { (it + 40).toByte() }
    private val active = activeRecord()
    private val tombstone = SyncRecord(
        recordId = RECORD_B,
        version = VersionVector.of(mapOf(DEVICE_B to 3L)),
        value = SyncRecordValue.Tombstone(1_700_000_300_000L),
    )
    private val snapshot = snapshotOf(listOf(active, tombstone), listOf(RECORD_A))

    @Test
    fun `pairing file round trips all metadata secret snapshot and kind`() {
        val payload = PairingFilePayload(
            packageId = PACKAGE_A,
            vaultId = VAULT_ID,
            sourceDeviceId = DEVICE_A,
            exportSequence = 4,
            exportedAtEpochMillis = 1_700_000_400_000L,
            expiresAtEpochMillis = 1_700_086_800_000L,
            keyEpoch = 1,
            syncSecret = SecretBytes(syncSecret),
            snapshot = snapshot,
        )
        val file = CardVaultSyncFiles.encodePairing(payload, pairingSecret)
        assertEquals(SyncFileKind.PAIRING, CardVaultSyncFiles.inspectKind(file))
        assertTrue(file.size <= SyncProtocolLimits.FILE_BYTES)
        assertEncryptedFileDoesNotContainSensitiveText(file)

        CardVaultSyncFiles.decodePairing(file, pairingSecret).usePayload { decoded ->
            assertEquals(payload.packageId, decoded.packageId)
            assertEquals(payload.vaultId, decoded.vaultId)
            assertEquals(payload.sourceDeviceId, decoded.sourceDeviceId)
            assertEquals(payload.exportSequence, decoded.exportSequence)
            assertEquals(payload.exportedAtEpochMillis, decoded.exportedAtEpochMillis)
            assertEquals(payload.expiresAtEpochMillis, decoded.expiresAtEpochMillis)
            assertEquals(payload.keyEpoch, decoded.keyEpoch)
            assertContentEquals(syncSecret, decoded.syncSecret.copyBytes())
            assertEquals(snapshot, decoded.snapshot)
        }
        payload.syncSecret.close()
    }

    @Test
    fun `sync file round trips and randomized encryption never repeats`() {
        val payload = SyncFilePayload(
            packageId = PACKAGE_B,
            vaultId = VAULT_ID,
            sourceDeviceId = DEVICE_B,
            exportSequence = 8,
            exportedAtEpochMillis = 1_700_000_500_000L,
            keyEpoch = 1,
            snapshot = snapshot,
        )
        val first = CardVaultSyncFiles.encodeSync(payload, syncSecret)
        val second = CardVaultSyncFiles.encodeSync(payload, syncSecret)
        assertFalse(first.contentEquals(second))
        assertEquals(SyncFileKind.SYNC, CardVaultSyncFiles.inspectKind(first))
        assertEquals(payload, CardVaultSyncFiles.decodeSync(first, syncSecret))
        assertEquals(payload, CardVaultSyncFiles.decodeSync(second, syncSecret))
        assertEncryptedFileDoesNotContainSensitiveText(first)
    }

    @Test
    fun `wrong pairing secret altered ciphertext and altered tag fail without leaking data`() {
        val payload = PairingFilePayload(
            packageId = PACKAGE_A,
            vaultId = VAULT_ID,
            sourceDeviceId = DEVICE_A,
            exportSequence = 1,
            exportedAtEpochMillis = 100,
            expiresAtEpochMillis = 200,
            keyEpoch = 1,
            syncSecret = SecretBytes(syncSecret),
            snapshot = snapshot,
        )
        val file = CardVaultSyncFiles.encodePairing(payload, pairingSecret)
        val candidates = listOf(
            file to pairingSecret.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() },
            file.copyOf().also { it[70] = (it[70].toInt() xor 1).toByte() } to pairingSecret,
            file.copyOf().also { it[it.lastIndex] = (it[it.lastIndex].toInt() xor 1).toByte() } to pairingSecret,
        )
        candidates.forEach { (candidate, secret) ->
            val error = assertFailsWith<SyncProtocolException> {
                CardVaultSyncFiles.decodePairing(candidate, secret)
            }
            assertEquals(SyncErrorCode.AUTHENTICATION_FAILED, error.code)
            assertFalse(error.message.orEmpty().contains(activePayload().cardNumber))
            assertFalse(error.message.orEmpty().contains(requireNotNull(activePayload().cvv)))
        }
        payload.syncSecret.close()
    }

    @Test
    fun `wrong file type is rejected even with a valid header`() {
        val payload = SyncFilePayload(
            PACKAGE_B,
            VAULT_ID,
            DEVICE_B,
            1,
            100,
            1,
            snapshot,
        )
        val file = CardVaultSyncFiles.encodeSync(payload, syncSecret)
        assertEquals(
            SyncErrorCode.INVALID_FORMAT,
            assertFailsWith<SyncProtocolException> {
                CardVaultSyncFiles.decodePairing(file, pairingSecret)
            }.code,
        )
    }

    @Test
    fun `pairing validity window is enforced explicitly`() {
        val payload = PairingFilePayload(
            PACKAGE_A,
            VAULT_ID,
            DEVICE_A,
            1,
            100,
            200,
            1,
            SecretBytes(syncSecret),
            snapshot,
        )
        CardVaultSyncFiles.requirePairingUsableAt(payload, 100)
        CardVaultSyncFiles.requirePairingUsableAt(payload, 200)
        assertEquals(
            SyncErrorCode.STALE_PACKAGE,
            assertFailsWith<SyncProtocolException> {
                CardVaultSyncFiles.requirePairingUsableAt(payload, 99)
            }.code,
        )
        assertEquals(
            SyncErrorCode.STALE_PACKAGE,
            assertFailsWith<SyncProtocolException> {
                CardVaultSyncFiles.requirePairingUsableAt(payload, 201)
            }.code,
        )
        payload.syncSecret.close()
    }

    @Test
    fun `plaintext package codec rejects truncation and trailing data`() {
        val payload = SyncFilePayload(PACKAGE_B, VAULT_ID, DEVICE_B, 1, 100, 1, snapshot)
        val encoded = PackagePayloadCodec.encodeSync(payload)
        assertEquals(payload, PackagePayloadCodec.decodeSync(encoded))
        listOf(0, 1, encoded.lastIndex).forEach { length ->
            assertFailsWith<SyncProtocolException> { PackagePayloadCodec.decodeSync(encoded.copyOf(length)) }
        }
        val trailing = encoded + byteArrayOf(0)
        assertEquals(
            SyncErrorCode.INVALID_FORMAT,
            assertFailsWith<SyncProtocolException> { PackagePayloadCodec.decodeSync(trailing) }.code,
        )
    }

    @Test
    fun `sensitive model textual forms remain redacted`() {
        val payload = activePayload()
        val recordText = active.toString()
        assertFalse(payload.toString().contains(payload.cardNumber))
        assertFalse(payload.toString().contains(requireNotNull(payload.cvv)))
        assertFalse(recordText.contains(payload.cardNumber))
        assertFalse(recordText.contains(requireNotNull(payload.cvv)))
        assertNotEquals(payload.cardNumber, payload.toString())
    }

    private fun activePayload(): CardSyncPayload = (active.value as SyncRecordValue.Active).payload

    private fun assertEncryptedFileDoesNotContainSensitiveText(file: ByteArray) {
        val raw = file.toString(StandardCharsets.ISO_8859_1)
        assertFalse(raw.contains(activePayload().cardNumber))
        assertFalse(raw.contains(requireNotNull(activePayload().cvv)))
        assertFalse(raw.contains(activePayload().nickname))
    }

    private inline fun PairingFilePayload.usePayload(block: (PairingFilePayload) -> Unit) {
        try {
            block(this)
        } finally {
            syncSecret.close()
        }
    }
}
