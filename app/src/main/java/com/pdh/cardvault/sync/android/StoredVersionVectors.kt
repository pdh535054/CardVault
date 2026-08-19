package com.pdh.cardvault.sync.android

import com.pdh.cardvault.sync.SyncProtocolException
import com.pdh.cardvault.sync.VersionVector
import com.pdh.cardvault.sync.VersionVectorCodec

/**
 * Canonical Room representation of a shared-core version vector.
 *
 * An empty blob is accepted only as the v1 migration sentinel and is converted to an empty
 * vector before the first local increment. Newly persisted vectors are always non-empty.
 */
internal object StoredVersionVectors {
    fun initial(deviceId: String): ByteArray =
        VersionVectorCodec.encode(VersionVector.empty().increment(deviceId))

    fun increment(encoded: ByteArray, deviceId: String): ByteArray =
        VersionVectorCodec.encode(decode(encoded).increment(deviceId))

    fun encode(vector: VersionVector): ByteArray {
        require(!vector.isEmpty) { "Invalid synchronization metadata." }
        return VersionVectorCodec.encode(vector)
    }

    fun decode(encoded: ByteArray): VersionVector = if (encoded.isEmpty()) {
        VersionVector.empty()
    } else {
        try {
            VersionVectorCodec.decode(encoded)
        } catch (_: SyncProtocolException) {
            throw InvalidStoredSyncMetadataException()
        }
    }
}

internal class InvalidStoredSyncMetadataException : IllegalStateException(
    "The stored synchronization metadata is invalid.",
)
