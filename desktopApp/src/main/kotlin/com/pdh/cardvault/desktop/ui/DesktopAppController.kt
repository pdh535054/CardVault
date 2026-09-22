package com.pdh.cardvault.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pdh.cardvault.desktop.data.DesktopCardRepository
import com.pdh.cardvault.desktop.data.LocalKeyUnavailableException
import com.pdh.cardvault.desktop.model.CardCoverStyle
import com.pdh.cardvault.desktop.model.AndroidTemplateStyleCodec
import com.pdh.cardvault.desktop.model.DesktopCard
import com.pdh.cardvault.desktop.model.DesktopAddress
import com.pdh.cardvault.desktop.model.DesktopAddressCopyPart
import com.pdh.cardvault.desktop.model.DesktopFolder
import com.pdh.cardvault.desktop.model.DesktopFolderKind
import com.pdh.cardvault.desktop.security.SensitiveAction
import com.pdh.cardvault.desktop.security.SensitiveActionAuthenticator
import com.pdh.cardvault.desktop.security.WindowsHelloAuthenticator
import com.pdh.cardvault.desktop.sync.DesktopSyncGateway
import com.pdh.cardvault.desktop.sync.DesktopTransferFiles
import com.pdh.cardvault.sync.CardVaultSyncFiles
import com.pdh.cardvault.sync.SyncFileKind
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.nio.file.Path
import java.io.IOException
import java.time.Clock
import java.util.Timer
import java.util.TimerTask
import java.util.prefs.Preferences

enum class DesktopSection { Home, Wallet, Editor, Addresses, AddressEditor, Transfer, Settings }

data class CardDraft(
    val id: String? = null,
    val nickname: String = "",
    val issuerName: String = "",
    val cardNumber: String = "",
    val expiryDigits: String = "",
    val cvv: String = "",
    val notes: String = "",
    val style: CardCoverStyle = CardCoverStyle.Default,
) {
    override fun toString(): String = "CardDraft(id=$id, sensitiveFields=redacted)"
}

data class DraftErrors(
    val nickname: String? = null,
    val cardNumber: String? = null,
    val expiry: String? = null,
    val cvv: String? = null,
    val notes: String? = null,
) {
    val hasErrors: Boolean get() = listOf(nickname, cardNumber, expiry, cvv, notes).any { it != null }
}

data class AddressDraft(
    val id: String? = null,
    val nickname: String = "",
    val detailedAddress: String = "",
    val city: String = "",
    val other: String = "",
    val postalCode: String = "",
    val country: String = "",
    val style: CardCoverStyle = CardCoverStyle.Default,
) {
    override fun toString(): String = "AddressDraft(id=$id, sensitiveFields=redacted)"
}

data class AddressDraftErrors(
    val nickname: String? = null,
    val detailedAddress: String? = null,
    val city: String? = null,
    val other: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
) {
    val hasErrors: Boolean
        get() = listOf(nickname, detailedAddress, city, other, postalCode, country).any { it != null }
}

data class TransferFeedback(
    val text: String,
    val isError: Boolean = false,
)

private enum class ImportStage { Read, Validate, Authenticate, Merge, Persist }

class DesktopAppController(
    private val repository: DesktopCardRepository,
    val syncGateway: DesktopSyncGateway,
    private val clock: Clock = Clock.systemUTC(),
    private val authenticator: SensitiveActionAuthenticator = WindowsHelloAuthenticator(),
) : AutoCloseable {
    var cards by mutableStateOf(repository.cards())
        private set
    var addresses by mutableStateOf(repository.addresses())
        private set
    var cardFolders by mutableStateOf(repository.folders(DesktopFolderKind.CARDS))
        private set
    var addressFolders by mutableStateOf(repository.folders(DesktopFolderKind.ADDRESSES))
        private set
    var cardFolderOrder by mutableStateOf(repository.folderOrder(DesktopFolderKind.CARDS))
        private set
    var addressFolderOrder by mutableStateOf(repository.folderOrder(DesktopFolderKind.ADDRESSES))
        private set
    var selectedCardFolderId by mutableStateOf<String?>(null)
        private set
    var selectedAddressFolderId by mutableStateOf<String?>(null)
        private set
    var section by mutableStateOf(DesktopSection.Home)
    var selectedId by mutableStateOf(cards.firstOrNull()?.id)
        private set
    var editingDraft by mutableStateOf(CardDraft())
        private set
    var draftErrors by mutableStateOf(DraftErrors())
        private set
    var selectedAddressId by mutableStateOf(addresses.firstOrNull()?.id)
        private set
    var editingAddressDraft by mutableStateOf(AddressDraft())
        private set
    var addressDraftErrors by mutableStateOf(AddressDraftErrors())
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var pairingCode by mutableStateOf<String?>(null)
        private set
    var syncKeyEpoch by mutableLongStateOf(
        repository.snapshot().syncState.keyEpoch.takeIf {
            repository.snapshot().syncState.sharedSyncKey != null
        } ?: 0L,
    )
        private set
    var exportDirectory by mutableStateOf(loadExportDirectory())
        private set
    var concealmentEpoch by mutableLongStateOf(0L)
        private set
    var authenticatingAction by mutableStateOf<SensitiveAction?>(null)
        private set
    var selectedImportPath by mutableStateOf<Path?>(null)
        private set
    var selectedImportKind by mutableStateOf<SyncFileKind?>(null)
        private set
    var transferBusy by mutableStateOf(false)
        private set
    var transferFeedback by mutableStateOf<TransferFeedback?>(null)
        private set

    private val authenticationMutex = Mutex()
    private var revealedCardId: String? = null

    val selectedCard: DesktopCard? get() = cards.firstOrNull { it.id == selectedId }
    val selectedAddress: DesktopAddress? get() = addresses.firstOrNull { it.id == selectedAddressId }

    fun selectCard(id: String) {
        if (selectedId != id) revealedCardId = null
        selectedId = id.takeIf { value -> cards.any { it.id == value } }
    }

    fun selectAddress(id: String) {
        selectedAddressId = id.takeIf { value -> addresses.any { it.id == value } }
    }

    fun selectCardFolder(id: String?) { selectedCardFolderId = id }
    fun selectAddressFolder(id: String?) { selectedAddressFolderId = id }

    fun createFolder(name: String, kind: DesktopFolderKind): Boolean = runCatching {
        repository.createFolder(name, kind)
        refreshFolders()
        true
    }.onFailure { notice = it.message ?: "无法创建文件夹" }.getOrDefault(false)

    fun renameFolder(id: String, name: String): Boolean = runCatching {
        val changed = repository.renameFolder(id, name)
        refreshFolders()
        changed
    }.onFailure { notice = it.message ?: "无法重命名文件夹" }.getOrDefault(false)

    fun deleteFolder(id: String): Boolean {
        val changed = repository.deleteFolder(id)
        if (changed) {
            if (selectedCardFolderId == id) selectedCardFolderId = null
            if (selectedAddressFolderId == id) selectedAddressFolderId = null
            refreshCards()
            refreshAddresses()
            refreshFolders()
        }
        return changed
    }

    fun moveCardToFolder(cardId: String, folderId: String?): Boolean =
        repository.moveCardToFolder(cardId, folderId).also { if (it) refreshCards() }

    fun moveAddressToFolder(addressId: String, folderId: String?): Boolean =
        repository.moveAddressToFolder(addressId, folderId).also { if (it) refreshAddresses() }

    fun reorderFolders(kind: DesktopFolderKind, orderedIds: List<String?>): Boolean =
        repository.reorderFolders(kind, orderedIds).also { changed ->
            if (changed) refreshFolders()
        }

    fun startAdd() {
        revealedCardId = null
        editingDraft = CardDraft()
        draftErrors = DraftErrors()
        section = DesktopSection.Editor
    }

    fun startAddAddress() {
        editingAddressDraft = AddressDraft()
        addressDraftErrors = AddressDraftErrors()
        section = DesktopSection.AddressEditor
    }

    suspend fun startEditAddress(addressId: String): Boolean {
        if (addresses.none { it.id == addressId }) return false
        if (!authenticate(SensitiveAction.EditAddress)) return false
        val current = addresses.firstOrNull { it.id == addressId } ?: return false
        editingAddressDraft = AddressDraft(
            id = current.id,
            nickname = current.nickname,
            detailedAddress = current.detailedAddress,
            city = current.city,
            other = current.other,
            postalCode = current.postalCode,
            country = current.country,
            style = current.style,
        )
        addressDraftErrors = AddressDraftErrors()
        section = DesktopSection.AddressEditor
        return true
    }

    fun updateAddressDraft(transform: (AddressDraft) -> AddressDraft) {
        editingAddressDraft = transform(editingAddressDraft)
        addressDraftErrors = AddressDraftErrors()
    }

    suspend fun startEdit(cardId: String): Boolean {
        if (cards.none { it.id == cardId }) return false
        if (!authenticate(SensitiveAction.EditCard)) return false
        val current = cards.firstOrNull { it.id == cardId } ?: return false
        startEditUnchecked(current)
        return true
    }

    private fun startEditUnchecked(card: DesktopCard) {
        revealedCardId = null
        editingDraft = CardDraft(
            id = card.id,
            nickname = card.nickname,
            issuerName = card.issuerName,
            cardNumber = card.cardNumber,
            expiryDigits = "%02d%02d".format(card.expiryMonth, card.expiryYear % 100),
            cvv = card.cvv.orEmpty(),
            notes = card.notes,
            style = card.style,
        )
        draftErrors = DraftErrors()
        section = DesktopSection.Editor
    }

    fun updateDraft(transform: (CardDraft) -> CardDraft) {
        editingDraft = transform(editingDraft)
        draftErrors = DraftErrors()
    }

    fun saveDraft(): Boolean {
        val draft = editingDraft
        val normalizedNumber = DesktopCard.normalizeCardNumber(draft.cardNumber)
        val errors = DraftErrors(
            nickname = if (draft.nickname.trim().codePointCount(0, draft.nickname.trim().length) !in 1..50) {
                "请输入 1–50 个字符的卡片名称"
            } else null,
            cardNumber = if (normalizedNumber == null) "卡号应为 12–19 位数字" else null,
            expiry = if (!isValidExpiry(draft.expiryDigits)) "请输入有效的 MM/YY" else null,
            cvv = if (draft.cvv.isNotEmpty() && (draft.cvv.length !in 3..4 || !draft.cvv.all(Char::isDigit))) {
                "CVV 应为 3 或 4 位数字"
            } else null,
            notes = if (draft.notes.codePointCount(0, draft.notes.length) > 1000) "备注不能超过 1000 个字符" else null,
        )
        draftErrors = errors
        if (errors.hasErrors || normalizedNumber == null) return false

        val month = draft.expiryDigits.take(2).toInt()
        val year = 2000 + draft.expiryDigits.drop(2).toInt()
        val existing = draft.id?.let { id -> cards.firstOrNull { it.id == id } }
        val now = clock.millis()
        val card = if (existing == null) {
            DesktopCard.create(
                nickname = draft.nickname,
                issuerName = draft.issuerName,
                cardNumber = normalizedNumber,
                expiryMonth = month,
                expiryYear = year,
                cvv = draft.cvv.ifBlank { null },
                notes = draft.notes,
                style = draft.style,
                sortOrder = 0,
                clock = clock,
            )
        } else {
            existing.copy(
                nickname = draft.nickname.trim(),
                issuerName = draft.issuerName.trim().ifBlank { "CardVault" },
                cardNumber = normalizedNumber,
                expiryMonth = month,
                expiryYear = year,
                cvv = draft.cvv.ifBlank { null },
                notes = draft.notes,
                cardTemplateId = AndroidTemplateStyleCodec.encode(draft.style.copy(sourceTemplateId = null)),
                updatedAtEpochMillis = now,
            )
        }

        if (existing == null) repository.add(card) else repository.update(card)
        refreshCards()
        selectedId = card.id
        editingDraft = CardDraft()
        section = DesktopSection.Wallet
        notice = if (existing == null) "卡片已加入卡包" else "修改已保存"
        return true
    }

    fun saveAddressDraft(): Boolean {
        val draft = editingAddressDraft
        val errors = AddressDraftErrors(
            nickname = draft.nickname.requiredLengthError(1..50, "请输入 1–50 个字符的地址名称"),
            detailedAddress = draft.detailedAddress.requiredLengthError(1..500, "请输入 1–500 个字符的详细地址"),
            city = draft.city.requiredLengthError(1..100, "请输入 1–100 个字符的城市"),
            other = if (draft.other.codePointLength() > 200) "其它信息不能超过 200 个字符" else null,
            postalCode = draft.postalCode.requiredLengthError(1..20, "请输入 1–20 个字符的邮编"),
            country = draft.country.requiredLengthError(1..100, "请输入 1–100 个字符的国家"),
        )
        addressDraftErrors = errors
        if (errors.hasErrors) return false

        val existing = draft.id?.let { id -> addresses.firstOrNull { it.id == id } }
        val now = clock.millis()
        val address = if (existing == null) {
            DesktopAddress.create(
                nickname = draft.nickname,
                detailedAddress = draft.detailedAddress,
                city = draft.city,
                other = draft.other,
                postalCode = draft.postalCode,
                country = draft.country,
                style = draft.style,
                sortOrder = 0,
                clock = clock,
            )
        } else {
            existing.copy(
                nickname = draft.nickname.trim(),
                detailedAddress = draft.detailedAddress.trim(),
                city = draft.city.trim(),
                other = draft.other.trim(),
                postalCode = draft.postalCode.trim(),
                country = draft.country.trim(),
                cardTemplateId = AndroidTemplateStyleCodec.encode(draft.style.copy(sourceTemplateId = null)),
                updatedAtEpochMillis = now,
            )
        }
        if (existing == null) repository.addAddress(address) else repository.updateAddress(address)
        refreshAddresses()
        selectedAddressId = address.id
        editingAddressDraft = AddressDraft()
        section = DesktopSection.Addresses
        notice = if (existing == null) "地址已保存" else "地址修改已保存"
        return true
    }

    suspend fun deleteSelected(): Boolean {
        val id = selectedId ?: return false
        if (!authenticate(SensitiveAction.DeleteCard)) return false
        if (selectedId != id) return false
        return deleteSelectedUnchecked(id)
    }

    suspend fun deleteSelectedAddress(): Boolean {
        val id = selectedAddressId ?: return false
        if (!authenticate(SensitiveAction.DeleteAddress)) return false
        if (selectedAddressId != id) return false
        if (!repository.deleteAddress(id)) return false
        refreshAddresses()
        selectedAddressId = addresses.firstOrNull()?.id
        notice = "地址已删除"
        return true
    }

    private fun deleteSelectedUnchecked(id: String): Boolean {
        if (repository.delete(id)) {
            revealedCardId = null
            refreshCards()
            selectedId = cards.firstOrNull()?.id
            notice = "卡片已删除"
            return true
        }
        return false
    }

    fun moveCard(id: String, targetIndex: Int) {
        if (repository.move(id, targetIndex.coerceIn(cards.indices))) {
            refreshCards()
        }
    }

    fun moveAddress(id: String, targetIndex: Int) {
        if (addresses.isNotEmpty() && repository.moveAddress(id, targetIndex.coerceIn(addresses.indices))) {
            refreshAddresses()
            refreshFolders()
        }
    }

    fun chooseExportDirectory(directory: Path) {
        exportDirectory = directory.toAbsolutePath().normalize().toString()
        preferences.put(PREF_EXPORT_DIRECTORY, exportDirectory)
    }

    fun selectImportFile(path: Path): Boolean {
        if (transferBusy) return false
        val bytes = runCatching { DesktopTransferFiles.readImport(path) }
            .getOrElse { error ->
                selectedImportPath = null
                selectedImportKind = null
                setTransferFeedback(safeTransferError(error), isError = true)
                return false
            }
        return try {
            val kind = CardVaultSyncFiles.inspectKind(bytes)
            selectedImportPath = path.toAbsolutePath().normalize()
            selectedImportKind = kind
            setTransferFeedback(
                when {
                    kind == SyncFileKind.PAIRING ->
                        "已选择首次配对文件，请粘贴手机上的配对码后开始导入"
                    syncKeyEpoch == 0L ->
                        "这是日常同步文件，但电脑尚未配对。请在手机选择“连接新设备”，重新导出 .cvpair 文件"
                    else -> "已选择同步文件，可以开始导入"
                },
                isError = kind == SyncFileKind.SYNC && syncKeyEpoch == 0L,
            )
            true
        } catch (error: Throwable) {
            selectedImportPath = null
            selectedImportKind = null
            setTransferFeedback(safeTransferError(error), isError = true)
            false
        } finally {
            bytes.fill(0)
        }
    }

    suspend fun importSelected(pairingCodeInput: String?): Boolean {
        val path = selectedImportPath
        if (path == null) {
            setTransferFeedback("请先选择手机导出的 .cvpair 或 .cvsync 文件", isError = true)
            return false
        }
        return import(path, pairingCodeInput).also { imported ->
            if (imported) {
                selectedImportPath = null
                selectedImportKind = null
            }
        }
    }

    fun pastePairingCodeFromClipboard(): String? {
        val value = runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
        }.getOrNull()
        val normalized = value?.let(::normalizePairingCodeInput).orEmpty()
        return normalized.takeIf(String::isNotBlank).also { code ->
            if (code == null) {
                setTransferFeedback("剪贴板中没有可用的配对码", isError = true)
            } else {
                setTransferFeedback("已从剪贴板粘贴配对码")
            }
        }
    }

    fun copyCurrentPairingCode() {
        val code = pairingCode ?: return
        copySensitiveText(code, "配对码已复制")
        setTransferFeedback("配对码已复制，剪贴板将在 60 秒后清除")
    }

    suspend fun export(newPairing: Boolean): Boolean {
        val directory = exportDirectory?.let(Path::of)
        if (directory == null) {
            notice = "请先选择导出位置"
            return false
        }
        if (!authenticate(SensitiveAction.ExportVault)) return false
        return runCatching {
            val exported = syncGateway.export(repository.snapshot(), newPairing)
            try {
                // Persist the consumed sequence before exposing the file. A failed write may leave
                // a harmless sequence gap, but a crash can never cause nonce/sequence reuse.
                repository.replaceSnapshot(exported.snapshotAfterExport)
                refreshSyncStatus()
                val path = DesktopTransferFiles.writeExport(directory, exported)
                pairingCode = exported.pairingCode
                notice = "已安全导出到 ${path.fileName}"
            } finally {
                exported.bytes.fill(0)
            }
            true
        }.onFailure {
            notice = it.message ?: "导出失败"
        }.getOrDefault(false)
    }

    suspend fun rotateSyncKeyAndExportPairing(): Boolean {
        val directory = exportDirectory?.let(Path::of)
        if (directory == null) {
            notice = "请先选择导出位置"
            return false
        }
        if (!authenticate(SensitiveAction.RotateSyncKey)) return false
        return runCatching {
            // Persist the replacement key before producing any package. If file creation fails,
            // old packages are still revoked and the user can create a fresh pairing file later.
            val rotated = syncGateway.rotateSyncKey(repository.snapshot())
            repository.replaceSnapshot(rotated)
            refreshSyncStatus()
            val exported = syncGateway.export(repository.snapshot(), newPairing = true)
            try {
                repository.replaceSnapshot(exported.snapshotAfterExport)
                refreshSyncStatus()
                val path = DesktopTransferFiles.writeExport(directory, exported)
                pairingCode = exported.pairingCode
                notice = "旧同步关系已撤销；新配对文件已保存到 ${path.fileName}"
            } finally {
                exported.bytes.fill(0)
            }
            true
        }.onFailure {
            notice = it.message ?: "同步密钥轮换失败"
        }.getOrDefault(false)
    }

    suspend fun import(path: Path, pairingCodeInput: String?): Boolean {
        if (transferBusy) return false
        transferBusy = true
        var stage = ImportStage.Read
        val bytes = runCatching { DesktopTransferFiles.readImport(path) }
            .getOrElse {
                setTransferFeedback(safeTransferError(it, stage), isError = true)
                transferBusy = false
                return false
            }
        return try {
            stage = ImportStage.Validate
            val kind = CardVaultSyncFiles.inspectKind(bytes)
            val normalizedCode = pairingCodeInput?.let(::normalizePairingCodeInput)
                ?.takeIf(String::isNotBlank)
            if (kind == SyncFileKind.PAIRING && normalizedCode == null) {
                setTransferFeedback("这是首次配对文件，请先粘贴手机显示的配对码", isError = true)
                return false
            }
            if (kind == SyncFileKind.SYNC && syncKeyEpoch == 0L) {
                setTransferFeedback(
                    "电脑尚未完成首次配对，无法解密 .cvsync。请在手机选择“连接新设备”并导出 .cvpair 文件",
                    isError = true,
                )
                return false
            }
            stage = ImportStage.Authenticate
            setTransferFeedback("正在等待 Windows Hello 验证…")
            if (!authenticate(SensitiveAction.ImportVault)) {
                setTransferFeedback("Windows Hello 验证未完成，尚未导入任何数据", isError = true)
                return false
            }
            stage = ImportStage.Merge
            setTransferFeedback("正在验证并合并加密数据…")
            val result = syncGateway.import(
                bytes,
                normalizedCode,
                repository.snapshot(),
            )
            stage = ImportStage.Persist
            repository.replaceSnapshot(result.snapshot)
            refreshSyncStatus()
            refreshCards()
            refreshAddresses()
            refreshFolders()
            selectedId = cards.firstOrNull()?.id
            selectedAddressId = addresses.firstOrNull()?.id
            notice = result.message
            setTransferFeedback(result.message)
            true
        } catch (error: Throwable) {
            setTransferFeedback(safeTransferError(error, stage), isError = true)
            false
        } finally {
            bytes.fill(0)
            transferBusy = false
        }
    }

    suspend fun authorizeReveal(cardId: String): Boolean {
        if (cards.none { it.id == cardId }) return false
        if (!authenticate(SensitiveAction.RevealCardSecrets)) return false
        if (cards.none { it.id == cardId }) return false
        revealedCardId = cardId
        return true
    }

    fun concealCard(cardId: String) {
        if (revealedCardId == cardId) revealedCardId = null
    }

    fun copyCardNumber(cardId: String) {
        val card = cards.firstOrNull { it.id == cardId }
        if (card == null || revealedCardId != cardId) {
            notice = "请先验证身份并显示卡号"
            return
        }
        copySensitiveText(card.cardNumber, "卡号已复制")
    }

    fun copyAddress(
        addressId: String,
        part: DesktopAddressCopyPart = DesktopAddressCopyPart.Complete,
    ) {
        val address = addresses.firstOrNull { it.id == addressId } ?: return
        val value = address.copyText(part)
        if (value.isBlank()) return
        val message = if (part == DesktopAddressCopyPart.Complete) {
            "完整地址已复制"
        } else {
            "地址内容已复制"
        }
        copySensitiveText(value, message)
    }

    private fun copySensitiveText(value: String, successMessage: String) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(value), null)
        notice = "$successMessage，剪贴板将在 60 秒后清除"
        val expected = value
        Timer("CardVaultClipboardClear", true).schedule(object : TimerTask() {
            override fun run() {
                runCatching {
                    val current = clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
                    if (current == expected) clipboard.setContents(StringSelection(""), null)
                }
            }
        }, 60_000L)
    }

    fun clearNotice() {
        notice = null
    }

    private fun setTransferFeedback(text: String, isError: Boolean = false) {
        transferFeedback = TransferFeedback(text, isError)
        notice = text
    }

    private fun safeTransferError(error: Throwable, stage: ImportStage? = null): String = when (error) {
        is com.pdh.cardvault.desktop.sync.DesktopSyncException -> when (error.code) {
            com.pdh.cardvault.sync.SyncErrorCode.INVALID_PAIRING_CODE ->
                "配对码格式不正确（CV-I201）"
            com.pdh.cardvault.sync.SyncErrorCode.AUTHENTICATION_FAILED ->
                "配对码与文件不匹配，或文件已被修改（CV-I202）"
            com.pdh.cardvault.sync.SyncErrorCode.REPLAYED_PACKAGE ->
                "这个传输文件已经导入过（CV-I203）"
            com.pdh.cardvault.sync.SyncErrorCode.STALE_PACKAGE ->
                "传输文件已过期，或早于本机已有版本（CV-I204）"
            com.pdh.cardvault.sync.SyncErrorCode.VAULT_MISMATCH ->
                "该文件属于另一个 CardVault 数据库（CV-I205）"
            com.pdh.cardvault.sync.SyncErrorCode.LIMIT_EXCEEDED ->
                "传输文件超过安全限制（CV-I206）"
            com.pdh.cardvault.sync.SyncErrorCode.UNSUPPORTED_VERSION ->
                "传输文件版本不受支持（CV-I207）"
            com.pdh.cardvault.sync.SyncErrorCode.INVARIANT_VIOLATION ->
                "传输数据内部状态不一致，已停止导入（CV-I208）"
            com.pdh.cardvault.sync.SyncErrorCode.INVALID_FORMAT ->
                "请选择有效的 CardVault 加密文件（CV-I209）"
        }
        is com.pdh.cardvault.sync.SyncProtocolException -> when (error.code) {
            com.pdh.cardvault.sync.SyncErrorCode.INVALID_PAIRING_CODE ->
                "配对码格式不正确（CV-I201）"
            com.pdh.cardvault.sync.SyncErrorCode.AUTHENTICATION_FAILED ->
                "配对码与文件不匹配，或文件已被修改（CV-I202）"
            com.pdh.cardvault.sync.SyncErrorCode.REPLAYED_PACKAGE ->
                "这个传输文件已经导入过（CV-I203）"
            com.pdh.cardvault.sync.SyncErrorCode.STALE_PACKAGE ->
                "传输文件已过期，或早于本机已有版本（CV-I204）"
            com.pdh.cardvault.sync.SyncErrorCode.VAULT_MISMATCH ->
                "该文件属于另一个 CardVault 数据库（CV-I205）"
            com.pdh.cardvault.sync.SyncErrorCode.LIMIT_EXCEEDED ->
                "传输文件超过安全限制（CV-I206）"
            com.pdh.cardvault.sync.SyncErrorCode.UNSUPPORTED_VERSION ->
                "传输文件版本不受支持（CV-I207）"
            com.pdh.cardvault.sync.SyncErrorCode.INVARIANT_VIOLATION ->
                "传输数据内部状态不一致，已停止导入（CV-I208）"
            com.pdh.cardvault.sync.SyncErrorCode.INVALID_FORMAT ->
                "请选择有效的 CardVault 加密文件（CV-I209）"
        }
        is LocalKeyUnavailableException ->
            "Windows 本地加密密钥不可用，数据没有写入（CV-I401）"
        is IOException ->
            "电脑本地文件无法读写，请检查磁盘和目录权限（CV-I402）"
        is IllegalArgumentException -> when (stage) {
            ImportStage.Merge -> "手机数据与当前电脑版本不兼容（CV-I301）"
            else -> "请选择有效的 CardVault 加密文件（CV-I101）"
        }
        is IllegalStateException -> when (error.message) {
            "请先导入配对文件。" ->
                "电脑尚未完成首次配对，请先导入手机生成的 .cvpair 文件"
            else -> when (stage) {
                ImportStage.Persist -> "电脑本地加密存储失败，数据没有写入（CV-I403）"
                ImportStage.Merge -> "传输数据无法安全合并（CV-I302）"
                else -> "导入失败，未更改本机数据（CV-I499）"
            }
        }
        else -> when (stage) {
            ImportStage.Persist -> "电脑本地加密存储失败，数据没有写入（CV-I404）"
            ImportStage.Merge -> "传输数据无法安全合并（CV-I303）"
            else -> "导入失败，未更改本机数据（CV-I499）"
        }
    }

    private fun normalizePairingCodeInput(value: String): String = value
        .uppercase()
        .filter { character ->
            character in 'A'..'Z' || character in '1'..'7' ||
                character == '-' || character.isWhitespace()
        }
        .take(MAX_PAIRING_CODE_INPUT)

    /** Invalidates every visible disclosure without touching an unfinished editor draft. */
    fun concealSensitiveInformation() {
        revealedCardId = null
        concealmentEpoch = if (concealmentEpoch == Long.MAX_VALUE) 0L else concealmentEpoch + 1L
    }

    private suspend fun authenticate(action: SensitiveAction): Boolean = authenticationMutex.withLock {
        authenticatingAction = action
        try {
            authenticator.authenticate(action).also { verified ->
                if (!verified) notice = "Windows Hello 验证未通过或不可用"
            }
        } finally {
            authenticatingAction = null
        }
    }

    override fun close() {
        revealedCardId = null
        repository.close()
    }

    private fun refreshCards() {
        cards = repository.cards()
    }

    private fun refreshAddresses() {
        addresses = repository.addresses()
    }

    private fun refreshFolders() {
        cardFolders = repository.folders(DesktopFolderKind.CARDS)
        addressFolders = repository.folders(DesktopFolderKind.ADDRESSES)
        cardFolderOrder = repository.folderOrder(DesktopFolderKind.CARDS)
        addressFolderOrder = repository.folderOrder(DesktopFolderKind.ADDRESSES)
    }

    private fun refreshSyncStatus() {
        val state = repository.snapshot().syncState
        syncKeyEpoch = state.keyEpoch.takeIf { state.sharedSyncKey != null } ?: 0L
    }

    private fun isValidExpiry(digits: String): Boolean =
        digits.length == 4 && digits.all { it in '0'..'9' } && digits.take(2).toInt() in 1..12

    private fun loadExportDirectory(): String? =
        preferences.get(PREF_EXPORT_DIRECTORY, null)?.takeIf(String::isNotBlank)

    private companion object {
        val preferences: Preferences = Preferences.userRoot().node("com/pdh/cardvault/desktop")
        const val PREF_EXPORT_DIRECTORY = "exportDirectory"
        const val MAX_PAIRING_CODE_INPUT = 48
    }
}

private fun String.codePointLength(): Int = codePointCount(0, length)

private fun String.requiredLengthError(range: IntRange, message: String): String? {
    val normalized = trim()
    return if (normalized.codePointLength() in range) null else message
}
