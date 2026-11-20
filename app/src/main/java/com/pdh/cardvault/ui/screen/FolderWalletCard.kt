package com.pdh.cardvault.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pdh.cardvault.presentation.VaultFolderUiModel
import com.pdh.cardvault.ui.card.CustomCardColorDialog
import com.pdh.cardvault.ui.card.WalletStackEntry
import com.pdh.cardvault.ui.theme.specularRim
import com.pdh.cardvault.wallet.FolderAppearance
import java.util.UUID

internal fun parentFolderForBack(folders: List<VaultFolderUiModel>, currentId: UUID): UUID? =
    folders.firstOrNull { it.id == currentId }?.parentFolderId

internal fun canDropWalletEntry(entry: WalletStackEntry, destination: UUID, folders: List<VaultFolderUiModel>): Boolean {
    if (folders.none { it.id == destination }) return false
    if (!entry.isFolder) return true
    return folders.any { it.id == entry.id } && !wouldCreateFolderCycle(folders, entry.id, destination)
}

@Composable
internal fun NewWalletFolderButton(onCreate: (String) -> Unit) {
    var create by remember { mutableStateOf(false) }
    Surface(
        onClick = { create = true },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.specularRim(cornerRadius = 20.dp, topAlpha = 0.16f),
    ) {
        Text(
            text = "＋ 新建文件夹",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (create) FolderNameDialog("新建文件夹", "", { create = false }) { create = false; onCreate(it) }
}

@Composable
internal fun FolderWalletCard(
    folder: VaultFolderUiModel,
    folders: List<VaultFolderUiModel>,
    onRename: (UUID, String) -> Unit,
    onMove: (UUID, UUID?) -> Unit,
    onDelete: (UUID) -> Unit,
    onSetColor: (UUID, Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var move by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var editColor by remember { mutableStateOf(false) }
    val base = Color(0xFF000000L or folder.colorRgb.toLong())
    val light = base.luminance() > 0.45f
    val foreground = if (light) Color(0xFF111827) else Color.White
    val children = remember(folders, folder.id) { folders.count { it.parentFolderId == folder.id } }

    Surface(
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .specularRim(
                cornerRadius = 28.dp,
                topAlpha = if (light) 0.45f else 0.28f,
                midAlpha = 0.08f,
                bottomAlpha = 0.03f,
            ),
        color = base,
        tonalElevation = 0.dp,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val bgBrush = Brush.linearGradient(
                        colors = listOf(
                            lerp(base, Color.White, if (light) 0.10f else 0.08f),
                            base,
                            lerp(base, Color.Black, if (light) 0.15f else 0.46f),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    val tabAccent = foreground.copy(alpha = 0.08f)
                    val pocketPath = Path().apply {
                        moveTo(0f, size.height * 0.36f)
                        lineTo(size.width * 0.44f, size.height * 0.36f)
                        lineTo(size.width * 0.52f, size.height * 0.44f)
                        lineTo(size.width, size.height * 0.44f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    onDrawBehind {
                        drawRect(brush = bgBrush)
                        drawPath(path = pocketPath, color = tabAccent)
                    }
                },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = foreground.copy(alpha = 0.14f),
                        modifier = Modifier.size(34.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(18.dp)) {
                                val tab = Path().apply {
                                    moveTo(size.width * 0.08f, size.height * 0.26f)
                                    lineTo(size.width * 0.42f, size.height * 0.26f)
                                    lineTo(size.width * 0.54f, size.height * 0.38f)
                                    lineTo(size.width * 0.92f, size.height * 0.38f)
                                    lineTo(size.width * 0.92f, size.height * 0.80f)
                                    lineTo(size.width * 0.08f, size.height * 0.80f)
                                    close()
                                }
                                drawPath(
                                    path = tab,
                                    color = foreground,
                                    style = Stroke(width = 1.8.dp.toPx()),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = folder.name,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        color = foreground,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Surface(
                        shape = CircleShape,
                        color = foreground.copy(alpha = 0.14f),
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Text(
                            text = "${folder.itemCount}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = foreground,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Text("•••", color = foreground) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; rename = true })
                            DropdownMenuItem(text = { Text("设置颜色") }, onClick = { menu = false; editColor = true })
                            DropdownMenuItem(text = { Text("移动到…") }, onClick = { menu = false; move = true })
                            DropdownMenuItem(text = { Text("删除文件夹") }, onClick = { menu = false; delete = true })
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "分类文件夹",
                        color = foreground.copy(alpha = 0.64f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = "${folder.itemCount} 张卡片" + if (children > 0) " · $children 个子文件夹" else "",
                        color = foreground,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
    if (editColor) FolderColorDialog(folder, { editColor = false }) {
        editColor = false
        onSetColor(folder.id, it)
    }
    if (rename) FolderNameDialog("重命名文件夹", folder.name, { rename = false }) { rename = false; onRename(folder.id, it) }
    if (move) AlertDialog(
        onDismissRequest = { move = false }, title = { Text("移动文件夹") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                TextButton(onClick = { move = false; onMove(folder.id, null) }) { Text("顶层") }
                folders.filterNot { wouldCreateFolderCycle(folders, folder.id, it.id) }.forEach { destination ->
                    TextButton(onClick = { move = false; onMove(folder.id, destination.id) }) {
                        Text(folderPath(folders, destination.id))
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { move = false }) { Text("取消") } },
    )
    if (delete) AlertDialog(
        onDismissRequest = { delete = false }, title = { Text("删除文件夹？") },
        text = { Text("卡片和子文件夹会移至上一级，不会删除内容。") },
        confirmButton = { TextButton(onClick = { delete = false; onDelete(folder.id) }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("取消") } },
    )
}

@Composable
private fun FolderColorDialog(folder: VaultFolderUiModel, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    var selected by remember(folder.colorRgb) { mutableIntStateOf(folder.colorRgb) }
    var custom by remember { mutableStateOf(false) }
    val color = Color(0xFF000000L or selected.toLong())
    if (custom) {
        CustomCardColorDialog("文件夹颜色", color, { custom = false }) { onSelect(it.toArgb() and 0xFFFFFF) }
    } else AlertDialog(
        onDismissRequest = onDismiss, title = { Text("文件夹颜色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(Modifier.fillMaxWidth().height(86.dp), shape = RoundedCornerShape(20.dp), color = color) {
                    Box(Modifier.padding(18.dp), contentAlignment = Alignment.CenterStart) {
                        Text("▰  ${folder.name}", color = if (color.luminance() > .45f) Color(0xFF111827) else Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    }
                }
                FolderAppearance.presetColors.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { rgb ->
                            Box(Modifier.size(34.dp).background(Color(0xFF000000L or rgb.toLong()), CircleShape)
                                .border(if (rgb == selected) 3.dp else 1.dp,
                                    if (rgb == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { selected = rgb })
                        }
                    }
                }
                TextButton(onClick = { custom = true }) { Text("自定义颜色…") }
            }
        },
        confirmButton = { TextButton(onClick = { onSelect(selected) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
