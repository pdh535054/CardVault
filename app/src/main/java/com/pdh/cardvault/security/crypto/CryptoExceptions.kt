package com.pdh.cardvault.security.crypto

sealed class VaultCryptoException(
    message: String,
) : Exception(message)

class EncryptedDataAuthenticationException : VaultCryptoException(
    "Encrypted data authentication failed.",
)

class DeviceAuthenticationRequiredException : VaultCryptoException(
    "Device authentication is required for this cryptographic operation.",
)

class VaultKeyInvalidatedException : VaultCryptoException(
    "The vault key is no longer available.",
)

class VaultKeyUnavailableException : VaultCryptoException(
    "The vault key is unavailable.",
)

class SecureLockScreenRequiredException : VaultCryptoException(
    "A secure device lock screen is required.",
)

class UnsupportedCryptoVersionException : VaultCryptoException(
    "The encrypted data version is unsupported.",
)

class InvalidEncryptedPayloadException : VaultCryptoException(
    "The encrypted payload is invalid.",
)

class CryptoOperationException : VaultCryptoException(
    "The cryptographic operation failed.",
)
