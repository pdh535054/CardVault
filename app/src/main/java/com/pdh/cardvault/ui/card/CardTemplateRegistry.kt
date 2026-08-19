package com.pdh.cardvault.ui.card

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pdh.cardvault.R
import java.util.Locale
import kotlin.math.pow

/**
 * Registry for locally drawn card covers.
 *
 * A persisted template ID contains only validated local style identifiers. No image, URI,
 * brand artwork or external resource is referenced by the ID.
 */
object CardTemplateRegistry {
    const val DEFAULT_COLOR_ID = "obsidian"
    const val DEFAULT_PATTERN_ID = "contours"
    const val DEFAULT_NICKNAME_STYLE_ID = "modern"
    const val DEFAULT_NICKNAME_COLOR_ID = "auto"
    const val DEFAULT_TEMPLATE_ID = "custom:v2:obsidian:contours:modern:auto"

    private const val TEMPLATE_PREFIX = "custom"
    private const val TEMPLATE_VERSION_PRESET = "v2"
    private const val TEMPLATE_VERSION_CUSTOM_COLOR = "v3"
    private const val CUSTOM_COLOR_PREFIX = "rgb-"
    private const val ISSUER_STORAGE_LABEL = "CardVault"
    private const val MIN_TEXT_CONTRAST = 4.5
    private val CUSTOM_COLOR_ID = Regex("^rgb-[0-9A-F]{6}$")

    val colors: List<CardCoverColorSpec> = listOf(
        CardCoverColorSpec(
            id = "obsidian",
            displayNameRes = R.string.card_color_obsidian,
            gradientStart = Color(0xFF292A2E),
            gradientEnd = Color(0xFF090A0C),
            accent = Color(0xFFD7D9DF),
            foreground = Color(0xFFF7F7FA),
        ),
        CardCoverColorSpec(
            id = "midnight",
            displayNameRes = R.string.card_color_midnight,
            gradientStart = Color(0xFF244B78),
            gradientEnd = Color(0xFF071526),
            accent = Color(0xFF87C7FF),
            foreground = Color(0xFFF3F8FF),
        ),
        CardCoverColorSpec(
            id = "jade",
            displayNameRes = R.string.card_color_jade,
            gradientStart = Color(0xFF247A68),
            gradientEnd = Color(0xFF082C29),
            accent = Color(0xFF92E5C5),
            foreground = Color(0xFFF2FFF9),
        ),
        CardCoverColorSpec(
            id = "garnet",
            displayNameRes = R.string.card_color_garnet,
            gradientStart = Color(0xFF8F3948),
            gradientEnd = Color(0xFF2A0710),
            accent = Color(0xFFFFA7B2),
            foreground = Color(0xFFFFF5F6),
        ),
        CardCoverColorSpec(
            id = "champagne",
            displayNameRes = R.string.card_color_champagne,
            gradientStart = Color(0xFFE4C98E),
            gradientEnd = Color(0xFFA67D3F),
            accent = Color(0xFFFFE7B7),
            foreground = Color(0xFF21170B),
        ),
        CardCoverColorSpec(
            id = "silver",
            displayNameRes = R.string.card_color_silver,
            gradientStart = Color(0xFFF2F4F7),
            gradientEnd = Color(0xFF9DA5B0),
            accent = Color(0xFFFFFFFF),
            foreground = Color(0xFF17191D),
        ),
        CardCoverColorSpec(
            id = "violet",
            displayNameRes = R.string.card_color_violet,
            gradientStart = Color(0xFF8066B8),
            gradientEnd = Color(0xFF24143F),
            accent = Color(0xFFD7BDFF),
            foreground = Color(0xFFF9F4FF),
        ),
        CardCoverColorSpec(
            id = "terracotta",
            displayNameRes = R.string.card_color_terracotta,
            gradientStart = Color(0xFFC46F55),
            gradientEnd = Color(0xFF54261D),
            accent = Color(0xFFFFC6A8),
            foreground = Color(0xFFFFF7F2),
        ),
        CardCoverColorSpec(
            id = "carbon",
            displayNameRes = R.string.card_color_carbon,
            gradientStart = Color(0xFF555C68),
            gradientEnd = Color(0xFF1B1F26),
            accent = Color(0xFFAAB2C0),
            foreground = Color(0xFFF7F8FA),
        ),
        CardCoverColorSpec(
            id = "cobalt",
            displayNameRes = R.string.card_color_cobalt,
            gradientStart = Color(0xFF3568D4),
            gradientEnd = Color(0xFF10245E),
            accent = Color(0xFFA9C6FF),
            foreground = Color(0xFFF7F9FF),
        ),
        CardCoverColorSpec(
            id = "glacier",
            displayNameRes = R.string.card_color_glacier,
            gradientStart = Color(0xFFC1EDF2),
            gradientEnd = Color(0xFF69A8BC),
            accent = Color(0xFFE8FCFF),
            foreground = Color(0xFF102D38),
        ),
        CardCoverColorSpec(
            id = "aurora",
            displayNameRes = R.string.card_color_aurora,
            gradientStart = Color(0xFF24A49E),
            gradientEnd = Color(0xFF073C49),
            accent = Color(0xFF8AF0DD),
            foreground = Color(0xFFF1FFFD),
        ),
        CardCoverColorSpec(
            id = "forest",
            displayNameRes = R.string.card_color_forest,
            gradientStart = Color(0xFF527A5E),
            gradientEnd = Color(0xFF173A2C),
            accent = Color(0xFFAAD8AF),
            foreground = Color(0xFFF4FFF5),
        ),
        CardCoverColorSpec(
            id = "coral",
            displayNameRes = R.string.card_color_coral,
            gradientStart = Color(0xFFF18A75),
            gradientEnd = Color(0xFF87352F),
            accent = Color(0xFFFFC1AE),
            foreground = Color(0xFFFFF7F4),
        ),
        CardCoverColorSpec(
            id = "pearl",
            displayNameRes = R.string.card_color_pearl,
            gradientStart = Color(0xFFFFFAF3),
            gradientEnd = Color(0xFFD9CCC2),
            accent = Color(0xFFFFFFFF),
            foreground = Color(0xFF29221F),
        ),
        CardCoverColorSpec(
            id = "magenta",
            displayNameRes = R.string.card_color_magenta,
            gradientStart = Color(0xFFB34D87),
            gradientEnd = Color(0xFF43162F),
            accent = Color(0xFFFFA7D0),
            foreground = Color(0xFFFFF5FA),
        ),
    )

    val patterns: List<CardCoverPatternSpec> = listOf(
        CardCoverPatternSpec("plain", R.string.card_pattern_plain, CardMotif.Plain),
        CardCoverPatternSpec("contours", R.string.card_pattern_contours, CardMotif.ObsidianContours),
        CardCoverPatternSpec("orbits", R.string.card_pattern_orbits, CardMotif.OrbitLines),
        CardCoverPatternSpec("waves", R.string.card_pattern_waves, CardMotif.SoftWaves),
        CardCoverPatternSpec("stars", R.string.card_pattern_stars, CardMotif.StarField),
        CardCoverPatternSpec("facets", R.string.card_pattern_facets, CardMotif.FacetedField),
        CardCoverPatternSpec("grid", R.string.card_pattern_grid, CardMotif.FineGrid),
        CardCoverPatternSpec("shards", R.string.card_pattern_shards, CardMotif.DiagonalShards),
        CardCoverPatternSpec("circuit", R.string.card_pattern_circuit, CardMotif.CircuitPaths),
        CardCoverPatternSpec("ribbons", R.string.card_pattern_ribbons, CardMotif.FlowingRibbons),
        CardCoverPatternSpec("halos", R.string.card_pattern_halos, CardMotif.OffsetHalos),
        CardCoverPatternSpec("dots", R.string.card_pattern_dots, CardMotif.DotMatrix),
        CardCoverPatternSpec("arches", R.string.card_pattern_arches, CardMotif.VaultArches),
        CardCoverPatternSpec("frame", R.string.card_pattern_frame, CardMotif.SilverFrame),
        CardCoverPatternSpec("loops", R.string.card_pattern_loops, CardMotif.SageLoops),
        CardCoverPatternSpec("tiles", R.string.card_pattern_tiles, CardMotif.SandTiles),
        CardCoverPatternSpec("arcs", R.string.card_pattern_arcs, CardMotif.MonochromeArcs),
        CardCoverPatternSpec(
            "continuous",
            R.string.card_pattern_continuous,
            CardMotif.ContinuousWave,
        ),
    )

    val nicknameStyles: List<CardNicknameStyleSpec> = listOf(
        CardNicknameStyleSpec(
            id = "modern",
            displayNameRes = R.string.card_name_style_modern,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            fontStyle = FontStyle.Normal,
            letterSpacing = (-0.35).sp,
            artwork = CardNicknameArtwork.ModernRule,
        ),
        CardNicknameStyleSpec(
            id = "editorial",
            displayNameRes = R.string.card_name_style_editorial,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Normal,
            letterSpacing = (-0.15).sp,
            artwork = CardNicknameArtwork.EditorialStack,
        ),
        CardNicknameStyleSpec(
            id = "elegant",
            displayNameRes = R.string.card_name_style_elegant,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontStyle = FontStyle.Italic,
            letterSpacing = 0.15.sp,
            artwork = CardNicknameArtwork.SignatureSweep,
        ),
        CardNicknameStyleSpec(
            id = "mono",
            displayNameRes = R.string.card_name_style_mono,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontStyle = FontStyle.Normal,
            letterSpacing = 1.1.sp,
            artwork = CardNicknameArtwork.TechnicalTicks,
        ),
        CardNicknameStyleSpec(
            id = "wide",
            displayNameRes = R.string.card_name_style_wide,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontStyle = FontStyle.Normal,
            letterSpacing = 1.8.sp,
            artwork = CardNicknameArtwork.WideDot,
        ),
        CardNicknameStyleSpec(
            id = "light",
            displayNameRes = R.string.card_name_style_light,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Light,
            fontStyle = FontStyle.Normal,
            letterSpacing = 0.45.sp,
            artwork = CardNicknameArtwork.OffsetEcho,
        ),
        CardNicknameStyleSpec(
            id = "monogram",
            displayNameRes = R.string.card_name_style_monogram,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Normal,
            letterSpacing = (-0.45).sp,
            artwork = CardNicknameArtwork.Monogram,
        ),
        CardNicknameStyleSpec(
            id = "split",
            displayNameRes = R.string.card_name_style_split,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
            fontStyle = FontStyle.Normal,
            letterSpacing = 0.1.sp,
            artwork = CardNicknameArtwork.SplitTone,
        ),
        CardNicknameStyleSpec(
            id = "orbit",
            displayNameRes = R.string.card_name_style_orbit,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            fontStyle = FontStyle.Italic,
            letterSpacing = 0.25.sp,
            artwork = CardNicknameArtwork.OrbitAccent,
        ),
    )

    val nicknameColors: List<CardNicknameColorSpec> = listOf(
        CardNicknameColorSpec("auto", R.string.card_name_color_auto, null),
        CardNicknameColorSpec("ivory", R.string.card_name_color_ivory, Color(0xFFFFF8E8)),
        CardNicknameColorSpec("ink", R.string.card_name_color_ink, Color(0xFF111318)),
        CardNicknameColorSpec("gold", R.string.card_name_color_gold, Color(0xFFFFD166)),
        CardNicknameColorSpec("silver", R.string.card_name_color_silver, Color(0xFFE3EAF2)),
        CardNicknameColorSpec("ice", R.string.card_name_color_ice, Color(0xFF87D9FF)),
        CardNicknameColorSpec("mint", R.string.card_name_color_mint, Color(0xFF8CE8C5)),
        CardNicknameColorSpec("rose", R.string.card_name_color_rose, Color(0xFFFF9DB6)),
        CardNicknameColorSpec("violet", R.string.card_name_color_violet, Color(0xFFD7B7FF)),
        CardNicknameColorSpec("coral", R.string.card_name_color_coral, Color(0xFFFFA080)),
    )

    private val colorsById = colors.associateBy(CardCoverColorSpec::id)
    private val patternsById = patterns.associateBy(CardCoverPatternSpec::id)
    private val nicknameStylesById = nicknameStyles.associateBy(CardNicknameStyleSpec::id)
    private val nicknameColorsById = nicknameColors.associateBy(CardNicknameColorSpec::id)

    /** A small curated gallery; the picker can still create every color/pattern combination. */
    val templates: List<CardTemplateSpec> = listOf(
        selection("obsidian", "contours"),
        selection("midnight", "orbits"),
        selection("jade", "waves"),
        selection("garnet", "shards"),
        selection("champagne", "grid"),
        selection("silver", "facets"),
        selection("violet", "stars"),
        selection("terracotta", "circuit"),
        selection("cobalt", "ribbons"),
        selection("glacier", "arcs"),
        selection("forest", "loops"),
        selection("magenta", "halos"),
    ).map(::resolve)

    val defaultTemplate: CardTemplateSpec = resolve(
        defaultSelection(),
    )

    init {
        require(colorsById.size == colors.size)
        require(patternsById.size == patterns.size)
        require(nicknameStylesById.size == nicknameStyles.size)
        require(nicknameColorsById.size == nicknameColors.size)
        require(defaultTemplate.id == DEFAULT_TEMPLATE_ID)
    }

    fun composeTemplateId(
        colorId: String,
        patternId: String,
        nicknameStyleId: String = DEFAULT_NICKNAME_STYLE_ID,
        nicknameColorId: String = DEFAULT_NICKNAME_COLOR_ID,
    ): String {
        require(colorId in colorsById || isCustomColorId(colorId)) {
            "Unsupported card color."
        }
        require(patternId in patternsById) { "Unsupported card pattern." }
        require(nicknameStyleId in nicknameStylesById) { "Unsupported nickname design." }
        require(nicknameColorId in nicknameColorsById || isCustomColorId(nicknameColorId)) {
            "Unsupported nickname color."
        }
        val version = if (
            isCustomColorId(colorId) || isCustomColorId(nicknameColorId)
        ) {
            TEMPLATE_VERSION_CUSTOM_COLOR
        } else {
            TEMPLATE_VERSION_PRESET
        }
        return "$TEMPLATE_PREFIX:$version:$colorId:$patternId:" +
            "$nicknameStyleId:$nicknameColorId"
    }

    fun customColorId(color: Color): String = CUSTOM_COLOR_PREFIX +
        String.format(Locale.ROOT, "%06X", color.toArgb() and 0x00FFFFFF)

    fun customColorForId(id: String): Color? = parseCustomColor(id)

    fun isCustomColorId(id: String): Boolean = parseCustomColor(id) != null

    fun findById(id: String): CardTemplateSpec? {
        val selection = parseCustomId(id) ?: legacySelection(id) ?: return null
        return resolve(selection, persistedId = id)
    }

    fun findOrDefault(id: String): CardTemplateSpec = findById(id) ?: defaultTemplate

    fun selectionFor(id: String): CardCoverSelection =
        parseCustomId(id) ?: legacySelection(id) ?: defaultSelection()

    fun issuerLabelFor(@Suppress("UNUSED_PARAMETER") id: String): String = ISSUER_STORAGE_LABEL

    private fun defaultSelection(): CardCoverSelection =
        CardCoverSelection(
            colorId = DEFAULT_COLOR_ID,
            patternId = DEFAULT_PATTERN_ID,
            nicknameStyleId = DEFAULT_NICKNAME_STYLE_ID,
            nicknameColorId = DEFAULT_NICKNAME_COLOR_ID,
        )

    private fun selection(
        colorId: String,
        patternId: String,
        nicknameStyleId: String = DEFAULT_NICKNAME_STYLE_ID,
        nicknameColorId: String = DEFAULT_NICKNAME_COLOR_ID,
    ): CardCoverSelection = CardCoverSelection(
        colorId = colorId,
        patternId = patternId,
        nicknameStyleId = nicknameStyleId,
        nicknameColorId = nicknameColorId,
    )

    private fun parseCustomId(id: String): CardCoverSelection? {
        val parts = id.split(':')
        val parsed = when {
            parts.size == 3 && parts[0] == TEMPLATE_PREFIX -> selection(
                colorId = parts[1],
                patternId = parts[2],
            ).takeIf { value -> value.colorId in colorsById }

            parts.size == 6 &&
                parts[0] == TEMPLATE_PREFIX &&
                parts[1] == TEMPLATE_VERSION_PRESET -> selection(
                    colorId = parts[2],
                    patternId = parts[3],
                    nicknameStyleId = parts[4],
                    nicknameColorId = parts[5],
                ).takeIf { value ->
                    value.colorId in colorsById && value.nicknameColorId in nicknameColorsById
                }

            parts.size == 6 &&
                parts[0] == TEMPLATE_PREFIX &&
                parts[1] == TEMPLATE_VERSION_CUSTOM_COLOR -> selection(
                colorId = parts[2],
                patternId = parts[3],
                nicknameStyleId = parts[4],
                nicknameColorId = parts[5],
                ).takeIf { value ->
                    val validCoverColor = value.colorId in colorsById ||
                        isCustomColorId(value.colorId)
                    val validNicknameColor = value.nicknameColorId in nicknameColorsById ||
                        isCustomColorId(value.nicknameColorId)
                    val usesCustomRgb = isCustomColorId(value.colorId) ||
                        isCustomColorId(value.nicknameColorId)
                    validCoverColor && validNicknameColor && usesCustomRgb
                }

            else -> return null
        } ?: return null
        if (
            parsed.patternId !in patternsById ||
            parsed.nicknameStyleId !in nicknameStylesById
        ) {
            return null
        }
        return parsed
    }

    private fun resolve(
        selection: CardCoverSelection,
        persistedId: String = composeTemplateId(
            colorId = selection.colorId,
            patternId = selection.patternId,
            nicknameStyleId = selection.nicknameStyleId,
            nicknameColorId = selection.nicknameColorId,
        ),
    ): CardTemplateSpec {
        val color = colorsById[selection.colorId]
            ?: customColorSpecV3(selection.colorId)
            ?: error("Validated card color is unavailable.")
        val pattern = patternsById.getValue(selection.patternId)
        val nicknameStyle = nicknameStylesById.getValue(selection.nicknameStyleId)
        val nicknameColor = nicknameColorsById[selection.nicknameColorId]
            ?: customNicknameColorSpecV3(selection.nicknameColorId)
            ?: error("Validated nickname color is unavailable.")
        return CardTemplateSpec(
            id = persistedId,
            displayNameRes = R.string.card_style_custom,
            issuerStorageLabel = ISSUER_STORAGE_LABEL,
            colorId = color.id,
            patternId = pattern.id,
            gradientStart = color.gradientStart,
            gradientEnd = color.gradientEnd,
            accent = color.accent,
            foreground = color.foreground,
            motif = pattern.motif,
            nicknameStyle = nicknameStyle,
            nicknameColor = nicknameColor,
        )
    }

    private fun legacySelection(id: String): CardCoverSelection? {
        return LEGACY_STYLE_MAPPINGS[id]
    }

    private fun parseCustomColor(id: String): Color? {
        if (!CUSTOM_COLOR_ID.matches(id)) return null
        val rgb = id.removePrefix(CUSTOM_COLOR_PREFIX).toIntOrNull(radix = 16) ?: return null
        return Color(0xFF000000.toInt() or rgb)
    }

    /**
     * Version 3 rendering is intentionally deterministic. If these blend rules ever change,
     * persisted IDs must move to a new template version rather than silently changing old cards.
     */
    private fun customColorSpecV3(id: String): CardCoverColorSpec? {
        val base = parseCustomColor(id) ?: return null
        val darkContrast = contrastRatio(Color.Black, base)
        val lightContrast = contrastRatio(Color.White, base)
        val foreground = if (darkContrast >= lightContrast) Color.Black else Color.White
        val darkerCandidate = blendRgb(base, Color.Black, targetPercent = 34)
        val lighterCandidate = blendRgb(base, Color.White, targetPercent = 24)
        val end = if (contrastRatio(foreground, darkerCandidate) >= MIN_TEXT_CONTRAST) {
            darkerCandidate
        } else {
            lighterCandidate
        }
        val accent = if (foreground == Color.Black) darkerCandidate else lighterCandidate
        return CardCoverColorSpec(
            id = id,
            displayNameRes = R.string.card_color_custom,
            gradientStart = base,
            gradientEnd = end,
            accent = accent,
            foreground = foreground,
        )
    }

    private fun customNicknameColorSpecV3(id: String): CardNicknameColorSpec? {
        val color = parseCustomColor(id) ?: return null
        return CardNicknameColorSpec(
            id = id,
            displayNameRes = R.string.card_color_custom,
            color = color,
        )
    }

    private fun blendRgb(source: Color, target: Color, targetPercent: Int): Color {
        require(targetPercent in 0..100)
        val sourceArgb = source.toArgb()
        val targetArgb = target.toArgb()
        val sourcePercent = 100 - targetPercent
        fun blendedChannel(shift: Int): Int {
            val sourceChannel = sourceArgb shr shift and 0xFF
            val targetChannel = targetArgb shr shift and 0xFF
            return (sourceChannel * sourcePercent + targetChannel * targetPercent + 50) / 100
        }
        return Color(
            0xFF000000.toInt() or
                (blendedChannel(16) shl 16) or
                (blendedChannel(8) shl 8) or
                blendedChannel(0),
        )
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = relativeLuminance(first)
        val secondLuminance = relativeLuminance(second)
        val lighter = maxOf(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        val argb = color.toArgb()
        fun linearChannel(shift: Int): Double {
            val value = (argb shr shift and 0xFF) / 255.0
            return if (value <= 0.04045) {
                value / 12.92
            } else {
                ((value + 0.055) / 1.055).pow(2.4)
            }
        }
        return 0.2126 * linearChannel(16) +
            0.7152 * linearChannel(8) +
            0.0722 * linearChannel(0)
    }

    /**
     * Explicit mappings keep an existing card's appearance stable when new colors or patterns
     * are inserted or the picker order changes in a future release.
     */
    private val LEGACY_STYLE_MAPPINGS: Map<String, CardCoverSelection> = mapOf(
        "bybit_original" to selection("obsidian", "shards"),
        "bitget_original" to selection("midnight", "orbits"),
        "krak_original" to selection("violet", "facets"),
        "starry_blu_original" to selection("midnight", "stars"),
        "cmb_original" to selection("garnet", "waves"),
        "pokepay_original" to selection("violet", "orbits"),
        "pokepay_virtual_official" to selection("violet", "waves"),
        "bitget_wallet_original" to selection("midnight", "circuit"),
        "redotpay_original" to selection("obsidian", "contours"),
        "onekey_legacy_original" to selection("obsidian", "grid"),
        "metamask_card_original" to selection("garnet", "facets"),
        "crypto_com_original" to selection("jade", "grid"),
        "crypto_com_midnight_official" to selection("midnight", "plain"),
        "crypto_com_ruby_official" to selection("garnet", "contours"),
        "crypto_com_icy_official" to selection("silver", "facets"),
        "crypto_com_obsidian_official" to selection("obsidian", "contours"),
        "coinbase_card_original" to selection("midnight", "waves"),
        "wirex_original" to selection("jade", "circuit"),
        "nexo_original" to selection("midnight", "shards"),
        "gnosis_pay_original" to selection("jade", "grid"),
        "kast_original" to selection("obsidian", "facets"),
        "kast_x_premium_official" to selection("violet", "contours"),
        "kast_gold_official" to selection("champagne", "grid"),
        "etherfi_cash_original" to selection("champagne", "waves"),
        "fiat24_original" to selection("silver", "grid"),
        "kucard_original" to selection("midnight", "circuit"),
        "gate_card_original" to selection("obsidian", "circuit"),
        "gemini_card_original" to selection("silver", "stars"),
        "uphold_card_original" to selection("jade", "orbits"),
        "cl_card_original" to selection("obsidian", "grid"),
        "holyheld_original" to selection("violet", "waves"),
        "avalanche_card_original" to selection("garnet", "shards"),
        "plutus_paused_original" to selection("champagne", "facets"),
        "generic_black" to selection("obsidian", "plain"),
        "generic_blue" to selection("midnight", "orbits"),
        "generic_light" to selection("silver", "plain"),
        "generic_crypto" to selection("violet", "circuit"),
        "generic_bank" to selection("champagne", "grid"),
        "minimal_obsidian" to selection("obsidian", "contours"),
        "minimal_silver" to selection("silver", "facets"),
        "minimal_midnight" to selection("midnight", "plain"),
        "minimal_sage" to selection("jade", "waves"),
        "minimal_sand" to selection("champagne", "plain"),
        "minimal_lilac" to selection("violet", "orbits"),
        "pattern_arc" to selection("obsidian", "orbits"),
        "pattern_grid" to selection("midnight", "grid"),
        "pattern_wave" to selection("jade", "waves"),
    )

    internal val legacyTemplateIds: Set<String> = LEGACY_STYLE_MAPPINGS.keys

}
