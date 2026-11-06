package com.pdh.cardvault.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pdh.cardvault.desktop.model.DesktopFolder

internal fun canMoveDesktopRecord(current: String?, destination: String?, folders: List<DesktopFolder>): Boolean =
    current != destination && (destination == null || folders.any { it.id == destination })

@Composable
internal fun DesktopRecordMoveButton(
    folders: List<DesktopFolder>, currentFolderId: String?, enabled: Boolean, onMove: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var destination by remember(currentFolderId) { mutableStateOf(currentFolderId) }
    Text("所在位置：" + (currentFolderId?.let { desktopFolderPath(folders, it) } ?: "根目录（未归档）"),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = { destination = currentFolderId; open = true }, enabled = enabled,
        modifier = Modifier.fillMaxWidth()) { Text("移动到…") }
    if (open) AlertDialog(
        onDismissRequest = { open = false }, title = { Text("移动到文件夹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("选择位置后确认移动，卡片内容和外观不变。")
                if (currentFolderId != null) TextButton(onClick = {
                    destination = folders.firstOrNull { it.id == currentFolderId }?.parentFolderId
                }) { Text("移出当前文件夹 · 上一级") }
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    @Composable fun DestinationRow(id: String?, label: String) {
                        Row(Modifier.fillMaxWidth().selectable(selected = destination == id,
                            role = Role.RadioButton, onClick = { destination = id }).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = destination == id, onClick = null)
                            Text(label + if (id == currentFolderId) "（当前位置）" else "", Modifier.weight(1f).padding(start = 8.dp))
                        }
                    }
                    DestinationRow(null, "根目录（未归档）")
                    folders.forEach { DestinationRow(it.id, desktopFolderPath(folders, it.id)) }
                }
            }
        },
        confirmButton = { TextButton(enabled = enabled && canMoveDesktopRecord(currentFolderId, destination, folders),
            onClick = {
                if (enabled && canMoveDesktopRecord(currentFolderId, destination, folders)) {
                    open = false
                    onMove(destination)
                }
            }) { Text("确认移动") } },
        dismissButton = { TextButton(onClick = { open = false }) { Text("取消") } },
    )
}
