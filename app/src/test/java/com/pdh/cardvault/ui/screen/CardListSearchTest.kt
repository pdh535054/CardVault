package com.pdh.cardvault.ui.screen

import com.pdh.cardvault.presentation.CardListItemUiModel
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class CardListSearchTest {
    private val folderId = UUID.randomUUID()
    private val unfiled = card("Synthetic unfiled", "1234", null)
    private val filed = card("Synthetic filed", "9876", folderId)
    private val duplicateSuffix = card("Synthetic duplicate", "1234", folderId)
    private val cards = listOf(unfiled, filed, duplicateSuffix)

    @Test
    fun blankSearchRespectsSelectedFolder() {
        assertEquals(listOf(unfiled), visibleCardsForWallet(cards, null, ""))
        assertEquals(listOf(filed, duplicateSuffix), visibleCardsForWallet(cards, folderId, ""))
    }

    @Test
    fun lastFourSearchFindsAllMatchesAcrossFoldersInWalletOrder() {
        assertEquals(
            listOf(unfiled, duplicateSuffix),
            visibleCardsForWallet(cards, folderId, "1234"),
        )
        assertEquals(listOf(filed), visibleCardsForWallet(cards, null, "876"))
        assertEquals(emptyList<CardListItemUiModel>(), visibleCardsForWallet(cards, null, "5555"))
    }

    private fun card(name: String, lastFour: String, folderId: UUID?) = CardListItemUiModel(
        id = UUID.randomUUID(),
        nickname = name,
        issuerName = "Synthetic issuer",
        cardTemplateId = "custom:onyx:clean",
        cardNetwork = null,
        lastFour = lastFour,
        folderId = folderId,
    )
}
