package com.pdh.cardvault.sync

data class ReplayMetadata(
    val recentPackageIds: List<String>,
    val highestSequenceByDevice: Map<String, Long>,
) {
    init {
        require(recentPackageIds.size <= SyncProtocolLimits.RECENT_PACKAGE_IDS) {
            "Invalid replay metadata."
        }
        require(recentPackageIds.distinct().size == recentPackageIds.size) { "Invalid replay metadata." }
        recentPackageIds.forEach(ProtocolValidation::requireUuid)
        require(highestSequenceByDevice.size <= SyncProtocolLimits.DEVICES_PER_VECTOR) {
            "Invalid replay metadata."
        }
        highestSequenceByDevice.forEach { (deviceId, sequence) ->
            ProtocolValidation.requireUuid(deviceId)
            require(sequence > 0L) { "Invalid replay metadata." }
        }
    }

    companion object {
        fun empty(): ReplayMetadata = ReplayMetadata(emptyList(), emptyMap())
    }
}

data class SyncPackageDescriptor(
    val packageId: String,
    val vaultId: String,
    val sourceDeviceId: String,
    val exportSequence: Long,
    val keyEpoch: Int,
) {
    init {
        ProtocolValidation.requireUuid(packageId)
        ProtocolValidation.requireUuid(vaultId)
        ProtocolValidation.requireUuid(sourceDeviceId)
        require(exportSequence > 0L && keyEpoch > 0) { "Invalid package metadata." }
    }
}

object ReplayProtector {
    @Throws(SyncProtocolException::class)
    fun accept(
        metadata: ReplayMetadata,
        descriptor: SyncPackageDescriptor,
        expectedVaultId: String? = null,
        expectedKeyEpoch: Int? = null,
    ): ReplayMetadata {
        expectedVaultId?.let {
            ProtocolValidation.requireUuid(it)
            if (descriptor.vaultId != it) throw SyncProtocolException(SyncErrorCode.VAULT_MISMATCH)
        }
        expectedKeyEpoch?.let {
            require(it > 0) { "Invalid key metadata." }
            if (descriptor.keyEpoch != it) throw SyncProtocolException(SyncErrorCode.STALE_PACKAGE)
        }
        if (descriptor.packageId in metadata.recentPackageIds) {
            throw SyncProtocolException(SyncErrorCode.REPLAYED_PACKAGE)
        }
        val highestSequence = metadata.highestSequenceByDevice[descriptor.sourceDeviceId] ?: 0L
        if (descriptor.exportSequence <= highestSequence) {
            throw SyncProtocolException(SyncErrorCode.STALE_PACKAGE)
        }
        val sequences = metadata.highestSequenceByDevice +
            (descriptor.sourceDeviceId to descriptor.exportSequence)
        if (sequences.size > SyncProtocolLimits.DEVICES_PER_VECTOR) {
            throw SyncProtocolException(SyncErrorCode.LIMIT_EXCEEDED)
        }
        val recent = (metadata.recentPackageIds + descriptor.packageId)
            .takeLast(SyncProtocolLimits.RECENT_PACKAGE_IDS)
        return ReplayMetadata(recent, sequences.toSortedMap())
    }

    fun descriptor(payload: PairingFilePayload): SyncPackageDescriptor = SyncPackageDescriptor(
        packageId = payload.packageId,
        vaultId = payload.vaultId,
        sourceDeviceId = payload.sourceDeviceId,
        exportSequence = payload.exportSequence,
        keyEpoch = payload.keyEpoch,
    )

    fun descriptor(payload: SyncFilePayload): SyncPackageDescriptor = SyncPackageDescriptor(
        packageId = payload.packageId,
        vaultId = payload.vaultId,
        sourceDeviceId = payload.sourceDeviceId,
        exportSequence = payload.exportSequence,
        keyEpoch = payload.keyEpoch,
    )
}
