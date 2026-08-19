package com.pdh.cardvault.presentation

import com.pdh.cardvault.security.auth.AuthenticationAction

enum class VaultPairingState {
    Unpaired,
    Paired,
}

enum class VaultTransferMessageKind {
    Success,
    Information,
    Error,
}

data class VaultTransferMessage(
    val text: String,
    val kind: VaultTransferMessageKind,
)

data class VaultTransferAuthenticationRequest(
    val requestId: Long,
    val action: AuthenticationAction,
) {
    init {
        require(action.isVaultTransferAction)
    }

    override fun toString(): String = "VaultTransferAuthenticationRequest(redacted)"
}

data class VaultTransferUiState(
    val pairingState: VaultPairingState = VaultPairingState.Unpaired,
    val keyEpoch: Int? = null,
    val busy: Boolean = false,
    val preparedFileName: String? = null,
    val pairingCode: String? = null,
    val importNeedsPairingCode: Boolean = false,
    val pairingCodeInput: String = "",
    val message: VaultTransferMessage? = null,
    val authenticationRequest: VaultTransferAuthenticationRequest? = null,
    val importedSnapshotRevision: Long = 0L,
) {
    override fun toString(): String =
        "VaultTransferUiState(pairing=$pairingState, keyEpoch=$keyEpoch, " +
            "busy=$busy, transferSecrets=redacted)"
}
