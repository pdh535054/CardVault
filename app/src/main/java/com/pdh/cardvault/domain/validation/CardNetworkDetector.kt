package com.pdh.cardvault.domain.validation

import com.pdh.cardvault.domain.model.CardNetwork

/**
 * Offline display-only network detection. This must never be used for payment routing,
 * authorization or as a substitute for an issuer account-range table.
 */
object CardNetworkDetector {
    fun detect(input: String): CardNetwork? {
        val normalized = CardNumberTools.normalize(input)
            ?.takeIf(CardNumberTools::isStructurallyValid)
            ?: return null

        return when {
            normalized.startsWith('4') -> CardNetwork.Visa
            isMastercard(normalized) -> CardNetwork.Mastercard
            normalized.startsWith("62") || normalized.startsWith("81") -> CardNetwork.UnionPay
            else -> null
        }
    }

    private fun isMastercard(cardNumber: String): Boolean {
        if (cardNumber.length != 16) return false
        val firstSix = cardNumber.take(6).toIntOrNull() ?: return false
        return firstSix in 510_000..559_999 || firstSix in 222_100..272_099
    }
}
