package com.pdh.cardvault.security.auth

import java.util.UUID

enum class AuthenticationAction {
    EnableStartup,
    DisableStartup,
    UnlockVault,
    RevealCardSecrets,
    EditCard,
    DeleteCard,
    ExportVault,
    ImportVault,
    RotateSyncKey,
    ;

    val requiresRecordId: Boolean
        get() = when (this) {
            RevealCardSecrets,
            EditCard,
            DeleteCard,
            -> true

            EnableStartup,
            DisableStartup,
            UnlockVault,
            ExportVault,
            ImportVault,
            RotateSyncKey,
            -> false
        }

    val isVaultTransferAction: Boolean
        get() = this == ExportVault || this == ImportVault || this == RotateSyncKey
}

data class AuthenticationScope(
    val action: AuthenticationAction,
    val recordId: UUID? = null,
) {
    init {
        require(action.requiresRecordId == (recordId != null)) {
            "Authentication scope does not match the requested action."
        }
    }

    override fun toString(): String =
        "AuthenticationScope(action=$action, recordId=redacted)"
}

data class SystemAuthenticationRequest(
    val requestId: UUID,
    val scope: AuthenticationScope,
) {
    override fun toString(): String =
        "SystemAuthenticationRequest(requestId=redacted, scope=$scope)"
}

enum class AuthenticationOutcome {
    None,
    Succeeded,
    FailedOrCancelled,
}
