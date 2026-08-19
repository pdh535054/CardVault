package com.pdh.cardvault.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdh.cardvault.desktop.model.CardPattern
import com.pdh.cardvault.desktop.model.CoverPalettes
import com.pdh.cardvault.desktop.model.DesktopCard
import com.pdh.cardvault.desktop.model.NicknameTypography
import java.util.UUID
import kotlin.math.roundToInt

internal val EditorPanel = Color(0xFF202020)
internal val EditorStroke = Color.White.copy(alpha = 0.09f)

@Composable
fun CardEditorScreen(controller: DesktopAppController, modifier: Modifier = Modifier) {
    val draft = controller.editingDraft
    val cvvFocus = remember { FocusRequester() }
    var colorPickerTarget by remember { mutableStateOf<ColorTarget?>(null) }
    var focusCvvAfterExpiryCommit by remember(draft.id) { mutableStateOf(false) }
    val paletteScrollState = rememberScrollState()
    val nicknameColorScrollState = rememberScrollState()

    LaunchedEffect(draft.expiryDigits, focusCvvAfterExpiryCommit) {
        if (focusCvvAfterExpiryCommit && draft.expiryDigits.length == 4) {
            focusCvvAfterExpiryCommit = false
            cvvFocus.requestFocus()
        }
    }

    Box(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(0.9f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { controller.section = DesktopSection.Wallet }) { Text("返回") }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (draft.id == null) "添加银行卡" else "编辑银行卡",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                Spacer(Modifier.height(30.dp))
                Text("实时预览", color = Color.White.copy(alpha = 0.54f), fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                CardFace(
                    card = previewCard(draft),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "卡面会随你的选择即时更新",
                    color = Color.White.copy(alpha = 0.42f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            WithOriginalDesktopFontSize {
                Column(
                    modifier = Modifier.weight(1.15f).fillMaxHeight()
                        .clip(RoundedCornerShape(28.dp))
                        .background(EditorPanel)
                        .border(1.dp, EditorStroke, RoundedCornerShape(28.dp))
                        .padding(28.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(17.dp),
                ) {
                EditorField(
                    value = draft.nickname,
                    onValueChange = { value -> controller.updateDraft { it.copy(nickname = value.take(50)) } },
                    label = "卡片名称",
                    error = controller.draftErrors.nickname,
                )
                EditorField(
                    value = draft.cardNumber,
                    onValueChange = { value ->
                        val digits = value.filter { it in '0'..'9' }.take(19)
                        controller.updateDraft { it.copy(cardNumber = digits) }
                    },
                    label = "卡号",
                    error = controller.draftErrors.cardNumber,
                    keyboardType = KeyboardType.Number,
                    visualTransformation = GroupedDigitsTransformation,
                )
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        EditorField(
                            value = draft.expiryDigits,
                            onValueChange = { value ->
                                val digits = value.filter { it in '0'..'9' }.take(4)
                                val reachedCompleteExpiry = draft.expiryDigits.length < 4 && digits.length == 4
                                controller.updateDraft { it.copy(expiryDigits = digits) }
                                if (reachedCompleteExpiry) focusCvvAfterExpiryCommit = true
                            },
                            label = "有效期  MM/YY",
                            error = controller.draftErrors.expiry,
                            keyboardType = KeyboardType.Number,
                            visualTransformation = ExpiryTransformation,
                            modifier = Modifier.weight(1f),
                        )
                        EditorField(
                            value = draft.cvv,
                            onValueChange = { value ->
                                controller.updateDraft { it.copy(cvv = value.filter { char -> char in '0'..'9' }.take(4)) }
                            },
                            label = "CVV（可选）",
                            error = controller.draftErrors.cvv,
                            keyboardType = KeyboardType.NumberPassword,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.weight(1f).focusRequester(cvvFocus),
                        )
                    }
                EditorField(
                    value = draft.notes,
                    onValueChange = { value -> controller.updateDraft { it.copy(notes = value.take(1000)) } },
                    label = "备注（可选）",
                    error = controller.draftErrors.notes,
                    minLines = 2,
                    maxLines = 3,
                )

                WithDesktopUiFontSize {
                    ChoiceTitle("卡面颜色")
                    Row(
                        Modifier.fillMaxWidth().dragScrollableHorizontally(paletteScrollState),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CoverPalettes.all.forEach { palette ->
                            val selected = draft.style.startArgb == palette.startArgb && draft.style.endArgb == palette.endArgb
                            ColorChoice(
                                label = palette.name,
                                start = Color(palette.startArgb),
                                end = Color(palette.endArgb),
                                selected = selected,
                                onClick = {
                                    controller.updateDraft {
                                        it.copy(style = it.style.copy(
                                            startArgb = palette.startArgb,
                                            endArgb = palette.endArgb,
                                            accentArgb = palette.accentArgb,
                                            nicknameArgb = palette.foregroundArgb,
                                            sourceTemplateId = null,
                                        ))
                                    }
                                },
                            )
                        }
                        ColorChoice(
                            label = "自定义",
                            start = Color(draft.style.startArgb),
                            end = Color(draft.style.endArgb),
                            selected = false,
                            onClick = { colorPickerTarget = ColorTarget.Cover },
                        )
                    }

                    ChoiceTitle("花纹")
                    ChoiceRow(CardPattern.entries, draft.style.pattern, CardPattern::displayName) { pattern ->
                        controller.updateDraft { it.copy(style = it.style.copy(pattern = pattern, sourceTemplateId = null)) }
                    }

                    ChoiceTitle("名称版式")
                    ChoiceRow(NicknameTypography.entries, draft.style.typography, NicknameTypography::displayName) { type ->
                        controller.updateDraft { it.copy(style = it.style.copy(typography = type, sourceTemplateId = null)) }
                    }

                    ChoiceTitle("名称颜色")
                    Row(
                        Modifier.fillMaxWidth().dragScrollableHorizontally(nicknameColorScrollState),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        listOf(
                            0xFFFFF8E8.toInt(),
                            0xFF111318.toInt(),
                            0xFFFFD166.toInt(),
                            0xFFE3EAF2.toInt(),
                            0xFF87D9FF.toInt(),
                            0xFF8CE8C5.toInt(),
                            0xFFFF9DB6.toInt(),
                            0xFFD7B7FF.toInt(),
                            0xFFFFA080.toInt(),
                        ).forEach { argb ->
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(Color(argb))
                                    .border(if (draft.style.nicknameArgb == argb) 3.dp else 1.dp, if (draft.style.nicknameArgb == argb) Color(0xFFAFC6FF) else EditorStroke, CircleShape)
                                    .clickable { controller.updateDraft { it.copy(style = it.style.copy(nicknameArgb = argb, sourceTemplateId = null)) } },
                            )
                        }
                        Box(
                            Modifier.height(36.dp).clip(CircleShape)
                                .background(Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta)))
                                .clickable { colorPickerTarget = ColorTarget.Nickname }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("自定义", fontSize = 12.sp, color = Color.Black, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = controller::saveDraft,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAFC6FF), contentColor = Color(0xFF10131A)),
                ) { Text(if (draft.id == null) "加入卡包" else "保存修改") }
                }
            }
        }

        colorPickerTarget?.let { target ->
            ColorPickerOverlay(
                initial = Color(if (target == ColorTarget.Cover) draft.style.startArgb else draft.style.nicknameArgb),
                onDismiss = { colorPickerTarget = null },
                onConfirm = { color ->
                    val argb = color.toArgb()
                    controller.updateDraft {
                        if (target == ColorTarget.Cover) {
                            it.copy(style = it.style.copy(
                                startArgb = argb,
                                endArgb = blendWithBlack(argb, 0.62f),
                                accentArgb = blendWithWhite(argb, 0.38f),
                                sourceTemplateId = null,
                            ))
                        } else {
                            it.copy(style = it.style.copy(nicknameArgb = argb, sourceTemplateId = null))
                        }
                    }
                    colorPickerTarget = null
                },
            )
        }
    }
}

private enum class ColorTarget { Cover, Nickname }

@Composable
internal fun ColorPickerOverlay(initial: Color, onDismiss: () -> Unit, onConfirm: (Color) -> Unit) {
    val initialHsv = remember(initial) { FloatArray(3).also { java.awt.Color.RGBtoHSB((initial.red * 255).roundToInt(), (initial.green * 255).roundToInt(), (initial.blue * 255).roundToInt(), it) } }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    val selected = Color(java.awt.Color.HSBtoRGB(hue, saturation, value))

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(620.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF242424))
                .border(1.dp, EditorStroke, RoundedCornerShape(28.dp))
                .clickable(enabled = false) {}
                .padding(26.dp),
        ) {
            Text("自定义颜色", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val hueColor = Color(java.awt.Color.HSBtoRGB(hue, 1f, 1f))
                Box(
                    Modifier.weight(1f).height(300.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        .pointerInput(hue) {
                            detectTapGestures { offset ->
                                saturation = (offset.x / size.width).coerceIn(0f, 1f)
                                value = (1f - offset.y / size.height).coerceIn(0f, 1f)
                            }
                        }
                        .pointerInput(hue) {
                            detectDragGestures { change, _ ->
                                saturation = (change.position.x / size.width).coerceIn(0f, 1f)
                                value = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                            }
                        },
                ) {
                    Box(
                        Modifier.align(Alignment.TopStart)
                            .padding(start = (saturation * 402).dp, top = ((1f - value) * 276).dp)
                            .size(18.dp).border(3.dp, Color.White, CircleShape).clip(CircleShape),
                    )
                }
                Canvas(
                    Modifier.width(34.dp).height(300.dp).clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { offset -> hue = (offset.y / size.height).coerceIn(0f, 1f) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ -> hue = (change.position.y / size.height).coerceIn(0f, 1f) }
                        },
                ) {
                    val segments = 180
                    repeat(segments) { index ->
                        val top = size.height * index / segments
                        drawRect(
                            color = Color(java.awt.Color.HSBtoRGB(index / segments.toFloat(), 1f, 1f)),
                            topLeft = Offset(0f, top),
                            size = Size(size.width, size.height / segments + 1f),
                        )
                    }
                    drawRect(Color.White, topLeft = Offset(0f, hue * size.height - 2f), size = Size(size.width, 4f))
                }
                Column(Modifier.width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(82.dp).clip(RoundedCornerShape(12.dp)).background(selected))
                    Spacer(Modifier.height(14.dp))
                    Text("#%06X".format(selected.toArgb() and 0xFFFFFF), fontSize = 14.sp)
                    Text("R ${(selected.red * 255).roundToInt()}", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                    Text("G ${(selected.green * 255).roundToInt()}", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                    Text("B ${(selected.blue * 255).roundToInt()}", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onConfirm(selected) }) { Text("使用此颜色") }
            }
        }
    }
}

@Composable
internal fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    minLines: Int = 1,
    maxLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        isError = error != null,
        supportingText = error?.let { message -> ({ Text(message) }) },
        minLines = minLines,
        maxLines = maxLines,
        singleLine = maxLines == 1,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFFAFC6FF),
            unfocusedBorderColor = Color.White.copy(alpha = 0.13f),
        ),
    )
}

@Composable
internal fun ChoiceTitle(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
}

@Composable
internal fun ColorChoice(label: String, start: Color, end: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape)
            .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.045f))
            .border(if (selected) 2.dp else 1.dp, if (selected) Color(0xFFAFC6FF) else EditorStroke, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Brush.linearGradient(listOf(start, end))))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 12.sp)
    }
}

@Composable
internal fun <T> ChoiceRow(values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val scrollState = rememberScrollState()
    Row(
        Modifier.fillMaxWidth().dragScrollableHorizontally(scrollState),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        values.forEach { value ->
            Text(
                text = label(value),
                modifier = Modifier.clip(CircleShape)
                    .background(if (value == selected) Color(0xFFAFC6FF) else Color.White.copy(alpha = 0.055f))
                    .clickable { onSelect(value) }
                    .padding(horizontal = 15.dp, vertical = 9.dp),
                color = if (value == selected) Color(0xFF10131A) else Color.White.copy(alpha = 0.78f),
                fontSize = 12.sp,
            )
        }
    }
}

internal fun Modifier.dragScrollableHorizontally(scrollState: ScrollState): Modifier =
    horizontalScroll(scrollState).pointerInput(scrollState) {
        detectHorizontalDragGestures { change, dragAmount ->
            change.consume()
            scrollState.dispatchRawDelta(-dragAmount)
        }
    }

private object GroupedDigitsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text.take(19)
        val transformed = original.chunked(4).joinToString(" ")
        return TransformedText(AnnotatedString(transformed), object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = (offset + (offset.coerceAtMost(original.length) / 4)).coerceAtMost(transformed.length)
            override fun transformedToOriginal(offset: Int): Int = (offset - offset / 5).coerceIn(0, original.length)
        })
    }
}

private object ExpiryTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text.take(4)
        val transformed = if (original.length < 2) original else original.take(2) + "/" + original.drop(2)
        return TransformedText(AnnotatedString(transformed), object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = (offset + if (offset >= 2) 1 else 0).coerceAtMost(transformed.length)
            override fun transformedToOriginal(offset: Int): Int = (offset - if (offset > 2) 1 else 0).coerceIn(0, original.length)
        })
    }
}

private fun previewCard(draft: CardDraft): DesktopCard {
    val number = draft.cardNumber.filter { it in '0'..'9' }.padEnd(12, '0').take(19)
    val expiry = draft.expiryDigits.padEnd(4, '0')
    return DesktopCard(
        id = draft.id ?: UUID(0L, 0L).toString(),
        nickname = draft.nickname.trim().ifBlank { "卡片名称" }.take(50),
        issuerName = draft.issuerName.trim().take(80).ifBlank { "CardVault" },
        cardNumber = number,
        expiryMonth = expiry.take(2).toIntOrNull()?.coerceIn(1, 12) ?: 1,
        expiryYear = 2000 + (expiry.drop(2).toIntOrNull() ?: 0),
        cvv = draft.cvv.takeIf { it.length in 3..4 },
        notes = draft.notes.take(1000),
        cardTemplateId = com.pdh.cardvault.desktop.model.AndroidTemplateStyleCodec.encode(draft.style),
        sortOrder = 0,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}

internal fun blendWithBlack(argb: Int, factor: Float): Int {
    val r = ((argb shr 16 and 0xFF) * factor).roundToInt()
    val g = ((argb shr 8 and 0xFF) * factor).roundToInt()
    val b = ((argb and 0xFF) * factor).roundToInt()
    return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
}

internal fun blendWithWhite(argb: Int, amount: Float): Int {
    fun channel(shift: Int): Int {
        val base = argb shr shift and 0xFF
        return (base + (255 - base) * amount).roundToInt()
    }
    return 0xFF000000.toInt() or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}
