package com.pdh.cardvault.wallet

data class FolderDropHover(val folderId: String? = null, val progress: Float = 0f, val armed: Boolean = false)

/** Shared Android/desktop drag intent. Contains opaque folder IDs and pointer geometry only. */
class FolderDropIntent(private val dwellMillis: Long = 800L, private val slop: Float = 12f) {
    init { require(dwellMillis > 0 && slop > 0 && slop.isFinite()) }

    var hover: FolderDropHover = FolderDropHover()
        private set
    private var enteredAt = 0L
    private var lastTime = 0L
    private var anchorX = 0f
    private var anchorY = 0f

    fun update(folderId: String?, x: Float, y: Float, nowMillis: Long): FolderDropHover {
        if (folderId == null || !x.isFinite() || !y.isFinite() || nowMillis < 0) {
            cancel()
            return hover
        }
        val dx = x - anchorX
        val dy = y - anchorY
        if (folderId != hover.folderId || nowMillis < lastTime || dx * dx + dy * dy > slop * slop) {
            enteredAt = nowMillis
            anchorX = x
            anchorY = y
        }
        lastTime = nowMillis
        val progress = ((nowMillis - enteredAt).toFloat() / dwellMillis).coerceIn(0f, 1f)
        hover = FolderDropHover(folderId, progress, progress >= 1f)
        return hover
    }

    /** Release never starts/finishes a dwell: the armed feedback must already have been displayed. */
    fun release(folderUnderPointer: String?): String? {
        val result = hover.folderId.takeIf { hover.armed && it == folderUnderPointer }
        cancel()
        return result
    }

    fun cancel() {
        hover = FolderDropHover()
        enteredAt = 0L
        lastTime = 0L
        anchorX = 0f
        anchorY = 0f
    }
}
