package com.pdh.cardvault.wallet

object FolderAppearance {
    const val DEFAULT_COLOR_RGB: Int = 0x3A527C
    val presetColors: List<Int> = listOf(
        DEFAULT_COLOR_RGB, 0x1E293B, 0x5856D6, 0x007AFF, 0x008C95, 0x23815C,
        0x8FBC8F, 0xB88B36, 0xC96737, 0xB84759, 0x9A577C, 0xE5DED4,
    )
    fun requireValidColor(colorRgb: Int) {
        require(colorRgb in 0..0xFFFFFF) { "Invalid folder color." }
    }
}
