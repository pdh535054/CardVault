package com.pdh.cardvault.security.crypto

import com.pdh.cardvault.security.auth.DeviceSecurityStatus
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DekKeyManagementTest {
    private val generator = DekGenerator()
    private val wrapper = DekWrapper()

    @Test
    fun generatedDekIsExactly256Bits() {
        val dek = generator.generate()
        try {
            assertEquals(32, dek.size)
        } finally {
            dek.fill(0)
        }
    }

    @Test
    fun dekWrapRoundTripsWithRandomTwelveByteIv() {
        val dek = generator.generate()
        val kekBytes = generator.generate()
        try {
            val wrapped = wrapper.wrap(dek, SecretKeySpec(kekBytes, "AES"))
            val unwrapped = wrapper.unwrap(wrapped, SecretKeySpec(kekBytes, "AES"))
            try {
                assertArrayEquals(dek, unwrapped)
                assertEquals(12, wrapped.wrappingIvCopy().size)
                assertEquals(32 + 16, wrapped.wrappedDekCopy().size)
            } finally {
                unwrapped.fill(0)
            }
        } finally {
            dek.fill(0)
            kekBytes.fill(0)
        }
    }

    @Test
    fun repeatedWrappingUsesDifferentIvAndCiphertext() {
        val dek = generator.generate()
        val kekBytes = generator.generate()
        try {
            val kek = SecretKeySpec(kekBytes, "AES")
            val first = wrapper.wrap(dek, kek)
            val second = wrapper.wrap(dek, kek)

            assertFalse(first.wrappingIvCopy().contentEquals(second.wrappingIvCopy()))
            assertFalse(first.wrappedDekCopy().contentEquals(second.wrappedDekCopy()))
        } finally {
            dek.fill(0)
            kekBytes.fill(0)
        }
    }

    @Test
    fun modifiedWrappedDekFailsAuthentication() {
        val dek = generator.generate()
        val kekBytes = generator.generate()
        try {
            val kek = SecretKeySpec(kekBytes, "AES")
            val wrapped = wrapper.wrap(dek, kek)
            val modifiedCiphertext = wrapped.wrappedDekCopy().also {
                it[it.lastIndex] = it.last().inc()
            }
            val modified = WrappedDek(
                wrapped.wrappingFormatVersion,
                wrapped.kekAliasVersion,
                modifiedCiphertext,
                wrapped.wrappingIvCopy(),
            )

            assertThrows(EncryptedDataAuthenticationException::class.java) {
                wrapper.unwrap(modified, kek)
            }
        } finally {
            dek.fill(0)
            kekBytes.fill(0)
        }
    }

    @Test
    fun wrongKekFailsAuthentication() {
        val dek = generator.generate()
        val correctKek = generator.generate()
        val wrongKek = generator.generate()
        try {
            val wrapped = wrapper.wrap(dek, SecretKeySpec(correctKek, "AES"))

            assertThrows(EncryptedDataAuthenticationException::class.java) {
                wrapper.unwrap(wrapped, SecretKeySpec(wrongKek, "AES"))
            }
        } finally {
            dek.fill(0)
            correctKek.fill(0)
            wrongKek.fill(0)
        }
    }

    @Test
    fun unknownWrappingVersionFailsClosed() {
        val dek = generator.generate()
        val kekBytes = generator.generate()
        try {
            val kek = SecretKeySpec(kekBytes, "AES")
            val wrapped = wrapper.wrap(dek, kek)
            val unknownVersion = WrappedDek(
                wrapped.wrappingFormatVersion + 1,
                wrapped.kekAliasVersion,
                wrapped.wrappedDekCopy(),
                wrapped.wrappingIvCopy(),
            )

            assertThrows(UnsupportedCryptoVersionException::class.java) {
                wrapper.unwrap(unknownVersion, kek)
            }
        } finally {
            dek.fill(0)
            kekBytes.fill(0)
        }
    }

    @Test
    fun foregroundSessionCopiesAndClearsDekMaterial() {
        val input = generator.generate()
        val session = ForegroundDekSession()
        session.load(input)
        input.fill(0)

        assertTrue(session.isLoaded())
        session.use { active -> assertFalse(active.all { it == 0.toByte() }) }
        val returnedWorkingCopy = session.use { active -> active }
        assertTrue(returnedWorkingCopy.all { it == 0.toByte() })

        session.clear()
        assertFalse(session.isLoaded())
        assertThrows(VaultKeyUnavailableException::class.java) {
            session.use<Nothing> {
                throw AssertionError("A cleared DEK session must not invoke its block.")
            }
        }
    }

    @Test
    fun insecureDeviceFailsBeforeKeystoreCreationOrLookup() {
        val manager = AndroidKeystoreKekManager(
            deviceSecurityChecker = { DeviceSecurityStatus.NoSecureLockScreen },
            keyAlias = "non-production-test-alias",
        )

        assertThrows(SecureLockScreenRequiredException::class.java) {
            manager.createOrGetForNewVault()
        }
        assertThrows(SecureLockScreenRequiredException::class.java) {
            manager.getExisting()
        }
    }
}
