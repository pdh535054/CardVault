package com.pdh.cardvault.desktop.model

import java.time.Clock
import java.util.UUID

enum class DesktopFolderKind { CARDS, ADDRESSES }

data class DesktopFolder(
    val id: String,
    val name: String,
    val kind: DesktopFolderKind,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val parentFolderId: String? = null,
    val colorRgb: Int = com.pdh.cardvault.wallet.FolderAppearance.DEFAULT_COLOR_RGB,
) {
    init {
        com.pdh.cardvault.wallet.FolderAppearance.requireValidColor(colorRgb)
        require(runCatching { UUID.fromString(id) }.isSuccess) { "文件夹无效。" }
        require(name == name.trim() && name.codePointCount(0, name.length) in 1..50) { "文件夹无效。" }
        require(createdAtEpochMillis >= 0 && updatedAtEpochMillis >= createdAtEpochMillis) { "文件夹无效。" }
        parentFolderId?.let { require(runCatching { UUID.fromString(it) }.isSuccess && it != id) { "文件夹无效。" } }
    }

    override fun toString(): String = "DesktopFolder(name=redacted, kind=$kind)"

    companion object {
        fun create(name: String, kind: DesktopFolderKind, clock: Clock = Clock.systemUTC(), parentFolderId: String? = null): DesktopFolder {
            val now = clock.millis()
            return DesktopFolder(UUID.randomUUID().toString(), name.trim(), kind, now, now, parentFolderId)
        }
    }
}
