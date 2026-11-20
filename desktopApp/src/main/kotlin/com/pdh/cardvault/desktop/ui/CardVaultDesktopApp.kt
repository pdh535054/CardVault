package com.pdh.cardvault.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.pdh.cardvault.desktop.DesktopAppInfo
import com.pdh.cardvault.desktop.firstDroppedLocalFile
import com.pdh.cardvault.wallet.BackupReminderPolicy
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import com.pdh.cardvault.desktop.data.EncryptedDesktopVault
import com.pdh.cardvault.desktop.model.DesktopCard
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val AppBackground = Color(0xFF0B0E14)
private val NavigationBackground = Color(0xFF10151F)
private val PanelBackground = Color(0xFF141B27)
private val ElevatedSurface = Color(0xFF1A2333)
private val Hairline = Color.White.copy(alpha = 0.085f)
private val Accent = Color(0xFFB4CBFF)
internal val DesktopEmphasized = CubicBezierEasing(0.08f, 0.82f, 0.17f, 1f)
internal const val DESKTOP_UI_FONT_SCALE = 1.5f

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CardVaultDesktopApp(controller: DesktopAppController) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * DESKTOP_UI_FONT_SCALE),
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Accent,
                onPrimary = Color(0xFF0A1220),
                background = AppBackground,
                surface = PanelBackground,
                surfaceContainerLow = Color(0xFF111722),
                surfaceContainer = PanelBackground,
                surfaceContainerHigh = ElevatedSurface,
                onSurface = Color(0xFFF1F4FA),
                onSurfaceVariant = Color(0xFF9AA5BA),
            ),
        ) {
            Surface(Modifier.fillMaxSize(), color = AppBackground) {
                val dropTarget = remember(controller) { TransferFileDropTarget(controller) }
                Box(
                    Modifier.fillMaxSize().dragAndDropTarget(
                        shouldStartDragAndDrop = { event -> event.dragData() is DragData.FilesList },
                        target = dropTarget,
                    ),
                ) {
                    Row(Modifier.fillMaxSize()) {
                        DesktopNavigation(controller)
                        AnimatedContent(
                            targetState = controller.section,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            transitionSpec = {
                                (
                                    fadeIn(tween(155, easing = DesktopEmphasized)) +
                                        slideInVertically(tween(195, easing = DesktopEmphasized)) { it / 48 }
                                    ) togetherWith fadeOut(tween(115))
                            },
                            label = "desktop-section",
                        ) { section ->
                            when (section) {
                                DesktopSection.Home -> VaultHomeScreen(controller, Modifier.fillMaxSize())
                                DesktopSection.Wallet -> WalletScreen(controller, Modifier.fillMaxSize())
                                DesktopSection.Editor -> CardEditorScreen(controller, Modifier.fillMaxSize())
                                DesktopSection.Addresses -> AddressWalletScreen(controller, Modifier.fillMaxSize())
                                DesktopSection.AddressEditor -> AddressEditorScreen(controller, Modifier.fillMaxSize())
                                DesktopSection.Transfer -> TransferScreen(controller, Modifier.fillMaxSize())
                                DesktopSection.Settings -> SettingsScreen(controller, Modifier.fillMaxSize())
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = controller.notice != null,
                        enter = fadeIn(tween(150)) + slideInVertically(tween(180)) { it / 2 },
                        exit = fadeOut(tween(120)) + slideOutVertically(tween(140)) { it / 2 },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp),
                    ) {
                        controller.notice?.let { message ->
                            LaunchedEffect(message) {
                                kotlinx.coroutines.delay(3_600)
                                controller.clearNotice()
                            }
                            Text(
                                text = message,
                                modifier = Modifier
                                    .shadow(18.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(ElevatedSurface)
                                    .desktopSpecularRim(cornerRadius = 22.dp, topAlpha = 0.24f)
                                    .padding(horizontal = 20.dp, vertical = 11.dp),
                                color = Color.White.copy(alpha = 0.94f),
                                fontSize = 13.sp,
                            )
                        }
                    }
                    if (controller.transferDropActive) {
                        Box(
                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)).padding(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                Modifier.clip(RoundedCornerShape(32.dp)).background(PanelBackground)
                                    .border(2.dp, Accent, RoundedCornerShape(32.dp))
                                    .padding(horizontal = 56.dp, vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("⇣", color = Accent, fontSize = 40.sp)
                                Spacer(Modifier.height(10.dp))
                                Text("松手导入加密文件", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                Text("支持手机导出的 .cvpair / .cvsync", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Drag a .cvpair / .cvsync from Explorer onto the window to start an import. */
@OptIn(ExperimentalComposeUiApi::class)
private class TransferFileDropTarget(
    private val controller: DesktopAppController,
) : DragAndDropTarget {
    override fun onEntered(event: DragAndDropEvent) {
        controller.transferDropActive = true
    }

    override fun onExited(event: DragAndDropEvent) {
        controller.transferDropActive = false
    }

    override fun onEnded(event: DragAndDropEvent) {
        controller.transferDropActive = false
    }

    override fun onDrop(event: DragAndDropEvent): Boolean {
        val files = (event.dragData() as? DragData.FilesList)?.readFiles().orEmpty()
        val path = firstDroppedLocalFile(files)
        controller.onFileDropped(path)
        return path != null
    }
}

@Composable
internal fun WithOriginalDesktopFontSize(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale / DESKTOP_UI_FONT_SCALE),
        content = content,
    )
}

@Composable
internal fun WithDesktopUiFontSize(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * DESKTOP_UI_FONT_SCALE),
        content = content,
    )
}

@Composable
private fun DesktopNavigation(controller: DesktopAppController) {
    Column(
        modifier = Modifier
            .width(108.dp)
            .fillMaxHeight()
            .background(NavigationBackground)
            .border(width = 1.dp, color = Hairline),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .drawWithCache {
                    val badgeBrush = Brush.linearGradient(
                        colors = listOf(Color(0xFFC8DAFF), Color(0xFF7CA5F8)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    onDrawBehind { drawRect(brush = badgeBrush) }
                }
                .desktopSpecularRim(cornerRadius = 16.dp, topAlpha = 0.42f),
            contentAlignment = Alignment.Center,
        ) { Text("CV", color = Color(0xFF0A1220), fontWeight = FontWeight.Black, fontSize = 15.sp) }
        Spacer(Modifier.height(5.dp))
        Text("Carbon", color = Accent.copy(alpha = 0.72f), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        Spacer(Modifier.height(18.dp))
        NavItem("⌂", "首页", controller.section == DesktopSection.Home) { controller.section = DesktopSection.Home }
        NavItem("▣", "银行卡", controller.section in setOf(DesktopSection.Wallet, DesktopSection.Editor)) {
            controller.section = DesktopSection.Wallet
        }
        NavItem("◇", "地址", controller.section in setOf(DesktopSection.Addresses, DesktopSection.AddressEditor)) {
            controller.section = DesktopSection.Addresses
        }
        Spacer(Modifier.weight(1f))
        NavItem("⚙", "设置", controller.section == DesktopSection.Settings) { controller.section = DesktopSection.Settings }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun NavItem(symbol: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background by animateColorAsState(
        when {
            selected -> Accent.copy(alpha = 0.14f)
            hovered -> Color.White.copy(alpha = 0.06f)
            else -> Color.Transparent
        },
        tween(150),
        label = "nav-item-background",
    )
    val iconScale by animateFloatAsState(
        if (hovered && !selected) 1.10f else 1f,
        spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "nav-item-icon",
    )
    Column(
        Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .then(
                if (selected) Modifier.desktopSpecularRim(cornerRadius = 18.dp, topAlpha = 0.24f) else Modifier,
            )
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            symbol,
            color = if (selected) Accent else Color.White.copy(alpha = if (hovered) 0.84f else 0.56f),
            fontSize = 18.sp,
            modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale },
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = if (selected) Color.White else Color.White.copy(alpha = if (hovered) 0.76f else 0.50f),
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
internal fun DesktopSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onClear: () -> Unit,
) {
    val iconColor = Color.White.copy(alpha = 0.52f)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        placeholder = { Text(placeholder, fontSize = 12.sp, color = Color.White.copy(alpha = 0.40f)) },
        singleLine = true,
        leadingIcon = {
            Canvas(modifier = Modifier.size(16.dp)) {
                val stroke = 1.7.dp.toPx()
                val r = size.minDimension * 0.33f
                val center = Offset(size.width * 0.43f, size.height * 0.43f)
                drawCircle(color = iconColor, radius = r, center = center, style = Stroke(width = stroke))
                drawLine(
                    color = iconColor,
                    start = Offset(center.x + r * 0.72f, center.y + r * 0.72f),
                    end = Offset(size.width * 0.88f, size.height * 0.88f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text("清空", fontSize = 11.sp)
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = ElevatedSurface,
            unfocusedContainerColor = PanelBackground,
            focusedBorderColor = Accent.copy(alpha = 0.68f),
            unfocusedBorderColor = Hairline,
        ),
    )
}

@Composable
private fun WalletScreen(controller: DesktopAppController, modifier: Modifier) {
    var deleteConfirmation by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val normalizedQuery = searchQuery.trim().lowercase()
    val searching = normalizedQuery.isNotEmpty()
    val searchResults = if (searching) {
        controller.cards.filter { card ->
            card.nickname.lowercase().contains(normalizedQuery) ||
                card.lastFour.lowercase().contains(normalizedQuery) ||
                (card.network?.label?.lowercase()?.contains(normalizedQuery) == true)
        }
    } else {
        emptyList()
    }
    val visibleCards = controller.cards.filter { it.folderId == controller.selectedCardFolderId }
    val children = controller.cardFolders.filter { it.parentFolderId == controller.selectedCardFolderId }
    val entries = orderedDesktopWalletEntries(
        controller.cardWalletOrder,
        children.map { DesktopWalletEntry(it.id, true) } + visibleCards.map { DesktopWalletEntry(it.id, false) },
    )
    Row(modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(34.dp)) {
        Column(Modifier.width(470.dp).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("卡包", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (controller.cards.isEmpty()) "开始建立你的离线卡包" else "${controller.cards.size} 张银行卡",
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 13.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircleAction("⇄", emphasized = false) { controller.section = DesktopSection.Transfer }
                    CircleAction("＋", emphasized = true) { controller.startAdd() }
                }
            }
            if (controller.cards.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                DesktopSearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "搜索卡片昵称、卡组织或后四位…",
                    onClear = { searchQuery = "" },
                )
            }
            Spacer(Modifier.height(16.dp))
            if (searching) {
                if (searchResults.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("未找到匹配的银行卡", color = Color.White.copy(alpha = 0.42f))
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            "跨全部分类找到 ${searchResults.size} 张卡片",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.48f),
                        )
                        searchResults.forEach { card ->
                            CardFace(
                                card = card,
                                compact = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { controller.selectCard(card.id) },
                            )
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val selectedFolder = controller.cardFolders.firstOrNull { it.id == controller.selectedCardFolderId }
                    TextButton(
                        enabled = selectedFolder != null,
                        onClick = { controller.selectCardFolder(selectedFolder?.parentFolderId) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(selectedFolder?.let { "‹ ${desktopFolderPath(controller.cardFolders, it.id)}" } ?: "全部分类", maxLines = 1)
                    }
                    DesktopNewFolderButton { name ->
                        controller.createFolder(name, com.pdh.cardvault.desktop.model.DesktopFolderKind.CARDS, controller.selectedCardFolderId)
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
                    EmptyWallet(controller)
                } else {
                    DesktopMixedWalletStack(
                        entries = entries,
                        onOpen = { if (it.isFolder) controller.selectCardFolder(it.id) else controller.selectCard(it.id) },
                        onReorder = { controller.reorderWalletEntries(com.pdh.cardvault.desktop.model.DesktopFolderKind.CARDS, it) },
                        folderName = { id -> children.firstOrNull { it.id == id }?.name.orEmpty() },
                        canMoveIntoFolder = { entry, destination -> canDropDesktopWalletEntry(entry, destination, controller.cardFolders) },
                        onMoveIntoFolder = { entry, folder ->
                            if (entry.isFolder) controller.moveFolder(entry.id, folder)
                            else controller.moveCardToFolder(entry.id, folder)
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) { entry ->
                        if (entry.isFolder) children.firstOrNull { it.id == entry.id }?.let { folder ->
                            DesktopFolderWalletCard(
                                folder,
                                controller.cardFolders,
                                countDesktopFolderTreeItems(controller.cardFolders, folder.id, controller.cards.map { it.folderId }),
                                controller::renameFolder,
                                controller::moveFolder,
                                controller::deleteFolder,
                                controller::setFolderColor,
                            )
                        } else visibleCards.firstOrNull { it.id == entry.id }?.let { card ->
                            CardFace(card, compact = true, modifier = Modifier.fillMaxWidth())
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
                .background(PanelBackground)
                .desktopSpecularRim(cornerRadius = 32.dp, topAlpha = 0.20f),
        ) {
            controller.selectedCard?.let { card ->
                CardDetailPanel(
                    card = card,
                    concealmentEpoch = controller.concealmentEpoch,
                    authenticating = controller.authenticatingAction != null,
                    onEdit = { scope.launch { controller.startEdit(card.id) } },
                    onCopyAsNew = { controller.startCopy(card.id) },
                    moveControl = {
                        DesktopRecordMoveButton(
                            controller.cardFolders,
                            card.folderId,
                            enabled = controller.authenticatingAction == null,
                            onMove = { controller.moveCardToFolder(card.id, it) },
                        )
                    },
                    onDelete = { deleteConfirmation = true },
                    onAuthorizeReveal = { controller.authorizeReveal(card.id) },
                    onConceal = { controller.concealCard(card.id) },
                    onCopy = { controller.copyCardNumber(card.id) },
                    onCopyNote = { controller.copyCardNote(card.id) },
                )
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("选择一张卡片", color = Color.White.copy(alpha = 0.38f))
            }
        }
    }

    if (deleteConfirmation) {
        AlertDialog(
            onDismissRequest = { deleteConfirmation = false },
            title = { Text("删除这张卡片？") },
            text = { Text("此操作会从本机加密卡包中移除该记录。") },
            confirmButton = {
                TextButton(
                    enabled = controller.authenticatingAction == null,
                    onClick = {
                        deleteConfirmation = false
                        scope.launch { controller.deleteSelected() }
                    },
                ) {
                    Text("删除", color = Color(0xFFFF817B))
                }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmation = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WalletStack(
    cards: List<DesktopCard>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onDrop: (String, Offset) -> Boolean,
    modifier: Modifier,
) {
    DesktopStackViewport(itemCount = cards.size, modifier = modifier) {
        cards.forEachIndexed { index, card ->
            val selected = card.id == selectedId
            val y by animateDpAsState(
                targetValue = (index * DESKTOP_STACK_STEP_DP).dp,
                animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.82f),
            )
            val scale by animateFloatAsState(if (selected) 1.018f else 1f)
            var dragY by remember(card.id) { mutableFloatStateOf(0f) }
            var dragX by remember(card.id) { mutableFloatStateOf(0f) }
            var origin by remember(card.id) { mutableStateOf(Offset.Zero) }
            var pointer by remember(card.id) { mutableStateOf(Offset.Unspecified) }
            CardFace(
                card = card,
                compact = true,
                modifier = Modifier.fillMaxWidth()
                    .offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
                    .offset(y = y)
                    .zIndex(index.toFloat() + if (dragY != 0f) 100f else 0f)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .onGloballyPositioned { if (dragX == 0f && dragY == 0f) origin = it.boundsInRoot().topLeft }
                    .clickable { onSelect(card.id) }
                    .pointerInput(card.id, cards.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { local -> onSelect(card.id); pointer = origin + local },
                            onDragEnd = {
                                val target = (
                                    index + dragY / DESKTOP_STACK_STEP_DP.dp.toPx()
                                ).roundToInt().coerceIn(cards.indices)
                                val dropped = onDrop(card.id, pointer)
                                dragX = 0f
                                dragY = 0f
                                pointer = Offset.Unspecified
                                if (!dropped) onMove(card.id, target)
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
            "长按卡片并拖动即可调整顺序",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
            color = Color.White.copy(alpha = 0.34f),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun CardDetailPanel(
    card: DesktopCard,
    concealmentEpoch: Long,
    authenticating: Boolean,
    onEdit: () -> Unit,
    onCopyAsNew: () -> Unit,
    moveControl: @Composable () -> Unit,
    onDelete: () -> Unit,
    onAuthorizeReveal: suspend () -> Boolean,
    onConceal: () -> Unit,
    onCopy: () -> Unit,
    onCopyNote: () -> Unit,
) {
    var flipped by remember(card.id) { mutableStateOf(false) }
    var revealed by remember(card.id) { mutableStateOf(false) }
    var initialEpoch by remember(card.id) { mutableLongStateOf(concealmentEpoch) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(card.id) {
        flipped = true
        revealed = false
    }
    LaunchedEffect(concealmentEpoch) {
        if (concealmentEpoch != initialEpoch) {
            initialEpoch = concealmentEpoch
            flipped = false
            revealed = false
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(card.nickname, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            Row {
                TextButton(onClick = onEdit, enabled = !authenticating) { Text("编辑") }
                TextButton(onClick = onDelete, enabled = !authenticating) { Text("删除", color = Color(0xFFFF817B)) }
            }
        }
        Spacer(Modifier.height(30.dp))
        FlippableCard(
            card = card,
            flipped = flipped,
            revealed = revealed,
            onToggleReveal = {
                if (revealed) {
                    revealed = false
                    onConceal()
                } else if (!authenticating) {
                    scope.launch {
                        if (onAuthorizeReveal()) {
                            flipped = true
                            revealed = true
                        }
                    }
                }
            },
            onCopy = onCopy,
            modifier = Modifier.fillMaxWidth().clickable {
                flipped = !flipped
                if (!flipped) {
                    revealed = false
                    onConceal()
                }
            },
        )
        Spacer(Modifier.height(18.dp))
        Text(
            if (flipped) "点击卡片返回正面" else "点击卡片查看背面",
            color = Color.White.copy(alpha = 0.36f),
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(12.dp))
        if (card.notes.isNotBlank()) {
            OutlinedButton(onClick = onCopyNote, enabled = !authenticating, modifier = Modifier.fillMaxWidth()) {
                Text("复制备注")
            }
            Spacer(Modifier.height(8.dp))
        }
        moveControl()
        OutlinedButton(onClick = onCopyAsNew, enabled = !authenticating, modifier = Modifier.fillMaxWidth()) {
            Text("以此卡新建")
        }
    }
}

@Composable
private fun EmptyWallet(controller: DesktopAppController) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(30.dp)).background(Color.White.copy(alpha = 0.025f))
            .desktopSpecularRim(cornerRadius = 30.dp, topAlpha = 0.16f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("◇", color = Accent, fontSize = 42.sp)
            Spacer(Modifier.height(12.dp))
            Text("你的卡包还是空的", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("银行卡信息只会加密保存在这台设备", color = Color.White.copy(alpha = 0.42f), fontSize = 12.sp)
            Spacer(Modifier.height(22.dp))
            Button(onClick = controller::startAdd) { Text("添加第一张卡") }
        }
    }
}

@Composable
private fun CircleAction(label: String, emphasized: Boolean = false, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.92f
            hovered -> 1.05f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 640f),
        label = "circle-action-scale",
    )
    Box(
        Modifier
            .size(50.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (emphasized) Accent else ElevatedSurface)
            .desktopSpecularRim(cornerRadius = 25.dp, topAlpha = if (emphasized) 0.38f else 0.22f)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 19.sp,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            color = if (emphasized) Color(0xFF0A1220) else Color.White,
        )
    }
}

@Composable
private fun TransferScreen(controller: DesktopAppController, modifier: Modifier) {
    var pairingCodeInput by remember { mutableStateOf("") }
    var rotationConfirmation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(controller.concealmentEpoch) { pairingCodeInput = "" }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(42.dp)) {
        Text("离线同步", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("通过微信等工具传输端到端加密文件；CardVault 本身不会联网。", color = Color.White.copy(alpha = 0.48f))
        Spacer(Modifier.height(6.dp))
        Text(
            desktopLastSyncLabel(controller.lastSyncAtEpochMillis) + "  ·  也可以把 .cvpair / .cvsync 文件直接拖进窗口",
            color = Color.White.copy(alpha = 0.38f),
            fontSize = 11.sp,
        )
        if (controller.syncKeyEpoch > 0L) {
            Spacer(Modifier.height(6.dp))
            Text("同步密钥代次：${controller.syncKeyEpoch}", color = Color.White.copy(alpha = 0.38f), fontSize = 11.sp)
        }
        Spacer(Modifier.height(30.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            TransferPanel("导出到手机", "每次生成可直接导入的加密传输文件与一次性配对码。", Modifier.weight(1f)) {
                Text(
                    controller.exportDirectory ?: "尚未选择导出位置",
                    color = Color.White.copy(alpha = if (controller.exportDirectory == null) 0.38f else 0.68f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = {
                    chooseDirectory(controller.exportDirectory)?.let(controller::chooseExportDirectory)
                }) { Text("选择位置") }
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = { scope.launch { controller.export(newPairing = true) } },
                    enabled = controller.syncGateway.isReady && controller.exportDirectory != null && controller.authenticatingAction == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("导出传输文件") }
            }

            TransferPanel("从手机导入", "打开手机分享来的 .cvsync 或 .cvpair 文件。导入会先验证完整性。", Modifier.weight(1f)) {
                OutlinedTextField(
                    value = pairingCodeInput,
                    onValueChange = { pairingCodeInput = it.uppercase().filter { char -> char.isLetterOrDigit() || char == '-' }.take(40) },
                    label = { Text("配对码（仅首次需要）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        controller.pastePairingCodeFromClipboard()?.let { pairingCodeInput = it }
                    },
                    enabled = !controller.transferBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("从剪贴板粘贴配对码") }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        chooseImportFile()?.let(controller::selectImportFile)
                    },
                    enabled = controller.syncGateway.isReady && !controller.transferBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("选择文件") }
                controller.selectedImportPath?.let { path ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "已选择：${path.fileName}",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (controller.selectedImportKind == com.pdh.cardvault.sync.SyncFileKind.PAIRING) {
                            "类型：首次配对文件（需要配对码）"
                        } else {
                            "类型：日常同步文件"
                        },
                        color = Color.White.copy(alpha = 0.42f),
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        scope.launch {
                            if (controller.importSelected(pairingCodeInput)) pairingCodeInput = ""
                        }
                    },
                    enabled = controller.syncGateway.isReady &&
                        controller.selectedImportPath != null &&
                        !controller.transferBusy &&
                        controller.authenticatingAction == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (controller.transferBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF111318),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("正在导入")
                    } else {
                        Text("开始导入")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("配对码和配对文件请分开发送。", color = Color(0xFFFFD49B), fontSize = 11.sp)
            }
        }

        controller.transferFeedback?.let { feedback ->
            Spacer(Modifier.height(18.dp))
            Text(
                feedback.text,
                color = if (feedback.isError) Color(0xFFFF9B94) else Color(0xFFBBD0FF),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.055f)).padding(14.dp),
            )
        }

        controller.pairingCode?.let { code ->
            Spacer(Modifier.height(24.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFF19202B))
                    .border(1.dp, Accent.copy(alpha = 0.25f), RoundedCornerShape(22.dp)).padding(20.dp),
            ) {
                Text("新设备配对码", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Text(code, color = Accent, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = controller::copyCurrentPairingCode,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("复制配对码") }
                Spacer(Modifier.height(8.dp))
                Text("不要把此配对码与 .cvpair 文件放在同一条微信消息中。", color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp)
            }
        }

        if (!controller.syncGateway.isReady) {
            Spacer(Modifier.height(22.dp))
            Text("加密同步组件正在初始化，当前不会生成任何明文文件。", color = Color(0xFFFFD49B), fontSize = 12.sp)
        }

        if (controller.syncKeyEpoch > 0L) {
            Spacer(Modifier.height(24.dp))
            TransferPanel(
                "撤销旧同步关系",
                "生成全新的同步密钥。旧同步文件将失效，其他设备必须使用新配对文件重新连接。",
                Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = { rotationConfirmation = true },
                    enabled = controller.authenticatingAction == null && controller.exportDirectory != null,
                ) { Text("轮换密钥并重新配对") }
            }
        }
    }


    if (rotationConfirmation) {
        AlertDialog(
            onDismissRequest = { rotationConfirmation = false },
            title = { Text("撤销所有旧同步关系？") },
            text = {
                Text("银行卡、地址和已保存的 CVV 不会被删除。旧同步文件将失效，其他设备需要导入随后生成的新配对文件。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        rotationConfirmation = false
                        scope.launch { controller.rotateSyncKeyAndExportPairing() }
                    },
                ) { Text("验证身份并继续") }
            },
            dismissButton = {
                TextButton(onClick = { rotationConfirmation = false }) { Text("取消") }
            },
        )
    }
}

internal fun desktopLastSyncLabel(
    lastSyncAtEpochMillis: Long?,
    nowEpochMillis: Long = System.currentTimeMillis(),
): String {
    if (lastSyncAtEpochMillis == null) return "尚未与其他设备同步或导出过加密备份"
    val days = BackupReminderPolicy.daysBetween(lastSyncAtEpochMillis, nowEpochMillis)
    return if (days == 0) "上次与其他设备同步：今天" else "上次与其他设备同步：$days 天前"
}

@Composable
private fun TransferPanel(title: String, description: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(PanelBackground)
            .desktopSpecularRim(cornerRadius = 28.dp, topAlpha = 0.20f)
            .padding(26.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(7.dp))
        Text(description, color = Color.White.copy(alpha = 0.48f), fontSize = 12.sp)
        Spacer(Modifier.height(22.dp))
        content()
    }
}

@Composable
private fun SettingsScreen(controller: DesktopAppController, modifier: Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(42.dp)) {
        Text("设置", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .drawWithCache {
                    val bannerBrush = Brush.linearGradient(
                        colors = listOf(Color(0xFF18253A), Color(0xFF121A28)),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    val accentGlow = Brush.radialGradient(
                        colors = listOf(Accent.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(size.width * 0.14f, size.height * 0.20f),
                        radius = size.maxDimension * 0.65f,
                    )
                    onDrawBehind {
                        drawRect(brush = bannerBrush)
                        drawRect(brush = accentGlow)
                    }
                }
                .desktopSpecularRim(cornerRadius = 24.dp, topAlpha = 0.28f)
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(8.dp).background(Accent, CircleShape))
                Text(
                    text = DesktopAppInfo.editionLabel,
                    color = Accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${DesktopAppInfo.settingsLabel}  ·  零帧延迟翻面 · 静态缓存光影引擎 · 完整兼容 v1.8.0 (Claude) 加密数据与同步文件",
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(18.dp))
        SettingsCard(
            title = "设备间同步",
            value = "打开",
            detail = "同时导入或导出银行卡与地址",
            onClick = { controller.section = DesktopSection.Transfer },
        )
        Spacer(Modifier.height(12.dp))
        SettingsCard("本地加密", "AES‑256‑GCM · Windows DPAPI", "随机密钥仅能由当前 Windows 用户解锁")
        Spacer(Modifier.height(12.dp))
        SettingsCard("敏感操作", "Windows Hello", "查看、编辑、删除和数据交换均需系统身份验证")
        Spacer(Modifier.height(12.dp))
        SettingsCard("网络访问", "完全离线", "应用不请求网络权限，也不连接任何服务器")
        Spacer(Modifier.height(12.dp))
        SettingsCard("数据交换", "加密文件", "仅在你主动同步时生成或读取密文文件")
        Spacer(Modifier.height(30.dp))
        Text(DesktopAppInfo.settingsLabel, color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp)
        Text(
            "本地数据目录：${EncryptedDesktopVault.defaultDirectory()}",
            color = Color.White.copy(alpha = 0.24f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    value: String,
    detail: String,
    onClick: (() -> Unit)? = null,
) {
    val interactionModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PanelBackground)
            .desktopSpecularRim(cornerRadius = 20.dp, topAlpha = 0.18f)
            .then(interactionModifier)
            .padding(horizontal = 20.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, color = Color.White.copy(alpha = 0.42f), fontSize = 11.sp)
        }
        Text(value, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private fun chooseDirectory(initial: String?): Path? {
    val chooser = JFileChooser(initial)
    chooser.dialogTitle = "选择 CardVault 导出位置"
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    chooser.isAcceptAllFileFilterUsed = false
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
}

private fun chooseImportFile(): Path? {
    val chooser = JFileChooser()
    chooser.dialogTitle = "导入 CardVault 加密文件"
    chooser.fileFilter = FileNameExtensionFilter("CardVault 加密文件 (*.cvsync, *.cvpair)", "cvsync", "cvpair")
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
}
