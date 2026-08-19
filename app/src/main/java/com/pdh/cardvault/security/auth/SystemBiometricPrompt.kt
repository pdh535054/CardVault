package com.pdh.cardvault.security.auth

import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.pdh.cardvault.R
import java.util.UUID

class SystemBiometricPrompt(
    activity: FragmentActivity,
    private val onResult: (requestId: UUID, authenticated: Boolean) -> Unit,
) {
    private var activeRequestId: UUID? = null

    private val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult,
            ) {
                complete(authenticated = true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                complete(authenticated = false)
            }

            override fun onAuthenticationFailed() = Unit
        },
    )

    private val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.authentication_prompt_title))
        .setSubtitle(activity.getString(R.string.authentication_prompt_subtitle))
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
        .build()

    fun authenticate(request: SystemAuthenticationRequest) {
        if (activeRequestId == request.requestId) return
        check(activeRequestId == null) { "A system authentication request is already active." }

        activeRequestId = request.requestId
        prompt.authenticate(promptInfo)
    }

    fun cancel() {
        activeRequestId = null
        prompt.cancelAuthentication()
    }

    private fun complete(authenticated: Boolean) {
        val completedRequestId = activeRequestId ?: return
        activeRequestId = null
        onResult(completedRequestId, authenticated)
    }
}
