package com.pdh.cardvault.security.crypto

import android.app.KeyguardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pdh.cardvault.security.auth.DeviceSecurityStatus
import java.security.KeyStore
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreKekManagerTest {
    private val alias = "com.pdh.cardvault.test.kek.${UUID.randomUUID()}"
    private lateinit var keyStore: KeyStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.deleteEntry(alias)
    }

    @After
    fun tearDown() {
        if (::keyStore.isInitialized) keyStore.deleteEntry(alias)
    }

    @Test
    fun generatedKekIsNonExportableAndWrapsDek() {
        val manager = AndroidKeystoreKekManager(
            deviceSecurityChecker = { DeviceSecurityStatus.Available },
            keyAlias = alias,
        )
        val kek = manager.createOrGetForNewVault()
        val dek = DekGenerator().generate()
        try {
            assertEquals("AES", kek.algorithm)
            assertNull(kek.encoded)
            val wrapper = DekWrapper()
            val wrapped = wrapper.wrap(dek, kek)
            val unwrapped = wrapper.unwrap(wrapped, manager.getExisting())
            try {
                assertArrayEquals(dek, unwrapped)
            } finally {
                unwrapped.fill(0)
            }
        } finally {
            dek.fill(0)
        }
    }

    @Test
    fun missingExistingKekFailsWithoutCreatingReplacement() {
        val manager = AndroidKeystoreKekManager(
            deviceSecurityChecker = { DeviceSecurityStatus.Available },
            keyAlias = alias,
        )

        assertThrows(VaultKeyUnavailableException::class.java) {
            manager.getExisting()
        }
        assertNull(keyStore.getEntry(alias, null))
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
