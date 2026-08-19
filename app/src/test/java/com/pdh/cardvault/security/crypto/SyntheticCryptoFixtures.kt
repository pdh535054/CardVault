package com.pdh.cardvault.security.crypto

internal fun syntheticCardPayload(saveCvv: Boolean = true): CardPayload = CardPayload(
    nickname = "Synthetic vault marker",
    issuerName = "Offline fixture issuer",
    cardNumber = "9".repeat(12),
    expiryMonth = 12,
    expiryYear = 2099,
    saveCvv = saveCvv,
    cvv = "8".repeat(3).takeIf { saveCvv },
    cardTemplateId = "generic_black",
    notes = "Synthetic encrypted payload marker",
)
