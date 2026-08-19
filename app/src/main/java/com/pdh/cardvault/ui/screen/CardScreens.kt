package com.pdh.cardvault.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.pdh.cardvault.R
import com.pdh.cardvault.domain.validation.ExpiryDateTools
import com.pdh.cardvault.presentation.CardDetailUiModel
import com.pdh.cardvault.presentation.CardDetailUiState
import com.pdh.cardvault.presentation.CardListItemUiModel
import com.pdh.cardvault.presentation.CardOperationMessage
import com.pdh.cardvault.presentation.RevealedCardSecretsUiState
import com.pdh.cardvault.ui.card.CardWalletStack
import com.pdh.cardvault.ui.card.FlippableMaskedCardDetail
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardListScreen(
    cards: List<CardListItemUiModel>,
    sortingInProgress: Boolean,
    operationMessage: CardOperationMessage,
    onBackToHome: () -> Unit,
    onAddCard: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTemplates: () -> Unit,
    onCardSelected: (UUID) -> Unit,
    onCardsReordered: (List<UUID>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "wallet-header") {
                WalletHeader(
                    cardCount = cards.size,
                    sortingInProgress = sortingInProgress,
                    onBackToHome = onBackToHome,
                    onAddCard = onAddCard,
                    onOpenTemplates = onOpenTemplates,
                    onOpenSettings = onOpenSettings,
                )
            }

            if (operationMessage != CardOperationMessage.None) {
                item(key = "safe-operation-message") {
                    SafeOperationMessage(operationMessage)
                }
            }

            if (cards.isEmpty()) {
                item(key = "empty-state") {
                    EmptyCardState()
                }
            } else {
                item(key = "wallet-stack") {
                    CardWalletStack(
                        cards = cards,
                        sortingInProgress = sortingInProgress,
                        openActionLabel = stringResource(R.string.action_open_card_detail),
                        moveUpActionLabel = stringResource(R.string.action_move_up),
                        moveDownActionLabel = stringResource(R.string.action_move_down),
                        onCardOpened = onCardSelected,
                        onCardsReordered = onCardsReordered,
                    )
                }
            }
        }
    }
}

@Composable
private fun WalletHeader(
    cardCount: Int,
    sortingInProgress: Boolean,
    onBackToHome: () -> Unit,
    onAddCard: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val backLabel = stringResource(R.string.action_back)
            Surface(
                onClick = onBackToHome,
                modifier = Modifier
                    .size(42.dp)
                    .clearAndSetSemantics {
                        contentDescription = backLabel
                        onClick(label = backLabel) {
                            onBackToHome()
                            true
                        }
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("‹", style = MaterialTheme.typography.headlineMedium)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = stringResource(R.string.card_wallet_title),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                )
                if (cardCount > 0) {
                    Text(
                        text = stringResource(R.string.card_wallet_card_count, cardCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WalletActionButton(
                icon = WalletActionIcon.Gallery,
                contentDescriptionText = stringResource(R.string.action_browse_templates),
                enabled = !sortingInProgress,
                onClick = onOpenTemplates,
            )
            WalletActionButton(
                icon = WalletActionIcon.Add,
                contentDescriptionText = stringResource(R.string.action_add_card),
                enabled = !sortingInProgress,
                emphasized = true,
                onClick = onAddCard,
            )
            WalletActionButton(
                icon = WalletActionIcon.More,
                contentDescriptionText = stringResource(R.string.settings_title),
                enabled = !sortingInProgress,
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun WalletActionButton(
    icon: WalletActionIcon,
    contentDescriptionText: String,
    enabled: Boolean,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .clearAndSetSemantics {
                contentDescription = contentDescriptionText
                onClick(label = contentDescriptionText) {
                    onClick()
                    true
                }
            },
        shape = CircleShape,
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (emphasized) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            WalletActionGlyph(icon)
        }
    }
}

@Composable
private fun WalletActionGlyph(icon: WalletActionIcon) {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(22.dp)) {
        val strokeWidth = 2.dp.toPx()
        when (icon) {
            WalletActionIcon.Gallery -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.08f, size.height * 0.2f),
                    size = Size(size.width * 0.7f, size.height * 0.64f),
                    cornerRadius = CornerRadius(size.minDimension * 0.12f),
                    style = Stroke(width = strokeWidth),
                )
                drawRoundRect(
                    color = color.copy(alpha = 0.72f),
                    topLeft = Offset(size.width * 0.27f, size.height * 0.06f),
                    size = Size(size.width * 0.65f, size.height * 0.58f),
                    cornerRadius = CornerRadius(size.minDimension * 0.12f),
                    style = Stroke(width = strokeWidth),
                )
            }
            WalletActionIcon.Add -> {
                drawLine(
                    color = color,
                    start = Offset(size.width / 2f, size.height * 0.18f),
                    end = Offset(size.width / 2f, size.height * 0.82f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.18f, size.height / 2f),
                    end = Offset(size.width * 0.82f, size.height / 2f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
            WalletActionIcon.More -> listOf(0.22f, 0.5f, 0.78f).forEach { fraction ->
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.07f,
                    center = Offset(size.width * fraction, size.height / 2f),
                )
            }
        }
    }
}

private enum class WalletActionIcon {
    Gallery,
    Add,
    More,
}

@Composable
private fun EmptyCardState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(76.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.card_face_monogram),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Text(
                text = stringResource(R.string.empty_cards_heading),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    detailState: CardDetailUiState,
    revealedSecrets: RevealedCardSecretsUiState,
    operationMessage: CardOperationMessage,
    onRevealCardSecrets: (UUID) -> Unit,
    onCopyRevealedCardNumber: (UUID) -> Unit,
    onHideCardSecrets: () -> Unit,
    onEdit: (UUID) -> Unit,
    onDeleteConfirmed: (UUID) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleteCandidate by remember { mutableStateOf<UUID?>(null) }
    LaunchedEffect(detailState) {
        val activeId = (detailState as? CardDetailUiState.Loaded)?.recordId
        if (deleteCandidate != activeId) deleteCandidate = null
    }
    deleteCandidate?.let { recordId ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(stringResource(R.string.card_delete_confirmation_title)) },
            text = { Text(stringResource(R.string.card_delete_confirmation_detail)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteCandidate = null
                        onDeleteConfirmed(recordId)
                    },
                ) {
                    Text(stringResource(R.string.action_delete_permanently))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.card_detail_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    val backLabel = stringResource(R.string.action_back)
                    Surface(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(40.dp)
                            .clearAndSetSemantics {
                                contentDescription = backLabel
                                onClick(label = backLabel) {
                                    onBack()
                                    true
                                }
                            },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "‹",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Light,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { contentPadding ->
        when (detailState) {
            CardDetailUiState.Hidden,
            CardDetailUiState.Loading,
            -> SafeDetailMessage(
                heading = stringResource(R.string.card_detail_loading_heading),
                detail = stringResource(R.string.card_detail_loading_detail),
                contentPadding = contentPadding,
            )
            CardDetailUiState.NotFound -> SafeDetailMessage(
                heading = stringResource(R.string.card_detail_not_found_heading),
                detail = stringResource(R.string.card_detail_not_found_detail),
                contentPadding = contentPadding,
            )
            is CardDetailUiState.Loaded -> LoadedCardDetail(
                recordId = detailState.recordId,
                card = detailState.card,
                revealedSecrets = revealedSecrets,
                operationMessage = operationMessage,
                contentPadding = contentPadding,
                onRevealCardSecrets = onRevealCardSecrets,
                onCopyRevealedCardNumber = onCopyRevealedCardNumber,
                onHideCardSecrets = onHideCardSecrets,
                onEdit = onEdit,
                onDelete = { deleteCandidate = detailState.recordId },
            )
        }
    }
}

@Composable
private fun LoadedCardDetail(
    recordId: UUID,
    card: CardDetailUiModel,
    revealedSecrets: RevealedCardSecretsUiState,
    operationMessage: CardOperationMessage,
    contentPadding: PaddingValues,
    onRevealCardSecrets: (UUID) -> Unit,
    onCopyRevealedCardNumber: (UUID) -> Unit,
    onHideCardSecrets: () -> Unit,
    onEdit: (UUID) -> Unit,
    onDelete: () -> Unit,
) {
    var showCardBack by remember(recordId) { mutableStateOf(false) }
    LaunchedEffect(recordId) { showCardBack = true }
    val cvvHiddenLabel = stringResource(R.string.card_detail_cvv_hidden)
    val cvvNotSavedLabel = stringResource(R.string.card_detail_cvv_not_saved)
    val sensitiveDescription = stringResource(R.string.card_detail_sensitive_content_description)
    val visibleSecrets = (revealedSecrets as? RevealedCardSecretsUiState.Visible)
        ?.takeIf { visible -> visible.recordId == recordId }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (operationMessage != CardOperationMessage.None) {
            item(key = "safe-operation-message") {
                SafeOperationMessage(operationMessage)
            }
        }
        item(key = "masked-detail") {
            FlippableMaskedCardDetail(
                card = card,
                cvvHiddenLabel = cvvHiddenLabel,
                cvvNotSavedLabel = cvvNotSavedLabel,
                showBack = showCardBack,
                revealedCardNumber = visibleSecrets?.cardNumber,
                revealedCvv = visibleSecrets?.cvv,
                revealedExpiryText = visibleSecrets?.let { secrets ->
                    ExpiryDateTools.format(secrets.expiryMonth, secrets.expiryYear)
                },
                revealedExpired = visibleSecrets?.expired == true,
                expiredLabel = stringResource(R.string.card_expired),
                contentDescriptionText = sensitiveDescription,
                showBackActionLabel = stringResource(R.string.card_flip_to_back),
                showFrontActionLabel = stringResource(R.string.card_flip_to_front),
                revealActionLabel = stringResource(R.string.action_reveal_card_secrets),
                hideActionLabel = stringResource(R.string.action_hide_card_secrets),
                copyActionLabel = stringResource(R.string.action_copy_revealed_card_number),
                onRevealSecrets = { onRevealCardSecrets(recordId) },
                onHideSecrets = onHideCardSecrets,
                onCopyCardNumber = { onCopyRevealedCardNumber(recordId) },
                onShowBackChanged = { showBack -> showCardBack = showBack },
            )
        }
        item(key = "detail-actions") {
            AnimatedVisibility(
                visible = showCardBack,
                enter = fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 120)),
                exit = fadeOut(animationSpec = tween(durationMillis = 120)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { onEdit(recordId) },
                        enabled = card.canEdit,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.action_edit_card))
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        enabled = card.canDelete,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.action_delete_card))
                    }
                }
            }
        }
    }
}

@Composable
private fun SafeOperationMessage(message: CardOperationMessage) {
    val text = when (message) {
        CardOperationMessage.None -> return
        CardOperationMessage.AuthenticationFailed ->
            stringResource(R.string.authentication_failed_generic)
        CardOperationMessage.StorageFailed -> stringResource(R.string.card_error_storage_generic)
        CardOperationMessage.DeviceSecurityRequired ->
            stringResource(R.string.card_error_device_security_required)
        CardOperationMessage.RecordUnavailable ->
            stringResource(R.string.card_detail_not_found_detail)
        CardOperationMessage.CardNumberCopied -> stringResource(R.string.card_number_copied)
        CardOperationMessage.ClipboardFailed -> stringResource(R.string.card_clipboard_failed)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            color = if (message == CardOperationMessage.CardNumberCopied) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SafeDetailMessage(
    heading: String,
    detail: String,
    contentPadding: PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                heading,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
