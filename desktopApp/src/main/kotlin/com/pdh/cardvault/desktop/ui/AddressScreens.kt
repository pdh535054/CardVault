package com.pdh.cardvault.desktop.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.pdh.cardvault.desktop.DesktopAppInfo
import com.pdh.cardvault.desktop.model.DesktopAddress
import com.pdh.cardvault.desktop.model.DesktopAddressCopyPart
import com.pdh.cardvault.wallet.BackupReminder
import com.pdh.cardvault.wallet.BackupReminderPolicy
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val AddressPanel = Color(0xFF141B27)
private val AddressHairline = Color.White.copy(alpha = 0.085f)
private val AddressAccent = Color(0xFFB4CBFF)
private val AddressTealAccent = Color(0xFF7FD8BE)

@Composable
fun VaultHomeScreen(controller: DesktopAppController, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val primaryGlow = Brush.radialGradient(
                    colors = listOf(AddressAccent.copy(alpha = 0.13f), Color.Transparent),
                    center = Offset(size.width * 0.82f, size.height * 0.12f),
                    radius = size.maxDimension * 0.56f,
                )
                val secondaryGlow = Brush.radialGradient(
                    colors = listOf(AddressTealAccent.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.18f, size.height * 0.88f),
                    radius = size.maxDimension * 0.48f,
                )
                onDrawBehind {
                    drawRect(brush = primaryGlow)
                    drawRect(brush = secondaryGlow)
                }
            },
    ) {
        Column(Modifier.fillMaxSize().padding(42.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF162030),
                    modifier = Modifier.desktopSpecularRim(cornerRadius = 18.dp, topAlpha = 0.22f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(7.dp).background(AddressTealAccent, CircleShape))
                        Text(
                            text = "本机离线加密 · AES-256-GCM",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Surface(
                    onClick = { controller.section = DesktopSection.Settings },
                    shape = CircleShape,
                    color = Color(0xFF162030),
                    modifier = Modifier.desktopSpecularRim(cornerRadius = 18.dp, topAlpha = 0.22f),
                ) {
                    Text(
                        text = DesktopAppInfo.editionLabel,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        color = AddressAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = "选择你要打开的卡包",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "银行卡和地址均加密保存在本机，随时极速调取",
                color = Color.White.copy(alpha = 0.52f),
            )
            controller.backupReminder?.let { reminder ->
                Spacer(Modifier.height(20.dp))
                DesktopBackupReminder(
                    reminder = reminder,
                    onOpenBackup = { controller.section = DesktopSection.Transfer },
                    onSnooze = controller::snoozeBackupReminder,
                )
            }
            Spacer(Modifier.height(30.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                HomeChoice(
                    portalKind = DesktopPortalKind.Cards,
                    accent = AddressAccent,
                    title = "银行卡",
                    subtitle = "${controller.cards.size} 张卡片",
                    modifier = Modifier.weight(1f),
                ) { controller.section = DesktopSection.Wallet }
                HomeChoice(
                    portalKind = DesktopPortalKind.Addresses,
                    accent = AddressTealAccent,
                    title = "地址",
                    subtitle = "${controller.addresses.size} 条地址",
                    modifier = Modifier.weight(1f),
                ) { controller.section = DesktopSection.Addresses }
            }
        }
    }
}

@Composable
private fun DesktopBackupReminder(
    reminder: BackupReminder,
    onOpenBackup: () -> Unit,
    onSnooze: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF282015))
            .border(1.dp, Color(0xFFFFD49B).copy(alpha = 0.35f), RoundedCornerShape(24.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                when (reminder) {
                    BackupReminder.NeverSynced -> "这台电脑上的卡包还没有备份过"
                    is BackupReminder.Overdue -> "已经 ${reminder.daysSinceSync} 天没有备份卡包了"
                },
                color = Color(0xFFFFD49B),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "CardVault 只把资料加密保存在本机。导出一份加密文件到手机，换电脑或重装系统时才能找回。",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
        }
        Button(onClick = onOpenBackup) { Text("去备份") }
        TextButton(onClick = onSnooze) { Text("${BackupReminderPolicy.SNOOZE_DAYS} 天后再提醒") }
    }
}

private enum class DesktopPortalKind {
    Cards,
    Addresses,
}

@Composable
private fun HomeChoice(
    portalKind: DesktopPortalKind,
    accent: Color,
    title: String,
    subtitle: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.982f
            hovered -> 1.014f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 620f),
        label = "home-choice-scale",
    )
    val borderAlpha by animateFloatAsState(if (hovered) 0.52f else 0f, tween(160), label = "home-choice-border")
    Column(
        modifier
            .fillMaxHeight(0.76f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(34.dp))
            .drawWithCache {
                val panelBrush = Brush.linearGradient(
                    colors = listOf(Color(0xFF192233), AddressPanel),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val cornerGlow = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.16f, size.height * 0.18f),
                    radius = size.maxDimension * 0.55f,
                )
                onDrawBehind {
                    drawRect(brush = panelBrush)
                    drawRect(brush = cornerGlow)
                }
            }
            .desktopSpecularRim(cornerRadius = 34.dp, topAlpha = 0.24f)
            .border(1.5.dp, accent.copy(alpha = borderAlpha), RoundedCornerShape(34.dp))
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(34.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(accent.copy(alpha = 0.14f))
                    .desktopSpecularRim(cornerRadius = 20.dp, topAlpha = 0.28f),
                contentAlignment = Alignment.Center,
            ) {
                DesktopPortalGlyph(portalKind, accent)
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (hovered) 0.12f else 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("›", color = accent, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column {
            Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.52f))
        }
    }
}

@Composable
private fun DesktopPortalGlyph(kind: DesktopPortalKind, color: Color) {
    Canvas(modifier = Modifier.size(30.dp)) {
        val stroke = 2.2.dp.toPx()
        when (kind) {
            DesktopPortalKind.Cards -> {
                val cardTop = size.height * 0.18f
                val cardHeight = size.height * 0.64f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.06f, cardTop),
                    size = Size(size.width * 0.88f, cardHeight),
                    cornerRadius = CornerRadius(size.minDimension * 0.16f),
                    style = Stroke(width = stroke),
                )
                drawLine(
                    color = color.copy(alpha = 0.55f),
                    start = Offset(size.width * 0.08f, cardTop + cardHeight * 0.34f),
                    end = Offset(size.width * 0.92f, cardTop + cardHeight * 0.34f),
                    strokeWidth = stroke * 0.85f,
                )
                drawRoundRect(
                    color = color.copy(alpha = 0.88f),
                    topLeft = Offset(size.width * 0.20f, cardTop + cardHeight * 0.54f),
                    size = Size(size.width * 0.22f, cardHeight * 0.24f),
                    cornerRadius = CornerRadius(size.minDimension * 0.06f),
                )
            }
            DesktopPortalKind.Addresses -> {
                val cx = size.width * 0.5f
                val pinRadius = size.width * 0.29f
                val cy = size.height * 0.38f
                val path = Path().apply {
                    moveTo(cx, size.height * 0.90f)
                    cubicTo(
                        cx - pinRadius * 1.12f,
                        cy + pinRadius * 0.86f,
                        cx - pinRadius,
                        cy - pinRadius * 0.2f,
                        cx - pinRadius,
                        cy,
                    )
                    cubicTo(
                        cx - pinRadius,
                        cy - pinRadius * 0.65f,
                        cx - pinRadius * 0.55f,
                        cy - pinRadius,
                        cx,
                        cy - pinRadius,
                    )
                    cubicTo(
                        cx + pinRadius * 0.55f,
                        cy - pinRadius,
                        cx + pinRadius,
                        cy - pinRadius * 0.65f,
                        cx + pinRadius,
                        cy,
                    )
                    cubicTo(
                        cx + pinRadius,
                        cy - pinRadius * 0.2f,
                        cx + pinRadius * 1.12f,
                        cy + pinRadius * 0.86f,
                        cx,
                        size.height * 0.90f,
                    )
                    close()
                }
                drawPath(path = path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
                drawCircle(color = color, radius = pinRadius * 0.34f, center = Offset(cx, cy))
            }
        }
    }
}

@Composable
fun AddressWalletScreen(controller: DesktopAppController, modifier: Modifier = Modifier) {
    var deleteConfirmation by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val normalizedQuery = searchQuery.trim().lowercase()
    val searching = normalizedQuery.isNotEmpty()
    val searchResults = if (searching) {
        controller.addresses.filter { address ->
            address.nickname.lowercase().contains(normalizedQuery) ||
                address.city.lowercase().contains(normalizedQuery) ||
                address.detailedAddress.lowercase().contains(normalizedQuery) ||
                address.other.lowercase().contains(normalizedQuery) ||
                address.postalCode.lowercase().contains(normalizedQuery) ||
                address.country.lowercase().contains(normalizedQuery)
        }
    } else {
        emptyList()
    }
    val visibleAddresses = controller.addresses.filter { it.folderId == controller.selectedAddressFolderId }
    val children = controller.addressFolders.filter { it.parentFolderId == controller.selectedAddressFolderId }
    val entries = orderedDesktopWalletEntries(
        controller.addressWalletOrder,
        children.map { DesktopWalletEntry(it.id, true) } + visibleAddresses.map { DesktopWalletEntry(it.id, false) },
    )
    Row(modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(34.dp)) {
        Column(Modifier.width(470.dp).fillMaxHeight()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("地址", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (controller.addresses.isEmpty()) "建立你的离线地址包" else "${controller.addresses.size} 条地址",
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 13.sp,
                    )
                }
                Surface(
                    onClick = controller::startAddAddress,
                    modifier = Modifier
                        .size(50.dp)
                        .desktopSpecularRim(cornerRadius = 25.dp, topAlpha = 0.36f),
                    shape = CircleShape,
                    color = AddressAccent,
                    contentColor = Color(0xFF0B111D),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("＋", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (controller.addresses.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                DesktopSearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "搜索地址昵称、城市或街道…",
                    onClear = { searchQuery = "" },
                )
            }
            Spacer(Modifier.height(16.dp))
            if (searching) {
                if (searchResults.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("未找到匹配的地址", color = Color.White.copy(alpha = 0.42f))
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            "跨全部分类找到 ${searchResults.size} 条结果",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.48f),
                        )
                        searchResults.forEach { address ->
                            AddressFace(
                                address = address,
                                compact = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { controller.selectAddress(address.id) },
                            )
                        }
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val selectedFolder = controller.addressFolders.firstOrNull { it.id == controller.selectedAddressFolderId }
                    TextButton(
                        enabled = selectedFolder != null,
                        onClick = { controller.selectAddressFolder(selectedFolder?.parentFolderId) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            selectedFolder?.let { "‹ ${desktopFolderPath(controller.addressFolders, it.id)}" } ?: "全部分类",
                            maxLines = 1,
                        )
                    }
                    DesktopNewFolderButton { name ->
                        controller.createFolder(
                            name,
                            com.pdh.cardvault.desktop.model.DesktopFolderKind.ADDRESSES,
                            controller.selectedAddressFolderId,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "拖动排序；在文件夹上停稳，显示「松手移入」后松手归档",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                if (entries.isEmpty()) {
                    EmptyAddresses(controller)
                } else {
                    DesktopMixedWalletStack(
                        entries = entries,
                        onOpen = { if (it.isFolder) controller.selectAddressFolder(it.id) else controller.selectAddress(it.id) },
                        onReorder = { controller.reorderWalletEntries(com.pdh.cardvault.desktop.model.DesktopFolderKind.ADDRESSES, it) },
                        folderName = { id -> children.firstOrNull { it.id == id }?.name.orEmpty() },
                        canMoveIntoFolder = { entry, destination -> canDropDesktopWalletEntry(entry, destination, controller.addressFolders) },
                        onMoveIntoFolder = { entry, folder ->
                            if (entry.isFolder) controller.moveFolder(entry.id, folder)
                            else controller.moveAddressToFolder(entry.id, folder)
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) { entry ->
                        if (entry.isFolder) children.firstOrNull { it.id == entry.id }?.let { folder ->
                            DesktopFolderWalletCard(
                                folder,
                                controller.addressFolders,
                                countDesktopFolderTreeItems(controller.addressFolders, folder.id, controller.addresses.map { it.folderId }),
                                controller::renameFolder,
                                controller::moveFolder,
                                controller::deleteFolder,
                                controller::setFolderColor,
                            )
                        } else visibleAddresses.firstOrNull { it.id == entry.id }?.let { address ->
                            AddressFace(address, compact = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(32.dp))
                .background(AddressPanel)
                .desktopSpecularRim(cornerRadius = 32.dp, topAlpha = 0.20f),
        ) {
            controller.selectedAddress?.let { address ->
                AddressDetailPanel(
                    address = address,
                    concealmentEpoch = controller.concealmentEpoch,
                    authenticating = controller.authenticatingAction != null,
                    onEdit = { scope.launch { controller.startEditAddress(address.id) } },
                    moveControl = {
                        DesktopRecordMoveButton(
                            controller.addressFolders,
                            address.folderId,
                            enabled = controller.authenticatingAction == null,
                            onMove = { controller.moveAddressToFolder(address.id, it) },
                        )
                    },
                    onDelete = { deleteConfirmation = true },
                    onCopy = { part -> controller.copyAddress(address.id, part) },
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
    onDrop: (String, Offset) -> Boolean,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    DesktopStackViewport(itemCount = addresses.size, modifier = modifier) {
        addresses.forEachIndexed { index, address ->
            val selected = address.id == selectedId
            val y by animateDpAsState(
                targetValue = (index * DESKTOP_STACK_STEP_DP).dp,
                animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.82f),
            )
            val scale by animateFloatAsState(if (selected) 1.018f else 1f)
            var dragY by remember(address.id) { mutableFloatStateOf(0f) }
            var dragX by remember(address.id) { mutableFloatStateOf(0f) }
            var origin by remember(address.id) { mutableStateOf(Offset.Zero) }
            var pointer by remember(address.id) { mutableStateOf(Offset.Unspecified) }
            AddressFace(
                address = address,
                compact = true,
                modifier = Modifier.fillMaxWidth().offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }.offset(y = y)
                    .zIndex(index.toFloat() + if (dragY != 0f) 100f else 0f)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .onGloballyPositioned { if (dragX == 0f && dragY == 0f) origin = it.boundsInRoot().topLeft }
                    .clickable { onSelect(address.id) }
                    .pointerInput(address.id, addresses.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { local -> onSelect(address.id); pointer = origin + local },
                            onDragEnd = {
                                val step = with(density) { DESKTOP_STACK_STEP_DP.dp.toPx() }
                                val target = (index + dragY / step).roundToInt().coerceIn(addresses.indices)
                                val dropped = onDrop(address.id, pointer)
                                dragX = 0f
                                dragY = 0f
                                pointer = Offset.Unspecified
                                if (!dropped) onMove(address.id, target)
                            },
                            onDragCancel = { dragX = 0f; dragY = 0f; pointer = Offset.Unspecified },
                            onDrag = { change, amount ->
                                change.consume(); dragX += amount.x; dragY += amount.y; pointer += amount
                            },
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
    moveControl: @Composable () -> Unit,
    onDelete: () -> Unit,
    onCopy: (DesktopAddressCopyPart) -> Unit,
) {
    var flipped by remember(address.id) { mutableStateOf(false) }
    var initialEpoch by remember(address.id) { mutableLongStateOf(concealmentEpoch) }
    LaunchedEffect(address.id) {
        flipped = true
    }
    LaunchedEffect(concealmentEpoch) {
        if (concealmentEpoch != initialEpoch) {
            initialEpoch = concealmentEpoch
            flipped = false
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(address.nickname, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
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
        Spacer(Modifier.height(12.dp))
        moveControl()
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
