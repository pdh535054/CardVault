package com.pdh.cardvault.desktop.model

import java.util.Locale
import kotlin.math.roundToInt

/** Lossless adapter for Android's locally rendered `custom:v2/v3` cover identifiers. */
object AndroidTemplateStyleCodec {
    private data class Palette(val start: Int, val end: Int, val accent: Int, val foreground: Int)

    private val palettes = mapOf(
        "obsidian" to Palette(0xFF292A2E.toInt(), 0xFF090A0C.toInt(), 0xFFD7D9DF.toInt(), 0xFFF7F7FA.toInt()),
        "midnight" to Palette(0xFF244B78.toInt(), 0xFF071526.toInt(), 0xFF87C7FF.toInt(), 0xFFF3F8FF.toInt()),
        "jade" to Palette(0xFF247A68.toInt(), 0xFF082C29.toInt(), 0xFF92E5C5.toInt(), 0xFFF2FFF9.toInt()),
        "garnet" to Palette(0xFF8F3948.toInt(), 0xFF2A0710.toInt(), 0xFFFFA7B2.toInt(), 0xFFFFF5F6.toInt()),
        "champagne" to Palette(0xFFE4C98E.toInt(), 0xFFA67D3F.toInt(), 0xFFFFE7B7.toInt(), 0xFF21170B.toInt()),
        "silver" to Palette(0xFFF2F4F7.toInt(), 0xFF9DA5B0.toInt(), 0xFFFFFFFF.toInt(), 0xFF17191D.toInt()),
        "violet" to Palette(0xFF8066B8.toInt(), 0xFF24143F.toInt(), 0xFFD7BDFF.toInt(), 0xFFF9F4FF.toInt()),
        "terracotta" to Palette(0xFFC46F55.toInt(), 0xFF54261D.toInt(), 0xFFFFC6A8.toInt(), 0xFFFFF7F2.toInt()),
        "carbon" to Palette(0xFF555C68.toInt(), 0xFF1B1F26.toInt(), 0xFFAAB2C0.toInt(), 0xFFF7F8FA.toInt()),
        "cobalt" to Palette(0xFF3568D4.toInt(), 0xFF10245E.toInt(), 0xFFA9C6FF.toInt(), 0xFFF7F9FF.toInt()),
        "glacier" to Palette(0xFFC1EDF2.toInt(), 0xFF69A8BC.toInt(), 0xFFE8FCFF.toInt(), 0xFF102D38.toInt()),
        "aurora" to Palette(0xFF24A49E.toInt(), 0xFF073C49.toInt(), 0xFF8AF0DD.toInt(), 0xFFF1FFFD.toInt()),
        "forest" to Palette(0xFF527A5E.toInt(), 0xFF173A2C.toInt(), 0xFFAAD8AF.toInt(), 0xFFF4FFF5.toInt()),
        "coral" to Palette(0xFFF18A75.toInt(), 0xFF87352F.toInt(), 0xFFFFC1AE.toInt(), 0xFFFFF7F4.toInt()),
        "pearl" to Palette(0xFFFFFAF3.toInt(), 0xFFD9CCC2.toInt(), 0xFFFFFFFF.toInt(), 0xFF29221F.toInt()),
        "magenta" to Palette(0xFFB34D87.toInt(), 0xFF43162F.toInt(), 0xFFFFA7D0.toInt(), 0xFFFFF5FA.toInt()),
    )
    private val nameColors = mapOf(
        "ivory" to 0xFFFFF8E8.toInt(), "ink" to 0xFF111318.toInt(), "gold" to 0xFFFFD166.toInt(),
        "silver" to 0xFFE3EAF2.toInt(), "ice" to 0xFF87D9FF.toInt(), "mint" to 0xFF8CE8C5.toInt(),
        "rose" to 0xFFFF9DB6.toInt(), "violet" to 0xFFD7B7FF.toInt(), "coral" to 0xFFFFA080.toInt(),
    )

    fun decode(templateId: String): CardCoverStyle {
        val parts = templateId.split(':')
        val fallback = CardCoverStyle.Default.copy(sourceTemplateId = templateId.take(100))
        if (parts.size != 6 || parts[0] != "custom" || parts[1] !in setOf("v2", "v3")) return fallback
        val palette = palettes[parts[2]] ?: parseRgb(parts[2])?.let(::customPalette) ?: return fallback
        val pattern = PATTERNS[parts[3]] ?: return fallback
        val typography = TYPOGRAPHY[parts[4]] ?: return fallback
        val nicknameColor = if (parts[5] == "auto") palette.foreground else nameColors[parts[5]] ?: parseRgb(parts[5]) ?: return fallback
        return CardCoverStyle(
            startArgb = palette.start,
            endArgb = palette.end,
            accentArgb = palette.accent,
            pattern = pattern,
            typography = typography,
            nicknameArgb = nicknameColor,
            sourceTemplateId = templateId,
        )
    }

    fun encode(style: CardCoverStyle): String {
        style.sourceTemplateId?.takeIf(::isPreservable)?.let { return it }
        val paletteId = palettes.entries.firstOrNull { it.value.start == style.startArgb && it.value.end == style.endArgb }?.key
            ?: rgb(style.startArgb)
        val nameId = nameColors.entries.firstOrNull { it.value == style.nicknameArgb }?.key
            ?: palettes[paletteId]?.foreground?.takeIf { it == style.nicknameArgb }?.let { "auto" }
            ?: rgb(style.nicknameArgb)
        val version = if (paletteId.startsWith("rgb-") || nameId.startsWith("rgb-")) "v3" else "v2"
        return "custom:$version:$paletteId:${patternId(style.pattern)}:${typographyId(style.typography)}:$nameId"
    }

    private fun isPreservable(value: String): Boolean = value.length <= 100 &&
        (value.startsWith("custom:v2:") || value.startsWith("custom:v3:"))

    private fun parseRgb(value: String): Int? = value.takeIf { RGB.matches(it) }
        ?.removePrefix("rgb-")?.toIntOrNull(16)?.let { 0xFF000000.toInt() or it }

    private fun rgb(value: Int): String = "rgb-" + String.format(Locale.ROOT, "%06X", value and 0xFFFFFF)

    private fun customPalette(start: Int): Palette {
        val foreground = if (contrast(ColorBlack, start) >= contrast(ColorWhite, start)) ColorBlack else ColorWhite
        val darker = blend(start, ColorBlack, 0.34f)
        val lighter = blend(start, ColorWhite, 0.24f)
        return Palette(
            start = start,
            end = if (contrast(foreground, darker) >= 4.5) darker else lighter,
            accent = if (foreground == ColorBlack) darker else lighter,
            foreground = foreground,
        )
    }

    private fun patternId(value: CardPattern): String = if (value == CardPattern.Circuits) "circuit" else value.name.lowercase(Locale.ROOT)
    private fun typographyId(value: NicknameTypography): String = when (value) {
        NicknameTypography.Signature -> "elegant"
        NicknameTypography.Technical -> "mono"
        NicknameTypography.Compact -> "light"
        else -> value.name.lowercase(Locale.ROOT)
    }

    private fun blend(source: Int, target: Int, amount: Float): Int {
        fun channel(shift: Int): Int = ((source shr shift and 0xFF) + ((target shr shift and 0xFF) - (source shr shift and 0xFF)) * amount).roundToInt()
        return ColorBlack or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun luminance(argb: Int): Double {
        fun c(shift: Int): Double {
            val v = (argb shr shift and 0xFF) / 255.0
            return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * c(16) + 0.7152 * c(8) + 0.0722 * c(0)
    }

    private fun contrast(first: Int, second: Int): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private val RGB = Regex("^rgb-[0-9A-F]{6}$")
    private const val ColorBlack: Int = -0x1000000
    private const val ColorWhite: Int = -0x1
    private val PATTERNS = mapOf(
        "plain" to CardPattern.Plain, "contours" to CardPattern.Contours, "orbits" to CardPattern.Orbits,
        "waves" to CardPattern.Waves, "stars" to CardPattern.Stars, "facets" to CardPattern.Facets,
        "grid" to CardPattern.Grid, "shards" to CardPattern.Shards, "circuit" to CardPattern.Circuits,
        "ribbons" to CardPattern.Ribbons, "halos" to CardPattern.Halos, "dots" to CardPattern.Dots,
        "arches" to CardPattern.Arches, "frame" to CardPattern.Frame, "loops" to CardPattern.Loops,
        "tiles" to CardPattern.Tiles, "arcs" to CardPattern.Arcs, "continuous" to CardPattern.Continuous,
    )
    private val TYPOGRAPHY = mapOf(
        "modern" to NicknameTypography.Modern, "editorial" to NicknameTypography.Editorial,
        "elegant" to NicknameTypography.Signature, "mono" to NicknameTypography.Technical,
        "wide" to NicknameTypography.Wide, "light" to NicknameTypography.Compact,
        "monogram" to NicknameTypography.Monogram, "split" to NicknameTypography.Split,
        "orbit" to NicknameTypography.Orbit,
    )
}
