package com.pdh.cardvault.desktop.data

import com.pdh.cardvault.desktop.model.CardCoverStyle
import com.pdh.cardvault.desktop.model.DesktopCard
import com.pdh.cardvault.desktop.model.DesktopAddress
import com.pdh.cardvault.desktop.model.DesktopFolderKind
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EncryptedDesktopVaultTest {
    @Test
    fun `opening an empty vault verifies and persists the protected local key`() {
        val directory = Files.createTempDirectory("cardvault-empty-key")
        val vault = EncryptedDesktopVault(directory, XorTestProtector())

        assertTrue(vault.load().cards.isEmpty())
        assertTrue(Files.isRegularFile(directory.resolve("vault.key")))
        assertFalse(Files.exists(directory.resolve("vault.data")))
    }

    private val fixedClock = Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `encrypted vault round trips without plaintext leakage`() {
        val directory = Files.createTempDirectory("cardvault-desktop-test")
        val vault = EncryptedDesktopVault(directory, XorTestProtector())
        val card = syntheticCard()
        val address = syntheticAddress()
        val state = DesktopSyncState.create().let { initial ->
            initial.copy(
                recordVectors = mapOf(card.id to VersionVector(mapOf(initial.deviceId to 1L))),
                addressRecordVectors = mapOf(address.id to VersionVector(mapOf(initial.deviceId to 1L))),
            )
        }
        vault.save(DesktopVaultSnapshot(listOf(card), revision = 4, syncState = state, addresses = listOf(address)))

        val restored = vault.load()
        assertEquals(4, restored.revision)
        assertEquals(card, restored.cards.single())
        assertEquals(address, restored.addresses.single())
        assertEquals(state.vaultId, restored.syncState.vaultId)
        assertEquals(state.recordVectors, restored.syncState.recordVectors)

        val encrypted = Files.readAllBytes(directory.resolve("vault.data"))
        assertFalse(encrypted.containsSequence(card.cardNumber.toByteArray()))
        assertFalse(encrypted.containsSequence(card.nickname.toByteArray()))
        assertFalse(encrypted.containsSequence(requireNotNull(card.cvv).toByteArray()))
        assertFalse(encrypted.containsSequence(address.detailedAddress.toByteArray()))
        assertFalse(encrypted.containsSequence(address.country.toByteArray()))
    }

    @Test
    fun `tampering is rejected without exposing payload`() {
        val directory = Files.createTempDirectory("cardvault-desktop-tamper")
        val vault = EncryptedDesktopVault(directory, XorTestProtector())
        val card = syntheticCard()
        val state = DesktopSyncState.create().let { initial ->
            initial.copy(recordVectors = mapOf(card.id to VersionVector(mapOf(initial.deviceId to 1L))))
        }
        vault.save(DesktopVaultSnapshot(listOf(card), 1, state))
        val path = directory.resolve("vault.data")
        val encoded = Files.readAllBytes(path)
        encoded[encoded.lastIndex] = (encoded.last().toInt() xor 1).toByte()
        Files.write(path, encoded)

        val failure = assertFailsWith<InvalidVaultException> { vault.load() }
        assertFalse(failure.message.orEmpty().contains(card.cardNumber))
        assertFalse(failure.message.orEmpty().contains(requireNotNull(card.cvv)))
    }

    @Test
    fun `repository maintains vectors tombstones and continuous order`() {
        val directory = Files.createTempDirectory("cardvault-desktop-repository")
        val repository = DesktopCardRepository(
            EncryptedDesktopVault(directory, XorTestProtector()),
            fixedClock,
        )
        val first = syntheticCard()
        val second = syntheticCard(nickname = "实验卡 B")
        repository.add(first)
        repository.add(second)
        assertEquals(listOf(second.id, first.id), repository.cards().map(DesktopCard::id))

        val orderBefore = repository.snapshot().syncState.orderVector
        assertTrue(repository.move(first.id, 0))
        assertNotEquals(orderBefore, repository.snapshot().syncState.orderVector)
        assertEquals(listOf(0, 1), repository.cards().map(DesktopCard::sortOrder))

        assertTrue(repository.delete(first.id))
        val afterDelete = repository.snapshot()
        assertTrue(first.id in afterDelete.syncState.tombstones)
        assertFalse(first.id in afterDelete.syncState.recordVectors)
        assertEquals(listOf(0), repository.cards().map(DesktopCard::sortOrder))
    }

    @Test
    fun `address repository maintains encrypted records vectors tombstones and order`() {
        val directory = Files.createTempDirectory("cardvault-desktop-address-repository")
        val repository = DesktopCardRepository(
            EncryptedDesktopVault(directory, XorTestProtector()),
            fixedClock,
        )
        val first = syntheticAddress()
        val second = syntheticAddress(nickname = "实验地址 B")

        repository.addAddress(first)
        repository.addAddress(second)
        assertEquals(listOf(second.id, first.id), repository.addresses().map(DesktopAddress::id))
        assertTrue(repository.moveAddress(first.id, 0))
        assertEquals(listOf(0, 1), repository.addresses().map(DesktopAddress::sortOrder))

        val edited = first.copy(city = "虚构新城", updatedAtEpochMillis = first.updatedAtEpochMillis + 1)
        assertTrue(repository.updateAddress(edited))
        assertEquals("虚构新城", repository.addresses().first().city)

        assertTrue(repository.deleteAddress(first.id))
        val snapshot = repository.snapshot()
        assertTrue(first.id in snapshot.syncState.addressTombstones)
        assertFalse(first.id in snapshot.syncState.addressRecordVectors)
        assertEquals(listOf(0), snapshot.addresses.map(DesktopAddress::sortOrder))

        val encrypted = Files.readAllBytes(directory.resolve("vault.data"))
        assertFalse(encrypted.containsSequence(second.detailedAddress.toByteArray()))
        repository.close()
    }

    @Test
    fun `folder order including unfiled is draggable and survives restart`() {
        val directory = Files.createTempDirectory("cardvault-desktop-folder-order")
        val firstRepository = DesktopCardRepository(
            EncryptedDesktopVault(directory, XorTestProtector()),
            fixedClock,
        )
        val first = firstRepository.createFolder("分组甲", DesktopFolderKind.CARDS)
        val second = firstRepository.createFolder("分组乙", DesktopFolderKind.CARDS)

        assertEquals(listOf(null, first.id, second.id), firstRepository.folderOrder(DesktopFolderKind.CARDS))
        assertTrue(firstRepository.reorderFolders(DesktopFolderKind.CARDS, listOf(second.id, null, first.id)))
        assertEquals(listOf(second.id, null, first.id), firstRepository.folderOrder(DesktopFolderKind.CARDS))
        assertEquals(listOf(second.id, first.id), firstRepository.folders(DesktopFolderKind.CARDS).map { it.id })
        firstRepository.close()

        val reopened = DesktopCardRepository(
            EncryptedDesktopVault(directory, XorTestProtector()),
            fixedClock,
        )
        assertEquals(listOf(second.id, null, first.id), reopened.folderOrder(DesktopFolderKind.CARDS))
        reopened.close()
    }

    @Test
    fun `Windows DPAPI protects and restores a local key`() {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return
        val protector = WindowsDpapiKeyProtector()
        val clear = ByteArray(32).also(java.security.SecureRandom()::nextBytes)
        val protected = protector.protect(clear)
        val restored = protector.unprotect(protected)
        try {
            assertFalse(clear.contentEquals(protected))
            assertTrue(clear.contentEquals(restored))
        } finally {
            clear.fill(0)
            protected.fill(0)
            restored.fill(0)
        }
    }

    @Test
    fun `desktop sync secret is redacted and unavailable after close`() {
        val source = ByteArray(32) { 0x5A }
        val secret = SecretBytes.of(source)
        source.fill(0)

        assertEquals("SecretBytes(redacted)", secret.toString())
        secret.close()

        assertFailsWith<IllegalStateException> { secret.copyBytes() }
    }

    @Test
    fun `legacy version two desktop snapshot opens with an empty address collection`() {
        val card = syntheticCard()
        val state = DesktopSyncState.create().let { initial ->
            initial.copy(
                recordVectors = mapOf(
                    card.id to VersionVector(mapOf(initial.deviceId to 1L)),
                ),
            )
        }

        val restored = DesktopVaultCodec.decode(legacyVersionTwoSnapshot(card, state))

        assertEquals(listOf(card), restored.cards)
        assertTrue(restored.addresses.isEmpty())
        assertEquals(
            VersionVector(mapOf(state.deviceId to 1L)),
            restored.syncState.addressOrderVector,
        )
        assertTrue(restored.syncState.addressRecordVectors.isEmpty())
        assertTrue(restored.syncState.addressTombstones.isEmpty())
    }

    @Test
    fun `version three desktop snapshot preserves cards and addresses with empty folders`() {
        val card = syntheticCard()
        val address = syntheticAddress()
        val state = DesktopSyncState.create().let { initial ->
            initial.copy(
                recordVectors = mapOf(card.id to VersionVector(mapOf(initial.deviceId to 1L))),
                addressRecordVectors = mapOf(address.id to VersionVector(mapOf(initial.deviceId to 1L))),
            )
        }

        val restored = DesktopVaultCodec.decode(legacyVersionThreeSnapshot(card, address, state))

        assertEquals(listOf(card), restored.cards)
        assertEquals(listOf(address), restored.addresses)
        assertTrue(restored.folders.isEmpty())
        assertTrue(restored.syncState.folderRecordVectors.isEmpty())
        assertTrue(restored.syncState.folderTombstones.isEmpty())
    }

    @Test
    fun `version four desktop snapshot upgrades without losing existing data`() {
        val card = syntheticCard()
        val address = syntheticAddress()
        val state = DesktopSyncState.create().let { initial ->
            initial.copy(
                recordVectors = mapOf(card.id to VersionVector(mapOf(initial.deviceId to 1L))),
                addressRecordVectors = mapOf(address.id to VersionVector(mapOf(initial.deviceId to 1L))),
            )
        }
        val versionFive = DesktopVaultCodec.encode(
            DesktopVaultSnapshot(listOf(card), 9L, state, listOf(address)),
        )
        val versionOffset = 1 + "CardVault/DesktopSnapshot".toByteArray(StandardCharsets.US_ASCII).size
        versionFive[versionOffset + 3] = 4
        val versionFour = versionFive.copyOf(versionFive.size - 10)

        val restored = DesktopVaultCodec.decode(versionFour)

        assertEquals(listOf(card), restored.cards)
        assertEquals(listOf(address), restored.addresses)
        assertEquals(listOf(null), restored.cardFolderOrder)
        assertEquals(listOf(null), restored.addressFolderOrder)
    }

    private fun legacyVersionTwoSnapshot(
        card: DesktopCard,
        state: DesktopSyncState,
    ): ByteArray = ByteArrayOutputStream().use { buffer ->
        DataOutputStream(buffer).use { data ->
            val magic = "CardVault/DesktopSnapshot".toByteArray(StandardCharsets.US_ASCII)
            data.writeByte(magic.size)
            data.write(magic)
            data.writeInt(2)
            data.writeLong(7L)
            data.writeLegacyString(state.vaultId)
            data.writeLegacyString(state.deviceId)
            data.writeLong(state.keyEpoch)
            data.writeBoolean(false)
            data.writeLong(state.exportSequence)
            data.writeInt(1)
            data.writeLegacyString(card.id)
            data.writeLegacyVector(state.recordVectors.getValue(card.id))
            data.writeInt(0)
            data.writeLegacyVector(state.orderVector)
            data.writeInt(0)
            data.writeInt(0)
            data.writeInt(1)
            data.writeLegacyString(card.id)
            data.writeLegacyString(card.nickname)
            data.writeLegacyString(card.issuerName)
            data.writeLegacyString(card.cardNumber)
            data.writeInt(card.expiryMonth)
            data.writeInt(card.expiryYear)
            data.writeBoolean(card.cvv != null)
            card.cvv?.let { value -> data.writeLegacyString(value) }
            data.writeLegacyString(card.notes)
            data.writeLegacyString(card.cardTemplateId)
            data.writeInt(card.sortOrder)
            data.writeLong(card.createdAtEpochMillis)
            data.writeLong(card.updatedAtEpochMillis)
        }
        buffer.toByteArray()
    }

    private fun legacyVersionThreeSnapshot(
        card: DesktopCard,
        address: DesktopAddress,
        state: DesktopSyncState,
    ): ByteArray = ByteArrayOutputStream().use { buffer ->
        DataOutputStream(buffer).use { data ->
            val magic = "CardVault/DesktopSnapshot".toByteArray(StandardCharsets.US_ASCII)
            data.writeByte(magic.size)
            data.write(magic)
            data.writeInt(3)
            data.writeLong(8L)
            data.writeLegacyString(state.vaultId)
            data.writeLegacyString(state.deviceId)
            data.writeLong(state.keyEpoch)
            data.writeBoolean(false)
            data.writeLong(state.exportSequence)
            data.writeInt(1)
            data.writeLegacyString(card.id)
            data.writeLegacyVector(state.recordVectors.getValue(card.id))
            data.writeInt(0)
            data.writeLegacyVector(state.orderVector)
            data.writeInt(0)
            data.writeInt(0)
            data.writeInt(1)
            data.writeLegacyString(address.id)
            data.writeLegacyVector(state.addressRecordVectors.getValue(address.id))
            data.writeInt(0)
            data.writeLegacyVector(state.addressOrderVector)
            data.writeInt(1)
            data.writeLegacyCard(card)
            data.writeInt(1)
            data.writeLegacyAddress(address)
        }
        buffer.toByteArray()
    }

    private fun DataOutputStream.writeLegacyCard(card: DesktopCard) {
        writeLegacyString(card.id)
        writeLegacyString(card.nickname)
        writeLegacyString(card.issuerName)
        writeLegacyString(card.cardNumber)
        writeInt(card.expiryMonth)
        writeInt(card.expiryYear)
        writeBoolean(card.cvv != null)
        card.cvv?.let { writeLegacyString(it) }
        writeLegacyString(card.notes)
        writeLegacyString(card.cardTemplateId)
        writeInt(card.sortOrder)
        writeLong(card.createdAtEpochMillis)
        writeLong(card.updatedAtEpochMillis)
    }

    private fun DataOutputStream.writeLegacyAddress(address: DesktopAddress) {
        writeLegacyString(address.id)
        writeLegacyString(address.nickname)
        writeLegacyString(address.detailedAddress)
        writeLegacyString(address.city)
        writeLegacyString(address.other)
        writeLegacyString(address.postalCode)
        writeLegacyString(address.country)
        writeLegacyString(address.cardTemplateId)
        writeInt(address.sortOrder)
        writeLong(address.createdAtEpochMillis)
        writeLong(address.updatedAtEpochMillis)
    }

    private fun DataOutputStream.writeLegacyString(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        try {
            writeInt(bytes.size)
            write(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun DataOutputStream.writeLegacyVector(vector: VersionVector) {
        writeInt(vector.entries.size)
        vector.entries.toSortedMap().forEach { (deviceId, sequence) ->
            writeLegacyString(deviceId)
            writeLong(sequence)
        }
    }

    private fun syntheticCard(nickname: String = "实验卡 A"): DesktopCard = DesktopCard.create(
        nickname = nickname,
        issuerName = "虚构发行方",
        cardNumber = "9".repeat(19),
        expiryMonth = 12,
        expiryYear = 2099,
        cvv = "9".repeat(3),
        notes = "仅用于自动化测试",
        style = CardCoverStyle.Default,
        sortOrder = 0,
        clock = fixedClock,
    )

    private fun syntheticAddress(nickname: String = "实验地址 A"): DesktopAddress = DesktopAddress.create(
        nickname = nickname,
        detailedAddress = "虚构大道 ${nickname.takeLast(1)} 号",
        city = "虚构城",
        other = "虚构楼层",
        postalCode = "000000",
        country = "虚构国",
        style = CardCoverStyle.Default,
        sortOrder = 0,
        clock = fixedClock,
    )
}

private class XorTestProtector : LocalKeyProtector {
    override fun protect(plainKey: ByteArray): ByteArray = plainKey.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    override fun unprotect(protectedKey: ByteArray): ByteArray = protect(protectedKey)
}

private fun ByteArray.containsSequence(sequence: ByteArray): Boolean {
    if (sequence.isEmpty() || sequence.size > size) return false
    return (0..size - sequence.size).any { offset ->
        sequence.indices.all { index -> this[offset + index] == sequence[index] }
    }
}
