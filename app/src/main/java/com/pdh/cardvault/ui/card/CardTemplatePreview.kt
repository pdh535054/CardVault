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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
    shadowElevation: Dp = 6.dp,
    compactLayout: Boolean = false,
) {
    val useCompactLayout = compactLayout || LocalDensity.current.fontScale > 1.3f
    val previewDescription = stringResource(R.string.card_preview_content_description)
    val displayedNickname = nickname
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: stringResource(R.string.card_style_nickname_preview)

    Surface(
        modifier = modifier
            .aspectRatio(CARD_ASPECT_RATIO)
            .clearAndSetSemantics {
                contentDescription = previewDescription
            },
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Transparent,
        shadowElevation = shadowElevation,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(template.gradientStart, template.gradientEnd),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
                drawOriginalMotif(template)
            }

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
private fun CustomCardColorDialog(
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(template.gradientStart, template.gradientEnd),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
            drawOriginalMotif(template)
        }
    }
}

private fun DrawScope.drawOriginalMotif(template: CardTemplateSpec) {
    val accent = template.accent
    val thinStroke = size.minDimension * 0.008f

    when (template.motif) {
        CardMotif.Plain -> Unit

        CardMotif.DiagonalShards -> {
            val shard = Path().apply {
                moveTo(size.width * 0.58f, 0f)
                lineTo(size.width * 0.78f, 0f)
                lineTo(size.width * 0.47f, size.height)
                lineTo(size.width * 0.28f, size.height)
                close()
            }
            drawPath(shard, accent.copy(alpha = 0.2f))
            drawLine(
                color = accent.copy(alpha = 0.46f),
                start = Offset(size.width * 0.86f, 0f),
                end = Offset(size.width * 0.58f, size.height),
                strokeWidth = thinStroke,
            )
        }

        CardMotif.OrbitLines -> {
            repeat(3) { index ->
                drawCircle(
                    color = accent.copy(alpha = 0.16f + index * 0.07f),
                    radius = size.minDimension * (0.25f + index * 0.14f),
                    center = Offset(size.width * 0.84f, size.height * 0.12f),
                    style = Stroke(width = thinStroke),
                )
            }
        }

        CardMotif.FacetedField -> {
            val upperFacet = Path().apply {
                moveTo(size.width * 0.45f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height * 0.42f)
                lineTo(size.width * 0.72f, size.height * 0.64f)
                close()
            }
            val lowerFacet = Path().apply {
                moveTo(size.width * 0.72f, size.height * 0.64f)
                lineTo(size.width, size.height * 0.42f)
                lineTo(size.width, size.height)
                lineTo(size.width * 0.38f, size.height)
                close()
            }
            drawPath(upperFacet, accent.copy(alpha = 0.18f))
            drawPath(lowerFacet, Color.White.copy(alpha = 0.06f))
        }

        CardMotif.StarField -> {
            STAR_POINTS.forEachIndexed { index, point ->
                drawCircle(
                    color = accent.copy(alpha = 0.32f + (index % 3) * 0.18f),
                    radius = size.minDimension * (0.006f + (index % 2) * 0.004f),
                    center = Offset(size.width * point.first, size.height * point.second),
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(size.width * 0.82f, size.height * 0.25f),
                    radius = size.minDimension * 0.34f,
                ),
                radius = size.minDimension * 0.34f,
                center = Offset(size.width * 0.82f, size.height * 0.25f),
            )
        }

        CardMotif.FlowingRibbons -> {
            repeat(3) { index ->
                val verticalOffset = size.height * (0.18f + index * 0.18f)
                val ribbon = Path().apply {
                    moveTo(-size.width * 0.05f, verticalOffset)
                    cubicTo(
                        size.width * 0.25f,
                        verticalOffset - size.height * 0.22f,
                        size.width * 0.65f,
                        verticalOffset + size.height * 0.25f,
                        size.width * 1.05f,
                        verticalOffset - size.height * 0.04f,
                    )
                }
                drawPath(
                    path = ribbon,
                    color = accent.copy(alpha = 0.2f + index * 0.06f),
                    style = Stroke(width = thinStroke * 1.5f, cap = StrokeCap.Round),
                )
            }
        }

        CardMotif.OffsetHalos -> {
            repeat(4) { index ->
                drawCircle(
                    color = accent.copy(alpha = 0.1f + index * 0.055f),
                    radius = size.minDimension * (0.16f + index * 0.12f),
                    center = Offset(size.width * 0.92f, size.height * 0.78f),
                    style = Stroke(width = thinStroke * 1.2f),
                )
            }
        }

        CardMotif.FineGrid -> {
            repeat(8) { index ->
                val x = size.width * index / 7f
                drawLine(
                    color = accent.copy(alpha = 0.1f),
                    start = Offset(x, 0f),
                    end = Offset(x - size.width * 0.18f, size.height),
                    strokeWidth = thinStroke * 0.55f,
                )
            }
            repeat(5) { index ->
                val y = size.height * index / 4f
                drawLine(
                    color = accent.copy(alpha = 0.08f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = thinStroke * 0.55f,
                )
            }
        }

        CardMotif.SoftWaves -> {
            repeat(3) { index ->
                val wave = Path().apply {
                    val base = size.height * (0.28f + index * 0.19f)
                    moveTo(0f, base)
                    cubicTo(
                        size.width * 0.28f,
                        base + size.height * 0.18f,
                        size.width * 0.62f,
                        base - size.height * 0.2f,
                        size.width,
                        base + size.height * 0.05f,
                    )
                }
                drawPath(
                    path = wave,
                    color = accent.copy(alpha = 0.15f + index * 0.05f),
                    style = Stroke(width = thinStroke * 1.3f, cap = StrokeCap.Round),
                )
            }
        }

        CardMotif.DotMatrix -> {
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
        }

        CardMotif.CircuitPaths -> {
            val nodes = listOf(
                Offset(size.width * 0.58f, size.height * 0.2f),
                Offset(size.width * 0.8f, size.height * 0.2f),
                Offset(size.width * 0.8f, size.height * 0.47f),
                Offset(size.width * 0.94f, size.height * 0.47f),
                Offset(size.width * 0.58f, size.height * 0.72f),
                Offset(size.width * 0.72f, size.height * 0.72f),
            )
            nodes.zipWithNext().forEach { (start, end) ->
                drawLine(
                    color = accent.copy(alpha = 0.28f),
                    start = start,
                    end = end,
                    strokeWidth = thinStroke,
                    cap = StrokeCap.Round,
                )
            }
            nodes.forEach { node ->
                drawCircle(
                    color = accent.copy(alpha = 0.52f),
                    radius = size.minDimension * 0.014f,
                    center = node,
                )
            }
        }

        CardMotif.VaultArches -> {
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
                    style = Stroke(width = thinStroke),
                )
            }
            drawRoundRect(
                color = accent.copy(alpha = 0.12f),
                topLeft = Offset(size.width * 0.72f, size.height * 0.38f),
                size = Size(size.width * 0.2f, size.height * 0.24f),
                cornerRadius = CornerRadius(size.minDimension * 0.04f),
                style = Stroke(width = thinStroke),
            )
        }

        CardMotif.ObsidianContours -> {
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
                    style = Stroke(width = thinStroke * 0.75f),
                )
            }
            drawLine(
                color = accent.copy(alpha = 0.45f),
                start = Offset(size.width * 0.64f, size.height * 0.18f),
                end = Offset(size.width * 0.9f, size.height * 0.18f),
                strokeWidth = thinStroke,
                cap = StrokeCap.Round,
            )
        }

        CardMotif.SilverFrame -> {
            val inset = size.minDimension * 0.07f
            drawRoundRect(
                color = accent.copy(alpha = 0.34f),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2f, size.height - inset * 2f),
                cornerRadius = CornerRadius(size.minDimension * 0.08f),
                style = Stroke(width = thinStroke * 0.8f),
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
        }

        CardMotif.MidnightBeam -> {
            val beam = Path().apply {
                moveTo(size.width * 0.58f, 0f)
                lineTo(size.width * 0.78f, 0f)
                lineTo(size.width, size.height * 0.72f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(beam, accent.copy(alpha = 0.13f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.24f), Color.Transparent),
                    center = Offset(size.width * 0.86f, size.height * 0.18f),
                    radius = size.minDimension * 0.28f,
                ),
                radius = size.minDimension * 0.28f,
                center = Offset(size.width * 0.86f, size.height * 0.18f),
            )
        }

        CardMotif.SageLoops -> {
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
                    style = Stroke(width = thinStroke, cap = StrokeCap.Round),
                )
            }
        }

        CardMotif.SandTiles -> {
            repeat(2) { row ->
                repeat(4) { column ->
                    val center = Offset(
                        x = size.width * (0.58f + column * 0.12f),
                        y = size.height * (0.24f + row * 0.27f),
                    )
                    val halfWidth = size.width * 0.045f
                    val halfHeight = size.height * 0.075f
                    val tile = Path().apply {
                        moveTo(center.x, center.y - halfHeight)
                        lineTo(center.x + halfWidth, center.y)
                        lineTo(center.x, center.y + halfHeight)
                        lineTo(center.x - halfWidth, center.y)
                        close()
                    }
                    drawPath(
                        path = tile,
                        color = accent.copy(alpha = 0.1f + (row + column) * 0.025f),
                        style = Stroke(width = thinStroke * 0.7f),
                    )
                }
            }
        }

        CardMotif.LilacAura -> {
            val auraCenter = Offset(size.width * 0.84f, size.height * 0.32f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.3f), Color.Transparent),
                    center = auraCenter,
                    radius = size.minDimension * 0.42f,
                ),
                radius = size.minDimension * 0.42f,
                center = auraCenter,
            )
            drawCircle(
                color = accent.copy(alpha = 0.22f),
                radius = size.minDimension * 0.2f,
                center = Offset(size.width * 0.7f, size.height * 0.72f),
                style = Stroke(width = thinStroke),
            )
        }

        CardMotif.MonochromeArcs -> {
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
                    style = Stroke(width = thinStroke * 0.85f, cap = StrokeCap.Round),
                )
            }
        }

        CardMotif.SparseGrid -> {
            val verticals = listOf(0.61f, 0.75f, 0.89f)
            val horizontals = listOf(0.23f, 0.47f, 0.71f)
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
        }

        CardMotif.ContinuousWave -> {
            repeat(2) { index ->
                val base = size.height * (0.35f + index * 0.2f)
                val wave = Path().apply {
                    moveTo(size.width * 0.42f, base)
                    cubicTo(
                        size.width * 0.58f,
                        base - size.height * 0.24f,
                        size.width * 0.72f,
                        base + size.height * 0.2f,
                        size.width * 0.86f,
                        base,
                    )
                    cubicTo(
                        size.width * 0.93f,
                        base - size.height * 0.1f,
                        size.width,
                        base - size.height * 0.08f,
                        size.width * 1.06f,
                        base,
                    )
                }
                drawPath(
                    path = wave,
                    color = accent.copy(alpha = 0.18f + index * 0.1f),
                    style = Stroke(width = thinStroke * 1.15f, cap = StrokeCap.Round),
                )
            }
        }
    }
}

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
