package com.pdh.cardvault.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pdh.cardvault.R
import com.pdh.cardvault.security.auth.DeviceSecurityStatus

@Composable
fun SecurityNoticeScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        EntryContent(
            modifier = Modifier.padding(contentPadding),
        ) {
            EntryMark(text = "CV")
            Text(
                text = stringResource(R.string.security_notice_heading),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.security_notice_storage_status),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NoticePoint(text = stringResource(R.string.security_notice_offline))
                    NoticePoint(text = stringResource(R.string.security_notice_no_backup))
                    NoticePoint(text = stringResource(R.string.security_notice_screen_protection))
                }
            }
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_understand_and_continue))
            }
        }
    }
}

@Composable
fun LockedScreen(
    authenticating: Boolean,
    authenticationFailed: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        EntryContent(
            modifier = Modifier.padding(contentPadding),
        ) {
            EntryMark(text = "•••")
            Text(
                text = stringResource(R.string.locked_heading),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.locked_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (authenticationFailed) {
                Text(
                    text = stringResource(R.string.authentication_failed_generic),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                onClick = onUnlock,
                enabled = !authenticating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (authenticating) {
                            R.string.authentication_in_progress
                        } else {
                            R.string.action_unlock
                        },
                    ),
                )
            }
        }
    }
}

@Composable
fun SecurityUnavailableScreen(
    status: DeviceSecurityStatus,
    onOpenSecuritySettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = when (status) {
        DeviceSecurityStatus.NoSecureLockScreen ->
            stringResource(R.string.security_unavailable_no_lock_screen)

        DeviceSecurityStatus.AuthenticationUnavailable,
        DeviceSecurityStatus.Available,
        -> stringResource(R.string.security_unavailable_authentication)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        EntryContent(
            modifier = Modifier.padding(contentPadding),
        ) {
            EntryMark(
                text = "!",
                isError = true,
            )
            Text(
                text = stringResource(R.string.security_unavailable_heading),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onOpenSecuritySettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_open_security_settings))
            }
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
fun VaultUnavailableScreen(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        EntryContent(
            modifier = Modifier.padding(contentPadding),
        ) {
            EntryMark(
                text = "!",
                isError = true,
            )
            Text(
                text = stringResource(R.string.vault_unavailable_heading),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.vault_unavailable_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun EntryContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun EntryMark(
    text: String,
    isError: Boolean = false,
) {
    Surface(
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun NoticePoint(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(6.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
        ) {}
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
