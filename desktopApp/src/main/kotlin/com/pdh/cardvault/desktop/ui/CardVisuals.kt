package com.pdh.cardvault.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdh.cardvault.desktop.model.CardCoverStyle
import com.pdh.cardvault.desktop.model.CardPattern
import com.pdh.cardvault.desktop.model.DesktopAddress
import com.pdh.cardvault.desktop.model.DesktopAddressCopyPart
import com.pdh.cardvault.desktop.model.DesktopCard
import com.pdh.cardvault.desktop.model.NicknameTypography
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val CardShape = RoundedCornerShape(28.dp)
internal const val STANDARD_CARD_ASPECT_RATIO = 85.60f / 53.98f

@Composable
fun CardFace(
    card: DesktopCard,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    CardSurface(style = card.style, modifier = modifier) {
        Box(Modifier.fillMaxSize().padding(if (compact) 18.dp else 26.dp)) {
            card.network?.let { network ->
                Text(
                    text = network.label,
                    color = Color(card.style.nicknameArgb),
                    fontSize = if (compact) 14.sp else 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = if (network.label == "VISA") 1.2.sp else 0.sp,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
            NicknameArtwork(
                nickname = card.nickname,
                style = card.style,
                compact = compact,
                modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.68f),
            )
            Row(
                modifier = Modifier.align(Alignment.BottomStart),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContactlessMark(Color(card.style.nicknameArgb), compact)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "••••  ${card.lastFour}",
                    color = Color(card.style.nicknameArgb).copy(alpha = 0.88f),
                    style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 1.1.sp,
                )
            }
        }
    }
}

@Composable
fun AddressFace(
    address: DesktopAddress,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    CardSurface(style = address.style, modifier = modifier) {
        Box(Modifier.fillMaxSize().padding(if (compact) 18.dp else 26.dp)) {
            Text(
                text = address.country,
                color = Color(address.style.nicknameArgb).copy(alpha = 0.76f),
                fontSize = if (compact) 12.sp else 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(0.28f),
            )
            NicknameArtwork(
                nickname = address.nickname,
                style = address.style,
                compact = compact,
                modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.68f),
            )
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = address.city,
                    color = Color(address.style.nicknameArgb),
                    fontSize = if (compact) 14.sp else 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = address.postalCode,
                    color = Color(address.style.nicknameArgb).copy(alpha = 0.64f),
                    fontSize = if (compact) 10.sp else 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun AddressBack(
    address: DesktopAddress,
    onCopy: (DesktopAddressCopyPart) -> Unit,
    modifier: Modifier = Modifier,
) {
    CardSurface(style = address.style, modifier = modifier) {
        val textColor = Color(address.style.nicknameArgb)
        Box(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 23.dp)) {
            Text(
                text = "复制完整地址",
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.14f))
                    .clickable { onCopy(DesktopAddressCopyPart.Complete) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                DesktopAddressCopyLine(
                    text = address.detailedAddress,
                    textColor = textColor,
                    emphasized = true,
                    maxLines = 3,
                    onClick = { onCopy(DesktopAddressCopyPart.DetailedAddress) },
                )
                address.other.takeIf(String::isNotBlank)?.let { other ->
                    DesktopAddressCopyLine(
                        text = other,
                        textColor = textColor,
                        maxLines = 2,
                        onClick = { onCopy(DesktopAddressCopyPart.Other) },
                    )
                }
                DesktopAddressCopyLine(
                    text = address.city,
                    textColor = textColor,
                    onClick = { onCopy(DesktopAddressCopyPart.City) },
                )
                DesktopAddressCopyLine(
                    text = address.postalCode,
                    textColor = textColor,
                    onClick = { onCopy(DesktopAddressCopyPart.PostalCode) },
                )
                address.country.takeIf(String::isNotBlank)?.let { country ->
                    DesktopAddressCopyLine(
                        text = country,
                        textColor = textColor,
                        onClick = { onCopy(DesktopAddressCopyPart.Country) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopAddressCopyLine(
    text: String,
    textColor: Color,
    onClick: () -> Unit,
    emphasized: Boolean = false,
    maxLines: Int = 1,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = textColor,
            fontSize = if (emphasized) 16.sp else 13.sp,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "⧉",
            color = textColor.copy(alpha = 0.56f),
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

internal fun desktopFlipSpecularIntensity(rotationDegrees: Float): Float {
    val normalized = ((rotationDegrees % 180f) + 180f) % 180f
    if (normalized <= 0.5f || normalized >= 179.5f) return 0f
    return abs(sin(normalized / 90.0 * PI)).toFloat().coerceIn(0f, 1f)
}

internal fun Modifier.desktopSpecularRim(
    cornerRadius: Dp = 28.dp,
    topAlpha: Float = 0.20f,
    midAlpha: Float = 0.06f,
    bottomAlpha: Float = 0.02f,
    strokeWidth: Dp = 1.dp,
): Modifier = drawWithCache {
    val strokePx = strokeWidth.toPx()
    val inset = strokePx * 0.5f
    val radiusPx = (cornerRadius.toPx() - inset).coerceAtLeast(0f)
    val rimBrush = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = topAlpha),
        0.48f to Color.White.copy(alpha = midAlpha),
        1.0f to Color.White.copy(alpha = bottomAlpha),
    )
    val stroke = Stroke(width = strokePx)
    onDrawWithContent {
        drawContent()
        drawRoundRect(
            brush = rimBrush,
            topLeft = Offset(inset, inset),
            size = Size(
                width = (size.width - strokePx).coerceAtLeast(0f),
                height = (size.height - strokePx).coerceAtLeast(0f),
            ),
            cornerRadius = CornerRadius(radiusPx, radiusPx),
            style = stroke,
        )
    }
}

@Composable
fun FlippableAddress(
    address: DesktopAddress,
    flipped: Boolean,
    onCopy: (DesktopAddressCopyPart) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 460f),
        label = "desktop-address-flip",
    )
    val flipScale = 0.965f + 0.035f * abs(cos(rotation / 180f * PI)).toFloat()
    val specular = desktopFlipSpecularIntensity(rotation)
    Box(
        modifier = modifier
            .aspectRatio(STANDARD_CARD_ASPECT_RATIO)
            .graphicsLayer {
                rotationY = rotation
                scaleX = flipScale
                scaleY = flipScale
                cameraDistance = 28f * density
                shape = CardShape
                clip = true
            }
            .drawWithContent {
                drawContent()
                if (specular > 0.004f) {
                    val sweepCenter = (rotation / 180f).coerceIn(0f, 1f)
                    val bandSpan = 0.42f * size.width
                    val cx = size.width * sweepCenter
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.24f * specular),
                                Color.Transparent,
                            ),
                            start = Offset(cx - bandSpan, 0f),
                            end = Offset(cx + bandSpan, size.height * 0.58f),
                        ),
                    )
                }
            },
    ) {
        if (rotation <= 90f) {
            AddressFace(address, Modifier.fillMaxSize())
        } else {
            AddressBack(
                address = address,
                onCopy = onCopy,
                modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f },
            )
        }
    }
}

@Composable
fun CardBack(
    card: DesktopCard,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardSurface(style = card.style, modifier = modifier) {
        val fg = Color(card.style.nicknameArgb)
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 23.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = if (revealed) "◉  隐藏" else "◉  显示",
                    color = fg,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.22f))
                        .clickable(onClick = onToggleReveal)
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    text = if (revealed) card.groupedNumber else card.maskedNumber,
                    color = fg,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.3.sp,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⧉  复制卡号",
                        color = if (revealed) fg else fg.copy(alpha = 0.34f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = if (revealed) 0.20f else 0.08f))
                            .clickable(enabled = revealed, onClick = onCopy)
                            .padding(vertical = 5.dp, horizontal = 11.dp),
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(42.dp)) {
                    BackValue("有效期", if (revealed) card.expiryText else "••/••", card.style.nicknameArgb)
                    BackValue("CVV", if (revealed) card.cvv ?: "—" else "•••", card.style.nicknameArgb)
                }
                card.notes.takeIf(String::isNotBlank)?.let { notes ->
                    Text(
                        text = notes,
                        color = fg.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(138.dp).align(Alignment.Bottom),
                    )
                }
            }
        }
    }
}

@Composable
fun FlippableCard(
    card: DesktopCard,
    flipped: Boolean,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 460f),
        label = "desktop-card-flip",
    )
    val flipScale = 0.965f + 0.035f * abs(cos(rotation / 180f * PI)).toFloat()
    val specular = desktopFlipSpecularIntensity(rotation)
    Box(
        modifier = modifier
            .aspectRatio(STANDARD_CARD_ASPECT_RATIO)
            .graphicsLayer {
                rotationY = rotation
                scaleX = flipScale
                scaleY = flipScale
                cameraDistance = 28f * density
                shape = CardShape
                clip = true
            }
            .drawWithContent {
                drawContent()
                if (specular > 0.004f) {
                    val sweepCenter = (rotation / 180f).coerceIn(0f, 1f)
                    val bandSpan = 0.42f * size.width
                    val cx = size.width * sweepCenter
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.24f * specular),
                                Color.Transparent,
                            ),
                            start = Offset(cx - bandSpan, 0f),
                            end = Offset(cx + bandSpan, size.height * 0.58f),
                        ),
                    )
                }
            },
    ) {
        if (rotation <= 90f) {
            CardFace(card, Modifier.fillMaxSize())
        } else {
            CardBack(
                card = card,
                revealed = revealed,
                onToggleReveal = onToggleReveal,
                onCopy = onCopy,
                modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f },
            )
        }
    }
}

@Composable
internal fun CardSurface(
    style: CardCoverStyle,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(STANDARD_CARD_ASPECT_RATIO)
            .clip(CardShape)
            .cachedDesktopCardSurface(style),
    ) {
        content()
    }
}

private fun Modifier.cachedDesktopCardSurface(style: CardCoverStyle): Modifier = drawWithCache {
    val startColor = Color(style.startArgb)
    val endColor = Color(style.endArgb)
    val midColor = lerp(startColor, endColor, 0.48f)
    val coverBrush = Brush.linearGradient(
        0.0f to startColor,
        0.52f to midColor,
        1.0f to endColor,
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    val studioGlow = Brush.radialGradient(
        colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
        center = Offset(size.width * 0.18f, size.height * 0.12f),
        radius = size.maxDimension * 0.65f,
    )
    val patternDrawer = buildCachedDesktopPattern(style, size)
    val strokePx = 1.dp.toPx()
    val inset = strokePx * 0.5f
    val cornerPx = (28.dp.toPx() - inset).coerceAtLeast(0f)
    val rimBrush = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = 0.25f),
        0.48f to Color.White.copy(alpha = 0.09f),
        1.0f to Color.White.copy(alpha = 0.03f),
    )
    val rimStroke = Stroke(width = strokePx)
    onDrawWithContent {
        drawRect(brush = coverBrush)
        drawRect(brush = studioGlow)
        patternDrawer()
        drawContent()
        drawRoundRect(
            brush = rimBrush,
            topLeft = Offset(inset, inset),
            size = Size(
                width = (size.width - strokePx).coerceAtLeast(0f),
                height = (size.height - strokePx).coerceAtLeast(0f),
            ),
            cornerRadius = CornerRadius(cornerPx, cornerPx),
            style = rimStroke,
        )
    }
}

private fun buildCachedDesktopPattern(style: CardCoverStyle, size: Size): DrawScope.() -> Unit {
    val rawAccent = Color(style.accentArgb)
    val accent = rawAccent.copy(alpha = 0.15f)
    val thinStroke = (min(size.width, size.height) * 0.008f).coerceAtLeast(1f)
    when (style.pattern) {
        CardPattern.Plain -> return {}
        CardPattern.Frame -> {
            val frameStroke = Stroke(width = thinStroke * 0.9f)
            val inset = min(size.width, size.height) * 0.07f
            val corner = CornerRadius(min(size.width, size.height) * 0.08f)
            return {
                drawRoundRect(
                    color = rawAccent.copy(alpha = 0.30f),
                    topLeft = Offset(inset, inset),
                    size = Size((size.width - inset * 2f).coerceAtLeast(0f), (size.height - inset * 2f).coerceAtLeast(0f)),
                    cornerRadius = corner,
                    style = frameStroke,
                )
                repeat(3) { index ->
                    drawCircle(
                        color = rawAccent.copy(alpha = 0.28f + index * 0.12f),
                        radius = min(size.width, size.height) * 0.012f,
                        center = Offset(size.width * (0.76f + index * 0.055f), size.height * 0.22f),
                    )
                }
            }
        }
        CardPattern.Contours -> {
            val stroke = Stroke(width = 1.2f)
            return {
                repeat(6) { index ->
                    drawOval(
                        color = accent.copy(alpha = 0.07f + index * 0.015f),
                        topLeft = Offset(
                            size.width * (0.18f + index * 0.06f),
                            -size.height * (0.36f - index * 0.05f),
                        ),
                        size = Size(
                            size.width * (0.88f - index * 0.06f),
                            size.height * (1.25f - index * 0.08f),
                        ),
                        style = stroke,
                    )
                }
            }
        }
        CardPattern.Shards, CardPattern.Facets -> {
            val path = Path().apply {
                moveTo(size.width * 0.32f, 0f)
                lineTo(size.width * 0.72f, 0f)
                lineTo(size.width * 0.51f, size.height)
                lineTo(size.width * 0.08f, size.height)
                close()
            }
            val slashColor = accent.copy(alpha = 0.22f)
            return {
                drawPath(path, accent)
                drawLine(slashColor, Offset(size.width * 0.74f, 0f), Offset(size.width * 0.45f, size.height), 2f)
            }
        }
        CardPattern.Grid, CardPattern.Tiles -> {
            return {
                repeat(11) { index ->
                    val x = size.width * index / 10f
                    drawLine(accent, Offset(x, 0f), Offset(x, size.height), 0.8f)
                }
                repeat(7) { index ->
                    val y = size.height * index / 6f
                    drawLine(accent, Offset(0f, y), Offset(size.width, y), 0.8f)
                }
            }
        }
        CardPattern.Orbits, CardPattern.Loops -> {
            val stroke = Stroke(1.3f)
            return {
                repeat(4) { index ->
                    drawOval(
                        accent.copy(alpha = 0.16f - index * 0.02f),
                        topLeft = Offset(
                            size.width * (0.55f - index * 0.09f),
                            size.height * (-0.2f + index * 0.07f),
                        ),
                        size = Size(
                            size.width * (0.45f + index * 0.14f),
                            size.height * (0.82f + index * 0.2f),
                        ),
                        style = stroke,
                    )
                }
            }
        }
        CardPattern.Waves, CardPattern.Continuous -> {
            val paths = List(5) { index ->
                Path().apply {
                    moveTo(-20f, size.height * (0.26f + index * 0.13f))
                    cubicTo(
                        size.width * 0.28f,
                        size.height * (0.02f + index * 0.14f),
                        size.width * 0.63f,
                        size.height * (0.58f + index * 0.07f),
                        size.width + 20f,
                        size.height * (0.22f + index * 0.11f),
                    )
                }
            }
            val stroke = Stroke(1.4f)
            return {
                paths.forEach { path -> drawPath(path, accent, style = stroke) }
            }
        }
        CardPattern.Ribbons -> {
            val path = Path().apply {
                moveTo(-20f, size.height * 0.72f)
                cubicTo(
                    size.width * 0.25f,
                    size.height * 0.18f,
                    size.width * 0.64f,
                    size.height * 1.08f,
                    size.width + 20f,
                    size.height * 0.26f,
                )
            }
            val ribbonColor = accent.copy(alpha = 0.23f)
            val stroke = Stroke(size.height * 0.22f, cap = StrokeCap.Round)
            return {
                drawPath(path, ribbonColor, style = stroke)
            }
        }
        CardPattern.Stars -> {
            return {
                repeat(44) { index ->
                    val x = ((index * 47) % 101) / 100f * size.width
                    val y = ((index * 71) % 97) / 96f * size.height
                    drawCircle(accent.copy(alpha = 0.08f + (index % 4) * 0.04f), 0.8f + index % 3, Offset(x, y))
                }
            }
        }
        CardPattern.Circuits -> {
            return {
                repeat(7) { index ->
                    val y = size.height * (0.14f + index * 0.12f)
                    val stop = size.width * (0.35f + (index % 4) * 0.15f)
                    drawLine(accent, Offset(0f, y), Offset(stop, y), 1.2f)
                    drawCircle(accent, 2.2f, Offset(stop, y))
                }
            }
        }
        CardPattern.Halos -> {
            return {
                repeat(4) { index ->
                    drawCircle(
                        accent.copy(alpha = 0.2f - index * 0.035f),
                        radius = min(size.width, size.height) * (0.18f + index * 0.13f),
                        center = Offset(size.width * 0.78f, size.height * 0.28f),
                        style = Stroke(width = 4f + index),
                    )
                }
            }
        }
        CardPattern.Dots -> {
            return {
                repeat(10) { row ->
                    repeat(18) { column ->
                        drawCircle(accent, 1.2f, Offset(column * size.width / 17f, row * size.height / 9f))
                    }
                }
            }
        }
        CardPattern.Arches, CardPattern.Arcs -> {
            val stroke = Stroke(1.2f)
            return {
                repeat(6) { index ->
                    val radius = size.height * (0.22f + index * 0.13f)
                    drawArc(
                        accent,
                        180f,
                        180f,
                        false,
                        Offset(size.width * 0.62f - radius, size.height * 0.54f - radius),
                        Size(radius * 2, radius * 2),
                        style = stroke,
                    )
                }
            }
        }
        CardPattern.Prism -> {
            val w = size.width
            val h = size.height
            val outerPrism = Path().apply {
                moveTo(w * 0.72f, h * 0.08f)
                lineTo(w * 0.96f, h * 0.46f)
                lineTo(w * 0.72f, h * 0.90f)
                lineTo(w * 0.48f, h * 0.46f)
                close()
            }
            val innerPrism = Path().apply {
                moveTo(w * 0.80f, h * 0.22f)
                lineTo(w * 0.99f, h * 0.58f)
                lineTo(w * 0.78f, h * 0.94f)
                lineTo(w * 0.60f, h * 0.56f)
                close()
            }
            val stroke = Stroke(width = thinStroke * 0.9f)
            return {
                drawPath(outerPrism, rawAccent.copy(alpha = 0.13f))
                drawPath(innerPrism, rawAccent.copy(alpha = 0.10f))
                drawPath(outerPrism, rawAccent.copy(alpha = 0.34f), style = stroke)
                drawLine(rawAccent.copy(alpha = 0.26f), Offset(w * 0.72f, h * 0.08f), Offset(w * 0.72f, h * 0.90f), thinStroke * 0.8f)
                drawLine(rawAccent.copy(alpha = 0.22f), Offset(w * 0.48f, h * 0.46f), Offset(w * 0.96f, h * 0.46f), thinStroke * 0.8f)
            }
        }
        CardPattern.Sunburst -> {
            val minDim = min(size.width, size.height)
            val maxDim = maxOf(size.width, size.height)
            val center = Offset(size.width * 0.86f, size.height * 0.86f)
            val innerR = minDim * 0.20f
            val outerR = maxDim * 0.78f
            val rays = List(13) { index ->
                val angle = (-178.0 + index * 7.8) * PI / 180.0
                val cosA = cos(angle).toFloat()
                val sinA = sin(angle).toFloat()
                Offset(center.x + cosA * innerR, center.y + sinA * innerR) to
                    Offset(center.x + cosA * outerR, center.y + sinA * outerR)
            }
            val ringStroke = Stroke(width = thinStroke)
            return {
                drawCircle(rawAccent.copy(alpha = 0.18f), radius = innerR * 0.76f, center = center)
                drawCircle(rawAccent.copy(alpha = 0.28f), radius = innerR, center = center, style = ringStroke)
                rays.forEachIndexed { index, (s, e) ->
                    drawLine(
                        color = rawAccent.copy(alpha = if (index % 2 == 0) 0.26f else 0.13f),
                        start = s,
                        end = e,
                        strokeWidth = if (index % 2 == 0) thinStroke * 1.1f else thinStroke * 0.65f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        CardPattern.Mountains -> {
            val w = size.width
            val h = size.height
            val backRange = Path().apply {
                moveTo(w * 0.18f, h); lineTo(w * 0.46f, h * 0.46f); lineTo(w * 0.64f, h * 0.68f)
                lineTo(w * 0.84f, h * 0.36f); lineTo(w * 1.04f, h * 0.62f); lineTo(w * 1.04f, h); close()
            }
            val midRange = Path().apply {
                moveTo(w * 0.30f, h); lineTo(w * 0.58f, h * 0.56f); lineTo(w * 0.75f, h * 0.76f)
                lineTo(w * 0.93f, h * 0.50f); lineTo(w * 1.04f, h * 0.68f); lineTo(w * 1.04f, h); close()
            }
            val frontRange = Path().apply {
                moveTo(w * 0.44f, h); lineTo(w * 0.70f, h * 0.68f); lineTo(w * 0.86f, h * 0.84f)
                lineTo(w * 1.02f, h * 0.66f); lineTo(w * 1.02f, h); close()
            }
            val moonCenter = Offset(w * 0.84f, h * 0.22f)
            val moonRadius = min(w, h) * 0.075f
            val haloStroke = Stroke(width = thinStroke * 0.8f)
            return {
                drawCircle(rawAccent.copy(alpha = 0.22f), radius = moonRadius, center = moonCenter)
                drawCircle(rawAccent.copy(alpha = 0.16f), radius = moonRadius * 1.48f, center = moonCenter, style = haloStroke)
                drawPath(backRange, rawAccent.copy(alpha = 0.09f))
                drawPath(midRange, rawAccent.copy(alpha = 0.15f))
                drawPath(frontRange, rawAccent.copy(alpha = 0.22f))
            }
        }
        CardPattern.Lotus -> {
            val minDim = min(size.width, size.height)
            val center = Offset(size.width * 0.80f, size.height * 0.54f)
            val petalLen = minDim * 0.34f
            val petalSpread = minDim * 0.13f
            val petals = List(8) { index ->
                val angle = index * (PI / 4.0)
                val cosA = cos(angle).toFloat()
                val sinA = sin(angle).toFloat()
                val perpX = -sinA
                val perpY = cosA
                val tip = Offset(center.x + cosA * petalLen, center.y + sinA * petalLen)
                val ctrl1 = Offset(center.x + cosA * petalLen * 0.52f + perpX * petalSpread, center.y + sinA * petalLen * 0.52f + perpY * petalSpread)
                val ctrl2 = Offset(center.x + cosA * petalLen * 0.52f - perpX * petalSpread, center.y + sinA * petalLen * 0.52f - perpY * petalSpread)
                Path().apply {
                    moveTo(center.x, center.y)
                    quadraticTo(ctrl1.x, ctrl1.y, tip.x, tip.y)
                    quadraticTo(ctrl2.x, ctrl2.y, center.x, center.y)
                    close()
                }
            }
            val stroke = Stroke(width = thinStroke * 0.9f)
            return {
                petals.forEachIndexed { index, petal ->
                    if (index % 2 == 0) drawPath(petal, rawAccent.copy(alpha = 0.08f))
                    drawPath(petal, rawAccent.copy(alpha = 0.26f), style = stroke)
                }
                drawCircle(rawAccent.copy(alpha = 0.30f), radius = petalLen * 0.26f, center = center, style = stroke)
                drawCircle(rawAccent.copy(alpha = 0.48f), radius = thinStroke * 2.2f, center = center)
            }
        }
        CardPattern.Constellation -> {
            val minDim = min(size.width, size.height)
            val nodes = listOf(
                Offset(size.width * 0.55f, size.height * 0.24f),
                Offset(size.width * 0.66f, size.height * 0.16f),
                Offset(size.width * 0.84f, size.height * 0.22f),
                Offset(size.width * 0.74f, size.height * 0.40f),
                Offset(size.width * 0.92f, size.height * 0.44f),
                Offset(size.width * 0.62f, size.height * 0.58f),
                Offset(size.width * 0.80f, size.height * 0.68f),
                Offset(size.width * 0.93f, size.height * 0.80f),
            )
            val edges = listOf(0 to 1, 1 to 2, 1 to 3, 2 to 4, 3 to 4, 3 to 5, 3 to 6, 6 to 7)
            val polaris = nodes[2]
            val rayLen = minDim * 0.055f
            return {
                edges.forEach { (a, b) ->
                    drawLine(rawAccent.copy(alpha = 0.24f), nodes[a], nodes[b], thinStroke * 0.85f, cap = StrokeCap.Round)
                }
                nodes.forEachIndexed { index, pt ->
                    val r = minDim * if (index == 2) 0.017f else 0.011f
                    drawCircle(rawAccent.copy(alpha = 0.18f), radius = r * 2.2f, center = pt)
                    drawCircle(rawAccent.copy(alpha = 0.62f), radius = r, center = pt)
                }
                drawLine(rawAccent.copy(alpha = 0.50f), Offset(polaris.x - rayLen, polaris.y), Offset(polaris.x + rayLen, polaris.y), thinStroke * 0.8f, cap = StrokeCap.Round)
                drawLine(rawAccent.copy(alpha = 0.50f), Offset(polaris.x, polaris.y - rayLen), Offset(polaris.x, polaris.y + rayLen), thinStroke * 0.8f, cap = StrokeCap.Round)
            }
        }
        CardPattern.Compass -> {
            val minDim = min(size.width, size.height)
            val center = Offset(size.width * 0.80f, size.height * 0.52f)
            val outerR = minDim * 0.36f
            val midR = minDim * 0.24f
            val innerR = minDim * 0.06f
            val ticks = List(12) { index ->
                val angle = index * (PI / 6.0)
                val cosA = cos(angle).toFloat()
                val sinA = sin(angle).toFloat()
                val tickIn = if (index % 3 == 0) outerR * 0.82f else outerR * 0.90f
                Offset(center.x + cosA * tickIn, center.y + sinA * tickIn) to Offset(center.x + cosA * outerR, center.y + sinA * outerR)
            }
            val needle = Path().apply {
                moveTo(center.x, center.y - outerR * 0.78f)
                lineTo(center.x + innerR, center.y - innerR)
                lineTo(center.x + outerR * 0.78f, center.y)
                lineTo(center.x + innerR, center.y + innerR)
                lineTo(center.x, center.y + outerR * 0.78f)
                lineTo(center.x - innerR, center.y + innerR)
                lineTo(center.x - outerR * 0.78f, center.y)
                lineTo(center.x - innerR, center.y - innerR)
                close()
            }
            val ringStroke = Stroke(width = thinStroke * 0.85f)
            return {
                drawCircle(rawAccent.copy(alpha = 0.24f), radius = outerR, center = center, style = ringStroke)
                drawCircle(rawAccent.copy(alpha = 0.18f), radius = midR, center = center, style = ringStroke)
                drawCircle(rawAccent.copy(alpha = 0.14f), radius = midR * 0.92f, center = Offset(center.x - outerR * 0.14f, center.y - outerR * 0.10f), style = ringStroke)
                ticks.forEach { (s, e) -> drawLine(rawAccent.copy(alpha = 0.30f), s, e, thinStroke * 0.8f) }
                drawPath(needle, rawAccent.copy(alpha = 0.16f))
                drawPath(needle, rawAccent.copy(alpha = 0.36f), style = ringStroke)
            }
        }
        CardPattern.Scales -> {
            val radius = min(size.width, size.height) * 0.15f
            val stroke = Stroke(width = thinStroke * 0.85f, cap = StrokeCap.Round)
            val centers = buildList {
                repeat(4) { row ->
                    val stagger = if (row % 2 == 1) radius else 0f
                    repeat(4) { col ->
                        add(Offset(size.width * 0.50f + col * radius * 1.92f + stagger, size.height * 0.34f + row * radius * 0.96f))
                    }
                }
            }
            return {
                centers.forEach { c ->
                    for (ring in 1..3) {
                        val r = radius * ring / 3f
                        drawArc(rawAccent.copy(alpha = 0.11f + ring * 0.055f), 195f, 150f, false, Offset(c.x - r, c.y - r), Size(r * 2f, r * 2f), style = stroke)
                    }
                }
            }
        }
        CardPattern.Honeycomb -> {
            val hexR = min(size.width, size.height) * 0.115f
            val dx = hexR * 1.5f
            val dy = hexR * 1.732f
            val stroke = Stroke(width = thinStroke * 0.9f)
            val hexagons = buildList {
                for (col in 0..3) {
                    for (row in 0..2) {
                        val cx = size.width * 0.56f + col * dx
                        val cy = size.height * 0.24f + row * dy + if (col % 2 == 1) dy * 0.5f else 0f
                        val hex = Path().apply {
                            repeat(6) { v ->
                                val a = v * (PI / 3.0)
                                val vx = cx + cos(a).toFloat() * hexR * 0.92f
                                val vy = cy + sin(a).toFloat() * hexR * 0.92f
                                if (v == 0) moveTo(vx, vy) else lineTo(vx, vy)
                            }
                            close()
                        }
                        add(hex to ((col + row) % 3 == 0))
                    }
                }
            }
            return {
                hexagons.forEach { (hex, filled) ->
                    if (filled) drawPath(hex, rawAccent.copy(alpha = 0.09f))
                    drawPath(hex, rawAccent.copy(alpha = 0.25f), style = stroke)
                }
            }
        }
        CardPattern.Chevrons -> {
            val w = size.width
            val h = size.height
            val chevrons = List(5) { index ->
                val x0 = w * (0.48f + index * 0.095f)
                Path().apply {
                    moveTo(x0, h * 0.14f)
                    lineTo(x0 + w * 0.11f, h * 0.50f)
                    lineTo(x0, h * 0.86f)
                }
            }
            val stroke = Stroke(width = thinStroke * 1.45f, cap = StrokeCap.Round)
            return {
                chevrons.forEachIndexed { index, path ->
                    drawPath(path, rawAccent.copy(alpha = 0.12f + index * 0.065f), style = stroke)
                }
            }
        }
        CardPattern.Silk -> {
            val w = size.width
            val h = size.height
            val strands = List(6) { strand ->
                val phase = strand * 0.42
                Path().apply {
                    val steps = 28
                    for (s in 0..steps) {
                        val t = s.toFloat() / steps
                        val x = w * t
                        val y = h * (0.54f + 0.24f * sin(t * PI * 2.2 + phase).toFloat() * (0.45f + 0.55f * t))
                        if (s == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
            }
            val stroke = Stroke(width = thinStroke * 0.8f, cap = StrokeCap.Round)
            return {
                strands.forEachIndexed { index, path ->
                    drawPath(path, rawAccent.copy(alpha = 0.11f + index * 0.032f), style = stroke)
                }
            }
        }
        CardPattern.Beams -> {
            val w = size.width
            val h = size.height
            val beams = List(4) { index ->
                val topX = w * (0.54f + index * 0.12f)
                val widthFrac = w * (0.09f - index * 0.012f)
                Path().apply {
                    moveTo(topX, 0f)
                    lineTo(topX + widthFrac, 0f)
                    lineTo(topX - w * 0.18f + widthFrac, h)
                    lineTo(topX - w * 0.18f, h)
                    close()
                }
            }
            val glowCenter = Offset(w * 0.82f, h * 0.24f)
            val glowRadius = (min(w, h) * 0.42f).coerceAtLeast(1f)
            val glowBrush = Brush.radialGradient(
                colors = listOf(rawAccent.copy(alpha = 0.26f), Color.Transparent),
                center = glowCenter,
                radius = glowRadius,
            )
            return {
                drawCircle(brush = glowBrush, radius = glowRadius, center = glowCenter)
                beams.forEachIndexed { index, beam ->
                    drawPath(beam, rawAccent.copy(alpha = 0.08f + index * 0.038f))
                }
            }
        }
        CardPattern.Monolith -> {
            val w = size.width
            val h = size.height
            val corner = CornerRadius(min(w, h) * 0.03f)
            val stroke = Stroke(width = thinStroke * 0.85f)
            return {
                repeat(3) { index ->
                    val slabW = w * 0.105f
                    val slabH = h * (0.42f + index * 0.18f)
                    val topLeft = Offset(w * (0.58f + index * 0.125f), h - slabH)
                    drawRoundRect(rawAccent.copy(alpha = 0.08f + index * 0.055f), topLeft = topLeft, size = Size(slabW, slabH + corner.y), cornerRadius = corner)
                    drawRoundRect(rawAccent.copy(alpha = 0.24f + index * 0.06f), topLeft = topLeft, size = Size(slabW, slabH + corner.y), cornerRadius = corner, style = stroke)
                }
            }
        }
    }
}

@Composable
internal fun NicknameArtwork(
    nickname: String,
    style: CardCoverStyle,
    compact: Boolean,
    modifier: Modifier,
) {
    val fontSize = when (style.typography) {
        NicknameTypography.Modern -> if (compact) 17.sp else 24.sp
        NicknameTypography.Editorial -> if (compact) 18.sp else 26.sp
        NicknameTypography.Signature -> if (compact) 17.sp else 25.sp
        NicknameTypography.Technical -> if (compact) 14.sp else 19.sp
        NicknameTypography.Wide -> if (compact) 15.sp else 21.sp
        NicknameTypography.Compact -> if (compact) 13.sp else 17.sp
        NicknameTypography.Monogram -> if (compact) 19.sp else 27.sp
        NicknameTypography.Split -> if (compact) 15.sp else 21.sp
        NicknameTypography.Orbit -> if (compact) 17.sp else 24.sp
    }
    val fontFamily = when (style.typography) {
        NicknameTypography.Technical, NicknameTypography.Compact -> FontFamily.Monospace
        NicknameTypography.Editorial, NicknameTypography.Split -> FontFamily.Serif
        else -> FontFamily.SansSerif
    }
    Text(
        text = nickname,
        modifier = modifier,
        color = Color(style.nicknameArgb),
        fontFamily = fontFamily,
        fontStyle = if (style.typography in setOf(NicknameTypography.Signature, NicknameTypography.Orbit)) FontStyle.Italic else FontStyle.Normal,
        fontWeight = when (style.typography) {
            NicknameTypography.Editorial -> FontWeight.Normal
            NicknameTypography.Compact -> FontWeight.Medium
            NicknameTypography.Monogram -> FontWeight.Black
            else -> FontWeight.SemiBold
        },
        fontSize = fontSize,
        letterSpacing = when (style.typography) {
            NicknameTypography.Wide -> 2.4.sp
            NicknameTypography.Technical -> 1.3.sp
            NicknameTypography.Compact -> (-0.25).sp
            NicknameTypography.Monogram -> (-0.45).sp
            NicknameTypography.Split -> 0.1.sp
            NicknameTypography.Orbit -> 0.25.sp
            else -> 0.sp
        },
        textAlign = TextAlign.End,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun BackValue(label: String, value: String, argb: Int) {
    Column {
        Text(label, color = Color(argb).copy(alpha = 0.55f), fontSize = 9.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = Color(argb), fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ContactlessMark(color: Color, compact: Boolean) {
    Canvas(Modifier.size(if (compact) 18.dp else 22.dp)) {
        repeat(3) { index ->
            val inset = index * size.minDimension * 0.17f
            drawArc(
                color = color.copy(alpha = 0.65f + index * 0.1f),
                startAngle = -55f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                style = Stroke(width = 1.6f, cap = StrokeCap.Round),
            )
        }
    }
}
