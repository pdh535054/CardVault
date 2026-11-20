package com.pdh.cardvault.presentation

private const val MAX_SEARCH_QUERY_LENGTH = 40
private val WHITESPACE = Regex("\\s+")

/** Collapses whitespace, drops leading blanks and caps the length of a search query. */
internal fun normalizeCardSearchQuery(value: String): String =
    value.replace(WHITESPACE, " ").trimStart().take(MAX_SEARCH_QUERY_LENGTH)

private fun searchTokens(query: String): List<String> =
    query.trim().split(' ').filter(String::isNotEmpty)

/**
 * Every whitespace-separated token must match. A purely numeric token matches the card's
 * last four digits (typing more digits, e.g. a full number, matches on its tail); any other
 * token matches the card name or issuer, ignoring case. Only fields already shown on the
 * masked card face are searched, so search never needs to decrypt card secrets.
 */
internal fun cardMatchesSearch(card: CardListItemUiModel, query: String): Boolean {
    val tokens = searchTokens(query)
    if (tokens.isEmpty()) return true
    return tokens.all { token ->
        if (token.all(Char::isDigit)) {
            card.lastFour.endsWith(token.takeLast(4))
        } else {
            card.nickname.contains(token, ignoreCase = true) ||
                card.issuerName.contains(token, ignoreCase = true)
        }
    }
}

/** Address search uses only the nickname, which is the only field in the list projection. */
internal fun addressMatchesSearch(address: AddressListItemUiModel, query: String): Boolean {
    val tokens = searchTokens(query)
    if (tokens.isEmpty()) return true
    return tokens.all { token -> address.nickname.contains(token, ignoreCase = true) }
}

/**
 * Cards in the vault that look like the card being entered: same last four digits and, when
 * both are known, the same payment network. Used for a non-blocking duplicate warning.
 */
internal fun findPossibleDuplicateCards(
    cardNumber: String,
    existingCards: List<CardListItemUiModel>,
    excludeId: java.util.UUID? = null,
): List<CardListItemUiModel> {
    val digits = cardNumber.filter(Char::isDigit)
    if (digits.length < 12) return emptyList()
    val lastFour = digits.takeLast(4)
    val network = com.pdh.cardvault.domain.validation.CardNetworkDetector.detect(digits)
    return existingCards.filter { card ->
        card.id != excludeId &&
            card.lastFour == lastFour &&
            (network == null || card.cardNetwork == null || card.cardNetwork == network)
    }
}
