package com.pdh.cardvault.desktop.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsHelloAuthenticatorTest {
    @Test
    fun `only verified outcome authorizes a sensitive action`() = runBlocking {
        UserConsentVerificationOutcome.entries.forEach { outcome ->
            val authenticator = WindowsHelloAuthenticator(
                verifier = UserConsentVerifier { outcome },
                dispatcher = Dispatchers.Unconfined,
            )

            val accepted = authenticator.authenticate(SensitiveAction.RevealCardSecrets)

            if (outcome == UserConsentVerificationOutcome.Verified) assertTrue(accepted)
            else assertFalse(accepted)
        }
    }

    @Test
    fun `verifier failure is fail closed`() = runBlocking {
        val authenticator = WindowsHelloAuthenticator(
            verifier = UserConsentVerifier { error("synthetic verifier failure") },
            dispatcher = Dispatchers.Unconfined,
        )

        assertFalse(authenticator.authenticate(SensitiveAction.ExportVault))
    }

    @Test
    fun `relative system root cannot launch a process`() {
        val verifier = PowerShellUserConsentVerifier(systemRoot = "relative-system-root")

        assertTrue(
            verifier.requestVerification("synthetic prompt") == UserConsentVerificationOutcome.Unavailable,
        )
    }
}
