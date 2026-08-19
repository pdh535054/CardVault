package com.pdh.cardvault.desktop.sync

import com.pdh.cardvault.desktop.data.DesktopVaultSnapshot

/** Keeps the desktop UI independent from the binary exchange protocol implementation. */
interface DesktopSyncGateway {
    val isReady: Boolean

    fun export(snapshot: DesktopVaultSnapshot, newPairing: Boolean): DesktopExportPackage

    /** Replaces the shared key and advances its epoch without changing vault records. */
    fun rotateSyncKey(snapshot: DesktopVaultSnapshot): DesktopVaultSnapshot

    fun import(bytes: ByteArray, pairingCode: String?, current: DesktopVaultSnapshot): DesktopImportResult
}

data class DesktopExportPackage(
    val suggestedFileName: String,
    val bytes: ByteArray,
    /** Only present for a new-device pairing package. Never put this code in the same message as the file. */
    val pairingCode: String?,
    val snapshotAfterExport: DesktopVaultSnapshot,
) {
    override fun toString(): String =
        "DesktopExportPackage(fileName=$suggestedFileName, byteCount=${bytes.size}, pairingCode=redacted)"
}

data class DesktopImportResult(
    val snapshot: DesktopVaultSnapshot,
    val added: Int,
    val updated: Int,
    val conflicts: Int,
    val message: String,
) {
    override fun toString(): String =
        "DesktopImportResult(cardCount=${snapshot.cards.size}, addressCount=${snapshot.addresses.size}, " +
            "added=$added, updated=$updated, conflicts=$conflicts)"
}
