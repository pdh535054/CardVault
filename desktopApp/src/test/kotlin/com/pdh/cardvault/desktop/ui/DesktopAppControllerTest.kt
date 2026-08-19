package com.pdh.cardvault.desktop.ui

import com.pdh.cardvault.desktop.data.DesktopCardRepository
import com.pdh.cardvault.desktop.data.EncryptedDesktopVault
import com.pdh.cardvault.desktop.data.LocalKeyProtector
import com.pdh.cardvault.desktop.model.CardCoverStyle
import com.pdh.cardvault.desktop.model.DesktopCard
import com.pdh.cardvault.desktop.security.SensitiveAction
import com.pdh.cardvault.desktop.security.SensitiveActionAuthenticator
import com.pdh.cardvault.desktop.sync.SyncCoreDesktopGateway
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
