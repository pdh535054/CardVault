package com.pdh.cardvault.ui.card

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

/**
 * Renders the nickname once, using typography and layout only.
 *
 * The persisted style IDs remain unchanged so existing cards keep resolving, while every style
 * avoids badges, outlines, duplicated glyphs and decorative marks around the user's own name.
 */
@Composable
internal fun CardNicknameArtworkView(
    template: CardTemplateSpec,
    nickname: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val layout = nicknameLayout(template.nicknameStyle.artwork, compact)
    val textColor = template.nicknameColor.color ?: template.foreground
    val textStyle = MaterialTheme.typography.titleLarge.copy(
        color = textColor,
        fontFamily = template.nicknameStyle.fontFamily,
        fontWeight = template.nicknameStyle.fontWeight,
        fontStyle = template.nicknameStyle.fontStyle,
        fontSize = layout.fontSize,
        lineHeight = layout.lineHeight,
        letterSpacing = template.nicknameStyle.letterSpacing,
    )

    Box(
        modifier = modifier.clearAndSetSemantics { },
        contentAlignment = layout.alignment,
    ) {
        NicknameText(
            nickname = nickname,
            style = textStyle,
            widthFraction = layout.widthFraction,
            maxLines = layout.maxLines,
        )
    }
}

@Composable
private fun NicknameText(
    nickname: String,
    style: TextStyle,
    widthFraction: Float,
    maxLines: Int,
) {
    Text(
        text = nickname,
        modifier = Modifier.fillMaxWidth(widthFraction),
        style = style,
        textAlign = TextAlign.End,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private data class NicknameLayout(
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val widthFraction: Float,
    val maxLines: Int = 1,
    val alignment: Alignment = Alignment.CenterEnd,
)

private fun nicknameLayout(
    artwork: CardNicknameArtwork,
    compact: Boolean,
): NicknameLayout = when (artwork) {
    CardNicknameArtwork.ModernRule -> NicknameLayout(
        fontSize = if (compact) 16.sp else 22.sp,
        lineHeight = if (compact) 18.sp else 25.sp,
        widthFraction = 1f,
    )

    CardNicknameArtwork.EditorialStack -> NicknameLayout(
        fontSize = if (compact) 17.sp else 24.sp,
        lineHeight = if (compact) 19.sp else 27.sp,
        widthFraction = 0.92f,
    )

    CardNicknameArtwork.SignatureSweep -> NicknameLayout(
        fontSize = if (compact) 16.sp else 22.sp,
        lineHeight = if (compact) 19.sp else 26.sp,
        widthFraction = 0.96f,
    )

    CardNicknameArtwork.TechnicalTicks -> NicknameLayout(
        fontSize = if (compact) 14.sp else 18.sp,
        lineHeight = if (compact) 17.sp else 21.sp,
        widthFraction = 0.94f,
    )

    CardNicknameArtwork.WideDot -> NicknameLayout(
        fontSize = if (compact) 13.sp else 17.sp,
        lineHeight = if (compact) 16.sp else 20.sp,
        widthFraction = 1f,
    )

    CardNicknameArtwork.OffsetEcho -> NicknameLayout(
        fontSize = if (compact) 15.sp else 20.sp,
        lineHeight = if (compact) 18.sp else 23.sp,
        widthFraction = 0.88f,
    )

    CardNicknameArtwork.Monogram -> NicknameLayout(
        fontSize = if (compact) 18.sp else 26.sp,
        lineHeight = if (compact) 20.sp else 28.sp,
        widthFraction = 0.82f,
    )

    CardNicknameArtwork.SplitTone -> NicknameLayout(
        fontSize = if (compact) 13.sp else 18.sp,
        lineHeight = if (compact) 15.sp else 20.sp,
        widthFraction = 0.68f,
        maxLines = 2,
    )

    CardNicknameArtwork.OrbitAccent -> NicknameLayout(
        fontSize = if (compact) 15.sp else 21.sp,
        lineHeight = if (compact) 18.sp else 24.sp,
        widthFraction = 0.93f,
    )
}
