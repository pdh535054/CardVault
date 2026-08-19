package com.pdh.cardvault.ui.card

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

/** A resolved, fully local card cover style. */
@Immutable
data class CardTemplateSpec(
    val id: String,
    @param:StringRes val displayNameRes: Int,
    val issuerStorageLabel: String,
    val colorId: String,
    val patternId: String,
    val gradientStart: Color,
    val gradientEnd: Color,
    val accent: Color,
    val foreground: Color,
    val motif: CardMotif,
    val nicknameStyle: CardNicknameStyleSpec,
    val nicknameColor: CardNicknameColorSpec,
)

@Immutable
data class CardCoverColorSpec(
    val id: String,
    @param:StringRes val displayNameRes: Int,
    val gradientStart: Color,
    val gradientEnd: Color,
    val accent: Color,
    val foreground: Color,
)

@Immutable
data class CardCoverPatternSpec(
    val id: String,
    @param:StringRes val displayNameRes: Int,
    val motif: CardMotif,
)

@Immutable
data class CardNicknameStyleSpec(
    val id: String,
    @param:StringRes val displayNameRes: Int,
    val fontFamily: FontFamily,
    val fontWeight: FontWeight,
    val fontStyle: FontStyle,
    val letterSpacing: TextUnit,
    val artwork: CardNicknameArtwork,
)

@Immutable
data class CardNicknameColorSpec(
    val id: String,
    @param:StringRes val displayNameRes: Int,
    /** Null means adapt to the selected card palette's foreground color. */
    val color: Color?,
)

@Immutable
data class CardCoverSelection(
    val colorId: String,
    val patternId: String,
    val nicknameStyleId: String,
    val nicknameColorId: String,
)

enum class CardNicknameArtwork {
    ModernRule,
    EditorialStack,
    SignatureSweep,
    TechnicalTicks,
    WideDot,
    OffsetEcho,
    Monogram,
    SplitTone,
    OrbitAccent,
}

enum class CardMotif {
    Plain,
    DiagonalShards,
    OrbitLines,
    FacetedField,
    StarField,
    FlowingRibbons,
    OffsetHalos,
    FineGrid,
    SoftWaves,
    DotMatrix,
    CircuitPaths,
    VaultArches,
    ObsidianContours,
    SilverFrame,
    MidnightBeam,
    SageLoops,
    SandTiles,
    LilacAura,
    MonochromeArcs,
    SparseGrid,
    ContinuousWave,
}
