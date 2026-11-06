package com.pdh.cardvault.security.crypto

import com.pdh.cardvault.wallet.FolderAppearance
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultFolderRecordCryptorTest {
    @Test fun customColorRoundTripsInsideAuthenticatedCiphertext() {
        val id = UUID.randomUUID()
        val dek = ByteArray(32).also(SecureRandom()::nextBytes)
        val cryptor = VaultFolderRecordCryptor()
        val payload = VaultFolderPayload(VaultFolderCollection.CARDS, "Synthetic folder", colorRgb = 0xDF7241)
        assertEquals(payload, cryptor.decrypt(id, cryptor.encrypt(id, payload, dek), dek))
        dek.fill(0)
    }

    @Test fun legacyFolderPayloadsKeepTheirParentAndDefaultColor() {
        val id = UUID.randomUUID()
        val parent = UUID.randomUUID()
        val dek = ByteArray(32).also(SecureRandom()::nextBytes)
        for (version in 1..2) {
            val magic = "CardVault/FolderPayload".toByteArray(StandardCharsets.UTF_8)
            val name = "Synthetic legacy folder".toByteArray(StandardCharsets.UTF_8)
            val plaintext = ByteBuffer.allocate(16 + magic.size + name.size + if (version >= 2) 17 else 0)
                .putInt(magic.size).put(magic).putInt(version).putInt(0).putInt(name.size).put(name)
                .apply { if (version >= 2) put(1.toByte()).putLong(parent.mostSignificantBits).putLong(parent.leastSignificantBits) }.array()
            val aad = AadEncoder.forFolder(id, version)
            val encrypted = AesGcmCipher().encrypt(plaintext, SecretKeySpec(dek, "AES"), aad)
            val restored = VaultFolderRecordCryptor().decrypt(id,
                EncryptedVaultFolderRecord(version, 1, encrypted.ciphertextCopy(), encrypted.ivCopy()), dek)
            assertEquals(if (version >= 2) parent else null, restored.parentFolderId)
            assertEquals(FolderAppearance.DEFAULT_COLOR_RGB, restored.colorRgb)
            plaintext.fill(0)
            aad.fill(0)
        }
        dek.fill(0)
    }
}
