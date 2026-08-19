package com.pdh.cardvault.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pdh.cardvault.data.FirstRunStateStore
import com.pdh.cardvault.data.StartupAuthenticationStore
import com.pdh.cardvault.security.auth.AuthenticationAction
import com.pdh.cardvault.security.auth.AuthenticationOutcome
import com.pdh.cardvault.security.auth.AuthenticationScope
import com.pdh.cardvault.security.auth.DeviceSecurityChecker
import com.pdh.cardvault.security.auth.DeviceSecurityStatus
import com.pdh.cardvault.security.auth.SystemAuthenticationRequest
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VaultLockState {
    data object Initializing : VaultLockState

    data object Locked : VaultLockState

    data class Authenticating(
        val scope: AuthenticationScope,
    ) : VaultLockState {
        override fun toString(): String = "VaultLockState.Authenticating(scope=$scope)"
    }

    data object UnlockedMasked : VaultLockState

    data object VaultUnavailable : VaultLockState

    data class SecurityUnavailable(
        val status: DeviceSecurityStatus,
    ) : VaultLockState
}

data class MainUiState(
    val showSecurityNotice: Boolean,
    val startupAuthenticationEnabled: Boolean,
    val deviceSecurityStatus: DeviceSecurityStatus,
    val vaultLockState: VaultLockState,
    val authenticationRequest: SystemAuthenticationRequest?,
    val authenticationOutcome: AuthenticationOutcome,
    val canRenderMaskedContent: Boolean,
    val screenCaptureAllowed: Boolean,
) {
    override fun toString(): String = "MainUiState(authenticationState=redacted)"
}

class MainViewModel internal constructor(
    private val firstRunStateStore: FirstRunStateStore,
    private val startupAuthenticationStore: StartupAuthenticationStore,
    private val deviceSecurityChecker: DeviceSecurityChecker,
) : ViewModel() {
    private var initialized = false
    private var isForeground = false
    // Entering the app exposes masked projections only and no longer requires authentication.
    // Keep the persisted flag disabled so an upgrade from an older build cannot restore the
    // former startup lock policy.
    private var startupAuthenticationEnabled = false
    private var deviceSecurityStatus = deviceSecurityChecker.check()
    private var activeRequest: SystemAuthenticationRequest? = null
    private var activeRequestCrossedStop = false
    private var deferredResult: DeferredAuthenticationResult? = null
    private var oneTimeAuthorization: AuthenticationScope? = null
    private var screenCaptureAllowed = false

    init {
        if (startupAuthenticationStore.isEnabled()) {
            startupAuthenticationStore.setEnabled(false)
        }
    }

    private val _uiState = MutableStateFlow(
        MainUiState(
            showSecurityNotice = !firstRunStateStore.isSecurityNoticeAcknowledged(),
            startupAuthenticationEnabled = startupAuthenticationEnabled,
            deviceSecurityStatus = deviceSecurityStatus,
            vaultLockState = VaultLockState.Initializing,
            authenticationRequest = null,
            authenticationOutcome = AuthenticationOutcome.None,
            canRenderMaskedContent = false,
            screenCaptureAllowed = false,
        ),
    )
    val uiState = _uiState.asStateFlow()

    fun onUiReady(@Suppress("UNUSED_PARAMETER") activityRecreated: Boolean) {
        if (initialized) return

        initialized = true
        isForeground = true
        oneTimeAuthorization = null
        deferredResult = null
        activeRequest = null
        activeRequestCrossedStop = false
        screenCaptureAllowed = false

        if (!refreshDeviceSecurityStatus()) return

        publish(VaultLockState.UnlockedMasked)
    }

    fun onAppForegrounded() {
        isForeground = true
        if (!initialized || !refreshDeviceSecurityStatus()) return
        val deferred = deferredResult
        if (deferred != null) {
            deferredResult = null
            applyAuthenticationResult(
                request = deferred.request,
                authenticated = deferred.authenticated,
            )
            return
        }

        val request = activeRequest
        if (request != null) {
            publish(
                vaultLockState = VaultLockState.Authenticating(request.scope),
                authenticationRequest = request,
            )
            return
        }

        when (_uiState.value.vaultLockState) {
            VaultLockState.Locked,
            VaultLockState.Initializing,
            -> publish(VaultLockState.UnlockedMasked)

            is VaultLockState.SecurityUnavailable -> publish(VaultLockState.UnlockedMasked)
            else -> publish(_uiState.value.vaultLockState)
        }
    }

    fun onAppBackgrounded() {
        if (!initialized) return

        isForeground = false
        oneTimeAuthorization = null
        screenCaptureAllowed = false
        val request = activeRequest
        activeRequestCrossedStop = request != null
        publish(
            vaultLockState = request?.let { VaultLockState.Authenticating(it.scope) }
                ?: VaultLockState.UnlockedMasked,
            authenticationRequest = request,
            authenticationOutcome = AuthenticationOutcome.None,
        )
    }

    fun retryDeviceSecurityCheck() {
        if (!initialized || !isForeground || !refreshDeviceSecurityStatus()) return
        publish(VaultLockState.UnlockedMasked)
    }

    fun onVaultAccessFailed() {
        if (!initialized) return
        activeRequest = null
        activeRequestCrossedStop = false
        deferredResult = null
        oneTimeAuthorization = null
        screenCaptureAllowed = false
        publish(
            vaultLockState = VaultLockState.VaultUnavailable,
            authenticationRequest = null,
            authenticationOutcome = AuthenticationOutcome.None,
        )
    }

    fun retryVaultAccess() {
        if (!initialized || !isForeground || !refreshDeviceSecurityStatus()) return
        publish(VaultLockState.UnlockedMasked)
    }

    fun requestUnlock() {
        if (!initialized || !isForeground || !refreshDeviceSecurityStatus()) return
        publish(VaultLockState.UnlockedMasked)
    }

    fun requestStartupAuthenticationChange(@Suppress("UNUSED_PARAMETER") enabled: Boolean) {
        startupAuthenticationStore.setEnabled(false)
        startupAuthenticationEnabled = false
        publish(VaultLockState.UnlockedMasked)
    }

    fun requestOneTimeAuthentication(action: AuthenticationAction, recordId: UUID): Boolean {
        require(action.requiresRecordId) {
            "A record-scoped authentication action is required."
        }
        if (!canStartAuthentication()) return false
        if (_uiState.value.vaultLockState != VaultLockState.UnlockedMasked) return false

        beginAuthentication(AuthenticationScope(action, recordId))
        return true
    }

    fun consumeOneTimeAuthorization(action: AuthenticationAction, recordId: UUID): Boolean {
        require(action.requiresRecordId) {
            "A record-scoped authentication action is required."
        }
        val expectedScope = AuthenticationScope(action, recordId)
        val grantedScope = oneTimeAuthorization
        oneTimeAuthorization = null
        return isForeground &&
            _uiState.value.vaultLockState == VaultLockState.UnlockedMasked &&
            grantedScope == expectedScope
    }

    fun requestVaultTransferAuthentication(action: AuthenticationAction): Boolean {
        require(action.isVaultTransferAction) {
            "A vault transfer authentication action is required."
        }
        if (!canStartAuthentication()) return false
        if (_uiState.value.vaultLockState != VaultLockState.UnlockedMasked) return false

        beginAuthentication(AuthenticationScope(action))
        return true
    }

    fun consumeVaultTransferAuthorization(action: AuthenticationAction): Boolean {
        require(action.isVaultTransferAction) {
            "A vault transfer authentication action is required."
        }
        val expectedScope = AuthenticationScope(action)
        val grantedScope = oneTimeAuthorization
        oneTimeAuthorization = null
        return isForeground &&
            _uiState.value.vaultLockState == VaultLockState.UnlockedMasked &&
            grantedScope == expectedScope
    }

    fun onAuthenticationResult(requestId: UUID, authenticated: Boolean) {
        val request = activeRequest?.takeIf { it.requestId == requestId } ?: return
        activeRequest = null

        if (!isForeground) {
            deferredResult = DeferredAuthenticationResult(request, authenticated)
            publish(
                // Keep the exact authentication scope while the result waits for ON_START.
                // Masked content remains hidden because activeRequestCrossedStop is true; for a
                // record action, the UI coordination layer can preserve only this pending scope.
                vaultLockState = VaultLockState.Authenticating(request.scope),
                authenticationRequest = null,
                authenticationOutcome = AuthenticationOutcome.None,
            )
            return
        }

        applyAuthenticationResult(
            request = request,
            authenticated = authenticated,
        )
    }

    fun acknowledgeSecurityNotice() {
        firstRunStateStore.acknowledgeSecurityNotice()
        _uiState.value = _uiState.value.copy(showSecurityNotice = false)
    }

    private fun applyAuthenticationResult(
        request: SystemAuthenticationRequest,
        authenticated: Boolean,
    ) {
        activeRequestCrossedStop = false
        if (!refreshDeviceSecurityStatus()) return

        if (!authenticated) {
            oneTimeAuthorization = null
            screenCaptureAllowed = false
            val canReturnToMaskedContent =
                request.scope.action != AuthenticationAction.UnlockVault
            publish(
                vaultLockState = if (canReturnToMaskedContent) {
                    VaultLockState.UnlockedMasked
                } else {
                    VaultLockState.Locked
                },
                authenticationOutcome = AuthenticationOutcome.FailedOrCancelled,
            )
            return
        }

        screenCaptureAllowed = true

        when (request.scope.action) {
            AuthenticationAction.EnableStartup -> {
                startupAuthenticationStore.setEnabled(false)
                startupAuthenticationEnabled = false
            }

            AuthenticationAction.DisableStartup -> {
                startupAuthenticationStore.setEnabled(false)
                startupAuthenticationEnabled = false
            }

            AuthenticationAction.RevealCardSecrets,
            AuthenticationAction.EditCard,
            AuthenticationAction.DeleteCard,
            AuthenticationAction.ExportVault,
            AuthenticationAction.ImportVault,
            AuthenticationAction.RotateSyncKey,
            -> oneTimeAuthorization = request.scope

            AuthenticationAction.UnlockVault -> Unit
        }

        publish(
            vaultLockState = VaultLockState.UnlockedMasked,
            authenticationOutcome = AuthenticationOutcome.Succeeded,
        )
    }

    private fun canStartAuthentication(): Boolean =
        initialized &&
            isForeground &&
            activeRequest == null &&
            refreshDeviceSecurityStatus()

    private fun beginAuthentication(scope: AuthenticationScope) {
        oneTimeAuthorization = null
        deferredResult = null
        screenCaptureAllowed = false
        val request = SystemAuthenticationRequest(
            requestId = UUID.randomUUID(),
            scope = scope,
        )
        activeRequest = request
        activeRequestCrossedStop = false
        publish(
            vaultLockState = VaultLockState.Authenticating(scope),
            authenticationRequest = request,
            authenticationOutcome = AuthenticationOutcome.None,
        )
    }

    private fun refreshDeviceSecurityStatus(): Boolean {
        deviceSecurityStatus = deviceSecurityChecker.check()
        if (deviceSecurityStatus == DeviceSecurityStatus.Available) return true

        activeRequest = null
        activeRequestCrossedStop = false
        deferredResult = null
        oneTimeAuthorization = null
        screenCaptureAllowed = false
        publish(
            vaultLockState = VaultLockState.SecurityUnavailable(deviceSecurityStatus),
            authenticationRequest = null,
            authenticationOutcome = AuthenticationOutcome.None,
        )
        return false
    }

    private fun publish(
        vaultLockState: VaultLockState,
        authenticationRequest: SystemAuthenticationRequest? = activeRequest,
        authenticationOutcome: AuthenticationOutcome = _uiState.value.authenticationOutcome,
    ) {
        val canRenderMaskedContent = when (vaultLockState) {
            VaultLockState.UnlockedMasked -> true
            is VaultLockState.Authenticating ->
                vaultLockState.scope.action != AuthenticationAction.UnlockVault &&
                    !activeRequestCrossedStop

            else -> false
        }
        _uiState.value = _uiState.value.copy(
            startupAuthenticationEnabled = startupAuthenticationEnabled,
            deviceSecurityStatus = deviceSecurityStatus,
            vaultLockState = vaultLockState,
            authenticationRequest = authenticationRequest,
            authenticationOutcome = authenticationOutcome,
            canRenderMaskedContent = canRenderMaskedContent,
            screenCaptureAllowed = isForeground && screenCaptureAllowed,
        )
    }

    class Factory(
        private val firstRunStateStore: FirstRunStateStore,
        private val startupAuthenticationStore: StartupAuthenticationStore,
        private val deviceSecurityChecker: DeviceSecurityChecker,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(
                    firstRunStateStore,
                    startupAuthenticationStore,
                    deviceSecurityChecker,
                ) as T
            }
            throw IllegalArgumentException("Unsupported ViewModel class")
        }
    }
}

private data class DeferredAuthenticationResult(
    val request: SystemAuthenticationRequest,
    val authenticated: Boolean,
) {
    override fun toString(): String = "DeferredAuthenticationResult(redacted)"
}
