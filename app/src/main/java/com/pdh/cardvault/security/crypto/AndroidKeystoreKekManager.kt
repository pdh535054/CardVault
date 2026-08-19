package com.pdh.cardvault.security.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import com.pdh.cardvault.security.auth.AndroidDeviceSecurityChecker
import com.pdh.cardvault.security.auth.DeviceSecurityChecker
import com.pdh.cardvault.security.auth.DeviceSecurityStatus
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal interface KekManager {
    fun createOrGetForNewVault(): SecretKey

    fun getExisting(): SecretKey
}

internal class AndroidKeystoreKekManager(
    private val deviceSecurityChecker: DeviceSecurityChecker,
    private val keyAlias: String,
) : KekManager {
    constructor(context: Context) : this(
        deviceSecurityChecker = AndroidDeviceSecurityChecker(context.applicationContext),
        keyAlias = PRODUCTION_KEY_ALIAS,
    )

    override fun createOrGetForNewVault(): SecretKey = synchronized(KEY_CREATION_LOCK) {
        ensureDeviceSecurity()
        val keyStore = loadKeyStore()
        existingKey(keyStore)?.let { return@synchronized it }

        try {
            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE,
            )
            generator.init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setKeySize(KEK_SIZE_BITS)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setUnlockedDeviceRequired(true)
                    .build(),
            )
            generator.generateKey()
        } catch (_: UserNotAuthenticatedException) {
            throw DeviceAuthenticationRequiredException()
        } catch (_: KeyPermanentlyInvalidatedException) {
            throw VaultKeyInvalidatedException()
        } catch (_: GeneralSecurityException) {
            throw VaultKeyUnavailableException()
        } catch (_: ProviderException) {
            throw VaultKeyUnavailableException()
        }
    }

    @Synchronized
    override fun getExisting(): SecretKey {
        ensureDeviceSecurity()
        return existingKey(loadKeyStore()) ?: throw VaultKeyUnavailableException()
    }

    private fun existingKey(keyStore: KeyStore): SecretKey? = try {
        val entry = keyStore.getEntry(keyAlias, null)
        when (entry) {
            null -> null
            is KeyStore.SecretKeyEntry -> entry.secretKey
            else -> throw VaultKeyUnavailableException()
        }
    } catch (_: UserNotAuthenticatedException) {
        throw DeviceAuthenticationRequiredException()
    } catch (_: KeyPermanentlyInvalidatedException) {
        throw VaultKeyInvalidatedException()
    } catch (_: GeneralSecurityException) {
        throw VaultKeyUnavailableException()
    } catch (_: ProviderException) {
        throw VaultKeyUnavailableException()
    }

    private fun loadKeyStore(): KeyStore = try {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    } catch (_: GeneralSecurityException) {
        throw VaultKeyUnavailableException()
    } catch (_: java.io.IOException) {
        throw VaultKeyUnavailableException()
    } catch (_: ProviderException) {
        throw VaultKeyUnavailableException()
    }

    private fun ensureDeviceSecurity() {
        if (deviceSecurityChecker.check() != DeviceSecurityStatus.Available) {
            throw SecureLockScreenRequiredException()
        }
    }

    companion object {
        internal const val PRODUCTION_KEY_ALIAS = "com.pdh.cardvault.kek.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEK_SIZE_BITS = 256
        private val KEY_CREATION_LOCK = Any()
    }
}
