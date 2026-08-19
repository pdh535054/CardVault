package com.pdh.cardvault.desktop.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdh.cardvault.desktop.model.CardCoverStyle
import com.pdh.cardvault.desktop.model.CardPattern
import com.pdh.cardvault.desktop.model.DesktopCard
import com.pdh.cardvault.desktop.model.DesktopAddress
import com.pdh.cardvault.desktop.model.NicknameTypography
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
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardSurface(style = address.style, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 23.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = address.country,
                    color = Color(address.style.nicknameArgb).copy(alpha = 0.62f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "复制完整地址",
                    color = Color(address.style.nicknameArgb),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onCopy)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = address.detailedAddress,
                    color = Color(address.style.nicknameArgb),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                address.other.takeIf(String::isNotBlank)?.let { other ->
                    Text(
                        text = other,
                        color = Color(address.style.nicknameArgb).copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BackValue("城市", address.city, address.style.nicknameArgb)
                BackValue("邮编", address.postalCode, address.style.nicknameArgb)
            }
        }
    }
}

@Composable
fun FlippableAddress(
    address: DesktopAddress,
    flipped: Boolean,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.84f),
    )
    Box(
        modifier = modifier.aspectRatio(STANDARD_CARD_ASPECT_RATIO).graphicsLayer {
            rotationY = rotation
            cameraDistance = 22f * density
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
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 23.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = if (revealed) "◉  隐藏" else "◉  显示",
                    color = Color(card.style.nicknameArgb),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(CircleShape)
                        .clickable(onClick = onToggleReveal)
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    text = if (revealed) card.groupedNumber else card.maskedNumber,
                    color = Color(card.style.nicknameArgb),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.3.sp,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "复制卡号",
                        color = if (revealed) Color(card.style.nicknameArgb) else Color(card.style.nicknameArgb).copy(alpha = 0.32f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape)
                            .clickable(enabled = revealed, onClick = onCopy)
                            .padding(vertical = 4.dp, horizontal = 2.dp),
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
                        color = Color(card.style.nicknameArgb).copy(alpha = 0.72f),
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
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.84f),
    )
    Box(
        modifier = modifier.aspectRatio(STANDARD_CARD_ASPECT_RATIO).graphicsLayer {
            rotationY = rotation
            cameraDistance = 22f * density
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
            .background(Brush.linearGradient(listOf(Color(style.startArgb), Color(style.endArgb))))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CardShape),
    ) {
        PatternCanvas(style, Modifier.fillMaxSize())
        content()
    }
}

@Composable
private fun PatternCanvas(style: CardCoverStyle, modifier: Modifier) {
    val accent = Color(style.accentArgb).copy(alpha = 0.15f)
    Canvas(modifier) {
        when (style.pattern) {
            CardPattern.Plain, CardPattern.Frame -> Unit
            CardPattern.Contours -> repeat(6) { index ->
                drawOval(
                    color = accent.copy(alpha = 0.07f + index * 0.015f),
                    topLeft = Offset(size.width * (0.18f + index * 0.06f), -size.height * (0.36f - index * 0.05f)),
                    size = Size(size.width * (0.88f - index * 0.06f), size.height * (1.25f - index * 0.08f)),
                    style = Stroke(width = 1.2f),
                )
            }
            CardPattern.Shards, CardPattern.Facets -> {
                val path = Path().apply {
                    moveTo(size.width * 0.32f, 0f)
                    lineTo(size.width * 0.72f, 0f)
                    lineTo(size.width * 0.51f, size.height)
                    lineTo(size.width * 0.08f, size.height)
                    close()
                }
                drawPath(path, accent)
                drawLine(accent.copy(alpha = 0.22f), Offset(size.width * 0.74f, 0f), Offset(size.width * 0.45f, size.height), 2f)
            }
            CardPattern.Grid, CardPattern.Tiles -> {
                repeat(11) { index ->
                    val x = size.width * index / 10f
                    drawLine(accent, Offset(x, 0f), Offset(x, size.height), 0.8f)
                }
                repeat(7) { index ->
                    val y = size.height * index / 6f
                    drawLine(accent, Offset(0f, y), Offset(size.width, y), 0.8f)
                }
            }
            CardPattern.Orbits, CardPattern.Loops -> repeat(4) { index ->
                drawOval(
                    accent.copy(alpha = 0.16f - index * 0.02f),
                    topLeft = Offset(size.width * (0.55f - index * 0.09f), size.height * (-0.2f + index * 0.07f)),
                    size = Size(size.width * (0.45f + index * 0.14f), size.height * (0.82f + index * 0.2f)),
                    style = Stroke(1.3f),
                )
            }
            CardPattern.Waves, CardPattern.Continuous -> repeat(5) { index ->
                val path = Path().apply {
                    moveTo(-20f, size.height * (0.26f + index * 0.13f))
                    cubicTo(
                        size.width * 0.28f, size.height * (0.02f + index * 0.14f),
                        size.width * 0.63f, size.height * (0.58f + index * 0.07f),
                        size.width + 20f, size.height * (0.22f + index * 0.11f),
                    )
                }
                drawPath(path, accent, style = Stroke(1.4f))
            }
            CardPattern.Ribbons -> {
                val path = Path().apply {
                    moveTo(-20f, size.height * 0.72f)
                    cubicTo(size.width * 0.25f, size.height * 0.18f, size.width * 0.64f, size.height * 1.08f, size.width + 20f, size.height * 0.26f)
                }
                drawPath(path, accent.copy(alpha = 0.23f), style = Stroke(size.height * 0.22f, cap = StrokeCap.Round))
            }
            CardPattern.Stars -> repeat(44) { index ->
                val x = ((index * 47) % 101) / 100f * size.width
                val y = ((index * 71) % 97) / 96f * size.height
                drawCircle(accent.copy(alpha = 0.08f + (index % 4) * 0.04f), 0.8f + index % 3, Offset(x, y))
            }
            CardPattern.Circuits -> repeat(7) { index ->
                val y = size.height * (0.14f + index * 0.12f)
                val stop = size.width * (0.35f + (index % 4) * 0.15f)
                drawLine(accent, Offset(0f, y), Offset(stop, y), 1.2f)
                drawCircle(accent, 2.2f, Offset(stop, y))
            }
            CardPattern.Halos -> repeat(4) { index ->
                drawCircle(
                    accent.copy(alpha = 0.2f - index * 0.035f),
                    radius = min(size.width, size.height) * (0.18f + index * 0.13f),
                    center = Offset(size.width * 0.78f, size.height * 0.28f),
                    style = Stroke(width = 4f + index),
                )
            }
            CardPattern.Dots -> repeat(10) { row -> repeat(18) { column ->
                drawCircle(accent, 1.2f, Offset(column * size.width / 17f, row * size.height / 9f))
            } }
            CardPattern.Arches, CardPattern.Arcs -> repeat(6) { index ->
                val radius = size.height * (0.22f + index * 0.13f)
                drawArc(accent, 180f, 180f, false, Offset(size.width * 0.62f - radius, size.height * 0.54f - radius), Size(radius * 2, radius * 2), style = Stroke(1.2f))
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
