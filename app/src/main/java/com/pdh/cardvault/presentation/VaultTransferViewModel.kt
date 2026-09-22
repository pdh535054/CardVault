package com.pdh.cardvault.presentation

import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pdh.cardvault.security.auth.AuthenticationAction
import com.pdh.cardvault.sync.CardVaultSyncFiles
import com.pdh.cardvault.sync.SyncErrorCode
import com.pdh.cardvault.sync.SyncFileKind
import com.pdh.cardvault.sync.SyncProtocolException
import com.pdh.cardvault.sync.AndroidSyncFileExchange
import com.pdh.cardvault.sync.android.AndroidVaultSyncCoordinator
import com.pdh.cardvault.sync.android.PairingExport
import com.pdh.cardvault.sync.android.SyncExport
import com.pdh.cardvault.sync.android.SyncImportResult
import com.pdh.cardvault.sync.android.SyncNotPairedException
import com.pdh.cardvault.sync.android.SyncPairingStatus
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal interface VaultSyncOperations {
    suspend fun pairingStatus(): SyncPairingStatus
    suspend fun createPairingExport(): PairingExport
    suspend fun rotateSyncKeyAndCreatePairingExport(): PairingExport
    suspend fun exportSync(): SyncExport
    suspend fun importPairing(bytes: ByteArray, displayCode: String): SyncImportResult
    suspend fun importSync(bytes: ByteArray): SyncImportResult
}

internal class CoordinatorVaultSyncOperations(
    private val coordinator: AndroidVaultSyncCoordinator,
) : VaultSyncOperations {
    override suspend fun pairingStatus(): SyncPairingStatus = coordinator.pairingStatus()
    override suspend fun createPairingExport(): PairingExport = coordinator.createPairingExport()
    override suspend fun rotateSyncKeyAndCreatePairingExport(): PairingExport =
        coordinator.rotateSyncKeyAndCreatePairingExport()
    override suspend fun exportSync(): SyncExport = coordinator.exportSync()
    override suspend fun importPairing(bytes: ByteArray, displayCode: String): SyncImportResult =
        coordinator.importPairing(bytes, displayCode)
    override suspend fun importSync(bytes: ByteArray): SyncImportResult = coordinator.importSync(bytes)
}

internal interface VaultTransferFiles {
    suspend fun publish(fileName: String, bytes: ByteArray): PublishedVaultTransferFile
    suspend fun read(uri: String): ByteArray
    fun shareIntent(file: PublishedVaultTransferFile): Intent
}

internal interface PublishedVaultTransferFile {
    val fileName: String
}

internal class AndroidVaultTransferFiles(
    private val exchange: AndroidSyncFileExchange,
) : VaultTransferFiles {
    override suspend fun publish(
        fileName: String,
        bytes: ByteArray,
    ): PublishedVaultTransferFile = AndroidPublishedVaultTransferFile(
        exchange.publishEncryptedExport(fileName, bytes),
    )

    override suspend fun read(uri: String): ByteArray = exchange.readEncryptedImport(uri.toUri())

    override fun shareIntent(file: PublishedVaultTransferFile): Intent {
        val androidFile = file as? AndroidPublishedVaultTransferFile
            ?: throw IllegalArgumentException("Unsupported encrypted transfer file.")
        return exchange.createShareIntent(androidFile.sharedFile)
    }

    private data class AndroidPublishedVaultTransferFile(
        val sharedFile: AndroidSyncFileExchange.SharedEncryptedFile,
    ) : PublishedVaultTransferFile {
        override val fileName: String
            get() = sharedFile.fileName

        override fun toString(): String = "AndroidPublishedVaultTransferFile(contents=redacted)"
    }
}

class VaultTransferViewModel internal constructor(
    private val operations: VaultSyncOperations,
    private val files: VaultTransferFiles,
    private val externalScope: CoroutineScope? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(VaultTransferUiState())
    val uiState = _uiState.asStateFlow()

    private var operationJob: Job? = null
    private var lifecycleGeneration = 0L
    private var authenticationSerial = 0L
    private var pendingAuthentication: AuthenticationAction? = null
    private var pendingImportBytes: ByteArray? = null
    private var pendingImportKind: SyncFileKind? = null
    private var preparedFile: PublishedVaultTransferFile? = null
    private var pendingExportMode: ExportMode? = null

    private val operationScope: CoroutineScope
        get() = externalScope ?: viewModelScope

    fun onVaultAccessAllowed() {
        if (_uiState.value.busy) return
        val expectedGeneration = lifecycleGeneration
        operationJob?.cancel()
        operationJob = operationScope.launch {
            try {
                val status = operations.pairingStatus()
                if (expectedGeneration != lifecycleGeneration) return@launch
                _uiState.update { current ->
                    current.copy(
                        pairingState = status.toUiPairingState(),
                        keyEpoch = status.keyEpoch,
                        message = current.message,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                if (expectedGeneration == lifecycleGeneration) {
                    showError("无法读取同步状态")
                }
            }
        }
    }

    fun onAppBackgrounded(preservePendingAuthentication: Boolean) {
        lifecycleGeneration += 1L
        operationJob?.cancel()
        operationJob = null
        if (!preservePendingAuthentication || pendingAuthentication == null) {
            pendingAuthentication = null
            pendingExportMode = null
            clearPendingImport()
            val retainPreparedExport = preparedFile != null
            _uiState.update { current ->
                current.copy(
                    busy = false,
                    preparedFileName = current.preparedFileName.takeIf { retainPreparedExport },
                    pairingCode = current.pairingCode.takeIf { retainPreparedExport },
                    importNeedsPairingCode = false,
                    pairingCodeInput = "",
                    authenticationRequest = null,
                    message = current.message.takeIf { retainPreparedExport },
                )
            }
        } else {
            _uiState.update { current ->
                current.copy(
                    busy = false,
                    preparedFileName = null,
                    pairingCode = null,
                    authenticationRequest = null,
                )
            }
        }
    }

    fun requestExport() {
        // Manual transfer through WeChat/QQ must also work when the destination has
        // never been paired (or has been reinstalled). Always create a self-contained
        // pairing package for the primary action so users cannot accidentally send a
        // .cvsync file that a fresh destination is unable to decrypt.
        requestExport(ExportMode.Pairing)
    }

    fun requestSyncKeyRotation() {
        if (_uiState.value.pairingState != VaultPairingState.Paired) return
        if (_uiState.value.busy || pendingAuthentication != null) return
        clearPreparedExport()
        pendingExportMode = ExportMode.Rotation
        queueAuthentication(AuthenticationAction.RotateSyncKey)
    }

    fun onImportUriSelected(uri: String) {
        if (_uiState.value.busy || pendingAuthentication != null) return
        clearPreparedExport()
        clearPendingImport()
        val expectedGeneration = lifecycleGeneration
        _uiState.update { current ->
            current.copy(
                busy = true,
                importNeedsPairingCode = false,
                pairingCodeInput = "",
                message = null,
            )
        }
        operationJob?.cancel()
        operationJob = operationScope.launch {
            try {
                val bytes = files.read(uri)
                val kind = try {
                    CardVaultSyncFiles.inspectKind(bytes)
                } catch (error: Throwable) {
                    bytes.fill(0)
                    throw error
                }
                if (expectedGeneration != lifecycleGeneration) {
                    bytes.fill(0)
                    return@launch
                }
                pendingImportBytes = bytes
                pendingImportKind = kind
                if (kind == SyncFileKind.PAIRING) {
                    _uiState.update { current ->
                        current.copy(
                            busy = false,
                            importNeedsPairingCode = true,
                            message = VaultTransferMessage(
                                text = "请输入另一台设备显示的一次性配对码",
                                kind = VaultTransferMessageKind.Information,
                            ),
                        )
                    }
                } else {
                    _uiState.update { current -> current.copy(busy = false) }
                    queueAuthentication(AuthenticationAction.ImportVault)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (expectedGeneration == lifecycleGeneration) {
                    clearPendingImport()
                    showError(safeMessage(exception))
                }
            }
        }
    }

    fun updatePairingCode(value: String) {
        if (!_uiState.value.importNeedsPairingCode || _uiState.value.busy) return
        val normalized = value.uppercase()
            .filter { character ->
                character in 'A'..'Z' || character in '1'..'7' ||
                    character == '-' || character.isWhitespace()
            }
            .take(MAX_PAIRING_CODE_INPUT)
        _uiState.update { current -> current.copy(pairingCodeInput = normalized, message = null) }
    }

    fun confirmPairingImport() {
        if (
            pendingImportKind != SyncFileKind.PAIRING ||
            pendingImportBytes == null ||
            _uiState.value.pairingCodeInput.isBlank() ||
            _uiState.value.busy ||
            pendingAuthentication != null
        ) {
            return
        }
        queueAuthentication(AuthenticationAction.ImportVault)
    }

    fun consumeAuthenticationRequest(requestId: Long): AuthenticationAction? {
        val request = _uiState.value.authenticationRequest
            ?.takeIf { it.requestId == requestId }
            ?: return null
        _uiState.update { current -> current.copy(authenticationRequest = null) }
        return request.action
    }

    fun pendingAuthenticationAction(): AuthenticationAction? = pendingAuthentication

    fun onAuthenticationDispatchRejected(action: AuthenticationAction) {
        if (pendingAuthentication != action) return
        pendingAuthentication = null
        clearPendingOperation(action)
        showError("当前无法启动系统认证，请稍后重试")
    }

    fun onAuthenticationFailed() {
        val action = pendingAuthentication ?: return
        pendingAuthentication = null
        clearPendingOperation(action)
        _uiState.update { current ->
            current.copy(
                busy = false,
                importNeedsPairingCode = false,
                pairingCodeInput = "",
                authenticationRequest = null,
                message = VaultTransferMessage(
                    text = "身份认证未完成",
                    kind = VaultTransferMessageKind.Error,
                ),
            )
        }
    }

    fun onAuthorizationGranted(action: AuthenticationAction): Boolean {
        if (pendingAuthentication != action || !action.isVaultTransferAction) return false
        pendingAuthentication = null
        val exportMode = pendingExportMode
        pendingExportMode = null
        val expectedGeneration = lifecycleGeneration
        _uiState.update { current ->
            current.copy(busy = true, authenticationRequest = null, message = null)
        }
        operationJob?.cancel()
        operationJob = operationScope.launch {
            try {
                when (action) {
                    AuthenticationAction.ExportVault -> performExport(
                        expectedGeneration = expectedGeneration,
                        mode = exportMode ?: ExportMode.Automatic,
                    )
                    AuthenticationAction.ImportVault -> performImport(expectedGeneration)
                    AuthenticationAction.RotateSyncKey -> performExport(
                        expectedGeneration = expectedGeneration,
                        mode = ExportMode.Rotation,
                    )
                    else -> error("Unexpected vault transfer action.")
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (expectedGeneration == lifecycleGeneration) {
                    if (
                        action == AuthenticationAction.ImportVault &&
                        !shouldRetainPairingImportForRetry(exception)
                    ) {
                        clearPendingImport()
                        _uiState.update { current ->
                            current.copy(
                                importNeedsPairingCode = false,
                                pairingCodeInput = "",
                            )
                        }
                    }
                    showError(safeMessage(exception))
                }
            }
        }
        return true
    }

    fun preparedShareIntent(): Intent? {
        val file = preparedFile ?: return null
        return try {
            files.shareIntent(file)
        } catch (_: Exception) {
            showError("无法打开系统分享，请稍后重试")
            null
        }
    }

    fun copyPreparedPairingCode(copyText: (String) -> Boolean) {
        val code = _uiState.value.pairingCode ?: return
        val copied = runCatching { copyText(code) }.getOrDefault(false)
        _uiState.update { current ->
            current.copy(
                message = VaultTransferMessage(
                    text = if (copied) {
                        "配对码已复制，剪贴板将在 30 秒后自动清除"
                    } else {
                        "无法复制配对码，请稍后重试"
                    },
                    kind = if (copied) {
                        VaultTransferMessageKind.Success
                    } else {
                        VaultTransferMessageKind.Error
                    },
                ),
            )
        }
    }

    fun onShareDispatchFailed() {
        if (preparedFile == null) return
        showError("无法打开系统分享，请稍后重试")
    }

    private suspend fun performExport(
        expectedGeneration: Long,
        mode: ExportMode,
    ) {
        val status = operations.pairingStatus()
        var fileBytes: ByteArray? = null
        try {
            val fileName: String
            val pairingCode: String?
            if (mode == ExportMode.Rotation) {
                val exported = operations.rotateSyncKeyAndCreatePairingExport()
                fileBytes = exported.fileBytesCopy()
                fileName = exported.fileName
                pairingCode = exported.displayCode
            } else if (status.isPaired && mode == ExportMode.Automatic) {
                val exported = operations.exportSync()
                fileBytes = exported.fileBytesCopy()
                fileName = exported.fileName
                pairingCode = null
            } else {
                val exported = operations.createPairingExport()
                fileBytes = exported.fileBytesCopy()
                fileName = exported.fileName
                pairingCode = exported.displayCode
            }
            val shared = files.publish(fileName, requireNotNull(fileBytes))
            if (expectedGeneration != lifecycleGeneration) return
            val statusAfter = operations.pairingStatus()
            preparedFile = shared
            _uiState.update { current ->
                current.copy(
                    pairingState = VaultPairingState.Paired,
                    keyEpoch = statusAfter.keyEpoch,
                    busy = false,
                    preparedFileName = fileName,
                    pairingCode = pairingCode,
                    message = VaultTransferMessage(
                        text = if (mode == ExportMode.Rotation) {
                            "旧同步关系已撤销；请用新配对文件重新连接其他设备"
                        } else {
                            "加密文件已准备好"
                        },
                        kind = VaultTransferMessageKind.Success,
                    ),
                )
            }
        } finally {
            fileBytes?.fill(0)
        }
    }

    private suspend fun performImport(expectedGeneration: Long) {
        val bytes = pendingImportBytes ?: throw IllegalStateException("No encrypted import is pending.")
        val kind = pendingImportKind ?: throw IllegalStateException("No encrypted import is pending.")
        val result = when (kind) {
            SyncFileKind.PAIRING -> operations.importPairing(
                bytes,
                _uiState.value.pairingCodeInput,
            )
            SyncFileKind.SYNC -> operations.importSync(bytes)
        }
        if (expectedGeneration != lifecycleGeneration) return
        clearPendingImport()
        val conflicts = result.conflicts.size
        _uiState.update { current ->
            current.copy(
                pairingState = VaultPairingState.Paired,
                busy = false,
                importNeedsPairingCode = false,
                pairingCodeInput = "",
                importedSnapshotRevision = current.importedSnapshotRevision + 1L,
                message = VaultTransferMessage(
                    text = if (conflicts == 0) {
                        "同步完成：银行卡 ${result.activeCards} 张，地址 ${result.activeAddresses} 条"
                    } else {
                        "同步完成：银行卡 ${result.activeCards} 张，地址 ${result.activeAddresses} 条；" +
                            "保留了 $conflicts 个冲突副本"
                    },
                    kind = VaultTransferMessageKind.Success,
                ),
            )
        }
    }

    private fun queueAuthentication(action: AuthenticationAction) {
        if (pendingAuthentication != null) return
        pendingAuthentication = action
        authenticationSerial += 1L
        _uiState.update { current ->
            current.copy(
                authenticationRequest = VaultTransferAuthenticationRequest(
                    requestId = authenticationSerial,
                    action = action,
                ),
                message = null,
            )
        }
    }

    private fun requestExport(mode: ExportMode) {
        if (_uiState.value.busy || pendingAuthentication != null) return
        clearPreparedExport()
        pendingExportMode = mode
        queueAuthentication(AuthenticationAction.ExportVault)
    }

    private fun clearPreparedExport() {
        preparedFile = null
        _uiState.update { current ->
            current.copy(preparedFileName = null, pairingCode = null)
        }
    }

    private fun clearPendingImport() {
        pendingImportBytes?.fill(0)
        pendingImportBytes = null
        pendingImportKind = null
    }

    private fun clearPendingOperation(action: AuthenticationAction) {
        when (action) {
            AuthenticationAction.ExportVault -> pendingExportMode = null
            AuthenticationAction.RotateSyncKey -> pendingExportMode = null
            AuthenticationAction.ImportVault -> {
                clearPendingImport()
                _uiState.update { current ->
                    current.copy(
                        importNeedsPairingCode = false,
                        pairingCodeInput = "",
                    )
                }
            }
            else -> Unit
        }
    }

    private fun shouldRetainPairingImportForRetry(exception: Exception): Boolean =
        pendingImportKind == SyncFileKind.PAIRING &&
            exception is SyncProtocolException &&
            exception.code in setOf(
                SyncErrorCode.INVALID_PAIRING_CODE,
                SyncErrorCode.AUTHENTICATION_FAILED,
            )

    private fun showError(message: String) {
        _uiState.update { current ->
            current.copy(
                busy = false,
                message = VaultTransferMessage(message, VaultTransferMessageKind.Error),
            )
        }
    }

    private fun safeMessage(exception: Exception): String = when (exception) {
        is SyncProtocolException -> when (exception.code) {
            SyncErrorCode.INVALID_PAIRING_CODE -> "配对码格式不正确"
            SyncErrorCode.AUTHENTICATION_FAILED -> "配对码错误或文件已被修改"
            SyncErrorCode.REPLAYED_PACKAGE -> "该同步文件已经导入过"
            SyncErrorCode.STALE_PACKAGE -> "同步文件已过期或不是最新版本"
            SyncErrorCode.VAULT_MISMATCH -> "该文件属于另一个 CardVault 卡包"
            SyncErrorCode.LIMIT_EXCEEDED -> "同步文件超过安全大小限制"
            SyncErrorCode.UNSUPPORTED_VERSION -> "同步文件版本不受支持"
            SyncErrorCode.INVALID_FORMAT,
            SyncErrorCode.INVARIANT_VIOLATION,
            -> "无法验证这个 CardVault 文件"
        }
        is SyncNotPairedException -> "请先通过配对文件连接另一台设备"
        is IOException -> "无法读取或写入加密同步文件"
        else -> "同步操作失败，未更改卡包"
    }

    private fun SyncPairingStatus.toUiPairingState(): VaultPairingState =
        if (isPaired) VaultPairingState.Paired else VaultPairingState.Unpaired

    override fun onCleared() {
        lifecycleGeneration += 1L
        operationJob?.cancel()
        clearPendingImport()
        pendingAuthentication = null
        pendingExportMode = null
        preparedFile = null
        super.onCleared()
    }

    internal class Factory(
        private val coordinator: AndroidVaultSyncCoordinator,
        private val fileExchange: AndroidSyncFileExchange,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VaultTransferViewModel::class.java)) {
                return VaultTransferViewModel(
                    operations = CoordinatorVaultSyncOperations(coordinator),
                    files = AndroidVaultTransferFiles(fileExchange),
                ) as T
            }
            throw IllegalArgumentException("Unsupported ViewModel class")
        }
    }

    private companion object {
        const val MAX_PAIRING_CODE_INPUT = 64
    }

    private enum class ExportMode {
        Automatic,
        Pairing,
        Rotation,
    }
}
