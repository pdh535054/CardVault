package com.pdh.cardvault.domain.model

/** A best-effort display hint derived in memory from the encrypted card number. */
enum class CardNetwork {
    Visa,
    Mastercard,
    UnionPay,
}
