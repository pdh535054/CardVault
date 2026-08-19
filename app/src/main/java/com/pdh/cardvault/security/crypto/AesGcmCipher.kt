package com.pdh.cardvault.security.crypto

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class AesGcmCipher(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun encrypt(plaintext: ByteArray, key: SecretKey, aad: ByteArray): AeadEnvelope {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, secureRandom)
            val iv = cipher.iv?.copyOf()
            if (iv == null || iv.size != GCM_IV_LENGTH_BYTES) {
                iv?.fill(0)
                throw CryptoOperationException()
            }
            cipher.updateAAD(aad)
            AeadEnvelope(cipher.doFinal(plaintext), iv)
        } catch (_: UserNotAuthenticatedException) {
            throw DeviceAuthenticationRequiredException()
        } catch (_: KeyPermanentlyInvalidatedException) {
            throw VaultKeyInvalidatedException()
        } catch (_: GeneralSecurityException) {
            throw CryptoOperationException()
        } catch (_: ProviderException) {
            throw CryptoOperationException()
        }
    }

    fun decrypt(envelope: AeadEnvelope, key: SecretKey, aad: ByteArray): ByteArray {
        val iv = envelope.ivCopy()
        val ciphertext = envelope.ciphertextCopy()
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
            )
            cipher.updateAAD(aad)
            cipher.doFinal(ciphertext)
        } catch (_: AEADBadTagException) {
            throw EncryptedDataAuthenticationException()
        } catch (_: UserNotAuthenticatedException) {
            throw DeviceAuthenticationRequiredException()
        } catch (_: KeyPermanentlyInvalidatedException) {
            throw VaultKeyInvalidatedException()
        } catch (_: GeneralSecurityException) {
            throw CryptoOperationException()
        } catch (_: ProviderException) {
            throw CryptoOperationException()
        } finally {
            iv.fill(0)
            ciphertext.fill(0)
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
