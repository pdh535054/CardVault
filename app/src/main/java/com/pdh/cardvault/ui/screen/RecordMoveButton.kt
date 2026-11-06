package com.pdh.cardvault.ui.screen

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
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.pdh.cardvault.presentation.VaultFolderUiModel
import java.util.UUID

internal fun canMoveRecordToFolder(current: UUID?, destination: UUID?, folders: List<VaultFolderUiModel>): Boolean =
    current != destination && (destination == null || folders.any { it.id == destination })

/** Moving is deliberately separate from drag-to-reorder and requires confirmation. */
@Composable
internal fun RecordMoveButton(
    folders: List<VaultFolderUiModel>,
    currentFolderId: UUID?,
    onMove: (UUID?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var destination by remember(currentFolderId) { mutableStateOf(currentFolderId) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("所在位置：" + (currentFolderId?.let { folderPath(folders, it) } ?: "根目录（未归档）"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { destination = currentFolderId; open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("移动到…")
        }
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text("移动到文件夹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("选择位置后确认移动，卡片内容和外观不变。", style = MaterialTheme.typography.bodySmall)
                if (currentFolderId != null) TextButton(onClick = {
                    destination = parentFolderForBack(folders, currentFolderId)
                }) { Text("移出当前文件夹 · 上一级") }
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    @Composable fun DestinationRow(id: UUID?, label: String) {
                        Row(Modifier.fillMaxWidth().selectable(
                            selected = destination == id, role = Role.RadioButton,
                            onClick = { destination = id },
                        ).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = destination == id, onClick = null)
                            Text(label + if (id == currentFolderId) "（当前位置）" else "",
                                modifier = Modifier.weight(1f).padding(start = 8.dp))
                        }
                    }
                    DestinationRow(null, "根目录（未归档）")
                    folders.forEach { DestinationRow(it.id, folderPath(folders, it.id)) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canMoveRecordToFolder(currentFolderId, destination, folders), onClick = {
                if (canMoveRecordToFolder(currentFolderId, destination, folders)) {
                    open = false
                    onMove(destination)
                }
            }) { Text("确认移动") }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text("取消") } },
    )
}
