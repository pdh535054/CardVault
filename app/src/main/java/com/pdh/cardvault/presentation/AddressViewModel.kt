package com.pdh.cardvault.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pdh.cardvault.data.room.PersistentAddressDetail
import com.pdh.cardvault.data.room.PersistentAddressListItem
import com.pdh.cardvault.data.room.PersistentAddressRepository
import com.pdh.cardvault.domain.model.AddressInput
import com.pdh.cardvault.domain.validation.AddressValidationError
import com.pdh.cardvault.domain.validation.AddressValidationException
import com.pdh.cardvault.domain.validation.AddressValidator
import com.pdh.cardvault.security.clipboard.SensitiveClipboardController
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AddressFormField {
    Nickname,
    DetailedAddress,
    City,
    Other,
    PostalCode,
    Country,
}

data class AddressFormUiState(
    val nickname: String,
    val detailedAddress: String,
    val city: String,
    val other: String,
    val postalCode: String,
    val country: String,
    val cardTemplateId: String,
    val validationErrors: Set<AddressValidationError>,
    val submitting: Boolean,
) {
    override fun toString(): String = "AddressFormUiState(sensitiveFields=redacted)"
}

data class AddressListItemUiModel(
    val id: UUID,
    val nickname: String,
    val cardTemplateId: String,
) {
    override fun toString(): String = "AddressListItemUiModel(sensitiveFields=redacted)"
}

data class AddressDetailUiModel(
    val nickname: String,
    val detailedAddress: String,
    val city: String,
    val other: String,
    val postalCode: String,
    val country: String,
    val cardTemplateId: String,
) {
    fun copyText(): String = listOf(detailedAddress, other, city, postalCode, country)
        .filter(String::isNotBlank)
        .joinToString(separator = "\n")

    override fun toString(): String = "AddressDetailUiModel(sensitiveFields=redacted)"
}

sealed interface AddressDetailUiState {
    data object Hidden : AddressDetailUiState
    data object Loading : AddressDetailUiState
    data object NotFound : AddressDetailUiState

    data class Loaded(
        val recordId: UUID,
        val address: AddressDetailUiModel,
    ) : AddressDetailUiState {
        override fun toString(): String = "AddressDetailUiState.Loaded(sensitiveFields=redacted)"
    }
}

sealed interface AddressEditUiState {
    data object Hidden : AddressEditUiState
    data object Loading : AddressEditUiState
    data object Unavailable : AddressEditUiState

    data class Ready(
        val recordId: UUID,
        val form: AddressFormUiState,
    ) : AddressEditUiState {
        override fun toString(): String = "AddressEditUiState.Ready(sensitiveFields=redacted)"
    }
}

enum class AddressOperationMessage {
    None,
    StorageFailed,
    AddressCopied,
    ClipboardFailed,
    RecordDeleted,
}

sealed interface AddressNavigationEvent {
    data object AddSaved : AddressNavigationEvent
    data object EditSaved : AddressNavigationEvent
    data object DeleteCompleted : AddressNavigationEvent
}

data class AddressesUiState(
    val vaultContentState: VaultContentState,
    val addresses: List<AddressListItemUiModel>,
    val sortingInProgress: Boolean,
    val form: AddressFormUiState,
    val detail: AddressDetailUiState,
    val edit: AddressEditUiState,
    val operationMessage: AddressOperationMessage,
    val navigationEvent: AddressNavigationEvent?,
) {
    override fun toString(): String = "AddressesUiState(sensitiveFields=redacted)"
}

class AddressViewModel internal constructor(
    private val repository: PersistentAddressRepository,
    private val validator: AddressValidator,
    private val defaultTemplateId: String,
    private val sensitiveClipboardController: SensitiveClipboardController,
    private val externalScope: CoroutineScope? = null,
) : ViewModel() {
    private var lifecycleEpoch = 0L
    private var activeDetailId: UUID? = null
    private var loadJob: Job? = null
    private var addJob: Job? = null
    private var detailJob: Job? = null
    private var editLoadJob: Job? = null
    private var editSaveJob: Job? = null
    private var deleteJob: Job? = null
    private var sortJob: Job? = null
    private val workScope: CoroutineScope
        get() = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(
        AddressesUiState(
            vaultContentState = VaultContentState.Locked,
            addresses = emptyList(),
            sortingInProgress = false,
            form = emptyForm(),
            detail = AddressDetailUiState.Hidden,
            edit = AddressEditUiState.Hidden,
            operationMessage = AddressOperationMessage.None,
            navigationEvent = null,
        ),
    )
    val uiState = _uiState.asStateFlow()

    fun onVaultAccessAllowed() {
        if (!repository.isUnlocked()) return
        if (_uiState.value.vaultContentState == VaultContentState.Ready) return
        val expectedEpoch = lifecycleEpoch
        loadJob?.cancel()
        _uiState.update { it.copy(vaultContentState = VaultContentState.Loading) }
        loadJob = workScope.launch {
            try {
                val addresses = repository.getAddressList().map(PersistentAddressListItem::toUiModel)
                if (lifecycleEpoch != expectedEpoch) return@launch
                _uiState.update {
                    it.copy(
                        vaultContentState = VaultContentState.Ready,
                        addresses = addresses,
                        sortingInProgress = false,
                        operationMessage = AddressOperationMessage.None,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                if (lifecycleEpoch == expectedEpoch) {
                    _uiState.update {
                        it.copy(
                            vaultContentState = VaultContentState.Unavailable,
                            operationMessage = AddressOperationMessage.StorageFailed,
                        )
                    }
                }
            }
        }
    }

    fun onVaultAccessRevoked() {
        lifecycleEpoch += 1L
        cancelJobs()
        sensitiveClipboardController.clearCardNumberIfOwned()
        activeDetailId = null
        _uiState.value = AddressesUiState(
            vaultContentState = VaultContentState.Locked,
            addresses = emptyList(),
            sortingInProgress = false,
            form = emptyForm(),
            detail = AddressDetailUiState.Hidden,
            edit = AddressEditUiState.Hidden,
            operationMessage = AddressOperationMessage.None,
            navigationEvent = null,
        )
    }

    fun onAppBackgrounded() {
        // Preserve unsaved address and edit drafts across a short or long app switch. The
        // clipboard is the only externally exposed transient value owned by this screen.
        try {
            sensitiveClipboardController.clearCardNumberIfOwned()
        } catch (_: RuntimeException) {
            // Draft preservation must not depend on clipboard service availability.
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
        loadJob?.cancel()
        loadJob = workScope.launch {
            try {
                val addresses = repository.getAddressList().map(PersistentAddressListItem::toUiModel)
                if (!isCurrentReadyEpoch(expectedEpoch)) return@launch
                activeDetailId = null
                _uiState.update { current ->
                    current.copy(
                        addresses = addresses,
                        sortingInProgress = false,
                        detail = AddressDetailUiState.Hidden,
                        edit = AddressEditUiState.Hidden,
                        operationMessage = AddressOperationMessage.None,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                if (isCurrentReadyEpoch(expectedEpoch)) {
                    _uiState.update {
                        it.copy(operationMessage = AddressOperationMessage.StorageFailed)
                    }
                }
            }
        }
    }

    fun updateField(field: AddressFormField, value: String) {
        _uiState.update { current ->
            if (current.form.submitting) return@update current
            val form = current.form.withField(field, value)
            current.copy(
                form = form.copy(validationErrors = emptySet()),
                operationMessage = AddressOperationMessage.None,
            )
        }
    }

    fun selectTemplate(templateId: String) {
        _uiState.update { current ->
            if (current.form.submitting) current else current.copy(
                form = current.form.copy(
                    cardTemplateId = templateId,
                    validationErrors = emptySet(),
                ),
            )
        }
    }

    fun submitAddress(): Boolean {
        val state = _uiState.value
        if (
            state.vaultContentState != VaultContentState.Ready ||
            state.form.submitting ||
            !repository.isUnlocked()
        ) {
            return false
        }
        val validation = validator.validate(state.form.toInput())
        if (!validation.isValid) {
            _uiState.update {
                it.copy(form = state.form.copy(validationErrors = validation.errors))
            }
            return false
        }
        val expectedEpoch = lifecycleEpoch
        _uiState.update { it.copy(form = state.form.copy(submitting = true)) }
        addJob = workScope.launch {
            try {
                repository.addAddress(validation.normalizedInput)
                val addresses = repository.getAddressList().map(PersistentAddressListItem::toUiModel)
                if (!isCurrentReadyEpoch(expectedEpoch)) return@launch
                _uiState.update {
                    it.copy(
                        addresses = addresses,
                        form = emptyForm(),
                        navigationEvent = AddressNavigationEvent.AddSaved,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: AddressValidationException) {
                if (!isCurrentReadyEpoch(expectedEpoch)) return@launch
                _uiState.update {
                    it.copy(
                        form = state.form.copy(
                            validationErrors = exception.validationErrors,
                            submitting = false,
                        ),
                    )
                }
            } catch (_: Exception) {
                if (!isCurrentReadyEpoch(expectedEpoch)) return@launch
                _uiState.update {
                    it.copy(
                        form = state.form.copy(submitting = false),
                        operationMessage = AddressOperationMessage.StorageFailed,
                    )
                }
            }
        }
        return true
    }

    fun clearForm() {
        if (_uiState.value.form.submitting) return
        _uiState.update { it.copy(form = emptyForm()) }
    }

    fun loadDetail(recordId: String?) {
        val id = recordId.toUuidOrNull()
        if (id == null || _uiState.value.vaultContentState != VaultContentState.Ready) {
            _uiState.update { it.copy(detail = AddressDetailUiState.NotFound) }
            return
        }
        activeDetailId = id
        val expectedEpoch = lifecycleEpoch
        detailJob?.cancel()
        _uiState.update { it.copy(detail = AddressDetailUiState.Loading) }
        detailJob = workScope.launch {
            try {
                val detail = repository.getAddressDetail(id)
                if (!isCurrentReadyEpoch(expectedEpoch) || activeDetailId != id) return@launch
                _uiState.update {
                    it.copy(
                        detail = detail?.let { value ->
                            AddressDetailUiState.Loaded(id, value.toUiModel())
                        } ?: AddressDetailUiState.NotFound,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                if (isCurrentReadyEpoch(expectedEpoch)) {
                    _uiState.update {
                        it.copy(
                            detail = AddressDetailUiState.NotFound,
                            operationMessage = AddressOperationMessage.StorageFailed,
                        )
                    }
                }
            }
        }
    }

    fun clearDetail() {
        detailJob?.cancel()
        detailJob = null
        activeDetailId = null
        sensitiveClipboardController.clearCardNumberIfOwned()
        _uiState.update { it.copy(detail = AddressDetailUiState.Hidden) }
    }

    fun loadEdit(recordId: String?) {
        val id = recordId.toUuidOrNull()
        if (id == null || _uiState.value.vaultContentState != VaultContentState.Ready) {
            _uiState.update { it.copy(edit = AddressEditUiState.Unavailable) }
            return
        }
        val expectedEpoch = lifecycleEpoch
        editLoadJob?.cancel()
        _uiState.update {
            it.copy(
                edit = AddressEditUiState.Loading,
                operationMessage = AddressOperationMessage.None,
            )
        }
        editLoadJob = workScope.launch {
            try {
                val input = repository.getAddressEditInput(id)
                if (!isCurrentReadyEpoch(expectedEpoch)) return@launch
                _uiState.update {
                    it.copy(
                        edit = input?.let { value ->
                            AddressEditUiState.Ready(id, value.toForm())
                        } ?: AddressEditUiState.Unavailable,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                if (isCurrentReadyEpoch(expectedEpoch)) {
                    _uiState.update {
                        it.copy(
                            edit = AddressEditUiState.Unavailable,
                            operationMessage = AddressOperationMessage.StorageFailed,
                        )
                    }
                }
            }
        }
    }

    fun updateEditField(field: AddressFormField, value: String) {
        _uiState.update { current ->
            val editState = current.edit as? AddressEditUiState.Ready ?: return@update current
            if (editState.form.submitting) return@update current
            current.copy(
                edit = editState.copy(
                    form = editState.form.withField(field, value).copy(validationErrors = emptySet()),
                ),
                operationMessage = AddressOperationMessage.None,
            )
        }
    }

    fun selectEditTemplate(templateId: String) {
        _uiState.update { current ->
            val editState = current.edit as? AddressEditUiState.Ready ?: return@update current
            if (editState.form.submitting) return@update current
            current.copy(
                edit = editState.copy(
                    form = editState.form.copy(
                        cardTemplateId = templateId,
                        validationErrors = emptySet(),
                    ),
                ),
            )
        }
    }

    fun submitEdit(): Boolean {
        val editState = _uiState.value.edit as? AddressEditUiState.Ready ?: return false
        if (
            _uiState.value.vaultContentState != VaultContentState.Ready ||
            editState.form.submitting ||
            !repository.isUnlocked()
        ) {
            return false
        }
        val validation = validator.validate(editState.form.toInput())
        if (!validation.isValid) {
            _uiState.update { current ->
                current.copy(
                    edit = editState.copy(
                        form = editState.form.copy(validationErrors = validation.errors),
                    ),
                )
            }
            return false
        }
        val expectedEpoch = lifecycleEpoch
        _uiState.update { current ->
            current.copy(
                edit = editState.copy(form = editState.form.copy(submitting = true)),
            )
        }
        editSaveJob = workScope.launch {
            try {
                if (!repository.updateAddress(editState.recordId, validation.normalizedInput)) {
                    throw IllegalStateException()
                }
                val addresses = repository.getAddressList().map(PersistentAddressListItem::toUiModel)
                val detail = repository.getAddressDetail(editState.recordId)
                if (!isCurrentReadyEpoch(expectedEpoch)) return@launch
                _uiState.update {
                    it.copy(
                        addresses = addresses,
                        detail = detail?.let { value ->
                            AddressDetailUiState.Loaded(editState.recordId, value.toUiModel())
                        } ?: AddressDetailUiState.NotFound,
                        edit = AddressEditUiState.Hidden,
                        operationMessage = AddressOperationMessage.None,
                        navigationEvent = AddressNavigationEvent.EditSaved,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                if (!isCurrentReadyEpoch(expectedEpoch)) return@launch
                _uiState.update { current ->
                    val currentEdit = current.edit as? AddressEditUiState.Ready
                    current.copy(
                        edit = currentEdit?.copy(
                            form = currentEdit.form.copy(submitting = false),
                        ) ?: AddressEditUiState.Unavailable,
                        operationMessage = AddressOperationMessage.StorageFailed,
                    )
                }
            }
        }
        return true
    }

    fun clearEditDraft() {
        val editState = _uiState.value.edit
        if (editState is AddressEditUiState.Ready && editState.form.submitting) return
        editLoadJob?.cancel()
        editSaveJob?.cancel()
        editLoadJob = null
        editSaveJob = null
        _uiState.update {
            it.copy(
                edit = AddressEditUiState.Hidden,
                operationMessage = AddressOperationMessage.None,
            )
        }
    }

    fun copyAddress(recordId: UUID): Boolean {
        val loaded = (_uiState.value.detail as? AddressDetailUiState.Loaded)
            ?.takeIf { it.recordId == recordId }
            ?: return false
        val copied = sensitiveClipboardController.copySensitiveText(loaded.address.copyText())
        _uiState.update {
            it.copy(
                operationMessage = if (copied) {
                    AddressOperationMessage.AddressCopied
                } else {
                    AddressOperationMessage.ClipboardFailed
                },
            )
        }
        return copied
    }

    fun deleteAddress(recordId: UUID): Boolean {
        if (!isCurrentReadyEpoch(lifecycleEpoch) || activeDetailId != recordId) return false
        val expectedEpoch = lifecycleEpoch
        deleteJob = workScope.launch {
            try {
                if (!repository.deleteAddress(recordId)) throw IllegalStateException()
                val addresses = repository.getAddressList().map(PersistentAddressListItem::toUiModel)
                if (!isCurrentReadyEpoch(expectedEpoch)) return@launch
                activeDetailId = null
                _uiState.update {
                    it.copy(
                        addresses = addresses,
                        detail = AddressDetailUiState.Hidden,
                        operationMessage = AddressOperationMessage.RecordDeleted,
                        navigationEvent = AddressNavigationEvent.DeleteCompleted,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                if (isCurrentReadyEpoch(expectedEpoch)) {
                    _uiState.update { it.copy(operationMessage = AddressOperationMessage.StorageFailed) }
                }
            }
        }
        return true
    }

    /** Persists one complete drag result atomically instead of writing on every crossed item. */
    fun reorderAddresses(orderedIds: List<UUID>): Boolean {
        val state = _uiState.value
        if (
            state.sortingInProgress ||
            state.vaultContentState != VaultContentState.Ready ||
            orderedIds.size != state.addresses.size ||
            orderedIds.distinct().size != orderedIds.size ||
            orderedIds.toSet() != state.addresses.map(AddressListItemUiModel::id).toSet()
        ) {
            return false
        }
        val addressesById = state.addresses.associateBy(AddressListItemUiModel::id)
        val reordered = orderedIds.map(addressesById::getValue)
        if (reordered == state.addresses) return false

        val original = state.addresses
        _uiState.update { current ->
            current.copy(
                addresses = reordered,
                sortingInProgress = true,
                operationMessage = AddressOperationMessage.None,
            )
        }
        val expectedEpoch = lifecycleEpoch
        sortJob = workScope.launch {
            try {
                repository.reorderAddresses(reordered.map(AddressListItemUiModel::id))
                val persisted = repository.getAddressList().map(PersistentAddressListItem::toUiModel)
                if (!isCurrentReadyEpoch(expectedEpoch)) return@launch
                _uiState.update { current ->
                    current.copy(addresses = persisted, sortingInProgress = false)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                if (!isCurrentReadyEpoch(expectedEpoch)) return@launch
                _uiState.update { current ->
                    current.copy(
                        addresses = original,
                        sortingInProgress = false,
                        operationMessage = AddressOperationMessage.StorageFailed,
                    )
                }
            }
        }
        return true
    }

    fun onNavigationEventHandled(event: AddressNavigationEvent) {
        _uiState.update { current ->
            if (current.navigationEvent == event) current.copy(navigationEvent = null) else current
        }
    }

    private fun emptyForm(): AddressFormUiState = AddressFormUiState(
        nickname = "",
        detailedAddress = "",
        city = "",
        other = "",
        postalCode = "",
        country = "",
        cardTemplateId = defaultTemplateId,
        validationErrors = emptySet(),
        submitting = false,
    )

    private fun isCurrentReadyEpoch(epoch: Long): Boolean =
        lifecycleEpoch == epoch &&
            _uiState.value.vaultContentState == VaultContentState.Ready &&
            repository.isUnlocked()

    private fun cancelJobs() {
        listOf(loadJob, addJob, detailJob, editLoadJob, editSaveJob, deleteJob, sortJob).forEach {
            it?.cancel()
        }
        loadJob = null
        addJob = null
        detailJob = null
        editLoadJob = null
        editSaveJob = null
        deleteJob = null
        sortJob = null
    }

    override fun onCleared() {
        cancelJobs()
        sensitiveClipboardController.clearCardNumberIfOwned()
        super.onCleared()
    }

    class Factory(
        private val repository: PersistentAddressRepository,
        private val validator: AddressValidator,
        private val defaultTemplateId: String,
        private val sensitiveClipboardController: SensitiveClipboardController,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AddressViewModel::class.java)) {
                return AddressViewModel(
                    repository,
                    validator,
                    defaultTemplateId,
                    sensitiveClipboardController,
                ) as T
            }
            throw IllegalArgumentException("Unsupported ViewModel class")
        }
    }
}

private fun AddressFormUiState.toInput(): AddressInput = AddressInput(
    nickname = nickname,
    detailedAddress = detailedAddress,
    city = city,
    other = other,
    postalCode = postalCode,
    country = country,
    cardTemplateId = cardTemplateId,
)

private fun PersistentAddressListItem.toUiModel(): AddressListItemUiModel =
    AddressListItemUiModel(id, nickname, cardTemplateId)

private fun PersistentAddressDetail.toUiModel(): AddressDetailUiModel = AddressDetailUiModel(
    nickname,
    detailedAddress,
    city,
    other,
    postalCode,
    country,
    cardTemplateId,
)

private fun AddressInput.toForm(): AddressFormUiState = AddressFormUiState(
    nickname = nickname,
    detailedAddress = detailedAddress,
    city = city,
    other = other,
    postalCode = postalCode,
    country = country,
    cardTemplateId = cardTemplateId,
    validationErrors = emptySet(),
    submitting = false,
)

private fun AddressFormUiState.withField(
    field: AddressFormField,
    value: String,
): AddressFormUiState = when (field) {
    AddressFormField.Nickname -> copy(nickname = value.take(50))
    AddressFormField.DetailedAddress -> copy(detailedAddress = value.take(500))
    AddressFormField.City -> copy(city = value.take(100))
    AddressFormField.Other -> copy(other = value.take(200))
    AddressFormField.PostalCode -> copy(postalCode = value.take(20))
    AddressFormField.Country -> copy(country = value.take(100))
}

private fun String?.toUuidOrNull(): UUID? = try {
    this?.let(UUID::fromString)
} catch (_: IllegalArgumentException) {
    null
}
