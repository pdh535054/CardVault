package com.pdh.cardvault

import com.pdh.cardvault.presentation.VaultLockState
import com.pdh.cardvault.security.auth.AuthenticationAction
import com.pdh.cardvault.security.auth.AuthenticationScope
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardVaultAppTest {
    @Test
    fun visibleDetailDisposalNeverPreservesRecordAuthentication() {
        val state = VaultLockState.Authenticating(
            AuthenticationScope(AuthenticationAction.RevealCardSecrets, UUID.randomUUID()),
        )

        assertFalse(
            shouldPreserveRecordAuthenticationOnContentDisposal(
                canRenderMaskedContent = true,
                vaultLockState = state,
            ),
        )
    }

    @Test
    fun systemPromptContentDisposalPreservesOnlyRecordAuthentication() {
        val state = VaultLockState.Authenticating(
            AuthenticationScope(AuthenticationAction.RevealCardSecrets, UUID.randomUUID()),
        )

        assertTrue(
            shouldPreserveRecordAuthenticationOnContentDisposal(
                canRenderMaskedContent = false,
                vaultLockState = state,
            ),
        )
    }

    @Test
    fun startupAuthenticationContentDisposalIsNeverRecordScoped() {
        val state = VaultLockState.Authenticating(
            AuthenticationScope(AuthenticationAction.UnlockVault),
        )

        assertFalse(
            shouldPreserveRecordAuthenticationOnContentDisposal(
                canRenderMaskedContent = false,
                vaultLockState = state,
            ),
        )
    }
}
