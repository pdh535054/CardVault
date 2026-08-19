package com.pdh.cardvault.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.pdh.cardvault.desktop.data.EncryptedDesktopVault
import com.pdh.cardvault.desktop.model.DesktopCard
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val AppBackground = Color(0xFF191919)
private val NavigationBackground = Color(0xFF1D1D1D)
private val PanelBackground = Color(0xFF202020)
private val Hairline = Color.White.copy(alpha = 0.085f)
private val Accent = Color(0xFFAFC6FF)
internal const val DESKTOP_UI_FONT_SCALE = 1.5f

@Composable
fun CardVaultDesktopApp(controller: DesktopAppController) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * DESKTOP_UI_FONT_SCALE),
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Accent,
                onPrimary = Color(0xFF10131A),
                background = AppBackground,
                surface = PanelBackground,
                onSurface = Color(0xFFF1F2F6),
            ),
        ) {
            Surface(Modifier.fillMaxSize(), color = AppBackground) {
                Box(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxSize()) {
                        DesktopNavigation(controller)
                        when (controller.section) {
                            DesktopSection.Home -> VaultHomeScreen(controller, Modifier.weight(1f))
                            DesktopSection.Wallet -> WalletScreen(controller, Modifier.weight(1f))
                            DesktopSection.Editor -> CardEditorScreen(controller, Modifier.weight(1f))
                            DesktopSection.Addresses -> AddressWalletScreen(controller, Modifier.weight(1f))
                            DesktopSection.AddressEditor -> AddressEditorScreen(controller, Modifier.weight(1f))
                            DesktopSection.Transfer -> TransferScreen(controller, Modifier.weight(1f))
                            DesktopSection.Settings -> SettingsScreen(controller, Modifier.weight(1f))
                        }
                    }
                    AnimatedVisibility(
                        visible = controller.notice != null,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp),
                    ) {
                        controller.notice?.let { message ->
                            LaunchedEffect(message) {
                                kotlinx.coroutines.delay(3_600)
                                controller.clearNotice()
                            }
                            Text(
                                text = message,
                                modifier = Modifier.shadow(18.dp, CircleShape).clip(CircleShape)
                                    .background(Color(0xFF2A2A2A)).border(1.dp, Hairline, CircleShape)
                                    .padding(horizontal = 20.dp, vertical = 11.dp),
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
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
        modifier = Modifier.width(108.dp).fillMaxHeight().background(NavigationBackground)
            .border(width = 1.dp, color = Hairline),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Accent),
            contentAlignment = Alignment.Center,
        ) { Text("CV", color = Color(0xFF10131A), fontWeight = FontWeight.Black, fontSize = 15.sp) }
        Spacer(Modifier.height(22.dp))
        NavItem("⌂", "首页", controller.section == DesktopSection.Home) { controller.section = DesktopSection.Home }
        NavItem("▣", "银行卡", controller.section in setOf(DesktopSection.Wallet, DesktopSection.Editor)) {
            controller.section = DesktopSection.Wallet
        }
        NavItem("◇", "地址", controller.section in setOf(DesktopSection.Addresses, DesktopSection.AddressEditor)) {
            controller.section = DesktopSection.Addresses
        }
        Spacer(Modifier.weight(1f))
        NavItem("⚙", "设置", controller.section == DesktopSection.Settings) { controller.section = DesktopSection.Settings }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun NavItem(symbol: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(88.dp).clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color.White.copy(alpha = 0.09f) else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(symbol, color = if (selected) Accent else Color.White.copy(alpha = 0.56f), fontSize = 18.sp)
        Spacer(Modifier.height(2.dp))
        Text(label, color = if (selected) Color.White else Color.White.copy(alpha = 0.48f), fontSize = 10.sp)
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun WalletScreen(controller: DesktopAppController, modifier: Modifier) {
    var deleteConfirmation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Row(modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(34.dp)) {
        Column(Modifier.width(470.dp).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("卡包", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (controller.cards.isEmpty()) "开始建立你的离线卡包" else "${controller.cards.size} 张银行卡",
                        color = Color.White.copy(alpha = 0.46f),
                        fontSize = 13.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircleAction("⇄") { controller.section = DesktopSection.Transfer }
                    CircleAction("＋") { controller.startAdd() }
                }
            }
            Spacer(Modifier.height(28.dp))
            if (controller.cards.isEmpty()) {
                EmptyWallet(controller)
            } else {
                WalletStack(
                    cards = controller.cards,
                    selectedId = controller.selectedId,
                    onSelect = controller::selectCard,
                    onMove = controller::moveCard,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Box(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(32.dp))
                .background(PanelBackground).border(1.dp, Hairline, RoundedCornerShape(32.dp)),
        ) {
            controller.selectedCard?.let { card ->
                CardDetailPanel(
                    card = card,
                    concealmentEpoch = controller.concealmentEpoch,
                    authenticating = controller.authenticatingAction != null,
                    onEdit = { scope.launch { controller.startEdit(card.id) } },
                    onDelete = { deleteConfirmation = true },
                    onAuthorizeReveal = { controller.authorizeReveal(card.id) },
                    onConceal = { controller.concealCard(card.id) },
                    onCopy = { controller.copyCardNumber(card.id) },
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
    modifier: Modifier,
) {
    Box(modifier) {
        cards.forEachIndexed { index, card ->
            val selected = card.id == selectedId
            val y by animateDpAsState(
                targetValue = (index * 74).dp,
                animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.82f),
            )
            val scale by animateFloatAsState(if (selected) 1.018f else 1f)
            var dragY by remember(card.id) { mutableFloatStateOf(0f) }
            CardFace(
                card = card,
                compact = true,
                modifier = Modifier.fillMaxWidth()
                    .offset { IntOffset(0, dragY.roundToInt()) }
                    .offset(y = y)
                    .zIndex(index.toFloat() + if (dragY != 0f) 100f else 0f)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .shadow(if (selected) 22.dp else 12.dp, RoundedCornerShape(28.dp))
                    .clickable { onSelect(card.id) }
                    .pointerInput(card.id, cards.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onSelect(card.id) },
                            onDragEnd = {
                                val target = (index + dragY / 74.dp.toPx()).roundToInt().coerceIn(cards.indices)
                                dragY = 0f
                                onMove(card.id, target)
                            },
                            onDragCancel = { dragY = 0f },
                            onDrag = { change, amount -> change.consume(); dragY += amount.y },
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
    onDelete: () -> Unit,
    onAuthorizeReveal: suspend () -> Boolean,
    onConceal: () -> Unit,
    onCopy: () -> Unit,
) {
    var flipped by remember(card.id) { mutableStateOf(false) }
    var revealed by remember(card.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(card.id, concealmentEpoch) {
        flipped = false
        revealed = false
    }
    Column(Modifier.fillMaxSize().padding(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
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
    }
}

@Composable
private fun EmptyWallet(controller: DesktopAppController) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(30.dp)).background(Color.White.copy(alpha = 0.025f))
            .border(1.dp, Hairline, RoundedCornerShape(30.dp)),
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
private fun CircleAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Hairline, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 20.sp) }
}

@Composable
private fun TransferScreen(controller: DesktopAppController, modifier: Modifier) {
    var pairingCodeInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(controller.concealmentEpoch) { pairingCodeInput = "" }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(42.dp)) {
        Text("离线同步", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("通过微信等工具传输端到端加密文件；CardVault 本身不会联网。", color = Color.White.copy(alpha = 0.48f))
        Spacer(Modifier.height(30.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            TransferPanel("导出到手机", "选择一个文件夹，CardVault 会把加密同步文件写入该位置。", Modifier.weight(1f)) {
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
                    onClick = { scope.launch { controller.export(newPairing = false) } },
                    enabled = controller.syncGateway.isReady && controller.exportDirectory != null && controller.authenticatingAction == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("导出同步文件") }
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = { scope.launch { controller.export(newPairing = true) } },
                    enabled = controller.syncGateway.isReady && controller.exportDirectory != null && controller.authenticatingAction == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("为新设备创建配对文件") }
            }

            TransferPanel("从手机导入", "打开手机分享来的 .cvsync 或 .cvpair 文件。导入会先验证完整性。", Modifier.weight(1f)) {
                OutlinedTextField(
                    value = pairingCodeInput,
                    onValueChange = { pairingCodeInput = it.uppercase().filter { char -> char.isLetterOrDigit() || char == '-' }.take(40) },
                    label = { Text("配对码（仅首次需要）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = {
                        chooseImportFile()?.let { path ->
                            scope.launch {
                                if (controller.import(path, pairingCodeInput)) pairingCodeInput = ""
                            }
                        }
                    },
                    enabled = controller.syncGateway.isReady && controller.authenticatingAction == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("选择并导入文件") }
                Spacer(Modifier.height(12.dp))
                Text("配对码和配对文件请分开发送。", color = Color(0xFFFFD49B), fontSize = 11.sp)
            }
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
                Spacer(Modifier.height(8.dp))
                Text("不要把此配对码与 .cvpair 文件放在同一条微信消息中。", color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp)
            }
        }

        if (!controller.syncGateway.isReady) {
            Spacer(Modifier.height(22.dp))
            Text("加密同步组件正在初始化，当前不会生成任何明文文件。", color = Color(0xFFFFD49B), fontSize = 12.sp)
        }
    }
}

@Composable
private fun TransferPanel(title: String, description: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(28.dp)).background(PanelBackground).border(1.dp, Hairline, RoundedCornerShape(28.dp)).padding(26.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(7.dp))
        Text(description, color = Color.White.copy(alpha = 0.44f), fontSize = 12.sp)
        Spacer(Modifier.height(22.dp))
        content()
    }
}

@Composable
private fun SettingsScreen(controller: DesktopAppController, modifier: Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(42.dp)) {
        Text("设置", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(30.dp))
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
        Text("CardVault 1.3.7  ·  Windows", color = Color.White.copy(alpha = 0.3f), fontSize = 11.sp)
        Text(
            "本地数据目录：${EncryptedDesktopVault.defaultDirectory()}",
            color = Color.White.copy(alpha = 0.22f),
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PanelBackground)
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
            .then(interactionModifier)
            .padding(horizontal = 20.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, color = Color.White.copy(alpha = 0.38f), fontSize = 11.sp)
        }
        Text(value, color = Accent, fontSize = 12.sp)
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
