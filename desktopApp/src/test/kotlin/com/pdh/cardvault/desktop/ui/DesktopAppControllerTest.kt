package com.pdh.cardvault.desktop.ui

import com.pdh.cardvault.desktop.data.DesktopCardRepository
import com.pdh.cardvault.desktop.data.EncryptedDesktopVault
import com.pdh.cardvault.desktop.data.LocalKeyProtector
import com.pdh.cardvault.desktop.model.CardCoverStyle
import com.pdh.cardvault.desktop.model.DesktopCard
import com.pdh.cardvault.desktop.security.SensitiveAction
import com.pdh.cardvault.desktop.security.SensitiveActionAuthenticator
import com.pdh.cardvault.desktop.sync.SyncCoreDesktopGateway
import com.pdh.cardvault.sync.SyncFileKind
import com.pdh.cardvault.sync.PairingCode
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAppControllerTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `concealment epoch advances without clearing unfinished draft`() {
        val controller = DesktopAppController(
            repository = DesktopCardRepository(
                EncryptedDesktopVault(Files.createTempDirectory("cardvault-conceal"), TestKeyProtector()),
            ),
            syncGateway = SyncCoreDesktopGateway(),
        )
        controller.startAdd()
        controller.updateDraft { it.copy(nickname = "尚未保存的卡片") }
        val before = controller.concealmentEpoch

        controller.concealSensitiveInformation()

        assertEquals(before + 1, controller.concealmentEpoch)
        assertEquals("尚未保存的卡片", controller.editingDraft.nickname)
        assertEquals(DesktopSection.Editor, controller.section)
        controller.close()
    }

    @Test
    fun `failed authentication blocks reveal edit delete and clipboard access`() = runBlocking {
        val authenticator = RecordingAuthenticator(accepted = false)
        val controller = controllerWithCard(authenticator)
        val cardId = requireNotNull(controller.selectedId)

        assertFalse(controller.authorizeReveal(cardId))
        controller.copyCardNumber(cardId)
        assertEquals("请先验证身份并显示卡号", controller.notice)
        assertFalse(controller.startEdit(cardId))
        assertFalse(controller.deleteSelected())

        assertEquals(1, controller.cards.size)
        assertEquals(DesktopSection.Home, controller.section)
        assertEquals(
            listOf(SensitiveAction.RevealCardSecrets, SensitiveAction.EditCard, SensitiveAction.DeleteCard),
            authenticator.actions,
        )
        controller.close()
    }

    @Test
    fun `verified authentication allows editing and deleting`() = runBlocking {
        val authenticator = RecordingAuthenticator(accepted = true)
        val controller = controllerWithCard(authenticator)
        val cardId = requireNotNull(controller.selectedId)

        assertTrue(controller.startEdit(cardId))
        assertEquals(DesktopSection.Editor, controller.section)
        assertEquals(cardId, controller.editingDraft.id)
        controller.section = DesktopSection.Wallet
        assertTrue(controller.deleteSelected())

        assertTrue(controller.cards.isEmpty())
        assertEquals(listOf(SensitiveAction.EditCard, SensitiveAction.DeleteCard), authenticator.actions)
        controller.close()
    }

    @Test
    fun `address draft validates saves edits and deletes encrypted address`() = runBlocking {
        val authenticator = RecordingAuthenticator(accepted = true)
        val controller = DesktopAppController(
            repository = DesktopCardRepository(
                EncryptedDesktopVault(Files.createTempDirectory("cardvault-address-ui"), TestKeyProtector()),
                clock,
            ),
            syncGateway = SyncCoreDesktopGateway(),
            clock = clock,
            authenticator = authenticator,
        )

        controller.startAddAddress()
        assertFalse(controller.saveAddressDraft())
        assertTrue(controller.addressDraftErrors.hasErrors)
        controller.updateAddressDraft {
            it.copy(
                nickname = "虚构收货地址",
                detailedAddress = "虚构大道 88 号",
                city = "虚构城",
                other = "二楼",
                postalCode = "000000",
                country = "虚构国",
            )
        }
        assertTrue(controller.saveAddressDraft())
        assertEquals(DesktopSection.Addresses, controller.section)
        assertEquals(1, controller.addresses.size)
        val addressId = requireNotNull(controller.selectedAddressId)

        assertTrue(controller.startEditAddress(addressId))
        controller.updateAddressDraft { it.copy(city = "虚构新城") }
        assertTrue(controller.saveAddressDraft())
        assertEquals("虚构新城", controller.addresses.single().city)

        assertTrue(controller.deleteSelectedAddress())
        assertTrue(controller.addresses.isEmpty())
        assertEquals(listOf(SensitiveAction.EditAddress, SensitiveAction.DeleteAddress), authenticator.actions)
        controller.close()
    }

    @Test
    fun `android compatible pairing file can be selected and imported with visible result`() = runBlocking {
        val sourceRepository = DesktopCardRepository(
            EncryptedDesktopVault(Files.createTempDirectory("cardvault-transfer-source"), TestKeyProtector()),
            clock,
        )
        sourceRepository.add(
            DesktopCard.create(
                nickname = "虚构同步卡",
                issuerName = "虚构发行方",
                cardNumber = "8".repeat(19),
                expiryMonth = 12,
                expiryYear = 2099,
                cvv = "8".repeat(3),
                notes = "自动化测试",
                style = CardCoverStyle.Default,
                sortOrder = 0,
                clock = clock,
            ),
        )
        val gateway = SyncCoreDesktopGateway(clock)
        val exported = gateway.export(sourceRepository.snapshot(), newPairing = true)
        val transferFile = Files.createTempDirectory("cardvault-transfer-file")
            .resolve(exported.suggestedFileName)
        Files.write(transferFile, exported.bytes)
        exported.bytes.fill(0)

        val targetController = DesktopAppController(
            repository = DesktopCardRepository(
                EncryptedDesktopVault(Files.createTempDirectory("cardvault-transfer-target"), TestKeyProtector()),
                clock,
            ),
            syncGateway = SyncCoreDesktopGateway(clock),
            clock = clock,
            authenticator = RecordingAuthenticator(accepted = true),
        )

        assertTrue(targetController.selectImportFile(transferFile))
        assertEquals(SyncFileKind.PAIRING, targetController.selectedImportKind)
        assertTrue(
            targetController.importSelected(exported.pairingCode),
            targetController.transferFeedback?.text,
        )
        assertEquals("虚构同步卡", targetController.cards.single().nickname)
        assertTrue(targetController.transferFeedback?.text?.startsWith("导入完成") == true)
        assertFalse(targetController.transferFeedback?.isError == true)
        assertEquals(null, targetController.selectedImportPath)

        targetController.close()
        sourceRepository.close()
    }

    @Test
    fun `pairing import without code stays selected and explains next step`() = runBlocking {
        val sourceRepository = DesktopCardRepository(
            EncryptedDesktopVault(Files.createTempDirectory("cardvault-code-source"), TestKeyProtector()),
            clock,
        )
        val gateway = SyncCoreDesktopGateway(clock)
        val exported = gateway.export(sourceRepository.snapshot(), newPairing = true)
        val transferFile = Files.createTempDirectory("cardvault-code-file")
            .resolve(exported.suggestedFileName)
        Files.write(transferFile, exported.bytes)
        exported.bytes.fill(0)
        val authenticator = RecordingAuthenticator(accepted = true)
        val targetController = DesktopAppController(
            repository = DesktopCardRepository(
                EncryptedDesktopVault(Files.createTempDirectory("cardvault-code-target"), TestKeyProtector()),
                clock,
            ),
            syncGateway = SyncCoreDesktopGateway(clock),
            clock = clock,
            authenticator = authenticator,
        )

        assertTrue(targetController.selectImportFile(transferFile))
        assertFalse(targetController.importSelected(""))
        assertTrue(targetController.transferFeedback?.isError == true)
        assertTrue(targetController.transferFeedback?.text?.contains("配对码") == true)
        assertEquals(transferFile.toAbsolutePath().normalize(), targetController.selectedImportPath)
        assertTrue(authenticator.actions.isEmpty())

        targetController.close()
        sourceRepository.close()
    }

    @Test
    fun `wrong pairing code reports a stable safe diagnostic without changing local data`() = runBlocking {
        val sourceRepository = DesktopCardRepository(
            EncryptedDesktopVault(Files.createTempDirectory("cardvault-wrong-code-source"), TestKeyProtector()),
            clock,
        )
        val exported = SyncCoreDesktopGateway(clock).export(sourceRepository.snapshot(), newPairing = true)
        val transferFile = Files.createTempDirectory("cardvault-wrong-code-file")
            .resolve(exported.suggestedFileName)
        Files.write(transferFile, exported.bytes)
        exported.bytes.fill(0)
        val wrongCode = PairingCode.generate().use { it.displayCode }
        val targetController = DesktopAppController(
            repository = DesktopCardRepository(
                EncryptedDesktopVault(Files.createTempDirectory("cardvault-wrong-code-target"), TestKeyProtector()),
                clock,
            ),
            syncGateway = SyncCoreDesktopGateway(clock),
            clock = clock,
            authenticator = RecordingAuthenticator(accepted = true),
        )

        assertTrue(targetController.selectImportFile(transferFile))
        assertFalse(targetController.importSelected(wrongCode))
        assertTrue(targetController.cards.isEmpty())
        assertTrue(targetController.transferFeedback?.text?.contains("CV-I202") == true)
        assertTrue(targetController.transferFeedback?.isError == true)

        targetController.close()
        sourceRepository.close()
    }

    @Test
    fun `unpaired computer rejects daily sync file with pairing instructions before authentication`() = runBlocking {
        val sourceRepository = DesktopCardRepository(
            EncryptedDesktopVault(Files.createTempDirectory("cardvault-sync-source"), TestKeyProtector()),
            clock,
        )
        val gateway = SyncCoreDesktopGateway(clock)
        val pairing = gateway.export(sourceRepository.snapshot(), newPairing = true)
        val sync = gateway.export(pairing.snapshotAfterExport, newPairing = false)
        pairing.bytes.fill(0)
        val transferFile = Files.createTempDirectory("cardvault-sync-file")
            .resolve(sync.suggestedFileName)
        Files.write(transferFile, sync.bytes)
        sync.bytes.fill(0)
        val authenticator = RecordingAuthenticator(accepted = true)
        val targetController = DesktopAppController(
            repository = DesktopCardRepository(
                EncryptedDesktopVault(Files.createTempDirectory("cardvault-sync-target"), TestKeyProtector()),
                clock,
            ),
            syncGateway = SyncCoreDesktopGateway(clock),
            clock = clock,
            authenticator = authenticator,
        )

        assertTrue(targetController.selectImportFile(transferFile))
        assertEquals(SyncFileKind.SYNC, targetController.selectedImportKind)
        assertTrue(targetController.transferFeedback?.text?.contains("电脑尚未配对") == true)
        assertFalse(targetController.importSelected(null))
        assertTrue(targetController.transferFeedback?.text?.contains(".cvpair") == true)
        assertTrue(authenticator.actions.isEmpty())

        targetController.close()
        sourceRepository.close()
    }

    private fun controllerWithCard(authenticator: SensitiveActionAuthenticator): DesktopAppController {
        val repository = DesktopCardRepository(
            EncryptedDesktopVault(Files.createTempDirectory("cardvault-auth"), TestKeyProtector()),
            clock,
        )
        repository.add(
            DesktopCard.create(
                nickname = "虚构安全卡",
                issuerName = "虚构发行方",
                cardNumber = "8".repeat(19),
                expiryMonth = 12,
                expiryYear = 2099,
                cvv = "8".repeat(3),
                notes = "自动化测试",
                style = CardCoverStyle.Default,
                sortOrder = 0,
                clock = clock,
            ),
        )
        return DesktopAppController(
            repository = repository,
            syncGateway = SyncCoreDesktopGateway(),
            clock = clock,
            authenticator = authenticator,
        )
    }
}

private class RecordingAuthenticator(private val accepted: Boolean) : SensitiveActionAuthenticator {
    val actions = mutableListOf<SensitiveAction>()

    override suspend fun authenticate(action: SensitiveAction): Boolean {
        actions += action
        return accepted
    }
}

private class TestKeyProtector : LocalKeyProtector {
    override fun protect(plainKey: ByteArray): ByteArray = plainKey.map { (it.toInt() xor 0x33).toByte() }.toByteArray()
    override fun unprotect(protectedKey: ByteArray): ByteArray = protect(protectedKey)
}
