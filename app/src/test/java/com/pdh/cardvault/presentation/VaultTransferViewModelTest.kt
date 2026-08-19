package com.pdh.cardvault.presentation

import android.content.Intent
import com.pdh.cardvault.security.auth.AuthenticationAction
import com.pdh.cardvault.sync.CardVaultSyncFiles
import com.pdh.cardvault.sync.PairingFilePayload
import com.pdh.cardvault.sync.SecretBytes
import com.pdh.cardvault.sync.SyncErrorCode
import com.pdh.cardvault.sync.SyncFilePayload
import com.pdh.cardvault.sync.SyncOrder
import com.pdh.cardvault.sync.SyncProtocolException
import com.pdh.cardvault.sync.SyncSnapshot
import com.pdh.cardvault.sync.VersionVector
import com.pdh.cardvault.sync.android.PairingExport
import com.pdh.cardvault.sync.android.SyncExport
import com.pdh.cardvault.sync.android.SyncImportResult
import com.pdh.cardvault.sync.android.SyncPairingStatus
import java.util.UUID
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultTransferViewModelTest {
    @Test
    fun unpairedExportWaitsForAuthorizationThenPublishesPairingFileAndWipesBuffer() {
        val fixture = fixture(paired = false)

        fixture.viewModel.requestExport()

        assertEquals(AuthenticationAction.ExportVault, fixture.viewModel.pendingAuthenticationAction())
        assertEquals(0, fixture.operations.pairingExportCount)
        assertNull(fixture.viewModel.uiState.value.preparedFileName)

        authorize(fixture.viewModel, AuthenticationAction.ExportVault)

        assertEquals(1, fixture.operations.pairingExportCount)
        assertEquals(0, fixture.operations.syncExportCount)
        assertEquals("CardVault-pair-1.cvpair", fixture.viewModel.uiState.value.preparedFileName)
        assertEquals(FakeOperations.PAIRING_CODE, fixture.viewModel.uiState.value.pairingCode)
        assertTrue(requireNotNull(fixture.files.publishedBytes).all { it == 0.toByte() })
    }

    @Test
    fun pairedExportUsesSyncWhileConnectNewDeviceAlwaysCreatesPairingFile() {
        val fixture = fixture(paired = true)

        fixture.viewModel.requestExport()
        authorize(fixture.viewModel, AuthenticationAction.ExportVault)
        assertEquals(1, fixture.operations.syncExportCount)
        assertNull(fixture.viewModel.uiState.value.pairingCode)

        fixture.viewModel.requestNewDevicePairing()
        authorize(fixture.viewModel, AuthenticationAction.ExportVault)

        assertEquals(1, fixture.operations.syncExportCount)
        assertEquals(1, fixture.operations.pairingExportCount)
        assertEquals("CardVault-pair-1.cvpair", fixture.viewModel.uiState.value.preparedFileName)
        assertEquals(FakeOperations.PAIRING_CODE, fixture.viewModel.uiState.value.pairingCode)
    }

    @Test
    fun keyRotationRequiresDedicatedAuthenticationAndPublishesFreshPairingFile() {
        val fixture = fixture(paired = true)
        fixture.viewModel.onVaultAccessAllowed()

        fixture.viewModel.requestSyncKeyRotation()

        assertEquals(
            AuthenticationAction.RotateSyncKey,
            fixture.viewModel.pendingAuthenticationAction(),
        )
        assertEquals(0, fixture.operations.rotationCount)
        authorize(fixture.viewModel, AuthenticationAction.RotateSyncKey)
        assertEquals(1, fixture.operations.rotationCount)
        assertEquals(2, fixture.viewModel.uiState.value.keyEpoch)
        assertEquals("CardVault-pair-rotated.cvpair", fixture.viewModel.uiState.value.preparedFileName)
        assertEquals(FakeOperations.PAIRING_CODE, fixture.viewModel.uiState.value.pairingCode)
        assertTrue(requireNotNull(fixture.files.publishedBytes).all { it == 0.toByte() })
    }

    @Test
    fun pairingImportRequiresCodeAndAuthenticationThenClearsAllTransientSecrets() {
        val fixture = fixture(readBytes = encodedPairingFile())

        fixture.viewModel.onImportUriSelected(TEST_URI)

        assertTrue(fixture.viewModel.uiState.value.importNeedsPairingCode)
        assertNull(fixture.viewModel.pendingAuthenticationAction())
        fixture.viewModel.updatePairingCode("cvp1 abcd-2345")
        fixture.viewModel.confirmPairingImport()
        assertEquals(AuthenticationAction.ImportVault, fixture.viewModel.pendingAuthenticationAction())

        authorize(fixture.viewModel, AuthenticationAction.ImportVault)

        assertEquals(1, fixture.operations.pairingImportCount)
        assertFalse(fixture.viewModel.uiState.value.importNeedsPairingCode)
        assertTrue(fixture.viewModel.uiState.value.pairingCodeInput.isEmpty())
        assertEquals(1L, fixture.viewModel.uiState.value.importedSnapshotRevision)
        assertTrue(fixture.files.readBytes.all { it == 0.toByte() })
    }

    @Test
    fun syncImportQueuesAuthenticationWithoutPairingCode() {
        val fixture = fixture(readBytes = encodedSyncFile())

        fixture.viewModel.onImportUriSelected(TEST_URI)

        assertFalse(fixture.viewModel.uiState.value.importNeedsPairingCode)
        assertEquals(AuthenticationAction.ImportVault, fixture.viewModel.pendingAuthenticationAction())
        authorize(fixture.viewModel, AuthenticationAction.ImportVault)

        assertEquals(1, fixture.operations.syncImportCount)
        assertEquals(1L, fixture.viewModel.uiState.value.importedSnapshotRevision)
        assertTrue(fixture.files.readBytes.all { it == 0.toByte() })
    }

    @Test
    fun failedAuthenticationWipesPendingImportAndRequiresSelectingFileAgain() {
        val fixture = fixture(readBytes = encodedPairingFile())
        fixture.viewModel.onImportUriSelected(TEST_URI)
        fixture.viewModel.updatePairingCode("CVP1-ABCD-2345")
        fixture.viewModel.confirmPairingImport()

        fixture.viewModel.onAuthenticationFailed()

        assertNull(fixture.viewModel.pendingAuthenticationAction())
        assertFalse(fixture.viewModel.uiState.value.importNeedsPairingCode)
        assertTrue(fixture.viewModel.uiState.value.pairingCodeInput.isEmpty())
        assertTrue(fixture.files.readBytes.all { it == 0.toByte() })
        fixture.viewModel.confirmPairingImport()
        assertNull(fixture.viewModel.pendingAuthenticationAction())
    }

    @Test
    fun rejectedAuthenticationDispatchWipesPendingImport() {
        val fixture = fixture(readBytes = encodedPairingFile())
        fixture.viewModel.onImportUriSelected(TEST_URI)
        fixture.viewModel.updatePairingCode("CVP1-ABCD-2345")
        fixture.viewModel.confirmPairingImport()

        fixture.viewModel.onAuthenticationDispatchRejected(AuthenticationAction.ImportVault)

        assertNull(fixture.viewModel.pendingAuthenticationAction())
        assertFalse(fixture.viewModel.uiState.value.importNeedsPairingCode)
        assertTrue(fixture.files.readBytes.all { it == 0.toByte() })
        assertEquals("当前无法启动系统认证，请稍后重试", fixture.viewModel.uiState.value.message?.text)
    }

    @Test
    fun systemAuthenticationBackgroundTransitionPreservesPendingImportUntilResult() {
        val fixture = fixture(readBytes = encodedPairingFile())
        fixture.viewModel.onImportUriSelected(TEST_URI)
        fixture.viewModel.updatePairingCode("CVP1-ABCD-2345")
        fixture.viewModel.confirmPairingImport()
        val request = requireNotNull(fixture.viewModel.uiState.value.authenticationRequest)
        assertEquals(
            AuthenticationAction.ImportVault,
            fixture.viewModel.consumeAuthenticationRequest(request.requestId),
        )

        fixture.viewModel.onAppBackgrounded(preservePendingAuthentication = true)

        assertFalse(fixture.files.readBytes.all { it == 0.toByte() })
        assertEquals(AuthenticationAction.ImportVault, fixture.viewModel.pendingAuthenticationAction())
        assertTrue(fixture.viewModel.onAuthorizationGranted(AuthenticationAction.ImportVault))
        assertEquals(1, fixture.operations.pairingImportCount)
        assertTrue(fixture.files.readBytes.all { it == 0.toByte() })
    }

    @Test
    fun wrongPairingCodeKeepsEncryptedFileOnlyForAnExplicitRetry() {
        val fixture = fixture(readBytes = encodedPairingFile())
        fixture.operations.pairingImportFailure =
            SyncProtocolException(SyncErrorCode.AUTHENTICATION_FAILED)
        fixture.viewModel.onImportUriSelected(TEST_URI)
        fixture.viewModel.updatePairingCode("CVP1-WRONG-2345")
        fixture.viewModel.confirmPairingImport()

        authorize(fixture.viewModel, AuthenticationAction.ImportVault)

        assertTrue(fixture.viewModel.uiState.value.importNeedsPairingCode)
        assertFalse(fixture.files.readBytes.all { it == 0.toByte() })
        assertEquals("配对码错误或文件已被修改", fixture.viewModel.uiState.value.message?.text)
        fixture.viewModel.updatePairingCode("CVP1-RETRY-2345")
        fixture.viewModel.confirmPairingImport()
        assertEquals(AuthenticationAction.ImportVault, fixture.viewModel.pendingAuthenticationAction())
    }

    @Test
    fun backgroundingWithoutSystemAuthenticationWipesPendingImportAndCode() {
        val fixture = fixture(readBytes = encodedPairingFile())
        fixture.viewModel.onImportUriSelected(TEST_URI)
        fixture.viewModel.updatePairingCode("CVP1-ABCD-2345")

        fixture.viewModel.onAppBackgrounded(preservePendingAuthentication = false)

        assertFalse(fixture.viewModel.uiState.value.importNeedsPairingCode)
        assertTrue(fixture.viewModel.uiState.value.pairingCodeInput.isEmpty())
        assertTrue(fixture.files.readBytes.all { it == 0.toByte() })
    }

    @Test
    fun malformedFileUsesGenericMessageAndWipesSelectedBytes() {
        val fixture = fixture(readBytes = byteArrayOf(7, 8, 9))

        fixture.viewModel.onImportUriSelected(TEST_URI)

        assertEquals(VaultTransferMessageKind.Error, fixture.viewModel.uiState.value.message?.kind)
        assertEquals("无法验证这个 CardVault 文件", fixture.viewModel.uiState.value.message?.text)
        assertTrue(fixture.files.readBytes.all { it == 0.toByte() })
    }

    @Test
    fun stateAndRequestsNeverRenderPairingSecretsInTextualDiagnostics() {
        val secret = "CVP1-SHOULD-NOT-APPEAR"
        val state = VaultTransferUiState(
            pairingCode = secret,
            pairingCodeInput = secret,
            preparedFileName = "CardVault-pair-7.cvpair",
        )
        val request = VaultTransferAuthenticationRequest(
            requestId = 9L,
            action = AuthenticationAction.ExportVault,
        )

        assertFalse(state.toString().contains(secret))
        assertFalse(state.toString().contains("CardVault-pair-7.cvpair"))
        assertFalse(request.toString().contains("ExportVault"))
    }

    @Test
    fun shareIntentFailureUsesGenericFeedbackWithoutDroppingPreparedFile() {
        val fixture = fixture(paired = true)
        fixture.viewModel.requestExport()
        authorize(fixture.viewModel, AuthenticationAction.ExportVault)
        fixture.files.failShareIntent = true

        assertNull(fixture.viewModel.preparedShareIntent())
        assertEquals("无法打开系统分享，请稍后重试", fixture.viewModel.uiState.value.message?.text)
        assertEquals("CardVault-sync-1.cvsync", fixture.viewModel.uiState.value.preparedFileName)
    }

    private fun authorize(viewModel: VaultTransferViewModel, action: AuthenticationAction) {
        val request = requireNotNull(viewModel.uiState.value.authenticationRequest)
        assertEquals(action, viewModel.consumeAuthenticationRequest(request.requestId))
        assertTrue(viewModel.onAuthorizationGranted(action))
    }

    private fun fixture(
        paired: Boolean = false,
        readBytes: ByteArray = encodedSyncFile(),
    ): Fixture {
        val operations = FakeOperations(paired)
        val files = FakeFiles(readBytes)
        val viewModel = VaultTransferViewModel(
            operations = operations,
            files = files,
            externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        return Fixture(viewModel, operations, files)
    }

    private data class Fixture(
        val viewModel: VaultTransferViewModel,
        val operations: FakeOperations,
        val files: FakeFiles,
    )

    private companion object {
        const val TEST_URI = "content://test.cardvault/encrypted-transfer"
        val SOURCE_DEVICE_ID: String = UUID.randomUUID().toString()
        val VAULT_ID: String = UUID.randomUUID().toString()

        fun emptySnapshot(): SyncSnapshot = SyncSnapshot(
            records = emptyList(),
            order = SyncOrder(
                version = VersionVector.empty().increment(SOURCE_DEVICE_ID),
                updatedAtEpochMillis = 1L,
                recordIds = emptyList(),
            ),
        )

        fun encodedPairingFile(): ByteArray {
            val syncSecret = SecretBytes(ByteArray(32) { 3 })
            return try {
                CardVaultSyncFiles.encodePairing(
                    payload = PairingFilePayload(
                        packageId = UUID.randomUUID().toString(),
                        vaultId = VAULT_ID,
                        sourceDeviceId = SOURCE_DEVICE_ID,
                        exportSequence = 1L,
                        exportedAtEpochMillis = 1L,
                        expiresAtEpochMillis = 2L,
                        keyEpoch = 1,
                        syncSecret = syncSecret,
                        snapshot = emptySnapshot(),
                    ),
                    pairingSecret = ByteArray(16) { 4 },
                )
            } finally {
                syncSecret.close()
            }
        }

        fun encodedSyncFile(): ByteArray = CardVaultSyncFiles.encodeSync(
            payload = SyncFilePayload(
                packageId = UUID.randomUUID().toString(),
                vaultId = VAULT_ID,
                sourceDeviceId = SOURCE_DEVICE_ID,
                exportSequence = 1L,
                exportedAtEpochMillis = 1L,
                keyEpoch = 1,
                snapshot = emptySnapshot(),
            ),
            syncSecret = ByteArray(32) { 5 },
        )
    }
}

private class FakeOperations(
    paired: Boolean,
) : VaultSyncOperations {
    private var currentEpoch: Int? = 1.takeIf { paired }
    var pairingExportCount = 0
    var syncExportCount = 0
    var pairingImportCount = 0
    var syncImportCount = 0
    var rotationCount = 0
    var pairingImportFailure: Exception? = null

    override suspend fun pairingStatus(): SyncPairingStatus = SyncPairingStatus(
        isPaired = currentEpoch != null,
        keyEpoch = currentEpoch,
    )

    override suspend fun createPairingExport(): PairingExport {
        pairingExportCount += 1
        return PairingExport(
            fileName = "CardVault-pair-1.cvpair",
            displayCode = PAIRING_CODE,
            fileBytes = byteArrayOf(11, 12, 13),
        )
    }

    override suspend fun rotateSyncKeyAndCreatePairingExport(): PairingExport {
        rotationCount += 1
        currentEpoch = requireNotNull(currentEpoch) + 1
        return PairingExport(
            fileName = "CardVault-pair-rotated.cvpair",
            displayCode = PAIRING_CODE,
            fileBytes = byteArrayOf(31, 32, 33),
        )
    }

    override suspend fun exportSync(): SyncExport {
        syncExportCount += 1
        return SyncExport(
            fileName = "CardVault-sync-1.cvsync",
            fileBytes = byteArrayOf(21, 22, 23),
        )
    }

    override suspend fun importPairing(
        bytes: ByteArray,
        displayCode: String,
    ): SyncImportResult {
        pairingImportCount += 1
        pairingImportFailure?.let { throw it }
        return SyncImportResult(activeCards = 2, tombstones = 0, conflicts = emptyList())
    }

    override suspend fun importSync(bytes: ByteArray): SyncImportResult {
        syncImportCount += 1
        return SyncImportResult(activeCards = 2, tombstones = 0, conflicts = emptyList())
    }

    companion object {
        const val PAIRING_CODE = "CVP1-ABCD-EFGH-2345"
    }
}

private class FakeFiles(
    val readBytes: ByteArray,
) : VaultTransferFiles {
    var publishedBytes: ByteArray? = null
    var failShareIntent: Boolean = false

    override suspend fun publish(
        fileName: String,
        bytes: ByteArray,
    ): PublishedVaultTransferFile {
        publishedBytes = bytes
        return FakePublishedVaultTransferFile(fileName)
    }

    override suspend fun read(uri: String): ByteArray = readBytes

    override fun shareIntent(file: PublishedVaultTransferFile): Intent {
        if (failShareIntent) throw IOException("Synthetic share failure")
        throw AssertionError("Share intent is outside this ViewModel unit test.")
    }

    private data class FakePublishedVaultTransferFile(
        override val fileName: String,
    ) : PublishedVaultTransferFile
}
