package com.pdh.cardvault.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.pdh.cardvault.presentation.VaultFolderUiModel
import java.util.UUID

@Composable
internal fun CardDestinationPicker(
    folders: List<VaultFolderUiModel>, selected: UUID?, enabled: Boolean,
    onSelect: (UUID?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text("保存到：" + (selected?.let { folderPath(folders, it) } ?: "未归档"), maxLines = 2)
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false }, title = { Text("选择目标文件夹") },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                TextButton(onClick = { open = false; onSelect(null) }) { Text("未归档") }
                folders.forEach { folder ->
                    TextButton(onClick = { open = false; onSelect(folder.id) }) { Text(folderPath(folders, folder.id)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("取消") } },
    )
}
