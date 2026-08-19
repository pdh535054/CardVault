package com.pdh.cardvault.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdh.cardvault.desktop.model.AndroidTemplateStyleCodec
import com.pdh.cardvault.desktop.model.CardPattern
import com.pdh.cardvault.desktop.model.CoverPalettes
import com.pdh.cardvault.desktop.model.DesktopAddress
import com.pdh.cardvault.desktop.model.NicknameTypography
import java.util.UUID

@Composable
fun AddressEditorScreen(controller: DesktopAppController, modifier: Modifier = Modifier) {
    val draft = controller.editingAddressDraft
    var colorPickerTarget by remember { mutableStateOf<AddressColorTarget?>(null) }
    val paletteScrollState = rememberScrollState()
    val nicknameColorScrollState = rememberScrollState()

    Box(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(0.9f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { controller.section = DesktopSection.Addresses }) { Text("返回") }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (draft.id == null) "添加地址" else "编辑地址",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                Spacer(Modifier.height(30.dp))
                Text("实时预览", color = Color.White.copy(alpha = 0.54f), fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                AddressFace(previewAddress(draft), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
                Text(
                    "地址信息保存后将加密存储在本机",
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
                        onValueChange = { value -> controller.updateAddressDraft { it.copy(nickname = value.take(50)) } },
                        label = "地址名称",
                        error = controller.addressDraftErrors.nickname,
                    )
                    EditorField(
                        value = draft.detailedAddress,
                        onValueChange = { value -> controller.updateAddressDraft { it.copy(detailedAddress = value.take(500)) } },
                        label = "详细地址",
                        error = controller.addressDraftErrors.detailedAddress,
                        minLines = 2,
                        maxLines = 4,
                    )
                    EditorField(
                        value = draft.city,
                        onValueChange = { value -> controller.updateAddressDraft { it.copy(city = value.take(100)) } },
                        label = "城市",
                        error = controller.addressDraftErrors.city,
                    )
                    EditorField(
                        value = draft.other,
                        onValueChange = { value -> controller.updateAddressDraft { it.copy(other = value.take(200)) } },
                        label = "其它（可选）",
                        error = controller.addressDraftErrors.other,
                        minLines = 2,
                        maxLines = 3,
                    )
                    EditorField(
                        value = draft.postalCode,
                        onValueChange = { value -> controller.updateAddressDraft { it.copy(postalCode = value.take(20)) } },
                        label = "邮编",
                        error = controller.addressDraftErrors.postalCode,
                    )
                    EditorField(
                        value = draft.country,
                        onValueChange = { value -> controller.updateAddressDraft { it.copy(country = value.take(100)) } },
                        label = "国家",
                        error = controller.addressDraftErrors.country,
                    )

                    WithDesktopUiFontSize {
                        ChoiceTitle("卡面颜色")
                        Row(
                            Modifier.fillMaxWidth().dragScrollableHorizontally(paletteScrollState),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CoverPalettes.all.forEach { palette ->
                                val selected = draft.style.startArgb == palette.startArgb &&
                                    draft.style.endArgb == palette.endArgb
                                ColorChoice(
                                    label = palette.name,
                                    start = Color(palette.startArgb),
                                    end = Color(palette.endArgb),
                                    selected = selected,
                                    onClick = {
                                        controller.updateAddressDraft {
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
                                onClick = { colorPickerTarget = AddressColorTarget.Cover },
                            )
                        }
                        ChoiceTitle("花纹")
                        ChoiceRow(CardPattern.entries, draft.style.pattern, CardPattern::displayName) { pattern ->
                            controller.updateAddressDraft {
                                it.copy(style = it.style.copy(pattern = pattern, sourceTemplateId = null))
                            }
                        }
                        ChoiceTitle("名称版式")
                        ChoiceRow(NicknameTypography.entries, draft.style.typography, NicknameTypography::displayName) { type ->
                            controller.updateAddressDraft {
                                it.copy(style = it.style.copy(typography = type, sourceTemplateId = null))
                            }
                        }
                        ChoiceTitle("名称颜色")
                        Row(
                            Modifier.fillMaxWidth().dragScrollableHorizontally(nicknameColorScrollState),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            nicknameColors.forEach { argb ->
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape).background(Color(argb))
                                        .border(
                                            if (draft.style.nicknameArgb == argb) 3.dp else 1.dp,
                                            if (draft.style.nicknameArgb == argb) Color(0xFFAFC6FF) else EditorStroke,
                                            CircleShape,
                                        )
                                        .clickable {
                                            controller.updateAddressDraft {
                                                it.copy(style = it.style.copy(nicknameArgb = argb, sourceTemplateId = null))
                                            }
                                        },
                                )
                            }
                            Box(
                                Modifier.height(36.dp).clip(CircleShape)
                                    .background(Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta)))
                                    .clickable { colorPickerTarget = AddressColorTarget.Nickname }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("自定义", fontSize = 12.sp, color = Color.Black) }
                        }
                    }

                    Button(
                        onClick = controller::saveAddressDraft,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFAFC6FF),
                            contentColor = Color(0xFF10131A),
                        ),
                    ) { Text(if (draft.id == null) "保存地址" else "保存修改") }
                }
            }
        }

        colorPickerTarget?.let { target ->
            ColorPickerOverlay(
                initial = Color(if (target == AddressColorTarget.Cover) draft.style.startArgb else draft.style.nicknameArgb),
                onDismiss = { colorPickerTarget = null },
                onConfirm = { color ->
                    val argb = color.toArgb()
                    controller.updateAddressDraft {
                        if (target == AddressColorTarget.Cover) {
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

private enum class AddressColorTarget { Cover, Nickname }

private val nicknameColors = listOf(
    0xFFFFF8E8.toInt(), 0xFF111318.toInt(), 0xFFFFD166.toInt(),
    0xFFE3EAF2.toInt(), 0xFF87D9FF.toInt(), 0xFF8CE8C5.toInt(),
    0xFFFF9DB6.toInt(), 0xFFD7B7FF.toInt(), 0xFFFFA080.toInt(),
)

private fun previewAddress(draft: AddressDraft): DesktopAddress = DesktopAddress(
    id = draft.id ?: UUID(0L, 1L).toString(),
    nickname = draft.nickname.trim().ifBlank { "地址名称" }.take(50),
    detailedAddress = draft.detailedAddress.trim().ifBlank { "详细地址" }.take(500),
    city = draft.city.trim().ifBlank { "城市" }.take(100),
    other = draft.other.trim().take(200),
    postalCode = draft.postalCode.trim().ifBlank { "000000" }.take(20),
    country = draft.country.trim().ifBlank { "国家" }.take(100),
    cardTemplateId = AndroidTemplateStyleCodec.encode(draft.style),
    sortOrder = 0,
    createdAtEpochMillis = 1,
    updatedAtEpochMillis = 1,
)
