package com.pdh.cardvault.ui.card

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdh.cardvault.R
import com.pdh.cardvault.domain.validation.CardNumberTools
import com.pdh.cardvault.presentation.CardDetailUiModel
import com.pdh.cardvault.presentation.CvvSaveStatusUi
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

@Composable
fun FlippableMaskedCardDetail(
    card: CardDetailUiModel,
    cvvHiddenLabel: String,
    cvvNotSavedLabel: String,
    showBack: Boolean,
    revealedCardNumber: String?,
    revealedCvv: String?,
    revealedExpiryText: String?,
    revealedExpired: Boolean,
    expiredLabel: String,
    contentDescriptionText: String,
    showBackActionLabel: String,
    showFrontActionLabel: String,
    revealActionLabel: String,
    hideActionLabel: String,
    copyActionLabel: String,
    onRevealSecrets: () -> Unit,
    onHideSecrets: () -> Unit,
    onCopyCardNumber: () -> Unit,
    onShowBackChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (showBack) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 320f),
        label = "card-detail-flip",
    )
    val density = LocalDensity.current.density
    val cardShape = MaterialTheme.shapes.extraLarge
    val template = CardTemplateRegistry.findOrDefault(card.cardTemplateId)
    val flipScale = 0.96f + 0.04f * abs(cos(rotation / 180f * PI)).toFloat()
    val flip = {
        if (showBack) {
            onHideSecrets()
            onShowBackChanged(false)
        } else {
            onShowBackChanged(true)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(CARD_ASPECT_RATIO)
            .graphicsLayer {
                rotationY = rotation
                scaleX = flipScale
                scaleY = flipScale
                cameraDistance = 28f * density
                shadowElevation = (12f + 8f * flipScale).dp.toPx()
                transformOrigin = TransformOrigin.Center
                shape = cardShape
                clip = false
            }
            .clickable(onClick = flip)
            .semantics {
                isTraversalGroup = true
                contentDescription = contentDescriptionText
                onClick(label = if (showBack) showFrontActionLabel else showBackActionLabel) {
                    flip()
                    true
                }
            },
    ) {
        if (rotation <= 90f) {
            CardTemplatePreview(
                template = template,
                modifier = Modifier.fillMaxWidth(),
                nickname = card.nickname,
                cardNetwork = card.cardNetwork,
                shadowElevation = 0.dp,
            )
        } else {
            CardBack(
                card = card,
                cvvHiddenLabel = cvvHiddenLabel,
                cvvNotSavedLabel = cvvNotSavedLabel,
                revealedCardNumber = revealedCardNumber,
                revealedCvv = revealedCvv,
                revealedExpiryText = revealedExpiryText,
                revealedExpired = revealedExpired,
                expiredLabel = expiredLabel,
                revealActionLabel = revealActionLabel,
                hideActionLabel = hideActionLabel,
                copyActionLabel = copyActionLabel,
                onRevealSecrets = onRevealSecrets,
                onHideSecrets = onHideSecrets,
                onCopyCardNumber = onCopyCardNumber,
                modifier = Modifier.graphicsLayer { rotationY = 180f },
            )
        }
    }
}

@Composable
private fun CardBack(
    card: CardDetailUiModel,
    cvvHiddenLabel: String,
    cvvNotSavedLabel: String,
    revealedCardNumber: String?,
    revealedCvv: String?,
    revealedExpiryText: String?,
    revealedExpired: Boolean,
    expiredLabel: String,
    revealActionLabel: String,
    hideActionLabel: String,
    copyActionLabel: String,
    onRevealSecrets: () -> Unit,
    onHideSecrets: () -> Unit,
    onCopyCardNumber: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = CardTemplateRegistry.findOrDefault(card.cardTemplateId)
    val hiddenSemantics = contentDescriptionTextForHiddenValues()
    val isRevealed = shouldDisplayCardSecrets(
        cardNumber = revealedCardNumber,
        expiryText = revealedExpiryText,
    )
    val numberText = revealedCardNumber
        ?.takeIf { isRevealed }
        ?.let(CardNumberTools::formatGrouped)
        ?: card.maskedCardNumber
    val cvvText = when {
        isRevealed && revealedCvv != null -> revealedCvv
        card.cvvSaveStatus == CvvSaveStatusUi.Saved -> cvvHiddenLabel
        else -> cvvNotSavedLabel
    }
    val expiryText = revealedExpiryText
        ?.takeIf { isRevealed }
        ?: stringResource(R.string.card_face_hidden_expiry)
    val noteText = card.notes.trim()
    val notesHiddenSemantics = stringResource(R.string.card_notes_hidden_semantics)

    Surface(
        modifier = modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.extraLarge,
        color = template.gradientEnd,
        tonalElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            template.gradientEnd,
                            template.gradientStart,
                            template.gradientEnd.copy(alpha = 0.96f),
                        ),
                    ),
                ),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = template.accent.copy(alpha = 0.1f),
                    radius = size.minDimension * 0.58f,
                    center = Offset(size.width * 0.92f, size.height * 0.02f),
                    style = Stroke(width = size.minDimension * 0.01f),
                )
                drawRect(
                    color = Color.Black.copy(alpha = 0.32f),
                    topLeft = Offset(0f, size.height * 0.29f),
                    size = Size(size.width, size.height * 0.15f),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 17.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clearAndSetSemantics { },
                    ) {
                        Text(
                            text = card.nickname,
                            color = template.foreground,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = template.foreground.copy(alpha = 0.12f),
                        contentColor = template.foreground,
                    ) {
                        IconButton(
                            onClick = if (isRevealed) onHideSecrets else onRevealSecrets,
                            enabled = card.canRevealCardSecrets,
                            modifier = Modifier
                                .size(42.dp)
                                .clearAndSetSemantics {
                                    contentDescription = if (isRevealed) {
                                        hideActionLabel
                                    } else {
                                        revealActionLabel
                                    }
                                    onClick(
                                        label = if (isRevealed) hideActionLabel else revealActionLabel,
                                    ) {
                                        if (isRevealed) onHideSecrets() else onRevealSecrets()
                                        true
                                    }
                                },
                        ) {
                            EyeGlyph(
                                crossed = isRevealed,
                                color = template.foreground,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = numberText,
                            modifier = Modifier
                                .weight(1f)
                                .clearAndSetSemantics {
                                    contentDescription = hiddenSemantics
                                },
                            color = template.foreground,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.45.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isRevealed) {
                            IconButton(
                                onClick = onCopyCardNumber,
                                modifier = Modifier.clearAndSetSemantics {
                                    contentDescription = copyActionLabel
                                    onClick(label = copyActionLabel) {
                                        onCopyCardNumber()
                                        true
                                    }
                                },
                            ) {
                                CopyGlyph(
                                    color = template.foreground,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1.08f)
                                .clearAndSetSemantics {
                                    contentDescription = hiddenSemantics
                                },
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            SecretField(
                                label = if (isRevealed && revealedExpired) {
                                    expiredLabel
                                } else {
                                    stringResource(R.string.card_field_expiry_placeholder)
                                },
                                value = expiryText,
                                foreground = template.foreground,
                            )
                            SecretField(
                                label = stringResource(R.string.card_field_cvv),
                                value = cvvText,
                                foreground = template.foreground,
                            )
                        }
                        if (noteText.isNotEmpty()) {
                            Text(
                                text = noteText,
                                modifier = Modifier
                                    .weight(0.92f)
                                    .widthIn(max = 142.dp)
                                    .clearAndSetSemantics {
                                        contentDescription = notesHiddenSemantics
                                    },
                                color = template.foreground.copy(alpha = 0.78f),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.End,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecretField(
    label: String,
    value: String,
    foreground: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = label,
            color = foreground.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 0.8.sp,
        )
        Text(
            text = value,
            color = foreground.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun EyeGlyph(
    crossed: Boolean,
    color: Color,
) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val eye = Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.5f)
            quadraticTo(size.width * 0.5f, size.height * 0.12f, size.width * 0.92f, size.height * 0.5f)
            quadraticTo(size.width * 0.5f, size.height * 0.88f, size.width * 0.08f, size.height * 0.5f)
            close()
        }
        drawPath(
            path = eye,
            color = color,
            style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round),
        )
        drawCircle(
            color = color,
            radius = size.minDimension * 0.12f,
            center = center,
        )
        if (crossed) {
            drawLine(
                color = color,
                start = Offset(size.width * 0.1f, size.height * 0.12f),
                end = Offset(size.width * 0.9f, size.height * 0.88f),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun CopyGlyph(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = 1.7.dp.toPx()
        val radius = size.minDimension * 0.12f
        drawRoundRect(
            color = color.copy(alpha = 0.72f),
            topLeft = Offset(size.width * 0.16f, size.height * 0.07f),
            size = Size(size.width * 0.6f, size.height * 0.62f),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = stroke),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.29f, size.height * 0.27f),
            size = Size(size.width * 0.6f, size.height * 0.62f),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = stroke),
        )
    }
}

@Composable
private fun contentDescriptionTextForHiddenValues(): String =
    stringResource(R.string.card_sensitive_value_content_description)

private const val CARD_ASPECT_RATIO = 1.586f

internal fun shouldDisplayCardSecrets(
    cardNumber: String?,
    expiryText: String?,
): Boolean = !cardNumber.isNullOrBlank() && !expiryText.isNullOrBlank()
