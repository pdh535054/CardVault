package com.pdh.cardvault.presentation

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pdh.cardvault.data.room.PersistentBankCardRepository
import com.pdh.cardvault.data.room.PersistentCardDetail
import com.pdh.cardvault.data.room.PersistentCardListItem
import com.pdh.cardvault.data.room.PersistentCardSecrets
import com.pdh.cardvault.domain.model.BankCardInput
import com.pdh.cardvault.domain.model.CardNetwork
import com.pdh.cardvault.domain.validation.BankCardValidationError
import com.pdh.cardvault.domain.validation.BankCardValidationException
import com.pdh.cardvault.domain.validation.BankCardValidationWarning
import com.pdh.cardvault.domain.validation.BankCardValidator
import com.pdh.cardvault.domain.validation.CardNumberTools
import com.pdh.cardvault.domain.validation.CardNetworkDetector
import com.pdh.cardvault.domain.validation.ExpiryDateTools
import com.pdh.cardvault.security.auth.AuthenticationAction
import com.pdh.cardvault.security.auth.AuthenticationScope
import com.pdh.cardvault.security.auth.DeviceSecurityChecker
import com.pdh.cardvault.security.auth.DeviceSecurityStatus
import com.pdh.cardvault.security.clipboard.SensitiveClipboardController
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CardFormField {
    Nickname,
    IssuerName,
    CardNumber,
    ExpiryMonth,
    ExpiryYear,
    ExpiryDate,
    Cvv,
    Notes,
}

data class CardFormUiState(
    val nickname: String,
    val issuerName: String,
    val cardNumber: String,
    val expiryMonth: String,
    val expiryYear: String,
    val saveCvv: Boolean,
    val cvv: String,
    val cardTemplateId: String,
    val notes: String,
    val validationErrors: Set<BankCardValidationError>,
    val showLuhnWarning: Boolean,
    val submissionFailed: Boolean,
    val submitting: Boolean,
    val cvvRiskAcknowledged: Boolean,
    val showCvvRiskConfirmation: Boolean,
) {
    val expiryDigits: String
        get() = expiryMonth + expiryYear

    val expiryDateText: String
        get() = ExpiryDateTools.formatInputDigits(expiryDigits)

    val cardNetwork: CardNetwork?
        get() = CardNetworkDetector.detect(cardNumber)

    override fun toString(): String = "CardFormUiState(sensitiveFields=redacted)"
}

data class CardListItemUiModel(
    val id: UUID,
    val nickname: String,
    val issuerName: String,
    val cardTemplateId: String,
    val cardNetwork: CardNetwork?,
) {
    override fun toString(): String = "CardListItemUiModel(sensitiveFields=redacted)"
}

enum class CvvSaveStatusUi {
    Saved,
    NotSaved,
    ;

    override fun toString(): String = "CvvSaveStatusUi(value=redacted)"
}

data class CardDetailUiModel(
    val nickname: String,
    val issuerName: String,
    val maskedCardNumber: String,
    val cardTemplateId: String,
    val notes: String,
    val cardNetwork: CardNetwork?,
    val cvvSaveStatus: CvvSaveStatusUi,
    val canRevealCardSecrets: Boolean,
    val canEdit: Boolean,
    val canDelete: Boolean,
) {
    override fun toString(): String = "CardDetailUiModel(sensitiveFields=redacted)"
}

sealed interface CardDetailUiState {
    data object Hidden : CardDetailUiState

    data object Loading : CardDetailUiState

    data object NotFound : CardDetailUiState

    data class Loaded(
        val recordId: UUID,
        val card: CardDetailUiModel,
    ) : CardDetailUiState {
        override fun toString(): String = "CardDetailUiState.Loaded(sensitiveFields=redacted)"
    }
}

sealed interface RevealedCardSecretsUiState {
    data object Hidden : RevealedCardSecretsUiState

    data class Visible(
        val recordId: UUID,
        val cardNumber: String,
        val expiryMonth: Int,
        val expiryYear: Int,
        val expired: Boolean,
        val cvv: String?,
        val expiresAtElapsedRealtime: Long,
    ) : RevealedCardSecretsUiState {
        override fun toString(): String = "RevealedCardSecretsUiState.Visible(values=redacted)"
    }
}

sealed interface CardEditUiState {
    data object Hidden : CardEditUiState

    data object Loading : CardEditUiState

    data object Unavailable : CardEditUiState

    data class Ready(
        val recordId: UUID,
        val form: CardFormUiState,
    ) : CardEditUiState {
        override fun toString(): String = "CardEditUiState.Ready(sensitiveFields=redacted)"
    }
}

enum class VaultContentState {
    Locked,
    Loading,
    Ready,
    Unavailable,
}

enum class CardOperationMessage {
    None,
    AuthenticationFailed,
    StorageFailed,
    DeviceSecurityRequired,
    RecordUnavailable,
    CardNumberCopied,
    ClipboardFailed,
}

sealed interface CardNavigationEvent {
    data object AddSaved : CardNavigationEvent

    data class EditReady(val recordId: UUID) : CardNavigationEvent

    data object EditSaved : CardNavigationEvent

    data object DeleteCompleted : CardNavigationEvent
}

data class CardsUiState(
    val vaultContentState: VaultContentState,
    val cards: List<CardListItemUiModel>,
    val form: CardFormUiState,
    val isSorting: Boolean,
    val sortingInProgress: Boolean,
    val detail: CardDetailUiState,
    val edit: CardEditUiState,
    val revealedSecrets: RevealedCardSecretsUiState,
    val operationMessage: CardOperationMessage,
    val navigationEvent: CardNavigationEvent?,
) {
    override fun toString(): String = "CardsUiState(sensitiveFields=redacted)"
}

internal fun interface MonotonicTimeSource {
    fun elapsedRealtime(): Long
}

internal fun interface SensitiveExpirationScheduler {
    fun schedule(scope: CoroutineScope, delayMillis: Long, onExpired: () -> Unit): Job
}

private object CoroutineSensitiveExpirationScheduler : SensitiveExpirationScheduler {
    override fun schedule(
        scope: CoroutineScope,
        delayMillis: Long,
        onExpired: () -> Unit,
    ): Job = scope.launch {
        delay(delayMillis)
        onExpired()
    }
}

class CardsViewModel internal constructor(
    private val repository: PersistentBankCardRepository,
    private val validator: BankCardValidator,
    private val defaultTemplateId: String,
    private val templateIssuerLabel: (String) -> String = { "CardVault" },
    private val deviceSecurityChecker: DeviceSecurityChecker,
    private val sensitiveClipboardController: SensitiveClipboardController,
    private val externalScope: CoroutineScope? = null,
    private val timeSource: MonotonicTimeSource = MonotonicTimeSource {
        SystemClock.elapsedRealtime()
    },
    private val expirationScheduler: SensitiveExpirationScheduler =
        CoroutineSensitiveExpirationScheduler,
) : ViewModel() {
    private var lifecycleEpoch = 0L
    private var revealGeneration = 0L
    private var activeDetailRecordId: UUID? = null
    private var pendingAuthentication: AuthenticationScope? = null
    private var authorizedOperation: AuthenticationScope? = null

    private var vaultJob: Job? = null
    private var addJob: Job? = null
    private var detailJob: Job? = null
    private var revealJob: Job? = null
    private var authenticatedJob: Job? = null
    private var editSaveJob: Job? = null
    private var sortJob: Job? = null
    private var revealedSecretsExpiration: Job? = null

    private val workScope: CoroutineScope
        get() = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(
        CardsUiState(
            vaultContentState = VaultContentState.Locked,
            cards = emptyList(),
            form = emptyForm(),
            isSorting = false,
            sortingInProgress = false,
            detail = CardDetailUiState.Hidden,
            edit = CardEditUiState.Hidden,
            revealedSecrets = RevealedCardSecretsUiState.Hidden,
            operationMessage = CardOperationMessage.None,
            navigationEvent = null,
        ),
    )
    val uiState = _uiState.asStateFlow()

    fun onVaultAccessAllowed(onFailure: () -> Unit) {
        if (
            _uiState.value.vaultContentState == VaultContentState.Ready &&
            repository.isUnlocked()
        ) {
            executeAuthorizedOperationIfReady()
            return
        }

        vaultJob?.cancel()
        val expectedEpoch = lifecycleEpoch
        _uiState.update { state ->
            state.copy(
                vaultContentState = VaultContentState.Loading,
                cards = emptyList(),
                detail = CardDetailUiState.Hidden,
                operationMessage = CardOperationMessage.None,
            )
        }
        vaultJob = workScope.launch {
            try {
                repository.unlockOrCreateVault()
                val cards = repository.getList().map(PersistentCardListItem::toUiModel)
                if (!isCurrentEpoch(expectedEpoch)) return@launch
                _uiState.update { state ->
                    state.copy(
                        vaultContentState = VaultContentState.Ready,
                        cards = cards,
                        operationMessage = CardOperationMessage.None,
                    )
                }
                executeAuthorizedOperationIfReady()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                repository.lock()
                if (!isCurrentEpoch(expectedEpoch)) return@launch
                pendingAuthentication = null
                authorizedOperation = null
                activeDetailRecordId = null
                clearSensitiveValues()
                _uiState.update { state ->
                    state.copy(
                        vaultContentState = VaultContentState.Unavailable,
                        cards = emptyList(),
                        detail = CardDetailUiState.Hidden,
                        edit = CardEditUiState.Hidden,
                        operationMessage = CardOperationMessage.StorageFailed,
                    )
                }
                onFailure()
            }
        }
    }

    fun onVaultAccessRevoked(preservePendingAuthentication: Boolean = false) {
        lifecycleEpoch += 1L
        cancelAllJobs()
        clearOwnedCardNumberClipboard()
        repository.lock()
        val preservedRequest = pendingAuthentication.takeIf { preservePendingAuthentication }
        pendingAuthentication = preservedRequest
        authorizedOperation = null
        activeDetailRecordId = preservedRequest?.recordId
        _uiState.value = CardsUiState(
            vaultContentState = VaultContentState.Locked,
            cards = emptyList(),
            form = emptyForm(),
            isSorting = false,
            sortingInProgress = false,
            detail = CardDetailUiState.Hidden,
            edit = CardEditUiState.Hidden,
            revealedSecrets = RevealedCardSecretsUiState.Hidden,
            operationMessage = CardOperationMessage.None,
            navigationEvent = null,
        )
    }

    fun onAppBackgrounded(preservePendingAuthentication: Boolean = false) {
        // A routine app switch must not destroy an add/edit draft or relock the encrypted
        // repository. Only values that were revealed after authentication are revoked.
        resetRevealedCardSecrets(
            invalidatePendingReveal = !preservePendingAuthentication,
        )
        if (!preservePendingAuthentication) {
            pendingAuthentication = null
            authorizedOperation = null
        }
    }

    fun refreshAfterExternalImport() {
        val expectedEpoch = lifecycleEpoch
        if (
            _uiState.value.vaultContentState != VaultContentState.Ready ||
            !repository.isUnlocked()
        ) {
            return
        }
        vaultJob?.cancel()
        vaultJob = workScope.launch {
            try {
                val cards = repository.getList().map(PersistentCardListItem::toUiModel)
                if (!isCurrentReadyEpoch(expectedEpoch)) return@launch
                clearSensitiveValues()
                activeDetailRecordId = null
                _uiState.update { current ->
                    current.copy(
                        cards = cards,
                        detail = CardDetailUiState.Hidden,
                        edit = CardEditUiState.Hidden,
                        isSorting = false,
                        sortingInProgress = false,
                        operationMessage = CardOperationMessage.None,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                if (isCurrentReadyEpoch(expectedEpoch)) showStorageFailure()
            }
        }
    }

    fun updateField(field: CardFormField, proposedValue: String) {
        updateAddForm { current -> current.withField(field, proposedValue) }
    }

    fun setSaveCvv(enabled: Boolean) {
        updateAddForm { current -> current.withSaveCvvRequest(enabled) }
    }

    fun confirmSaveCvvRisk() {
        updateAddForm(CardFormUiState::confirmCvvRisk)
    }

    fun dismissSaveCvvRisk() {
        updateAddForm(CardFormUiState::dismissCvvRisk)
    }

    fun selectTemplate(templateId: String) {
        updateAddForm { current -> current.copy(cardTemplateId = templateId) }
    }

    fun submitCard(): Boolean {
        val state = _uiState.value
        val form = state.form
        if (
            state.vaultContentState != VaultContentState.Ready ||
            form.submitting ||
            !repository.isUnlocked()
        ) {
            return false
        }
        val validation = validator.validate(form.toInput())
        if (!validation.isValid) {
            _uiState.update { current ->
                current.copy(
                    form = form.copy(
                        validationErrors = validation.errors,
                        showLuhnWarning =
                            BankCardValidationWarning.LUHN_CHECK_FAILED in validation.warnings,
                        submissionFailed = false,
                    ),
                )
            }
            return false
        }
        if (deviceSecurityChecker.check() != DeviceSecurityStatus.Available) {
            _uiState.update { current ->
                current.copy(
                    form = form.copy(submissionFailed = true),
                    operationMessage = CardOperationMessage.DeviceSecurityRequired,
                )
            }
            return false
        }

        val expectedEpoch = lifecycleEpoch
        _uiState.update { current ->
            current.copy(
                form = form.copy(submitting = true, submissionFailed = false),
                operationMessage = CardOperationMessage.None,
            )
        }
        addJob = workScope.launch {
            try {
                repository.add(validation.normalizedInput)
                val cards = repository.getList().map(PersistentCardListItem::toUiModel)
                if (!isCurrentReadyEpoch(expectedEpoch)) return@launch
                _uiState.update { current ->
                    current.copy(
                        cards = cards,
                        form = emptyForm(),
                        isSorting = false,
                        operationMessage = CardOperationMessage.None,
                        navigationEvent = CardNavigationEvent.AddSaved,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: BankCardValidationException) {
                if (!isCurrentReadyEpoch(expectedEpoch)) return@launch
                _uiState.update { current ->
                    current.copy(
                        form = form.copy(
                            validationErrors = exception.validationErrors,
                            submissionFailed = true,
                            submitting = false,
                        ),
                    )
                }
            } catch (_: Exception) {
                if (!isCurrentReadyEpoch(expectedEpoch)) return@launch
                _uiState.update { current ->
                    current.copy(
                        form = form.copy(submissionFailed = true, submitting = false),
                        operationMessage = CardOperationMessage.StorageFailed,
                    )
                }
            }
        }
        return true
    }

    fun clearForm() {
        if (_uiState.value.form.submitting) return
        _uiState.update { current -> current.copy(form = emptyForm()) }
    }

    fun loadCardDetail(recordId: String?) {
        val parsedRecordId = recordId.toUuidOrNull()
        if (parsedRecordId == null || _uiState.value.vaultContentState != VaultContentState.Ready) {
            activeDetailRecordId = null
            clearSensitiveValues()
            _uiState.update { current -> current.copy(detail = CardDetailUiState.NotFound) }
            return
        }

        if (activeDetailRecordId != parsedRecordId) clearSensitiveValues()
        activeDetailRecordId = parsedRecordId
        detailJob?.cancel()
        _uiState.update { current ->
            current.copy(
                detail = CardDetailUiState.Loading,
                operationMessage = CardOperationMessage.None,
            )
        }
        val expectedEpoch = lifecycleEpoch
        detailJob = workScope.launch {
            try {
                val projection = repository.getMaskedDetail(parsedRecordId)
                if (
                    !isCurrentReadyEpoch(expectedEpoch) ||
                    activeDetailRecordId != parsedRecordId
                ) {
                    return@launch
                }
                _uiState.update { current ->
                    current.copy(
                        detail = projection?.let { detail ->
                            CardDetailUiState.Loaded(parsedRecordId, detail.toUiModel())
                        } ?: CardDetailUiState.NotFound,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                if (!isCurrentReadyEpoch(expectedEpoch)) return@launch
                clearSensitiveValues()
                _uiState.update { current ->
                    current.copy(
                        detail = CardDetailUiState.NotFound,
                        operationMessage = CardOperationMessage.StorageFailed,
                    )
                }
            }
        }
    }

    fun clearCardDetail(preservePendingAuthentication: Boolean = false) {
        val preservedRequest = pendingAuthentication?.takeIf { scope ->
            preservePendingAuthentication &&
                scope.recordId != null &&
                scope.recordId == activeDetailRecordId
        }
        detailJob?.cancel()
        resetRevealedCardSecrets(invalidatePendingReveal = preservedRequest == null)
        pendingAuthentication = preservedRequest
        authorizedOperation = null
        activeDetailRecordId = preservedRequest?.recordId
        _uiState.update { current ->
            current.copy(
                detail = CardDetailUiState.Hidden,
                operationMessage = CardOperationMessage.None,
            )
        }
    }

    fun prepareAuthentication(action: AuthenticationAction, recordId: UUID): Boolean {
        if (
            !action.requiresRecordId ||
            _uiState.value.vaultContentState != VaultContentState.Ready ||
            !repository.isUnlocked() ||
            pendingAuthentication != null ||
            authorizedOperation != null
        ) {
            return false
        }
        if (activeDetailRecordId != recordId) return false

        when (action) {
            AuthenticationAction.RevealCardSecrets -> hideRevealedCardSecrets()
            AuthenticationAction.EditCard,
            AuthenticationAction.DeleteCard,
            -> clearSensitiveValues()

            else -> return false
        }
        pendingAuthentication = AuthenticationScope(action, recordId)
        _uiState.update { current ->
            current.copy(operationMessage = CardOperationMessage.None)
        }
        return true
    }

    fun cancelPreparedAuthentication(scope: AuthenticationScope) {
        if (pendingAuthentication == scope) pendingAuthentication = null
    }

    fun pendingAuthenticationScope(): AuthenticationScope? = pendingAuthentication

    fun onAuthenticationFailed() {
        if (pendingAuthentication == null) return
        pendingAuthentication = null
        authorizedOperation = null
        _uiState.update { current ->
            current.copy(operationMessage = CardOperationMessage.AuthenticationFailed)
        }
    }

    fun onAuthorizationGranted(scope: AuthenticationScope): Boolean {
        if (pendingAuthentication != scope) return false
        pendingAuthentication = null
        authorizedOperation = scope
        _uiState.update { current ->
            current.copy(operationMessage = CardOperationMessage.None)
        }
        executeAuthorizedOperationIfReady()
        return true
    }

    fun hideRevealedCardSecrets() {
        resetRevealedCardSecrets(invalidatePendingReveal = true)
    }

    private fun resetRevealedCardSecrets(invalidatePendingReveal: Boolean) {
        revealGeneration += 1L
        if (invalidatePendingReveal) {
            pendingAuthentication = pendingAuthentication
                ?.takeUnless { scope -> scope.action == AuthenticationAction.RevealCardSecrets }
            authorizedOperation = authorizedOperation
                ?.takeUnless { scope -> scope.action == AuthenticationAction.RevealCardSecrets }
        }
        revealJob?.cancel()
        revealJob = null
        revealedSecretsExpiration?.cancel()
        revealedSecretsExpiration = null
        clearOwnedCardNumberClipboard()
        _uiState.update { current ->
            current.copy(revealedSecrets = RevealedCardSecretsUiState.Hidden)
        }
    }

    fun copyRevealedCardNumber(recordId: UUID): Boolean {
        val visible = _uiState.value.revealedSecrets as? RevealedCardSecretsUiState.Visible
            ?: return false
        if (
            visible.recordId != recordId ||
            activeDetailRecordId != recordId ||
            _uiState.value.vaultContentState != VaultContentState.Ready ||
            !repository.isUnlocked() ||
            remainingMillis(visible.expiresAtElapsedRealtime) == 0L
        ) {
            hideRevealedCardSecrets()
            return false
        }

        val copied = try {
            sensitiveClipboardController.copyCardNumber(visible.cardNumber)
        } catch (_: RuntimeException) {
            false
        }
        _uiState.update { current ->
            current.copy(
                operationMessage = if (copied) {
                    CardOperationMessage.CardNumberCopied
                } else {
                    CardOperationMessage.ClipboardFailed
                },
            )
        }
        return copied
    }

    fun updateEditField(field: CardFormField, proposedValue: String) {
        updateEditForm { current -> current.withField(field, proposedValue) }
    }

    fun setEditSaveCvv(enabled: Boolean) {
        updateEditForm { current -> current.withSaveCvvRequest(enabled) }
    }

    fun confirmEditSaveCvvRisk() {
        updateEditForm(CardFormUiState::confirmCvvRisk)
    }

    fun dismissEditSaveCvvRisk() {
        updateEditForm(CardFormUiState::dismissCvvRisk)
    }

    fun selectEditTemplate(templateId: String) {
        updateEditForm { current -> current.copy(cardTemplateId = templateId) }
    }

    fun submitEdit(): Boolean {
        val editState = _uiState.value.edit as? CardEditUiState.Ready ?: return false
        val form = editState.form
        if (
            _uiState.value.vaultContentState != VaultContentState.Ready ||
            form.submitting ||
            !repository.isUnlocked()
        ) {
            return false
        }
        if (form.saveCvv && !form.cvvRiskAcknowledged) {
            updateEditForm { current -> current.copy(showCvvRiskConfirmation = true) }
            return false
        }
        val validation = validator.validate(form.toInput())
        if (!validation.isValid) {
            updateEditForm { current ->
                current.copy(
                    validationErrors = validation.errors,
                    showLuhnWarning =
                        BankCardValidationWarning.LUHN_CHECK_FAILED in validation.warnings,
                    submissionFailed = false,
                )
            }
            return false
        }
        if (deviceSecurityChecker.check() != DeviceSecurityStatus.Available) {
            updateEditForm { current -> current.copy(submissionFailed = true) }
            _uiState.update { current ->
                current.copy(operationMessage = CardOperationMessage.DeviceSecurityRequired)
            }
            return false
        }

        val recordId = editState.recordId
        val expectedEpoch = lifecycleEpoch
        updateEditForm { current ->
            current.copy(submitting = true, submissionFailed = false)
        }
        editSaveJob = workScope.launch {
            try {
                if (!repository.update(recordId, validation.normalizedInput)) {
                    throw RecordUnavailableException()
                }
                val cards = repository.getList().map(PersistentCardListItem::toUiModel)
                val detail = repository.getMaskedDetail(recordId)
                if (!isCurrentReadyEpoch(expectedEpoch)) return@launch
                clearSensitiveValues()
                _uiState.update { current ->
                    current.copy(
                        cards = cards,
                        edit = CardEditUiState.Hidden,
                        detail = detail?.let { projection ->
                            CardDetailUiState.Loaded(recordId, projection.toUiModel())
                        } ?: CardDetailUiState.NotFound,
                        operationMessage = CardOperationMessage.None,
                        navigationEvent = CardNavigationEvent.EditSaved,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: RecordUnavailableException) {
                if (!isCurrentReadyEpoch(expectedEpoch)) return@launch
                _uiState.update { current ->
                    current.copy(
                        edit = CardEditUiState.Unavailable,
                        operationMessage = CardOperationMessage.RecordUnavailable,
                    )
                }
            } catch (_: Exception) {
                if (!isCurrentReadyEpoch(expectedEpoch)) return@launch
                updateEditForm { current ->
                    current.copy(submitting = false, submissionFailed = true)
                }
                _uiState.update { current ->
                    current.copy(operationMessage = CardOperationMessage.StorageFailed)
                }
            }
        }
        return true
    }

    fun clearEditDraft() {
        val editState = _uiState.value.edit
        if (editState is CardEditUiState.Ready && editState.form.submitting) return
        editSaveJob?.cancel()
        _uiState.update { current ->
            current.copy(
                edit = CardEditUiState.Hidden,
                operationMessage = CardOperationMessage.None,
            )
        }
    }

    fun enterSortMode() {
        _uiState.update { current ->
            if (
                current.vaultContentState == VaultContentState.Ready &&
                current.cards.size > 1 &&
                !current.sortingInProgress
            ) {
                current.copy(isSorting = true)
            } else {
                current
            }
        }
    }

    fun exitSortMode() {
        _uiState.update { current ->
            if (current.sortingInProgress) current else current.copy(isSorting = false)
        }
    }

    fun moveCardUp(recordId: UUID): Boolean = moveCard(recordId, offset = -1)

    fun moveCardDown(recordId: UUID): Boolean = moveCard(recordId, offset = 1)

    /** Persists one complete drag result atomically instead of writing on every crossed item. */
    fun reorderCards(orderedIds: List<UUID>): Boolean {
        val state = _uiState.value
        if (
            state.sortingInProgress ||
            state.vaultContentState != VaultContentState.Ready ||
            orderedIds.size != state.cards.size ||
            orderedIds.distinct().size != orderedIds.size ||
            orderedIds.toSet() != state.cards.map(CardListItemUiModel::id).toSet()
        ) {
            return false
        }
        val cardsById = state.cards.associateBy(CardListItemUiModel::id)
        val reordered = orderedIds.map(cardsById::getValue)
        if (reordered == state.cards) return false
        return persistReorderedCards(original = state.cards, reordered = reordered)
    }

    fun onNavigationEventHandled(event: CardNavigationEvent) {
        _uiState.update { current ->
            if (current.navigationEvent == event) current.copy(navigationEvent = null) else current
        }
    }

    fun clearOperationMessage() {
        _uiState.update { current ->
            current.copy(operationMessage = CardOperationMessage.None)
        }
    }

    private fun executeAuthorizedOperationIfReady() {
        val scope = authorizedOperation ?: return
        if (
            _uiState.value.vaultContentState != VaultContentState.Ready ||
            !repository.isUnlocked()
        ) {
            return
        }
        if (activeDetailRecordId != scope.recordId) {
            authorizedOperation = null
            return
        }
        authorizedOperation = null
        when (scope.action) {
            AuthenticationAction.RevealCardSecrets -> revealCardSecrets(scope)
            AuthenticationAction.EditCard -> loadAuthenticatedEdit(scope)
            AuthenticationAction.DeleteCard -> deleteAuthenticatedRecord(scope)
            else -> Unit
        }
    }

    private fun revealCardSecrets(scope: AuthenticationScope) {
        val recordId = requireNotNull(scope.recordId)
        revealJob?.cancel()
        val expectedEpoch = lifecycleEpoch
        val expectedRevealGeneration = revealGeneration
        revealJob = workScope.launch {
            try {
                val secrets = repository.getSecretsForAuthenticatedUse(recordId)
                if (
                    !isCurrentDetailEpoch(expectedEpoch, recordId) ||
                    revealGeneration != expectedRevealGeneration
                ) {
                    return@launch
                }
                if (secrets == null) {
                    showRecordUnavailable()
                    return@launch
                }
                showRevealedCardSecrets(recordId, secrets)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                if (isCurrentEpoch(expectedEpoch)) showStorageFailure()
            }
        }
    }

    private fun loadAuthenticatedEdit(scope: AuthenticationScope) {
        val recordId = requireNotNull(scope.recordId)
        authenticatedJob?.cancel()
        clearSensitiveValues()
        _uiState.update { current -> current.copy(edit = CardEditUiState.Loading) }
        val expectedEpoch = lifecycleEpoch
        authenticatedJob = workScope.launch {
            try {
                val input = repository.getEditInputForAuthenticatedUse(recordId)
                if (!isCurrentDetailEpoch(expectedEpoch, recordId)) return@launch
                if (input == null) {
                    _uiState.update { current ->
                        current.copy(
                            edit = CardEditUiState.Unavailable,
                            operationMessage = CardOperationMessage.RecordUnavailable,
                        )
                    }
                    return@launch
                }
                _uiState.update { current ->
                    current.copy(
                        edit = CardEditUiState.Ready(recordId, input.toForm()),
                        navigationEvent = CardNavigationEvent.EditReady(recordId),
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                if (!isCurrentEpoch(expectedEpoch)) return@launch
                _uiState.update { current ->
                    current.copy(
                        edit = CardEditUiState.Unavailable,
                        operationMessage = CardOperationMessage.StorageFailed,
                    )
                }
            }
        }
    }

    private fun deleteAuthenticatedRecord(scope: AuthenticationScope) {
        val recordId = requireNotNull(scope.recordId)
        authenticatedJob?.cancel()
        clearSensitiveValues()
        val expectedEpoch = lifecycleEpoch
        authenticatedJob = workScope.launch {
            try {
                if (!repository.delete(recordId)) throw RecordUnavailableException()
                val cards = repository.getList().map(PersistentCardListItem::toUiModel)
                if (!isCurrentEpoch(expectedEpoch)) return@launch
                activeDetailRecordId = null
                _uiState.update { current ->
                    current.copy(
                        cards = cards,
                        detail = CardDetailUiState.Hidden,
                        edit = CardEditUiState.Hidden,
                        operationMessage = CardOperationMessage.None,
                        navigationEvent = CardNavigationEvent.DeleteCompleted,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: RecordUnavailableException) {
                if (isCurrentEpoch(expectedEpoch)) showRecordUnavailable()
            } catch (_: Exception) {
                if (isCurrentEpoch(expectedEpoch)) showStorageFailure()
            }
        }
    }

    private fun moveCard(recordId: UUID, offset: Int): Boolean {
        val state = _uiState.value
        if (
            !state.isSorting ||
            state.sortingInProgress ||
            state.vaultContentState != VaultContentState.Ready
        ) {
            return false
        }
        val currentIndex = state.cards.indexOfFirst { card -> card.id == recordId }
        if (currentIndex < 0) return false
        val targetIndex = currentIndex + offset
        if (targetIndex !in state.cards.indices) return false

        val original = state.cards
        val reordered = original.toMutableList().apply {
            val moved = removeAt(currentIndex)
            add(targetIndex, moved)
        }
        return persistReorderedCards(original = original, reordered = reordered)
    }

    private fun persistReorderedCards(
        original: List<CardListItemUiModel>,
        reordered: List<CardListItemUiModel>,
    ): Boolean {
        _uiState.update { current ->
            current.copy(
                cards = reordered,
                sortingInProgress = true,
                operationMessage = CardOperationMessage.None,
            )
        }
        val expectedEpoch = lifecycleEpoch
        sortJob = workScope.launch {
            try {
                repository.reorder(reordered.map(CardListItemUiModel::id))
                val persisted = repository.getList().map(PersistentCardListItem::toUiModel)
                if (!isCurrentReadyEpoch(expectedEpoch)) return@launch
                _uiState.update { current ->
                    current.copy(cards = persisted, sortingInProgress = false)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                if (!isCurrentReadyEpoch(expectedEpoch)) return@launch
                _uiState.update { current ->
                    current.copy(
                        cards = original,
                        sortingInProgress = false,
                        operationMessage = CardOperationMessage.StorageFailed,
                    )
                }
            }
        }
        return true
    }

    private fun updateAddForm(transform: (CardFormUiState) -> CardFormUiState) {
        _uiState.update { current ->
            if (current.form.submitting) return@update current
            val updated = transform(current.form).normalizedAfterUserChange()
            current.copy(
                form = updated.withLuhnWarning(validator),
                operationMessage = CardOperationMessage.None,
            )
        }
    }

    private fun updateEditForm(transform: (CardFormUiState) -> CardFormUiState) {
        _uiState.update { current ->
            val editState = current.edit as? CardEditUiState.Ready ?: return@update current
            if (editState.form.submitting) return@update current
            val updated = transform(editState.form).normalizedAfterUserChange()
            current.copy(
                edit = editState.copy(form = updated.withLuhnWarning(validator)),
                operationMessage = CardOperationMessage.None,
            )
        }
    }

    private fun showRevealedCardSecrets(recordId: UUID, secrets: PersistentCardSecrets) {
        val expiresAt = safeExpiry(REVEALED_CARD_SECRETS_VISIBLE_MILLIS)
        _uiState.update { current ->
            current.copy(
                revealedSecrets = RevealedCardSecretsUiState.Visible(
                    recordId = recordId,
                    cardNumber = secrets.cardNumber,
                    expiryMonth = secrets.expiryMonth,
                    expiryYear = secrets.expiryYear,
                    expired = validator.isExpired(secrets.expiryMonth, secrets.expiryYear),
                    cvv = secrets.cvv,
                    expiresAtElapsedRealtime = expiresAt,
                ),
            )
        }
        scheduleRevealedCardSecretsExpiration(recordId, expiresAt)
    }

    private fun scheduleRevealedCardSecretsExpiration(recordId: UUID, expiresAt: Long) {
        revealedSecretsExpiration?.cancel()
        val remaining = remainingMillis(expiresAt)
        if (remaining == 0L) {
            hideRevealedCardSecrets()
            return
        }
        revealedSecretsExpiration = expirationScheduler.schedule(workScope, remaining) {
            val visible = _uiState.value.revealedSecrets as? RevealedCardSecretsUiState.Visible
            if (visible?.recordId != recordId || visible.expiresAtElapsedRealtime != expiresAt) {
                return@schedule
            }
            if (remainingMillis(expiresAt) == 0L) {
                hideRevealedCardSecrets()
            } else {
                scheduleRevealedCardSecretsExpiration(recordId, expiresAt)
            }
        }
    }

    private fun clearSensitiveValues() {
        hideRevealedCardSecrets()
    }

    private fun clearOwnedCardNumberClipboard() {
        try {
            sensitiveClipboardController.clearCardNumberIfOwned()
        } catch (_: RuntimeException) {
            // Sensitive UI state must still be cleared if the platform clipboard is unavailable.
        }
    }

    private fun cancelAllJobs() {
        listOf(
            vaultJob,
            addJob,
            detailJob,
            revealJob,
            authenticatedJob,
            editSaveJob,
            sortJob,
            revealedSecretsExpiration,
        ).forEach { job -> job?.cancel() }
        vaultJob = null
        addJob = null
        detailJob = null
        revealJob = null
        authenticatedJob = null
        editSaveJob = null
        sortJob = null
        revealedSecretsExpiration = null
    }

    private fun showStorageFailure() {
        clearSensitiveValues()
        _uiState.update { current ->
            current.copy(operationMessage = CardOperationMessage.StorageFailed)
        }
    }

    private fun showRecordUnavailable() {
        clearSensitiveValues()
        _uiState.update { current ->
            current.copy(operationMessage = CardOperationMessage.RecordUnavailable)
        }
    }

    private fun safeExpiry(durationMillis: Long): Long {
        val now = timeSource.elapsedRealtime().coerceAtLeast(0L)
        return if (Long.MAX_VALUE - now < durationMillis) Long.MAX_VALUE else now + durationMillis
    }

    private fun remainingMillis(expiresAt: Long): Long =
        (expiresAt - timeSource.elapsedRealtime().coerceAtLeast(0L)).coerceAtLeast(0L)

    private fun isCurrentEpoch(expectedEpoch: Long): Boolean = lifecycleEpoch == expectedEpoch

    private fun isCurrentReadyEpoch(expectedEpoch: Long): Boolean =
        isCurrentEpoch(expectedEpoch) &&
            _uiState.value.vaultContentState == VaultContentState.Ready &&
            repository.isUnlocked()

    private fun isCurrentDetailEpoch(expectedEpoch: Long, recordId: UUID): Boolean =
        isCurrentReadyEpoch(expectedEpoch) && activeDetailRecordId == recordId

    private fun emptyForm(): CardFormUiState = CardFormUiState(
        nickname = "",
        issuerName = templateIssuerLabel(defaultTemplateId).trim().take(80),
        cardNumber = "",
        expiryMonth = "",
        expiryYear = "",
        saveCvv = false,
        cvv = "",
        cardTemplateId = defaultTemplateId,
        notes = "",
        validationErrors = emptySet(),
        showLuhnWarning = false,
        submissionFailed = false,
        submitting = false,
        cvvRiskAcknowledged = false,
        showCvvRiskConfirmation = false,
    )

    override fun onCleared() {
        cancelAllJobs()
        clearOwnedCardNumberClipboard()
        repository.lock()
        super.onCleared()
    }

    class Factory(
        private val repository: PersistentBankCardRepository,
        private val validator: BankCardValidator,
        private val defaultTemplateId: String,
        private val templateIssuerLabel: (String) -> String,
        private val deviceSecurityChecker: DeviceSecurityChecker,
        private val sensitiveClipboardController: SensitiveClipboardController,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CardsViewModel::class.java)) {
                return CardsViewModel(
                    repository = repository,
                    validator = validator,
                    defaultTemplateId = defaultTemplateId,
                    templateIssuerLabel = templateIssuerLabel,
                    deviceSecurityChecker = deviceSecurityChecker,
                    sensitiveClipboardController = sensitiveClipboardController,
                ) as T
            }
            throw IllegalArgumentException("Unsupported ViewModel class")
        }
    }

    private companion object {
        const val MAX_CARD_DIGITS = 19
        const val REVEALED_CARD_SECRETS_VISIBLE_MILLIS = 15_000L
    }
}

private class RecordUnavailableException : Exception("The requested record is unavailable.")

private fun CardFormUiState.withField(
    field: CardFormField,
    proposedValue: String,
): CardFormUiState = when (field) {
    CardFormField.Nickname -> copy(nickname = proposedValue)
    CardFormField.IssuerName -> copy(issuerName = proposedValue)
    CardFormField.CardNumber -> copy(
        cardNumber = proposedValue.filter(Char::isAsciiDigit).take(19),
    )
    CardFormField.ExpiryMonth -> copy(
        expiryMonth = proposedValue.filter(Char::isAsciiDigit).take(2),
    )
    CardFormField.ExpiryYear -> copy(
        expiryYear = proposedValue
            .filter(Char::isAsciiDigit)
            .let { digits -> if (digits.length > 2) digits.takeLast(2) else digits },
    )
    CardFormField.ExpiryDate -> {
        val digits = ExpiryDateTools.normalizeInputDigits(proposedValue)
        digits.let {
            copy(
                expiryMonth = it.take(2),
                expiryYear = it.drop(2),
            )
        }
    }
    CardFormField.Cvv -> proposedValue
        .filter(Char::isAsciiDigit)
        .take(4)
        .let { sanitized ->
            copy(
                cvv = sanitized,
                saveCvv = sanitized.isNotEmpty(),
                cvvRiskAcknowledged = cvvRiskAcknowledged && sanitized.isNotEmpty(),
                showCvvRiskConfirmation = false,
            )
        }
    CardFormField.Notes -> copy(notes = proposedValue)
}

private fun CardFormUiState.withSaveCvvRequest(enabled: Boolean): CardFormUiState = when {
    !enabled -> copy(
        saveCvv = false,
        cvv = "",
        cvvRiskAcknowledged = false,
        showCvvRiskConfirmation = false,
    )
    saveCvv -> this
    else -> copy(showCvvRiskConfirmation = true)
}

private fun CardFormUiState.confirmCvvRisk(): CardFormUiState = copy(
    saveCvv = true,
    cvvRiskAcknowledged = true,
    showCvvRiskConfirmation = false,
)

private fun CardFormUiState.dismissCvvRisk(): CardFormUiState = copy(
    saveCvv = false,
    cvv = "",
    cvvRiskAcknowledged = false,
    showCvvRiskConfirmation = false,
)

private fun CardFormUiState.normalizedAfterUserChange(): CardFormUiState = copy(
    validationErrors = emptySet(),
    submissionFailed = false,
)

private fun CardFormUiState.withLuhnWarning(
    validator: BankCardValidator,
): CardFormUiState {
    val validation = validator.validate(toInput())
    return copy(
        showLuhnWarning = BankCardValidationWarning.LUHN_CHECK_FAILED in validation.warnings,
    )
}

private fun CardFormUiState.toInput(): BankCardInput {
    val parsedExpiry = ExpiryDateTools.parse(expiryDateText)
    return BankCardInput(
        nickname = nickname,
        issuerName = issuerName,
        cardNumber = CardNumberTools.normalize(cardNumber).orEmpty(),
        expiryMonth = parsedExpiry?.month ?: expiryMonth.toIntOrNull() ?: 0,
        expiryYear = parsedExpiry?.year ?: 0,
        saveCvv = saveCvv,
        cvv = cvv.takeIf { saveCvv },
        cardTemplateId = cardTemplateId,
        notes = notes,
    )
}

private fun BankCardInput.toForm(): CardFormUiState = CardFormUiState(
    nickname = nickname,
    issuerName = issuerName,
    cardNumber = cardNumber,
    expiryMonth = expiryMonth.toString().padStart(2, '0'),
    expiryYear = (expiryYear % 100).toString().padStart(2, '0'),
    saveCvv = saveCvv,
    cvv = cvv.orEmpty(),
    cardTemplateId = cardTemplateId,
    notes = notes,
    validationErrors = emptySet(),
    showLuhnWarning = false,
    submissionFailed = false,
    submitting = false,
    cvvRiskAcknowledged = saveCvv,
    showCvvRiskConfirmation = false,
)

private fun PersistentCardListItem.toUiModel(): CardListItemUiModel = CardListItemUiModel(
    id = id,
    nickname = nickname,
    issuerName = issuerName,
    cardTemplateId = cardTemplateId,
    cardNetwork = cardNetwork,
)

private fun PersistentCardDetail.toUiModel(): CardDetailUiModel = CardDetailUiModel(
    nickname = nickname,
    issuerName = issuerName,
    maskedCardNumber = maskedCardNumber,
    cardTemplateId = cardTemplateId,
    notes = notes,
    cardNetwork = cardNetwork,
    cvvSaveStatus = if (cvvSaved) CvvSaveStatusUi.Saved else CvvSaveStatusUi.NotSaved,
    canRevealCardSecrets = true,
    canEdit = true,
    canDelete = true,
)

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

private fun String?.toUuidOrNull(): UUID? = try {
    this?.let(UUID::fromString)
} catch (_: IllegalArgumentException) {
    null
}
