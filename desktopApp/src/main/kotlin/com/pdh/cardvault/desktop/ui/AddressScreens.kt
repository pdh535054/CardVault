package com.pdh.cardvault.desktop.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.pdh.cardvault.desktop.model.DesktopAddress
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val AddressPanel = Color(0xFF202020)
private val AddressHairline = Color.White.copy(alpha = 0.085f)
private val AddressAccent = Color(0xFFAFC6FF)

@Composable
fun VaultHomeScreen(controller: DesktopAppController, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(42.dp)) {
        Text("选择你要打开的卡包", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("银行卡和地址均会加密保存在这台设备", color = Color.White.copy(alpha = 0.46f))
        Spacer(Modifier.height(34.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            HomeChoice(
                symbol = "▣",
                title = "银行卡",
                subtitle = "${controller.cards.size} 张卡片",
                modifier = Modifier.weight(1f),
            ) { controller.section = DesktopSection.Wallet }
            HomeChoice(
                symbol = "◇",
                title = "地址",
                subtitle = "${controller.addresses.size} 条地址",
                modifier = Modifier.weight(1f),
            ) { controller.section = DesktopSection.Addresses }
        }
    }
}

@Composable
private fun HomeChoice(symbol: String, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight(0.72f).clip(RoundedCornerShape(36.dp)).background(AddressPanel)
            .border(1.dp, AddressHairline, RoundedCornerShape(36.dp)).clickable(onClick = onClick).padding(34.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(symbol, color = AddressAccent, fontSize = 58.sp)
        Column {
            Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.42f))
        }
    }
}

@Composable
fun AddressWalletScreen(controller: DesktopAppController, modifier: Modifier = Modifier) {
    var deleteConfirmation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Row(modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(34.dp)) {
        Column(Modifier.width(470.dp).fillMaxHeight()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("地址", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (controller.addresses.isEmpty()) "建立你的离线地址包" else "${controller.addresses.size} 条地址",
                        color = Color.White.copy(alpha = 0.46f),
                        fontSize = 13.sp,
                    )
                }
                Box(
                    Modifier.width(54.dp).height(54.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, AddressHairline, CircleShape).clickable(onClick = controller::startAddAddress),
                    contentAlignment = Alignment.Center,
                ) { Text("＋", fontSize = 20.sp) }
            }
            Spacer(Modifier.height(28.dp))
            if (controller.addresses.isEmpty()) {
                EmptyAddresses(controller)
            } else {
                AddressStack(
                    addresses = controller.addresses,
                    selectedId = controller.selectedAddressId,
                    onSelect = controller::selectAddress,
                    onMove = controller::moveAddress,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(32.dp))
                .background(AddressPanel).border(1.dp, AddressHairline, RoundedCornerShape(32.dp)),
        ) {
            controller.selectedAddress?.let { address ->
                AddressDetailPanel(
                    address = address,
                    concealmentEpoch = controller.concealmentEpoch,
                    authenticating = controller.authenticatingAction != null,
                    onEdit = { scope.launch { controller.startEditAddress(address.id) } },
                    onDelete = { deleteConfirmation = true },
                    onCopy = { controller.copyAddress(address.id) },
                )
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("选择一条地址", color = Color.White.copy(alpha = 0.38f))
            }
        }
    }

    if (deleteConfirmation) {
        AlertDialog(
            onDismissRequest = { deleteConfirmation = false },
            title = { Text("删除这条地址？") },
            text = { Text("此操作会从本机加密地址包中移除该记录。") },
            confirmButton = {
                TextButton(
                    enabled = controller.authenticatingAction == null,
                    onClick = {
                        deleteConfirmation = false
                        scope.launch { controller.deleteSelectedAddress() }
                    },
                ) { Text("删除", color = Color(0xFFFF817B)) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmation = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun AddressStack(
    addresses: List<DesktopAddress>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    Box(modifier) {
        addresses.forEachIndexed { index, address ->
            val selected = address.id == selectedId
            val y by animateDpAsState(
                targetValue = (index * 74).dp,
                animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.82f),
            )
            val scale by animateFloatAsState(if (selected) 1.018f else 1f)
            var dragY by remember(address.id) { mutableFloatStateOf(0f) }
            AddressFace(
                address = address,
                compact = true,
                modifier = Modifier.fillMaxWidth().offset { IntOffset(0, dragY.roundToInt()) }.offset(y = y)
                    .zIndex(index.toFloat() + if (dragY != 0f) 100f else 0f)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .shadow(if (selected) 22.dp else 12.dp, RoundedCornerShape(28.dp))
                    .clickable { onSelect(address.id) }
                    .pointerInput(address.id, addresses.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onSelect(address.id) },
                            onDragEnd = {
                                val step = with(density) { 74.dp.toPx() }
                                val target = (index + dragY / step).roundToInt().coerceIn(addresses.indices)
                                dragY = 0f
                                onMove(address.id, target)
                            },
                            onDragCancel = { dragY = 0f },
                            onDrag = { change, amount -> change.consume(); dragY += amount.y },
                        )
                    },
            )
        }
        Text(
            "长按地址卡片并拖动即可调整顺序",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
            color = Color.White.copy(alpha = 0.34f),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun AddressDetailPanel(
    address: DesktopAddress,
    concealmentEpoch: Long,
    authenticating: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
) {
    var flipped by remember(address.id) { mutableStateOf(false) }
    LaunchedEffect(address.id) {
        flipped = false
        delay(120)
        flipped = true
    }
    LaunchedEffect(concealmentEpoch) {
        flipped = false
    }
    Column(Modifier.fillMaxSize().padding(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(address.nickname, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Row {
                TextButton(onClick = onEdit, enabled = !authenticating) { Text("编辑") }
                TextButton(onClick = onDelete, enabled = !authenticating) {
                    Text("删除", color = Color(0xFFFF817B))
                }
            }
        }
        Spacer(Modifier.height(30.dp))
        FlippableAddress(
            address = address,
            flipped = flipped,
            onCopy = onCopy,
            modifier = Modifier.fillMaxWidth().clickable { flipped = !flipped },
        )
        Spacer(Modifier.height(18.dp))
        Text(
            if (flipped) "点击卡片返回正面" else "点击卡片查看地址",
            color = Color.White.copy(alpha = 0.36f),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun EmptyAddresses(controller: DesktopAppController) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(30.dp)).background(Color.White.copy(alpha = 0.025f))
            .border(1.dp, AddressHairline, RoundedCornerShape(30.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("◇", color = AddressAccent, fontSize = 42.sp)
            Spacer(Modifier.height(12.dp))
            Text("还没有保存地址", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))
            Button(onClick = controller::startAddAddress) { Text("添加第一条地址") }
        }
    }
}
