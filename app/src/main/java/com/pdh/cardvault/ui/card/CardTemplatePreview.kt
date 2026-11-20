package com.pdh.cardvault.ui.card

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pdh.cardvault.R
import com.pdh.cardvault.domain.model.CardNetwork
import androidx.compose.ui.res.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val CARD_ASPECT_RATIO = 1.586f

private enum class CustomColorTarget {
    Cover,
    Nickname,
}

@Composable
fun CardTemplatePreview(
    template: CardTemplateSpec,
    modifier: Modifier = Modifier,
    nickname: String? = null,
    cardNetwork: CardNetwork? = null,
    lastFour: String? = null,
    shadowElevation: Dp = 6.dp,
    compactLayout: Boolean = false,
    aspectRatio: Float = CARD_ASPECT_RATIO,
) {
    val useCompactLayout = compactLayout || LocalDensity.current.fontScale > 1.3f
    val previewDescription = stringResource(R.string.card_preview_content_description)
    val displayedNickname = nickname
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: stringResource(R.string.card_style_nickname_preview)
    val footerColor = template.nicknameColor.color ?: template.foreground

    Surface(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clearAndSetSemantics {
                contentDescription = previewDescription
            },
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Transparent,
        shadowElevation = shadowElevation,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .cachedCardCoverArtwork(template = template, cornerRadius = 32.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (useCompactLayout) 12.dp else 20.dp,
                        vertical = if (useCompactLayout) 9.dp else 18.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(
                    if (useCompactLayout) 6.dp else 10.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (cardNetwork != null) {
                    PaymentNetworkMark(network = cardNetwork)
                }
                CardNicknameArtworkView(
                    template = template,
                    nickname = displayedNickname,
                    modifier = Modifier.weight(1f),
                    compact = useCompactLayout,
                )
            }

            if (!lastFour.isNullOrBlank() && !useCompactLayout) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .clearAndSetSemantics { },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(16.dp)) {
                        val waveStroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        repeat(3) { index ->
                            val inset = index * size.minDimension * 0.17f
                            drawArc(
                                color = footerColor.copy(alpha = 0.58f + index * 0.1f),
                                startAngle = -52f,
                                sweepAngle = 104f,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - inset * 2f, size.height - inset * 2f),
                                style = waveStroke,
                            )
                        }
                    }
                    Text(
                        text = "•••• $lastFour",
                        color = footerColor.copy(alpha = 0.84f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
fun CardTemplatePicker(
    selectedTemplateId: String,
    onTemplateSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    nickname: String? = null,
    cardNetwork: CardNetwork? = null,
    showPreview: Boolean = true,
) {
    var selection by remember(selectedTemplateId) {
        mutableStateOf(CardTemplateRegistry.selectionFor(selectedTemplateId))
    }
    val previewNickname = nickname
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: stringResource(R.string.card_style_nickname_preview)
    fun idFor(value: CardCoverSelection): String =
        CardTemplateRegistry.composeTemplateId(
            colorId = value.colorId,
            patternId = value.patternId,
            nicknameStyleId = value.nicknameStyleId,
            nicknameColorId = value.nicknameColorId,
        )
    val selectedTemplate = CardTemplateRegistry.findOrDefault(idFor(selection))
    var customColorTarget by remember { mutableStateOf<CustomColorTarget?>(null) }
    val presetListState = rememberLazyListState(
        initialFirstVisibleItemIndex = CardTemplateRegistry.presets
            .indexOfFirst { preset -> preset.template.id == selectedTemplate.id }
            .coerceAtLeast(0),
    )
    val colorListState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (CardTemplateRegistry.isCustomColorId(selection.colorId)) {
            0
        } else {
            CardTemplateRegistry.colors
                .indexOfFirst { color -> color.id == selection.colorId }
                .coerceAtLeast(0) + 1
        },
    )
    val patternListState = rememberLazyListState(
        initialFirstVisibleItemIndex = CardTemplateRegistry.patterns
            .indexOfFirst { pattern -> pattern.id == selection.patternId }
            .coerceAtLeast(0),
    )
    val nicknameStyleListState = rememberLazyListState(
        initialFirstVisibleItemIndex = CardTemplateRegistry.nicknameStyles
            .indexOfFirst { style -> style.id == selection.nicknameStyleId }
            .coerceAtLeast(0),
    )
    val nicknameColorListState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (
            CardTemplateRegistry.isCustomColorId(selection.nicknameColorId)
        ) {
            0
        } else {
            CardTemplateRegistry.nicknameColors
                .indexOfFirst { color -> color.id == selection.nicknameColorId }
                .coerceAtLeast(0) + 1
        },
    )

    customColorTarget?.let { target ->
        CustomCardColorDialog(
            title = stringResource(
                if (target == CustomColorTarget.Cover) {
                    R.string.card_color_custom_title
                } else {
                    R.string.card_name_color_custom_title
                },
            ),
            initialColor = when (target) {
                CustomColorTarget.Cover -> {
                    CardTemplateRegistry.customColorForId(selection.colorId)
                        ?: selectedTemplate.gradientStart
                }

                CustomColorTarget.Nickname -> {
                    CardTemplateRegistry.customColorForId(selection.nicknameColorId)
                        ?: selectedTemplate.nicknameColor.color
                        ?: selectedTemplate.foreground
                }
            },
            onDismiss = { customColorTarget = null },
            onColorSelected = { color ->
                val customColorId = CardTemplateRegistry.customColorId(color)
                val updatedSelection = when (target) {
                    CustomColorTarget.Cover -> selection.copy(colorId = customColorId)
                    CustomColorTarget.Nickname -> selection.copy(nicknameColorId = customColorId)
                }
                selection = updatedSelection
                onTemplateSelected(idFor(updatedSelection))
                customColorTarget = null
            },
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showPreview) {
            CardTemplatePreview(
                template = selectedTemplate,
                modifier = Modifier.fillMaxWidth(),
                nickname = previewNickname,
                cardNetwork = cardNetwork,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.card_style_presets_heading),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.card_style_presets_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyRow(
            state = presetListState,
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = CardTemplateRegistry.presets,
                key = CardPresetSpec::id,
            ) { presetSpec ->
                val selected = presetSpec.template.id == selectedTemplate.id
                val presetTitle = stringResource(presetSpec.titleRes)
                val presetSubtitle = stringResource(presetSpec.subtitleRes)
                Surface(
                    onClick = {
                        val updatedSelection = CardTemplateRegistry.selectionFor(presetSpec.template.id)
                        selection = updatedSelection
                        onTemplateSelected(presetSpec.template.id)
                    },
                    modifier = Modifier
                        .width(172.dp)
                        .semantics {
                            this.selected = selected
                            role = Role.RadioButton
                        },
                    shape = MaterialTheme.shapes.large,
                    color = if (selected) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        },
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        CardTemplatePreview(
                            template = presetSpec.template,
                            modifier = Modifier.fillMaxWidth(),
                            nickname = presetTitle,
                            shadowElevation = 0.dp,
                            compactLayout = true,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = presetTitle,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = presetSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.card_style_customizer_heading),
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = stringResource(R.string.card_style_color_heading),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(
            state = colorListState,
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "custom-color") {
                val selected = CardTemplateRegistry.isCustomColorId(selection.colorId)
                Surface(
                    onClick = { customColorTarget = CustomColorTarget.Cover },
                    modifier = Modifier.semantics {
                        this.selected = selected
                        role = Role.RadioButton
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = if (selected) 2.dp else 0.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(
                                    brush = Brush.sweepGradient(
                                        listOf(
                                            Color(0xFFE85D75),
                                            Color(0xFFFFD166),
                                            Color(0xFF45D69C),
                                            Color(0xFF5DBBFF),
                                            Color(0xFF9B78FF),
                                            Color(0xFFE85D75),
                                        ),
                                    ),
                                    shape = CircleShape,
                                ),
                        )
                        Text(
                            text = stringResource(R.string.card_color_custom),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
            items(
                items = CardTemplateRegistry.colors,
                key = CardCoverColorSpec::id,
            ) { colorSpec ->
                val selected = colorSpec.id == selection.colorId
                Surface(
                    onClick = {
                        val updatedSelection = selection.copy(colorId = colorSpec.id)
                        selection = updatedSelection
                        onTemplateSelected(idFor(updatedSelection))
                    },
                    modifier = Modifier.semantics {
                        this.selected = selected
                        role = Role.RadioButton
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = if (selected) 2.dp else 0.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        listOf(colorSpec.gradientStart, colorSpec.gradientEnd),
                                    ),
                                    shape = CircleShape,
                                ),
                        )
                        Text(
                            text = stringResource(colorSpec.displayNameRes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.card_style_pattern_heading),
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(
            state = patternListState,
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = CardTemplateRegistry.patterns,
                key = CardCoverPatternSpec::id,
            ) { patternSpec ->
                val patternSelection = selection.copy(patternId = patternSpec.id)
                val patternTemplate = CardTemplateRegistry.findOrDefault(idFor(patternSelection))
                val selected = patternSpec.id == selection.patternId
                Surface(
                    onClick = {
                        val updatedSelection = selection.copy(patternId = patternSpec.id)
                        selection = updatedSelection
                        onTemplateSelected(idFor(updatedSelection))
                    },
                    modifier = Modifier
                        .width(142.dp)
                        .semantics {
                            this.selected = selected
                            role = Role.RadioButton
                        },
                    shape = MaterialTheme.shapes.large,
                    color = if (selected) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        Color.Transparent
                    },
                    border = BorderStroke(
                        width = if (selected) 2.dp else 0.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        CardPatternSwatch(
                            template = patternTemplate,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(patternSpec.displayNameRes),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.card_style_name_font_heading),
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(
            state = nicknameStyleListState,
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = CardTemplateRegistry.nicknameStyles,
                key = CardNicknameStyleSpec::id,
            ) { styleSpec ->
                val styleSelection = selection.copy(nicknameStyleId = styleSpec.id)
                val styleTemplate = CardTemplateRegistry.findOrDefault(idFor(styleSelection))
                val selected = styleSpec.id == selection.nicknameStyleId
                Surface(
                    onClick = {
                        selection = styleSelection
                        onTemplateSelected(idFor(styleSelection))
                    },
                    modifier = Modifier
                        .width(154.dp)
                        .semantics {
                            this.selected = selected
                            role = Role.RadioButton
                        },
                    shape = MaterialTheme.shapes.large,
                    color = if (selected) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        Color.Transparent
                    },
                    border = BorderStroke(
                        width = if (selected) 2.dp else 0.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        NicknameStyleSwatch(
                            template = styleTemplate,
                            nickname = previewNickname,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(styleSpec.displayNameRes),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.card_style_name_color_heading),
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(
            state = nicknameColorListState,
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "custom-nickname-color") {
                val selected = CardTemplateRegistry.isCustomColorId(selection.nicknameColorId)
                Surface(
                    onClick = { customColorTarget = CustomColorTarget.Nickname },
                    modifier = Modifier.semantics {
                        this.selected = selected
                        role = Role.RadioButton
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = if (selected) 2.dp else 0.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(
                                    brush = Brush.sweepGradient(
                                        listOf(
                                            Color(0xFFE85D75),
                                            Color(0xFFFFD166),
                                            Color(0xFF45D69C),
                                            Color(0xFF5DBBFF),
                                            Color(0xFF9B78FF),
                                            Color(0xFFE85D75),
                                        ),
                                    ),
                                    shape = CircleShape,
                                ),
                        )
                        Text(
                            text = stringResource(R.string.card_color_custom),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
            items(
                items = CardTemplateRegistry.nicknameColors,
                key = CardNicknameColorSpec::id,
            ) { colorSpec ->
                val selected = colorSpec.id == selection.nicknameColorId
                Surface(
                    onClick = {
                        val updatedSelection = selection.copy(nicknameColorId = colorSpec.id)
                        selection = updatedSelection
                        onTemplateSelected(idFor(updatedSelection))
                    },
                    modifier = Modifier.semantics {
                        this.selected = selected
                        role = Role.RadioButton
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = if (selected) 2.dp else 0.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(
                                    color = colorSpec.color ?: selectedTemplate.foreground,
                                    shape = CircleShape,
                                ),
                        )
                        Text(
                            text = stringResource(colorSpec.displayNameRes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CustomCardColorDialog(
    title: String,
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit,
) {
    val hueLabel = stringResource(R.string.card_color_custom_hue)
    val paletteLabel = stringResource(R.string.card_color_custom_palette)
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { values ->
            AndroidColor.colorToHSV(initialColor.toArgb(), values)
        }
    }
    var hue by remember(initialColor) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initialColor) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(initialColor) { mutableFloatStateOf(initialHsv[2]) }
    val previewColor = Color.hsv(hue, saturation, brightness)
    val hexValue = String.format(
        java.util.Locale.ROOT,
        "#%06X",
        previewColor.toArgb() and 0x00FFFFFF,
    )
    val colorStateDescription = stringResource(R.string.card_color_custom_hex, hexValue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = paletteLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val paletteSize = (maxWidth - 60.dp)
                        .coerceAtLeast(144.dp)
                        .coerceAtMost(280.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SaturationBrightnessPalette(
                            hue = hue,
                            saturation = saturation,
                            brightness = brightness,
                            stateDescription = colorStateDescription,
                            onSelectionChange = { newSaturation, newBrightness ->
                                saturation = newSaturation
                                brightness = newBrightness
                            },
                            modifier = Modifier.size(paletteSize),
                        )
                        HuePicker(
                            hue = hue,
                            stateDescription = colorStateDescription,
                            onHueChange = { hue = it },
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .width(48.dp)
                                .height(paletteSize),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(previewColor, CircleShape),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = hexValue,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = hueLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(previewColor) }) {
                Text(stringResource(R.string.card_color_custom_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun SaturationBrightnessPalette(
    hue: Float,
    saturation: Float,
    brightness: Float,
    stateDescription: String,
    onSelectionChange: (saturation: Float, brightness: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paletteDescription = stringResource(R.string.card_color_custom_palette)
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)
    Canvas(
        modifier = modifier
            .semantics {
                contentDescription = paletteDescription
                this.stateDescription = stateDescription
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun update(position: Offset) {
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        currentOnSelectionChange(
                            (position.x / width).coerceIn(0f, 1f),
                            (1f - position.y / height).coerceIn(0f, 1f),
                        )
                    }
                    update(down.position)
                    down.consume()
                    drag(down.id) { change ->
                        update(change.position)
                        change.consume()
                    }
                }
            },
    ) {
        val cornerRadius = CornerRadius(14.dp.toPx())
        drawRoundRect(
            color = Color.hsv(hue, 1f, 1f),
            cornerRadius = cornerRadius,
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color.White, Color.Transparent)),
            cornerRadius = cornerRadius,
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
            cornerRadius = cornerRadius,
        )

        val markerRadius = 9.dp.toPx()
        val markerCenter = Offset(
            x = (saturation * size.width).coerceIn(markerRadius, size.width - markerRadius),
            y = ((1f - brightness) * size.height)
                .coerceIn(markerRadius, size.height - markerRadius),
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.62f),
            radius = markerRadius + 1.5.dp.toPx(),
            center = markerCenter,
            style = Stroke(width = 3.dp.toPx()),
        )
        drawCircle(
            color = Color.White,
            radius = markerRadius,
            center = markerCenter,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
private fun HuePicker(
    hue: Float,
    stateDescription: String,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hueDescription = stringResource(R.string.card_color_custom_hue)
    val currentOnHueChange by rememberUpdatedState(onHueChange)
    Canvas(
        modifier = modifier
            .semantics {
                contentDescription = hueDescription
                this.stateDescription = stateDescription
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun update(position: Offset) {
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        currentOnHueChange(
                            (position.y / height * 360f).coerceIn(0f, 360f),
                        )
                    }
                    update(down.position)
                    down.consume()
                    drag(down.id) { change ->
                        update(change.position)
                        change.consume()
                    }
                }
            },
    ) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.Red,
                    Color.Yellow,
                    Color.Green,
                    Color.Cyan,
                    Color.Blue,
                    Color.Magenta,
                    Color.Red,
                ),
            ),
            cornerRadius = CornerRadius(12.dp.toPx()),
        )
        val markerY = (hue / 360f * size.height)
            .coerceIn(2.dp.toPx(), size.height - 2.dp.toPx())
        drawLine(
            color = Color.Black.copy(alpha = 0.72f),
            start = Offset(2.dp.toPx(), markerY),
            end = Offset(size.width - 2.dp.toPx(), markerY),
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(3.dp.toPx(), markerY),
            end = Offset(size.width - 3.dp.toPx(), markerY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun NicknameStyleSwatch(
    template: CardTemplateSpec,
    nickname: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(68.dp)
            .clearAndSetSemantics { },
        shape = MaterialTheme.shapes.medium,
        color = template.gradientEnd,
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(template.gradientStart, template.gradientEnd)))
                .padding(8.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            CardNicknameArtworkView(
                template = template,
                nickname = nickname,
                modifier = Modifier.fillMaxWidth(),
                compact = true,
            )
        }
    }
}

@Composable
private fun CardPatternSwatch(
    template: CardTemplateSpec,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.aspectRatio(CARD_ASPECT_RATIO),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .cachedCardCoverArtwork(template = template, cornerRadius = 22.dp),
        )
    }
}

/**
 * Pre-builds gradients, paths, and anodized specular bevel strokes once per (size, template)
 * inside [drawWithCache] so scrolling wallet stacks and pattern galleries allocate zero objects
 * during 120Hz frame rendering.
 */
private fun Modifier.cachedCardCoverArtwork(
    template: CardTemplateSpec,
    cornerRadius: Dp,
): Modifier = drawWithCache {
    val midStop = lerp(template.gradientStart, template.gradientEnd, 0.52f)
    val baseBrush = Brush.linearGradient(
        0.0f to template.gradientStart,
        0.54f to midStop,
        1.0f to template.gradientEnd,
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    val studioGlowRadius = size.maxDimension.coerceAtLeast(1f) * 0.72f
    val studioGlowCenter = Offset(size.width * 0.14f, size.height * 0.08f)
    val studioGlowBrush = Brush.radialGradient(
        colors = listOf(Color.White.copy(alpha = 0.085f), Color.Transparent),
        center = studioGlowCenter,
        radius = studioGlowRadius,
    )
    val rimStrokePx = 1.dp.toPx()
    val rimInset = rimStrokePx * 0.5f
    val rimCorner = CornerRadius((cornerRadius.toPx() - rimInset).coerceAtLeast(0f))
    val rimSize = Size(
        width = (size.width - rimStrokePx).coerceAtLeast(0f),
        height = (size.height - rimStrokePx).coerceAtLeast(0f),
    )
    val rimStroke = Stroke(width = rimStrokePx)
    val rimBrush = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = 0.28f),
        0.45f to Color.White.copy(alpha = 0.07f),
        1.0f to Color.Black.copy(alpha = 0.24f),
    )
    val motifDrawer = buildCachedMotifDrawer(template, size)

    onDrawBehind {
        drawRect(brush = baseBrush)
        drawCircle(brush = studioGlowBrush, radius = studioGlowRadius, center = studioGlowCenter)
        motifDrawer()
        drawRoundRect(
            brush = rimBrush,
            topLeft = Offset(rimInset, rimInset),
            size = rimSize,
            cornerRadius = rimCorner,
            style = rimStroke,
        )
    }
}

private fun buildCachedMotifDrawer(
    template: CardTemplateSpec,
    canvasSize: Size,
): DrawScope.() -> Unit {
    val accent = template.accent
    val thinStroke = canvasSize.minDimension * 0.008f

    return when (template.motif) {
        CardMotif.Plain -> ({})

        CardMotif.DiagonalShards -> {
            val shard = Path().apply {
                moveTo(canvasSize.width * 0.58f, 0f)
                lineTo(canvasSize.width * 0.78f, 0f)
                lineTo(canvasSize.width * 0.47f, canvasSize.height)
                lineTo(canvasSize.width * 0.28f, canvasSize.height)
                close()
            }
            val shardColor = accent.copy(alpha = 0.2f)
            val lineColor = accent.copy(alpha = 0.46f)
            val lineStart = Offset(canvasSize.width * 0.86f, 0f)
            val lineEnd = Offset(canvasSize.width * 0.58f, canvasSize.height)
            ({
                drawPath(shard, shardColor)
                drawLine(color = lineColor, start = lineStart, end = lineEnd, strokeWidth = thinStroke)
            })
        }

        CardMotif.OrbitLines -> {
            val stroke = Stroke(width = thinStroke)
            val orbitCenter = Offset(canvasSize.width * 0.84f, canvasSize.height * 0.12f)
            ({
                repeat(3) { index ->
                    drawCircle(
                        color = accent.copy(alpha = 0.16f + index * 0.07f),
                        radius = size.minDimension * (0.25f + index * 0.14f),
                        center = orbitCenter,
                        style = stroke,
                    )
                }
            })
        }

        CardMotif.FacetedField -> {
            val upperFacet = Path().apply {
                moveTo(canvasSize.width * 0.45f, 0f)
                lineTo(canvasSize.width, 0f)
                lineTo(canvasSize.width, canvasSize.height * 0.42f)
                lineTo(canvasSize.width * 0.72f, canvasSize.height * 0.64f)
                close()
            }
            val lowerFacet = Path().apply {
                moveTo(canvasSize.width * 0.72f, canvasSize.height * 0.64f)
                lineTo(canvasSize.width, canvasSize.height * 0.42f)
                lineTo(canvasSize.width, canvasSize.height)
                lineTo(canvasSize.width * 0.38f, canvasSize.height)
                close()
            }
            val upperColor = accent.copy(alpha = 0.18f)
            val lowerColor = Color.White.copy(alpha = 0.06f)
            ({
                drawPath(upperFacet, upperColor)
                drawPath(lowerFacet, lowerColor)
            })
        }

        CardMotif.StarField -> {
            val nebulaCenter = Offset(canvasSize.width * 0.82f, canvasSize.height * 0.25f)
            val nebulaRadius = (canvasSize.minDimension * 0.34f).coerceAtLeast(1f)
            val nebulaBrush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.28f), Color.Transparent),
                center = nebulaCenter,
                radius = nebulaRadius,
            )
            ({
                STAR_POINTS.forEachIndexed { index, point ->
                    drawCircle(
                        color = accent.copy(alpha = 0.32f + (index % 3) * 0.18f),
                        radius = size.minDimension * (0.006f + (index % 2) * 0.004f),
                        center = Offset(size.width * point.first, size.height * point.second),
                    )
                }
                drawCircle(brush = nebulaBrush, radius = nebulaRadius, center = nebulaCenter)
            })
        }

        CardMotif.FlowingRibbons -> {
            val stroke = Stroke(width = thinStroke * 1.5f, cap = StrokeCap.Round)
            val ribbons = List(3) { index ->
                val verticalOffset = canvasSize.height * (0.18f + index * 0.18f)
                Path().apply {
                    moveTo(-canvasSize.width * 0.05f, verticalOffset)
                    cubicTo(
                        canvasSize.width * 0.25f,
                        verticalOffset - canvasSize.height * 0.22f,
                        canvasSize.width * 0.65f,
                        verticalOffset + canvasSize.height * 0.25f,
                        canvasSize.width * 1.05f,
                        verticalOffset - canvasSize.height * 0.04f,
                    )
                }
            }
            ({
                ribbons.forEachIndexed { index, ribbon ->
                    drawPath(
                        path = ribbon,
                        color = accent.copy(alpha = 0.2f + index * 0.06f),
                        style = stroke,
                    )
                }
            })
        }

        CardMotif.OffsetHalos -> {
            val stroke = Stroke(width = thinStroke * 1.2f)
            val haloCenter = Offset(canvasSize.width * 0.92f, canvasSize.height * 0.78f)
            ({
                repeat(4) { index ->
                    drawCircle(
                        color = accent.copy(alpha = 0.1f + index * 0.055f),
                        radius = size.minDimension * (0.16f + index * 0.12f),
                        center = haloCenter,
                        style = stroke,
                    )
                }
            })
        }

        CardMotif.FineGrid -> {
            val vColor = accent.copy(alpha = 0.1f)
            val hColor = accent.copy(alpha = 0.08f)
            val gridStroke = thinStroke * 0.55f
            ({
                repeat(8) { index ->
                    val x = size.width * index / 7f
                    drawLine(
                        color = vColor,
                        start = Offset(x, 0f),
                        end = Offset(x - size.width * 0.18f, size.height),
                        strokeWidth = gridStroke,
                    )
                }
                repeat(5) { index ->
                    val y = size.height * index / 4f
                    drawLine(
                        color = hColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = gridStroke,
                    )
                }
            })
        }

        CardMotif.SoftWaves -> {
            val stroke = Stroke(width = thinStroke * 1.3f, cap = StrokeCap.Round)
            val waves = List(3) { index ->
                val base = canvasSize.height * (0.28f + index * 0.19f)
                Path().apply {
                    moveTo(0f, base)
                    cubicTo(
                        canvasSize.width * 0.28f,
                        base + canvasSize.height * 0.18f,
                        canvasSize.width * 0.62f,
                        base - canvasSize.height * 0.2f,
                        canvasSize.width,
                        base + canvasSize.height * 0.05f,
                    )
                }
            }
            ({
                waves.forEachIndexed { index, wave ->
                    drawPath(
                        path = wave,
                        color = accent.copy(alpha = 0.15f + index * 0.05f),
                        style = stroke,
                    )
                }
            })
        }

        CardMotif.DotMatrix -> ({
            repeat(7) { column ->
                repeat(4) { row ->
                    drawCircle(
                        color = accent.copy(alpha = 0.12f + row * 0.035f),
                        radius = size.minDimension * 0.009f,
                        center = Offset(
                            x = size.width * (0.57f + column * 0.06f),
                            y = size.height * (0.18f + row * 0.13f),
                        ),
                    )
                }
            }
        })

        CardMotif.CircuitPaths -> {
            val nodes = listOf(
                Offset(canvasSize.width * 0.58f, canvasSize.height * 0.2f),
                Offset(canvasSize.width * 0.8f, canvasSize.height * 0.2f),
                Offset(canvasSize.width * 0.8f, canvasSize.height * 0.47f),
                Offset(canvasSize.width * 0.94f, canvasSize.height * 0.47f),
                Offset(canvasSize.width * 0.58f, canvasSize.height * 0.72f),
                Offset(canvasSize.width * 0.72f, canvasSize.height * 0.72f),
            )
            val lineColor = accent.copy(alpha = 0.28f)
            val nodeColor = accent.copy(alpha = 0.52f)
            val nodeRadius = canvasSize.minDimension * 0.014f
            ({
                for (i in 0 until nodes.lastIndex) {
                    drawLine(
                        color = lineColor,
                        start = nodes[i],
                        end = nodes[i + 1],
                        strokeWidth = thinStroke,
                        cap = StrokeCap.Round,
                    )
                }
                nodes.forEach { node ->
                    drawCircle(color = nodeColor, radius = nodeRadius, center = node)
                }
            })
        }

        CardMotif.VaultArches -> {
            val stroke = Stroke(width = thinStroke)
            ({
                repeat(4) { index ->
                    val inset = size.minDimension * (0.04f + index * 0.07f)
                    drawArc(
                        color = accent.copy(alpha = 0.12f + index * 0.045f),
                        startAngle = 190f,
                        sweepAngle = 160f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.48f + inset, size.height * 0.08f + inset),
                        size = Size(
                            width = size.width * 0.62f - inset * 2f,
                            height = size.height * 0.9f - inset * 2f,
                        ),
                        style = stroke,
                    )
                }
                drawRoundRect(
                    color = accent.copy(alpha = 0.12f),
                    topLeft = Offset(size.width * 0.72f, size.height * 0.38f),
                    size = Size(size.width * 0.2f, size.height * 0.24f),
                    cornerRadius = CornerRadius(size.minDimension * 0.04f),
                    style = stroke,
                )
            })
        }

        CardMotif.ObsidianContours -> {
            val contourStroke = Stroke(width = thinStroke * 0.75f)
            ({
                repeat(3) { index ->
                    val inset = size.minDimension * index * 0.055f
                    drawRoundRect(
                        color = accent.copy(alpha = 0.1f + index * 0.06f),
                        topLeft = Offset(
                            x = size.width * 0.54f + inset,
                            y = size.height * 0.08f + inset,
                        ),
                        size = Size(
                            width = size.width * 0.5f - inset * 2f,
                            height = size.height * 0.84f - inset * 2f,
                        ),
                        cornerRadius = CornerRadius(size.minDimension * 0.1f),
                        style = contourStroke,
                    )
                }
                drawLine(
                    color = accent.copy(alpha = 0.45f),
                    start = Offset(size.width * 0.64f, size.height * 0.18f),
                    end = Offset(size.width * 0.9f, size.height * 0.18f),
                    strokeWidth = thinStroke,
                    cap = StrokeCap.Round,
                )
            })
        }

        CardMotif.SilverFrame -> {
            val frameStroke = Stroke(width = thinStroke * 0.8f)
            ({
                val inset = size.minDimension * 0.07f
                drawRoundRect(
                    color = accent.copy(alpha = 0.34f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2f, size.height - inset * 2f),
                    cornerRadius = CornerRadius(size.minDimension * 0.08f),
                    style = frameStroke,
                )
                repeat(3) { index ->
                    drawCircle(
                        color = accent.copy(alpha = 0.3f + index * 0.12f),
                        radius = size.minDimension * 0.012f,
                        center = Offset(
                            x = size.width * (0.76f + index * 0.055f),
                            y = size.height * 0.22f,
                        ),
                    )
                }
            })
        }

        CardMotif.MidnightBeam -> {
            val beam = Path().apply {
                moveTo(canvasSize.width * 0.58f, 0f)
                lineTo(canvasSize.width * 0.78f, 0f)
                lineTo(canvasSize.width, canvasSize.height * 0.72f)
                lineTo(canvasSize.width, canvasSize.height)
                close()
            }
            val glowCenter = Offset(canvasSize.width * 0.86f, canvasSize.height * 0.18f)
            val glowRadius = (canvasSize.minDimension * 0.28f).coerceAtLeast(1f)
            val glowBrush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.24f), Color.Transparent),
                center = glowCenter,
                radius = glowRadius,
            )
            ({
                drawPath(beam, accent.copy(alpha = 0.13f))
                drawCircle(brush = glowBrush, radius = glowRadius, center = glowCenter)
            })
        }

        CardMotif.SageLoops -> {
            val stroke = Stroke(width = thinStroke, cap = StrokeCap.Round)
            ({
                repeat(3) { index ->
                    val inset = size.minDimension * index * 0.06f
                    drawArc(
                        color = accent.copy(alpha = 0.16f + index * 0.07f),
                        startAngle = 120f,
                        sweepAngle = 250f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.56f + inset, size.height * 0.1f + inset),
                        size = Size(
                            width = size.width * 0.48f - inset * 2f,
                            height = size.height * 0.78f - inset * 2f,
                        ),
                        style = stroke,
                    )
                }
            })
        }

        CardMotif.SandTiles -> {
            val tileStroke = Stroke(width = thinStroke * 0.7f)
            val halfWidth = canvasSize.width * 0.045f
            val halfHeight = canvasSize.height * 0.075f
            val tiles = buildList {
                repeat(2) { row ->
                    repeat(4) { column ->
                        val center = Offset(
                            x = canvasSize.width * (0.58f + column * 0.12f),
                            y = canvasSize.height * (0.24f + row * 0.27f),
                        )
                        val tile = Path().apply {
                            moveTo(center.x, center.y - halfHeight)
                            lineTo(center.x + halfWidth, center.y)
                            lineTo(center.x, center.y + halfHeight)
                            lineTo(center.x - halfWidth, center.y)
                            close()
                        }
                        add(tile to accent.copy(alpha = 0.1f + (row + column) * 0.025f))
                    }
                }
            }
            ({
                tiles.forEach { (tile, color) ->
                    drawPath(path = tile, color = color, style = tileStroke)
                }
            })
        }

        CardMotif.LilacAura -> {
            val auraCenter = Offset(canvasSize.width * 0.84f, canvasSize.height * 0.32f)
            val auraRadius = (canvasSize.minDimension * 0.42f).coerceAtLeast(1f)
            val auraBrush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.3f), Color.Transparent),
                center = auraCenter,
                radius = auraRadius,
            )
            val ringStroke = Stroke(width = thinStroke)
            val ringCenter = Offset(canvasSize.width * 0.7f, canvasSize.height * 0.72f)
            ({
                drawCircle(brush = auraBrush, radius = auraRadius, center = auraCenter)
                drawCircle(
                    color = accent.copy(alpha = 0.22f),
                    radius = size.minDimension * 0.2f,
                    center = ringCenter,
                    style = ringStroke,
                )
            })
        }

        CardMotif.MonochromeArcs -> {
            val stroke = Stroke(width = thinStroke * 0.85f, cap = StrokeCap.Round)
            ({
                repeat(5) { index ->
                    val inset = size.minDimension * index * 0.055f
                    drawArc(
                        color = accent.copy(alpha = 0.12f + index * 0.045f),
                        startAngle = 155f,
                        sweepAngle = 145f,
                        useCenter = false,
                        topLeft = Offset(size.width * 0.52f + inset, size.height * 0.2f + inset),
                        size = Size(
                            width = size.width * 0.62f - inset * 2f,
                            height = size.height * 0.86f - inset * 2f,
                        ),
                        style = stroke,
                    )
                }
            })
        }

        CardMotif.SparseGrid -> {
            val verticals = floatArrayOf(0.61f, 0.75f, 0.89f)
            val horizontals = floatArrayOf(0.23f, 0.47f, 0.71f)
            ({
                verticals.forEach { xFraction ->
                    drawLine(
                        color = accent.copy(alpha = 0.16f),
                        start = Offset(size.width * xFraction, size.height * 0.12f),
                        end = Offset(size.width * xFraction, size.height * 0.84f),
                        strokeWidth = thinStroke * 0.65f,
                    )
                }
                horizontals.forEach { yFraction ->
                    drawLine(
                        color = accent.copy(alpha = 0.14f),
                        start = Offset(size.width * 0.54f, size.height * yFraction),
                        end = Offset(size.width * 0.96f, size.height * yFraction),
                        strokeWidth = thinStroke * 0.65f,
                    )
                }
                drawCircle(
                    color = accent.copy(alpha = 0.5f),
                    radius = size.minDimension * 0.014f,
                    center = Offset(size.width * verticals[1], size.height * horizontals[1]),
                )
            })
        }

        CardMotif.ContinuousWave -> {
            val stroke = Stroke(width = thinStroke * 1.15f, cap = StrokeCap.Round)
            val waves = List(2) { index ->
                val base = canvasSize.height * (0.35f + index * 0.2f)
                Path().apply {
                    moveTo(canvasSize.width * 0.42f, base)
                    cubicTo(
                        canvasSize.width * 0.58f,
                        base - canvasSize.height * 0.24f,
                        canvasSize.width * 0.72f,
                        base + canvasSize.height * 0.2f,
                        canvasSize.width * 0.86f,
                        base,
                    )
                    cubicTo(
                        canvasSize.width * 0.93f,
                        base - canvasSize.height * 0.1f,
                        canvasSize.width,
                        base - canvasSize.height * 0.08f,
                        canvasSize.width * 1.06f,
                        base,
                    )
                }
            }
            ({
                waves.forEachIndexed { index, wave ->
                    drawPath(
                        path = wave,
                        color = accent.copy(alpha = 0.18f + index * 0.1f),
                        style = stroke,
                    )
                }
            })
        }

        CardMotif.CrystalPrism -> {
            val w = canvasSize.width
            val h = canvasSize.height
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
            ({
                drawPath(outerPrism, accent.copy(alpha = 0.13f))
                drawPath(innerPrism, accent.copy(alpha = 0.10f))
                drawPath(outerPrism, accent.copy(alpha = 0.34f), style = stroke)
                drawLine(
                    color = accent.copy(alpha = 0.28f),
                    start = Offset(w * 0.72f, h * 0.08f),
                    end = Offset(w * 0.72f, h * 0.90f),
                    strokeWidth = thinStroke * 0.8f,
                )
                drawLine(
                    color = accent.copy(alpha = 0.24f),
                    start = Offset(w * 0.48f, h * 0.46f),
                    end = Offset(w * 0.96f, h * 0.46f),
                    strokeWidth = thinStroke * 0.8f,
                )
            })
        }

        CardMotif.SunburstRays -> {
            val center = Offset(canvasSize.width * 0.86f, canvasSize.height * 0.86f)
            val innerR = canvasSize.minDimension * 0.20f
            val outerR = canvasSize.maxDimension * 0.78f
            val rays = List(13) { index ->
                val angle = (-178.0 + index * 7.8) * PI / 180.0
                val cosA = cos(angle).toFloat()
                val sinA = sin(angle).toFloat()
                Offset(center.x + cosA * innerR, center.y + sinA * innerR) to
                    Offset(center.x + cosA * outerR, center.y + sinA * outerR)
            }
            val ringStroke = Stroke(width = thinStroke)
            ({
                drawCircle(color = accent.copy(alpha = 0.18f), radius = innerR * 0.76f, center = center)
                drawCircle(color = accent.copy(alpha = 0.28f), radius = innerR, center = center, style = ringStroke)
                rays.forEachIndexed { index, (startPt, endPt) ->
                    drawLine(
                        color = accent.copy(alpha = if (index % 2 == 0) 0.26f else 0.13f),
                        start = startPt,
                        end = endPt,
                        strokeWidth = if (index % 2 == 0) thinStroke * 1.1f else thinStroke * 0.65f,
                        cap = StrokeCap.Round,
                    )
                }
            })
        }

        CardMotif.MountainPeaks -> {
            val w = canvasSize.width
            val h = canvasSize.height
            val backRange = Path().apply {
                moveTo(w * 0.18f, h)
                lineTo(w * 0.46f, h * 0.46f)
                lineTo(w * 0.64f, h * 0.68f)
                lineTo(w * 0.84f, h * 0.36f)
                lineTo(w * 1.04f, h * 0.62f)
                lineTo(w * 1.04f, h)
                close()
            }
            val midRange = Path().apply {
                moveTo(w * 0.30f, h)
                lineTo(w * 0.58f, h * 0.56f)
                lineTo(w * 0.75f, h * 0.76f)
                lineTo(w * 0.93f, h * 0.50f)
                lineTo(w * 1.04f, h * 0.68f)
                lineTo(w * 1.04f, h)
                close()
            }
            val frontRange = Path().apply {
                moveTo(w * 0.44f, h)
                lineTo(w * 0.70f, h * 0.68f)
                lineTo(w * 0.86f, h * 0.84f)
                lineTo(w * 1.02f, h * 0.66f)
                lineTo(w * 1.02f, h)
                close()
            }
            val moonCenter = Offset(w * 0.84f, h * 0.22f)
            val moonRadius = canvasSize.minDimension * 0.075f
            val haloStroke = Stroke(width = thinStroke * 0.8f)
            ({
                drawCircle(color = accent.copy(alpha = 0.22f), radius = moonRadius, center = moonCenter)
                drawCircle(
                    color = accent.copy(alpha = 0.16f),
                    radius = moonRadius * 1.48f,
                    center = moonCenter,
                    style = haloStroke,
                )
                drawPath(backRange, accent.copy(alpha = 0.09f))
                drawPath(midRange, accent.copy(alpha = 0.15f))
                drawPath(frontRange, accent.copy(alpha = 0.22f))
            })
        }

        CardMotif.LotusMandala -> {
            val center = Offset(canvasSize.width * 0.80f, canvasSize.height * 0.54f)
            val petalLen = canvasSize.minDimension * 0.34f
            val petalSpread = canvasSize.minDimension * 0.13f
            val petals = List(8) { index ->
                val angle = index * (PI / 4.0)
                val cosA = cos(angle).toFloat()
                val sinA = sin(angle).toFloat()
                val perpX = -sinA
                val perpY = cosA
                val tip = Offset(center.x + cosA * petalLen, center.y + sinA * petalLen)
                val ctrl1 = Offset(
                    center.x + cosA * petalLen * 0.52f + perpX * petalSpread,
                    center.y + sinA * petalLen * 0.52f + perpY * petalSpread,
                )
                val ctrl2 = Offset(
                    center.x + cosA * petalLen * 0.52f - perpX * petalSpread,
                    center.y + sinA * petalLen * 0.52f - perpY * petalSpread,
                )
                Path().apply {
                    moveTo(center.x, center.y)
                    quadraticTo(ctrl1.x, ctrl1.y, tip.x, tip.y)
                    quadraticTo(ctrl2.x, ctrl2.y, center.x, center.y)
                    close()
                }
            }
            val stroke = Stroke(width = thinStroke * 0.9f)
            ({
                petals.forEachIndexed { index, petal ->
                    if (index % 2 == 0) {
                        drawPath(petal, accent.copy(alpha = 0.08f))
                    }
                    drawPath(petal, accent.copy(alpha = 0.26f), style = stroke)
                }
                drawCircle(
                    color = accent.copy(alpha = 0.30f),
                    radius = petalLen * 0.26f,
                    center = center,
                    style = stroke,
                )
                drawCircle(color = accent.copy(alpha = 0.48f), radius = thinStroke * 2.2f, center = center)
            })
        }

        CardMotif.ConstellationMap -> {
            val stars = CONSTELLATION_NODES.map { (fx, fy) ->
                Offset(canvasSize.width * fx, canvasSize.height * fy)
            }
            val polaris = stars[2]
            val rayLen = canvasSize.minDimension * 0.055f
            ({
                CONSTELLATION_EDGES.forEach { (a, b) ->
                    drawLine(
                        color = accent.copy(alpha = 0.24f),
                        start = stars[a],
                        end = stars[b],
                        strokeWidth = thinStroke * 0.85f,
                        cap = StrokeCap.Round,
                    )
                }
                stars.forEachIndexed { index, pt ->
                    val r = canvasSize.minDimension * if (index == 2) 0.017f else 0.011f
                    drawCircle(color = accent.copy(alpha = 0.18f), radius = r * 2.2f, center = pt)
                    drawCircle(color = accent.copy(alpha = 0.62f), radius = r, center = pt)
                }
                drawLine(
                    color = accent.copy(alpha = 0.50f),
                    start = Offset(polaris.x - rayLen, polaris.y),
                    end = Offset(polaris.x + rayLen, polaris.y),
                    strokeWidth = thinStroke * 0.8f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = accent.copy(alpha = 0.50f),
                    start = Offset(polaris.x, polaris.y - rayLen),
                    end = Offset(polaris.x, polaris.y + rayLen),
                    strokeWidth = thinStroke * 0.8f,
                    cap = StrokeCap.Round,
                )
            })
        }

        CardMotif.AstrolabeCompass -> {
            val center = Offset(canvasSize.width * 0.80f, canvasSize.height * 0.52f)
            val outerR = canvasSize.minDimension * 0.36f
            val midR = canvasSize.minDimension * 0.24f
            val innerR = canvasSize.minDimension * 0.06f
            val ticks = List(12) { index ->
                val angle = index * (PI / 6.0)
                val cosA = cos(angle).toFloat()
                val sinA = sin(angle).toFloat()
                val tickIn = if (index % 3 == 0) outerR * 0.82f else outerR * 0.90f
                Offset(center.x + cosA * tickIn, center.y + sinA * tickIn) to
                    Offset(center.x + cosA * outerR, center.y + sinA * outerR)
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
            ({
                drawCircle(color = accent.copy(alpha = 0.24f), radius = outerR, center = center, style = ringStroke)
                drawCircle(color = accent.copy(alpha = 0.18f), radius = midR, center = center, style = ringStroke)
                drawCircle(
                    color = accent.copy(alpha = 0.14f),
                    radius = midR * 0.92f,
                    center = Offset(center.x - outerR * 0.14f, center.y - outerR * 0.10f),
                    style = ringStroke,
                )
                ticks.forEach { (s, e) ->
                    drawLine(accent.copy(alpha = 0.30f), s, e, strokeWidth = thinStroke * 0.8f)
                }
                drawPath(needle, accent.copy(alpha = 0.16f))
                drawPath(needle, accent.copy(alpha = 0.36f), style = ringStroke)
            })
        }

        CardMotif.OceanScales -> {
            val radius = canvasSize.minDimension * 0.15f
            val stroke = Stroke(width = thinStroke * 0.85f, cap = StrokeCap.Round)
            val centers = buildList {
                repeat(4) { row ->
                    val stagger = if (row % 2 == 1) radius else 0f
                    repeat(4) { col ->
                        add(
                            Offset(
                                x = canvasSize.width * 0.50f + col * radius * 1.92f + stagger,
                                y = canvasSize.height * 0.34f + row * radius * 0.96f,
                            ),
                        )
                    }
                }
            }
            ({
                centers.forEach { c ->
                    for (ring in 1..3) {
                        val r = radius * ring / 3f
                        drawArc(
                            color = accent.copy(alpha = 0.11f + ring * 0.055f),
                            startAngle = 195f,
                            sweepAngle = 150f,
                            useCenter = false,
                            topLeft = Offset(c.x - r, c.y - r),
                            size = Size(r * 2f, r * 2f),
                            style = stroke,
                        )
                    }
                }
            })
        }

        CardMotif.HoneycombLattice -> {
            val hexR = canvasSize.minDimension * 0.115f
            val dx = hexR * 1.5f
            val dy = hexR * 1.732f
            val stroke = Stroke(width = thinStroke * 0.9f)
            val hexagons = buildList {
                for (col in 0..3) {
                    for (row in 0..2) {
                        val cx = canvasSize.width * 0.56f + col * dx
                        val cy = canvasSize.height * 0.24f + row * dy + if (col % 2 == 1) dy * 0.5f else 0f
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
            ({
                hexagons.forEach { (hex, filled) ->
                    if (filled) drawPath(hex, accent.copy(alpha = 0.09f))
                    drawPath(hex, accent.copy(alpha = 0.25f), style = stroke)
                }
            })
        }

        CardMotif.ChevronStripes -> {
            val w = canvasSize.width
            val h = canvasSize.height
            val chevrons = List(5) { index ->
                val x0 = w * (0.48f + index * 0.095f)
                Path().apply {
                    moveTo(x0, h * 0.14f)
                    lineTo(x0 + w * 0.11f, h * 0.50f)
                    lineTo(x0, h * 0.86f)
                }
            }
            val stroke = Stroke(width = thinStroke * 1.45f, cap = StrokeCap.Round)
            ({
                chevrons.forEachIndexed { index, path ->
                    drawPath(path, accent.copy(alpha = 0.12f + index * 0.065f), style = stroke)
                }
            })
        }

        CardMotif.SilkGuilloche -> {
            val w = canvasSize.width
            val h = canvasSize.height
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
            ({
                strands.forEachIndexed { index, path ->
                    drawPath(path, accent.copy(alpha = 0.11f + index * 0.032f), style = stroke)
                }
            })
        }

        CardMotif.AuroraBeams -> {
            val w = canvasSize.width
            val h = canvasSize.height
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
            val glowRadius = (canvasSize.minDimension * 0.42f).coerceAtLeast(1f)
            val glowBrush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.26f), Color.Transparent),
                center = glowCenter,
                radius = glowRadius,
            )
            ({
                drawCircle(brush = glowBrush, radius = glowRadius, center = glowCenter)
                beams.forEachIndexed { index, beam ->
                    drawPath(beam, accent.copy(alpha = 0.08f + index * 0.038f))
                }
            })
        }

        CardMotif.MonolithSlabs -> {
            val w = canvasSize.width
            val h = canvasSize.height
            val corner = CornerRadius(canvasSize.minDimension * 0.03f)
            val stroke = Stroke(width = thinStroke * 0.85f)
            ({
                repeat(3) { index ->
                    val slabW = w * 0.105f
                    val slabH = h * (0.42f + index * 0.18f)
                    val topLeft = Offset(w * (0.58f + index * 0.125f), h - slabH)
                    drawRoundRect(
                        color = accent.copy(alpha = 0.08f + index * 0.055f),
                        topLeft = topLeft,
                        size = Size(slabW, slabH + corner.y),
                        cornerRadius = corner,
                    )
                    drawRoundRect(
                        color = accent.copy(alpha = 0.24f + index * 0.06f),
                        topLeft = topLeft,
                        size = Size(slabW, slabH + corner.y),
                        cornerRadius = corner,
                        style = stroke,
                    )
                }
            })
        }
    }
}

private val CONSTELLATION_NODES = listOf(
    0.55f to 0.24f,
    0.66f to 0.16f,
    0.84f to 0.22f,
    0.74f to 0.40f,
    0.92f to 0.44f,
    0.62f to 0.58f,
    0.80f to 0.68f,
    0.93f to 0.80f,
)

private val CONSTELLATION_EDGES = listOf(
    0 to 1,
    1 to 2,
    1 to 3,
    2 to 4,
    3 to 4,
    3 to 5,
    3 to 6,
    6 to 7,
)

private val STAR_POINTS = listOf(
    0.56f to 0.16f,
    0.69f to 0.27f,
    0.83f to 0.12f,
    0.91f to 0.34f,
    0.74f to 0.43f,
    0.94f to 0.58f,
    0.64f to 0.61f,
    0.82f to 0.73f,
    0.53f to 0.84f,
    0.91f to 0.88f,
)
