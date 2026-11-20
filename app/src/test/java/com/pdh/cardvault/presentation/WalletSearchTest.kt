package com.pdh.cardvault.presentation

import com.pdh.cardvault.domain.model.CardNetwork
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletSearchTest {
    private val merchants = card("招商银行 经典白", "China Merchants Bank", "1234", CardNetwork.UnionPay)
    private val paypal = card("PayPal", "CardVault", "9876", CardNetwork.Mastercard)

    @Test
    fun queryNormalisationCollapsesWhitespaceAndCapsLength() {
        assertEquals("", normalizeCardSearchQuery("   \t "))
        assertEquals("招商 1234 ", normalizeCardSearchQuery("  招商\t\t1234  "))
        assertEquals(40, normalizeCardSearchQuery("a".repeat(100)).length)
    }

    @Test
    fun digitsMatchLastFourAndTextMatchesNameOrIssuerIgnoringCase() {
        assertTrue(cardMatchesSearch(merchants, ""))
        assertTrue(cardMatchesSearch(merchants, "234"))
        assertTrue(cardMatchesSearch(merchants, "6222020000001234"))
        assertFalse(cardMatchesSearch(merchants, "9876"))
        assertTrue(cardMatchesSearch(merchants, "招商"))
        assertTrue(cardMatchesSearch(merchants, "merchants"))
        assertTrue(cardMatchesSearch(paypal, "paypal"))
        assertFalse(cardMatchesSearch(paypal, "招商"))
    }

    @Test
    fun everyTokenMustMatch() {
        assertTrue(cardMatchesSearch(merchants, "招商 1234"))
        assertFalse(cardMatchesSearch(merchants, "招商 9876"))
        assertFalse(cardMatchesSearch(paypal, "paypal 1234"))
    }

    @Test
    fun addressSearchMatchesNicknameOnly() {
        val address = AddressListItemUiModel(UUID.randomUUID(), "公司 收货地址", "custom:onyx:clean")
        assertTrue(addressMatchesSearch(address, "公司"))
        assertTrue(addressMatchesSearch(address, "收货 公司"))
        assertFalse(addressMatchesSearch(address, "家里"))
    }

    @Test
    fun duplicateWarningMatchesSameLastFourAndCompatibleNetwork() {
        val unionPayNumber = "6222020000001234"
        assertEquals(listOf(merchants), findPossibleDuplicateCards(unionPayNumber, listOf(merchants, paypal)))
        assertTrue(findPossibleDuplicateCards("62220200", listOf(merchants)).isEmpty())
        assertTrue(
            findPossibleDuplicateCards(unionPayNumber, listOf(merchants), excludeId = merchants.id).isEmpty(),
        )
        val visaSameSuffix = "4111111111111234"
        assertTrue(findPossibleDuplicateCards(visaSameSuffix, listOf(merchants)).isEmpty())
        val unknownNetworkCard = merchants.copy(id = UUID.randomUUID(), cardNetwork = null)
        assertEquals(
            listOf(unknownNetworkCard),
            findPossibleDuplicateCards(visaSameSuffix, listOf(unknownNetworkCard)),
        )
    }

    private fun card(name: String, issuer: String, lastFour: String, network: CardNetwork?) =
        CardListItemUiModel(
            id = UUID.randomUUID(),
            nickname = name,
            issuerName = issuer,
            cardTemplateId = "custom:onyx:clean",
            cardNetwork = network,
            lastFour = lastFour,
        )
}
