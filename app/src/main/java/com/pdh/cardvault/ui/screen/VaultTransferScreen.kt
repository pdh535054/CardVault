package com.pdh.cardvault.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pdh.cardvault.R
import com.pdh.cardvault.presentation.VaultPairingState
import com.pdh.cardvault.presentation.VaultTransferMessageKind
import com.pdh.cardvault.presentation.VaultTransferUiState
import com.pdh.cardvault.sync.AndroidSyncFileExchange

@Composable
fun VaultTransferScreen(
    state: VaultTransferUiState,
    onBack: () -> Unit,
    onPrepareExport: () -> Unit,
    onPrepareNewDevicePairing: () -> Unit,
    onRotateSyncKey: () -> Unit,
    onSharePreparedExport: () -> Unit,
    onImportUriSelected: (String) -> Unit,
    onPairingCodeChanged: (String) -> Unit,
    onConfirmPairingImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRotationConfirmation by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> if (uri != null) onImportUriSelected(uri.toString()) },
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { MinimalTransferTopBar(onBack) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.vault_transfer_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(state.pairingState == VaultPairingState.Paired)
                Spacer(Modifier.width(9.dp))
                Text(
                    text = if (state.pairingState == VaultPairingState.Paired) {
                        stringResource(R.string.vault_transfer_status_paired)
                    } else {
                        stringResource(R.string.vault_transfer_status_unpaired)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.pairingState == VaultPairingState.Paired) {
                Text(
                    text = stringResource(
                        R.string.vault_transfer_key_epoch,
                        state.keyEpoch ?: 0,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TransferActionCard(
                eyebrow = stringResource(R.string.vault_transfer_export_eyebrow),
                title = if (state.pairingState == VaultPairingState.Paired) {
                    stringResource(R.string.vault_transfer_generate_sync_title)
                } else {
                    stringResource(R.string.vault_transfer_connect_first_title)
                },
                description = if (state.pairingState == VaultPairingState.Paired) {
                    stringResource(R.string.vault_transfer_sync_description)
                } else {
                    stringResource(R.string.vault_transfer_pair_description)
                },
            ) {
                if (state.preparedFileName == null) {
                    Button(
                        onClick = onPrepareExport,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (state.busy) {
                                    R.string.vault_transfer_encrypting
                                } else {
                                    R.string.vault_transfer_prepare_export
                                },
                            ),
                        )
                    }
                    if (state.pairingState == VaultPairingState.Paired) {
                        OutlinedButton(
                            onClick = onPrepareNewDevicePairing,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.vault_transfer_connect_device))
                        }
                    }
                } else {
                    Button(
                        onClick = onSharePreparedExport,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.vault_transfer_open_share))
                    }
                    if (state.pairingState == VaultPairingState.Paired) {
                        OutlinedButton(
                            onClick = onPrepareNewDevicePairing,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.vault_transfer_connect_device))
                        }
                    }
                    Text(
                        text = state.preparedFileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.pairingCode?.let { pairingCode ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.vault_transfer_pairing_code_title),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = pairingCode,
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.vault_transfer_pairing_code_instruction),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            TransferActionCard(
                eyebrow = stringResource(R.string.vault_transfer_import_eyebrow),
                title = stringResource(R.string.vault_transfer_import_title),
                description = stringResource(R.string.vault_transfer_import_description),
            ) {
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(
                            arrayOf(
                                AndroidSyncFileExchange.MIME_TYPE,
                                "application/octet-stream",
                                "application/zip",
                                "*/*",
                            ),
                        )
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.vault_transfer_choose_file))
                }
            }

            if (state.importNeedsPairingCode) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.vault_transfer_pairing_code_input_title),
                            fontWeight = FontWeight.SemiBold,
                        )
                        OutlinedTextField(
                            value = state.pairingCodeInput,
                            onValueChange = onPairingCodeChanged,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Ascii,
                            ),
                            placeholder = {
                                Text(stringResource(R.string.vault_transfer_pairing_code_placeholder))
                            },
                        )
                        Button(
                            onClick = onConfirmPairingImport,
                            enabled = !state.busy && state.pairingCodeInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.vault_transfer_verify_import))
                        }
                    }
                }
            }

            if (state.pairingState == VaultPairingState.Paired) {
                TransferActionCard(
                    eyebrow = stringResource(R.string.vault_transfer_security_eyebrow),
                    title = stringResource(R.string.vault_transfer_rotate_title),
                    description = stringResource(R.string.vault_transfer_rotate_description),
                ) {
                    OutlinedButton(
                        onClick = { showRotationConfirmation = true },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.vault_transfer_rotate_action))
                    }
                }
            }

            state.message?.let { message ->
                val colors = when (message.kind) {
                    VaultTransferMessageKind.Success ->
                        MaterialTheme.colorScheme.tertiaryContainer to
                            MaterialTheme.colorScheme.onTertiaryContainer
                    VaultTransferMessageKind.Information ->
                        MaterialTheme.colorScheme.secondaryContainer to
                            MaterialTheme.colorScheme.onSecondaryContainer
                    VaultTransferMessageKind.Error ->
                        MaterialTheme.colorScheme.errorContainer to
                            MaterialTheme.colorScheme.onErrorContainer
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = colors.first,
                    contentColor = colors.second,
                ) {
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (state.busy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.width(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.vault_transfer_secure_processing))
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.vault_transfer_encryption_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }


    if (showRotationConfirmation) {
        AlertDialog(
            onDismissRequest = { showRotationConfirmation = false },
            title = { Text(stringResource(R.string.vault_transfer_rotate_confirm_title)) },
            text = { Text(stringResource(R.string.vault_transfer_rotate_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRotationConfirmation = false
                        onRotateSyncKey()
                    },
                ) {
                    Text(stringResource(R.string.vault_transfer_rotate_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRotationConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun TransferActionCard(
    eyebrow: String,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun StatusDot(active: Boolean) {
    Surface(
        modifier = Modifier.width(9.dp).height(9.dp),
        shape = CircleShape,
        color = if (active) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.outline
        },
    ) {}
}

@Composable
private fun MinimalTransferTopBar(onBack: () -> Unit) {
    val backLabel = stringResource(R.string.action_back)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onBack,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = backLabel
                    onClick(label = backLabel) {
                        onBack()
                        true
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = "‹",
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }
    }
}
