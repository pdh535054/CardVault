package com.pdh.cardvault.security.auth

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager

enum class DeviceSecurityStatus {
    Available,
    NoSecureLockScreen,
    AuthenticationUnavailable,
}

fun interface DeviceSecurityChecker {
    fun check(): DeviceSecurityStatus
}

class AndroidDeviceSecurityChecker(
    context: Context,
) : DeviceSecurityChecker {
    private val applicationContext = context.applicationContext

    override fun check(): DeviceSecurityStatus {
        val keyguardManager = applicationContext.getSystemService(KeyguardManager::class.java)
        if (keyguardManager?.isDeviceSecure != true) {
            return DeviceSecurityStatus.NoSecureLockScreen
        }

        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return if (
            BiometricManager.from(applicationContext).canAuthenticate(authenticators) ==
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            DeviceSecurityStatus.Available
        } else {
            DeviceSecurityStatus.AuthenticationUnavailable
        }
    }
}
