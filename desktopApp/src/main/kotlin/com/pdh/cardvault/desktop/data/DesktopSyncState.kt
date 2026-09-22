package com.pdh.cardvault.desktop.data

import java.util.UUID

class SecretBytes private constructor(value: ByteArray) : AutoCloseable {
    private var value: ByteArray? = value

    init { require(value.size == 32) { "同步密钥无效。" } }

    @Synchronized
    fun copyBytes(): ByteArray = value?.copyOf() ?: throw IllegalStateException("同步密钥不可用。")

    override fun equals(other: Any?): Boolean = this === other ||
        other is SecretBytes && runCatching { copyBytes().useAndClear { left ->
            other.copyBytes().useAndClear { right -> left.contentEquals(right) }
        } }.getOrDefault(false)

    override fun hashCode(): Int = runCatching { copyBytes().useAndClear(ByteArray::contentHashCode) }.getOrDefault(0)
    override fun toString(): String = "SecretBytes(redacted)"

    @Synchronized
    override fun close() {
        value?.fill(0)
        value = null
    }

    companion object {
        fun of(value: ByteArray): SecretBytes = SecretBytes(value.copyOf())
    }
}

private inline fun <T> ByteArray.useAndClear(block: (ByteArray) -> T): T = try {
    block(this)
} finally {
    fill(0)
}

data class VersionVector(val entries: Map<String, Long> = emptyMap()) {
    init {
        require(entries.size <= 16 && entries.all { (device, sequence) ->
            runCatching { UUID.fromString(device) }.isSuccess && sequence > 0
        }) { "同步版本无效。" }
    }

    fun increment(deviceId: String): VersionVector =
        copy(entries = entries + (deviceId to ((entries[deviceId] ?: 0L) + 1L)))

    override fun toString(): String = "VersionVector(deviceCount=${entries.size})"
}

data class DesktopTombstone(
    val recordId: String,
    val deletedAtEpochMillis: Long,
    val vector: VersionVector,
) {
    init {
        require(runCatching { UUID.fromString(recordId) }.isSuccess && deletedAtEpochMillis >= 0) {
            "同步墓碑无效。"
        }
    }
}

data class DesktopSyncState(
    val vaultId: String,
    val deviceId: String,
    val keyEpoch: Long = 0,
    val sharedSyncKey: SecretBytes? = null,
    val exportSequence: Long = 0,
    val recordVectors: Map<String, VersionVector> = emptyMap(),
    val tombstones: Map<String, DesktopTombstone> = emptyMap(),
    val orderVector: VersionVector = VersionVector(),
    val addressRecordVectors: Map<String, VersionVector> = emptyMap(),
    val addressTombstones: Map<String, DesktopTombstone> = emptyMap(),
    val addressOrderVector: VersionVector = VersionVector(mapOf(deviceId to 1L)),
    val folderRecordVectors: Map<String, VersionVector> = emptyMap(),
    val folderTombstones: Map<String, DesktopTombstone> = emptyMap(),
    val recentPackageIds: List<String> = emptyList(),
    val replaySequences: Map<String, Long> = emptyMap(),
) {
    init {
        require(runCatching { UUID.fromString(vaultId) }.isSuccess) { "同步状态无效。" }
        require(runCatching { UUID.fromString(deviceId) }.isSuccess) { "同步状态无效。" }
        require(keyEpoch in 0..Int.MAX_VALUE.toLong() && exportSequence >= 0) { "同步状态无效。" }
        require(
            recordVectors.size <= 1_024 && tombstones.size <= 1_024 &&
                addressRecordVectors.size <= 1_024 && addressTombstones.size <= 1_024 &&
                folderRecordVectors.size <= 512 && folderTombstones.size <= 512,
        ) { "同步状态无效。" }
        require((recordVectors.keys + addressRecordVectors.keys + folderRecordVectors.keys).all { runCatching { UUID.fromString(it) }.isSuccess }) {
            "同步状态无效。"
        }
        require(tombstones.all { (id, tombstone) -> id == tombstone.recordId }) { "同步状态无效。" }
        require(addressTombstones.all { (id, tombstone) -> id == tombstone.recordId }) { "同步状态无效。" }
        require(folderTombstones.all { (id, tombstone) -> id == tombstone.recordId }) { "同步状态无效。" }
        require(recentPackageIds.size <= 64 && recentPackageIds.all { runCatching { UUID.fromString(it) }.isSuccess }) {
            "同步状态无效。"
        }
        require(replaySequences.size <= 16 && replaySequences.all { (id, sequence) ->
            runCatching { UUID.fromString(id) }.isSuccess && sequence > 0
        }) { "同步状态无效。" }
    }

    override fun toString(): String =
        "DesktopSyncState(vaultId=$vaultId, deviceId=$deviceId, keyEpoch=$keyEpoch, key=redacted)"

    companion object {
        fun create(): DesktopSyncState {
            val deviceId = UUID.randomUUID().toString()
            return DesktopSyncState(
                vaultId = UUID.randomUUID().toString(),
                deviceId = deviceId,
                orderVector = VersionVector(mapOf(deviceId to 1L)),
                addressOrderVector = VersionVector(mapOf(deviceId to 1L)),
            )
        }
    }
}
