package com.pdh.cardvault.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.pdh.cardvault.InboundTransferRequest
import com.pdh.cardvault.presentation.CardFormField
import com.pdh.cardvault.presentation.AddressFormField
import com.pdh.cardvault.presentation.AddressCopyPart
import com.pdh.cardvault.presentation.AddressNavigationEvent
import com.pdh.cardvault.presentation.AddressesUiState
import com.pdh.cardvault.presentation.CardEditUiState
import com.pdh.cardvault.presentation.CardNavigationEvent
import com.pdh.cardvault.presentation.CardsUiState
import com.pdh.cardvault.presentation.VaultTransferUiState
import com.pdh.cardvault.presentation.findPossibleDuplicateCards
import com.pdh.cardvault.wallet.BackupReminderPolicy
import com.pdh.cardvault.ui.theme.CardVaultMotion
import com.pdh.cardvault.security.auth.AuthenticationAction
import com.pdh.cardvault.security.auth.AuthenticationOutcome
import com.pdh.cardvault.ui.screen.AboutPrivacyScreen
import com.pdh.cardvault.ui.screen.AddAddressScreen
import com.pdh.cardvault.ui.screen.AddressDetailScreen
import com.pdh.cardvault.ui.screen.AddressListScreen
import com.pdh.cardvault.ui.screen.AddCardScreen
import com.pdh.cardvault.ui.screen.CardDetailScreen
import com.pdh.cardvault.ui.screen.CardListScreen
import com.pdh.cardvault.ui.screen.EditCardScreen
import com.pdh.cardvault.ui.screen.EditCardStatusScreen
import com.pdh.cardvault.ui.screen.EditAddressScreen
import com.pdh.cardvault.ui.screen.SecurityNoticeScreen
import com.pdh.cardvault.ui.screen.SettingsScreen
import com.pdh.cardvault.ui.screen.TemplateGalleryScreen
import com.pdh.cardvault.ui.screen.VaultTransferScreen
import com.pdh.cardvault.ui.screen.VaultHomeScreen
import java.util.UUID

@Composable
internal fun CardVaultNavHost(
    navController: NavHostController,
    showSecurityNoticeAtLaunch: Boolean,
    showSecurityNotice: Boolean,
    inboundTransferRequest: InboundTransferRequest?,
    onInboundTransfer: (InboundTransferRequest) -> Boolean,
    onSecurityNoticeAcknowledged: () -> Unit,
    startupAuthenticationEnabled: Boolean,
    authenticationInProgress: Boolean,
    authenticationOutcome: AuthenticationOutcome,
    onStartupAuthenticationChanged: (Boolean) -> Unit,
    vaultTransferUiState: VaultTransferUiState,
    onPrepareVaultExport: () -> Unit,
    onRotateVaultSyncKey: () -> Unit,
    onSharePreparedVaultExport: () -> Unit,
    onSavePreparedVaultExport: (String) -> Unit,
    onCopyVaultPairingCode: () -> Unit,
    onVaultImportUriSelected: (String) -> Unit,
    onVaultImportPickerUnavailable: () -> Unit,
    onVaultSavePickerUnavailable: () -> Unit,
    onVaultPairingCodeChanged: (String) -> Unit,
    onVaultPastePairingCode: (String?) -> Unit,
    onSnoozeBackupReminder: () -> Unit,
    onConfirmVaultPairingImport: () -> Unit,
    cardsUiState: CardsUiState,
    addressesUiState: AddressesUiState,
    onAddressFormFieldChanged: (AddressFormField, String) -> Unit,
    onAddressTemplateSelected: (String) -> Unit,
    onSubmitAddress: () -> Unit,
    onClearAddressForm: () -> Unit,
    onLoadAddressDetail: (String?) -> Unit,
    onClearAddressDetail: () -> Unit,
    onCopyAddress: (UUID, AddressCopyPart) -> Unit,
    onDeleteAddress: (UUID) -> Unit,
    onAddressesReordered: (List<UUID>) -> Unit,
    onAddressFolderSelected: (UUID?) -> Unit,
    onAddressFoldersReordered: (List<UUID?>) -> Unit,
    onAddressWalletReordered: (List<String>) -> Unit,
    onCreateAddressFolder: (String, UUID?) -> Unit,
    onRenameAddressFolder: (UUID, String) -> Unit,
    onSetAddressFolderColor: (UUID, Int) -> Unit,
    onMoveAddressFolder: (UUID, UUID?) -> Unit,
    onDeleteAddressFolder: (UUID) -> Unit,
    onMoveAddressToFolder: (UUID, UUID?) -> Unit,
    onLoadAddressEdit: (String?) -> Unit,
    onAddressEditFieldChanged: (AddressFormField, String) -> Unit,
    onAddressEditTemplateSelected: (String) -> Unit,
    onSubmitAddressEdit: () -> Unit,
    onClearAddressEditDraft: () -> Unit,
    onAddressNavigationEventHandled: (AddressNavigationEvent) -> Unit,
    onCardFormFieldChanged: (CardFormField, String) -> Unit,
    onCardFormFolderSelected: (UUID?) -> Unit,
    onCreateFromCard: (UUID) -> Unit,
    onConfirmSaveCvvRisk: () -> Unit,
    onDismissSaveCvvRisk: () -> Unit,
    onTemplateSelected: (String) -> Unit,
    onSubmitCard: () -> Unit,
    onClearCardForm: () -> Unit,
    onLoadCardDetail: (String?) -> Unit,
    onClearCardDetail: () -> Unit,
    onCardsReordered: (List<UUID>) -> Unit,
    onCardFolderSelected: (UUID?) -> Unit,
    onCardSearchQueryChanged: (String) -> Unit,
    onCardFoldersReordered: (List<UUID?>) -> Unit,
    onCardWalletReordered: (List<String>) -> Unit,
    onCreateCardFolder: (String) -> Unit,
    onRenameCardFolder: (UUID, String) -> Unit,
    onSetCardFolderColor: (UUID, Int) -> Unit,
    onMoveCardFolder: (UUID, UUID?) -> Unit,
    onDeleteCardFolder: (UUID) -> Unit,
    onMoveCardToFolder: (UUID, UUID?) -> Unit,
    onRequestCardAuthentication: (AuthenticationAction, UUID) -> Unit,
    onHideCardSecrets: () -> Unit,
    onCopyRevealedCardNumber: (UUID) -> Unit,
    onEditFormFieldChanged: (CardFormField, String) -> Unit,
    onConfirmEditSaveCvvRisk: () -> Unit,
    onDismissEditSaveCvvRisk: () -> Unit,
    onEditTemplateSelected: (String) -> Unit,
    onSubmitEdit: () -> Unit,
    onClearEditDraft: () -> Unit,
    onNavigationEventHandled: (CardNavigationEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val startDestination = if (showSecurityNoticeAtLaunch) {
        CardVaultDestination.SecurityNotice.route
    } else {
        CardVaultDestination.Home.route
    }

    val navigationEvent = cardsUiState.navigationEvent
    LaunchedEffect(navigationEvent) {
        val event = navigationEvent ?: return@LaunchedEffect
        when (event) {
            CardNavigationEvent.CopyReady -> navController.navigate(CardVaultDestination.AddCard.route) {
                popUpTo(CardVaultDestination.Cards.route) { inclusive = false }
                launchSingleTop = true
            }
            CardNavigationEvent.AddSaved -> navController.popBackStack()
            is CardNavigationEvent.EditReady -> {
                navController.navigate(CardVaultDestination.editCardRoute(event.recordId)) {
                    launchSingleTop = true
                }
            }
            CardNavigationEvent.EditSaved -> navController.popBackStack()
            CardNavigationEvent.DeleteCompleted -> {
                navController.popBackStack(CardVaultDestination.Cards.route, inclusive = false)
            }
        }
        onNavigationEventHandled(event)
    }

    val addressNavigationEvent = addressesUiState.navigationEvent
    LaunchedEffect(addressNavigationEvent) {
        val event = addressNavigationEvent ?: return@LaunchedEffect
        when (event) {
            AddressNavigationEvent.AddSaved -> navController.popBackStack()
            AddressNavigationEvent.EditSaved -> navController.popBackStack()
            AddressNavigationEvent.DeleteCompleted -> {
                navController.popBackStack(CardVaultDestination.Addresses.route, inclusive = false)
            }
        }
        onAddressNavigationEventHandled(event)
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = CardVaultMotion.navEnterFade()) +
                slideInHorizontally(
                    animationSpec = CardVaultMotion.navSlide(),
                    initialOffsetX = { fullWidth -> fullWidth / 14 },
                ) +
                scaleIn(
                    animationSpec = CardVaultMotion.navSlide(),
                    initialScale = 0.98f,
                )
        },
        exitTransition = {
            fadeOut(animationSpec = CardVaultMotion.navExitFade()) +
                scaleOut(
                    animationSpec = CardVaultMotion.navSlide(),
                    targetScale = 0.968f,
                )
        },
        popEnterTransition = {
            fadeIn(animationSpec = CardVaultMotion.navEnterFade()) +
                scaleIn(
                    animationSpec = CardVaultMotion.navPopSlide(),
                    initialScale = 0.968f,
                )
        },
        popExitTransition = {
            fadeOut(animationSpec = CardVaultMotion.navExitFade()) +
                slideOutHorizontally(
                    animationSpec = CardVaultMotion.navPopSlide(),
                    targetOffsetX = { fullWidth -> fullWidth / 12 },
                ) +
                scaleOut(
                    animationSpec = CardVaultMotion.navPopSlide(),
                    targetScale = 0.98f,
                )
        },
    ) {
        screen(CardVaultDestination.SecurityNotice.route) {
            SecurityNoticeScreen(
                onContinue = {
                    onSecurityNoticeAcknowledged()
                    navController.navigate(CardVaultDestination.Home.route) {
                        popUpTo(CardVaultDestination.SecurityNotice.route) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
            )
        }
        screen(CardVaultDestination.Home.route) {
            VaultHomeScreen(
                onOpenCards = {
                    navController.navigate(CardVaultDestination.Cards.route) {
                        launchSingleTop = true
                    }
                },
                onOpenAddresses = {
                    navController.navigate(CardVaultDestination.Addresses.route) {
                        launchSingleTop = true
                    }
                },
                onOpenSettings = {
                    navController.navigate(CardVaultDestination.Settings.route) {
                        launchSingleTop = true
                    }
                },
                cardCount = cardsUiState.cards.size,
                addressCount = addressesUiState.addresses.size,
                backupReminder = BackupReminderPolicy.evaluate(
                    hasVaultData = cardsUiState.cards.isNotEmpty() ||
                        addressesUiState.addresses.isNotEmpty(),
                    lastSyncAtEpochMillis = vaultTransferUiState.lastSyncAtEpochMillis,
                    snoozedUntilEpochMillis =
                        vaultTransferUiState.backupReminderSnoozedUntilEpochMillis,
                    nowEpochMillis = System.currentTimeMillis(),
                ),
                onOpenBackup = {
                    navController.navigate(CardVaultDestination.VaultTransfer.route) {
                        launchSingleTop = true
                    }
                },
                onSnoozeBackupReminder = onSnoozeBackupReminder,
            )
        }
        screen(CardVaultDestination.Cards.route) {
            CardListScreen(
                cards = cardsUiState.cards,
                folders = cardsUiState.folders,
                folderOrder = cardsUiState.folderOrder,
                walletOrder = cardsUiState.walletOrder,
                selectedFolderId = cardsUiState.selectedFolderId,
                cardSearchQuery = cardsUiState.cardSearchQuery,
                sortingInProgress = cardsUiState.sortingInProgress,
                operationMessage = cardsUiState.operationMessage,
                onBackToHome = navController::popBackStack,
                onAddCard = {
                    navController.navigate(CardVaultDestination.AddCard.route)
                },
                onOpenSettings = {
                    navController.navigate(CardVaultDestination.Settings.route)
                },
                onOpenTemplates = {
                    navController.navigate(CardVaultDestination.TemplateGallery.route)
                },
                onCardSelected = { recordId ->
                    navController.navigate(CardVaultDestination.cardDetailRoute(recordId))
                },
                onFolderSelected = onCardFolderSelected,
                onCardSearchQueryChanged = onCardSearchQueryChanged,
                onCardsReordered = onCardsReordered,
                onFoldersReordered = onCardFoldersReordered,
                onWalletEntriesReordered = onCardWalletReordered,
                onCreateFolder = onCreateCardFolder,
                onRenameFolder = onRenameCardFolder,
                onSetFolderColor = onSetCardFolderColor,
                onMoveFolder = onMoveCardFolder,
                onMoveCardToFolder = onMoveCardToFolder,
                onDeleteFolder = onDeleteCardFolder,
            )
        }
        screen(CardVaultDestination.AddCard.route) {
            val leaveForm = {
                onClearCardForm()
                navController.popBackStack()
                Unit
            }
            AddCardScreen(
                form = cardsUiState.form,
                folders = cardsUiState.folders,
                onFolderSelected = onCardFormFolderSelected,
                onFieldChanged = onCardFormFieldChanged,
                onConfirmSaveCvvRisk = onConfirmSaveCvvRisk,
                onDismissSaveCvvRisk = onDismissSaveCvvRisk,
                onTemplateSelected = onTemplateSelected,
                onSubmit = {
                    onSubmitCard()
                },
                onBack = leaveForm,
                duplicateCardNames = findPossibleDuplicateCards(
                    cardNumber = cardsUiState.form.cardNumber,
                    existingCards = cardsUiState.cards,
                ).map { it.nickname },
            )
        }
        screen(
            route = CardVaultDestination.CardDetail.route,
            arguments = listOf(
                navArgument(CardVaultDestination.RECORD_ID_ARGUMENT) {
                    type = NavType.StringType
                    nullable = false
                },
            ),
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getString(
                CardVaultDestination.RECORD_ID_ARGUMENT,
            )
            LaunchedEffect(recordId) {
                onLoadCardDetail(recordId)
            }
            DisposableEffect(recordId) {
                onDispose {
                    onClearCardDetail()
                }
            }
            CardDetailScreen(
                detailState = cardsUiState.detail,
                revealedSecrets = cardsUiState.revealedSecrets,
                operationMessage = cardsUiState.operationMessage,
                onRevealCardSecrets = { id ->
                    onRequestCardAuthentication(AuthenticationAction.RevealCardSecrets, id)
                },
                onHideCardSecrets = onHideCardSecrets,
                onCopyRevealedCardNumber = onCopyRevealedCardNumber,
                onEdit = { id ->
                    onRequestCardAuthentication(AuthenticationAction.EditCard, id)
                },
                onCopyAsNew = onCreateFromCard,
                folders = cardsUiState.folders,
                currentFolderId = cardsUiState.cards.firstOrNull { it.id.toString() == recordId }?.folderId,
                onMove = onMoveCardToFolder,
                onDeleteConfirmed = { id ->
                    onRequestCardAuthentication(AuthenticationAction.DeleteCard, id)
                },
                onBack = navController::popBackStack,
            )
        }
        screen(
            route = CardVaultDestination.EditCard.route,
            arguments = listOf(
                navArgument(CardVaultDestination.RECORD_ID_ARGUMENT) {
                    type = NavType.StringType
                    nullable = false
                },
            ),
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments
                ?.getString(CardVaultDestination.RECORD_ID_ARGUMENT)
                ?.let { value -> runCatching { UUID.fromString(value) }.getOrNull() }
            val ready = (cardsUiState.edit as? CardEditUiState.Ready)
                ?.takeIf { edit -> edit.recordId == recordId }
            DisposableEffect(recordId) {
                onDispose { onClearEditDraft() }
            }
            if (ready != null) {
                EditCardScreen(
                    form = ready.form,
                    onFieldChanged = onEditFormFieldChanged,
                    onConfirmSaveCvvRisk = onConfirmEditSaveCvvRisk,
                    onDismissSaveCvvRisk = onDismissEditSaveCvvRisk,
                    onTemplateSelected = onEditTemplateSelected,
                    onSubmit = onSubmitEdit,
                    onBack = {
                        onClearEditDraft()
                        navController.popBackStack()
                    },
                    duplicateCardNames = findPossibleDuplicateCards(
                        cardNumber = ready.form.cardNumber,
                        existingCards = cardsUiState.cards,
                        excludeId = recordId,
                    ).map { it.nickname },
                )
            } else {
                EditCardStatusScreen(
                    loading = cardsUiState.edit == CardEditUiState.Loading,
                    onBack = navController::popBackStack,
                )
            }
        }
        screen(CardVaultDestination.Settings.route) {
            SettingsScreen(
                startupAuthenticationEnabled = startupAuthenticationEnabled,
                authenticationInProgress = authenticationInProgress,
                authenticationOutcome = authenticationOutcome,
                onStartupAuthenticationChanged = onStartupAuthenticationChanged,
                onBack = navController::popBackStack,
                onOpenVaultTransfer = {
                    navController.navigate(CardVaultDestination.VaultTransfer.route) {
                        launchSingleTop = true
                    }
                },
                onOpenAboutPrivacy = {
                    navController.navigate(CardVaultDestination.AboutPrivacy.route)
                },
            )
        }
        screen(CardVaultDestination.VaultTransfer.route) {
            VaultTransferScreen(
                state = vaultTransferUiState,
                onBack = navController::popBackStack,
                onPrepareExport = onPrepareVaultExport,
                onRotateSyncKey = onRotateVaultSyncKey,
                onSharePreparedExport = onSharePreparedVaultExport,
                onSavePreparedExport = onSavePreparedVaultExport,
                onCopyPairingCode = onCopyVaultPairingCode,
                onImportUriSelected = onVaultImportUriSelected,
                onImportPickerUnavailable = onVaultImportPickerUnavailable,
                onSavePickerUnavailable = onVaultSavePickerUnavailable,
                onPairingCodeChanged = onVaultPairingCodeChanged,
                onPastePairingCode = onVaultPastePairingCode,
                onConfirmPairingImport = onConfirmVaultPairingImport,
            )
        }
        screen(CardVaultDestination.AboutPrivacy.route) {
            AboutPrivacyScreen(onBack = navController::popBackStack)
        }
        screen(CardVaultDestination.TemplateGallery.route) {
            TemplateGalleryScreen(onBack = navController::popBackStack)
        }
        screen(CardVaultDestination.Addresses.route) {
            AddressListScreen(
                addresses = addressesUiState.addresses,
                folders = addressesUiState.folders,
                folderOrder = addressesUiState.folderOrder,
                walletOrder = addressesUiState.walletOrder,
                selectedFolderId = addressesUiState.selectedFolderId,
                sortingInProgress = addressesUiState.sortingInProgress,
                operationMessage = addressesUiState.operationMessage,
                onBack = navController::popBackStack,
                onAddAddress = {
                    navController.navigate(CardVaultDestination.AddAddress.route)
                },
                onAddressSelected = { recordId ->
                    navController.navigate(CardVaultDestination.addressDetailRoute(recordId))
                },
                onAddressesReordered = onAddressesReordered,
                onFolderSelected = onAddressFolderSelected,
                onFoldersReordered = onAddressFoldersReordered,
                onWalletEntriesReordered = onAddressWalletReordered,
                onCreateFolder = onCreateAddressFolder,
                onRenameFolder = onRenameAddressFolder,
                onSetFolderColor = onSetAddressFolderColor,
                onMoveFolder = onMoveAddressFolder,
                onMoveAddressToFolder = onMoveAddressToFolder,
                onDeleteFolder = onDeleteAddressFolder,
            )
        }
        screen(CardVaultDestination.AddAddress.route) {
            val leaveForm = {
                onClearAddressForm()
                navController.popBackStack()
                Unit
            }
            AddAddressScreen(
                form = addressesUiState.form,
                onFieldChanged = onAddressFormFieldChanged,
                onTemplateSelected = onAddressTemplateSelected,
                onSubmit = onSubmitAddress,
                onBack = leaveForm,
            )
        }
        screen(
            route = CardVaultDestination.AddressDetail.route,
            arguments = listOf(
                navArgument(CardVaultDestination.RECORD_ID_ARGUMENT) {
                    type = NavType.StringType
                    nullable = false
                },
            ),
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getString(
                CardVaultDestination.RECORD_ID_ARGUMENT,
            )
            LaunchedEffect(recordId) { onLoadAddressDetail(recordId) }
            DisposableEffect(recordId) {
                onDispose { onClearAddressDetail() }
            }
            AddressDetailScreen(
                state = addressesUiState.detail,
                folders = addressesUiState.folders,
                currentFolderId = addressesUiState.addresses.firstOrNull { it.id.toString() == recordId }?.folderId,
                onMove = onMoveAddressToFolder,
                operationMessage = addressesUiState.operationMessage,
                onCopy = onCopyAddress,
                onEdit = { recordId ->
                    navController.navigate(CardVaultDestination.editAddressRoute(recordId)) {
                        launchSingleTop = true
                    }
                },
                onDelete = onDeleteAddress,
                onBack = navController::popBackStack,
            )
        }
        screen(
            route = CardVaultDestination.EditAddress.route,
            arguments = listOf(
                navArgument(CardVaultDestination.RECORD_ID_ARGUMENT) {
                    type = NavType.StringType
                    nullable = false
                },
            ),
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getString(
                CardVaultDestination.RECORD_ID_ARGUMENT,
            )
            LaunchedEffect(recordId) { onLoadAddressEdit(recordId) }
            DisposableEffect(recordId) {
                onDispose { onClearAddressEditDraft() }
            }
            EditAddressScreen(
                editState = addressesUiState.edit,
                onFieldChanged = onAddressEditFieldChanged,
                onTemplateSelected = onAddressEditTemplateSelected,
                onSubmit = onSubmitAddressEdit,
                onBack = navController::popBackStack,
            )
        }
    }

    // A file opened from WeChat / QQ / a file manager is handled once the vault is visible and
    // the first-run security notice has been acknowledged. If a transfer step is still running
    // the request stays pending and is retried on the next transfer state change.
    LaunchedEffect(inboundTransferRequest?.id, showSecurityNotice, vaultTransferUiState) {
        val request = inboundTransferRequest ?: return@LaunchedEffect
        if (showSecurityNotice) return@LaunchedEffect
        if (onInboundTransfer(request)) {
            navController.navigate(CardVaultDestination.VaultTransfer.route) {
                launchSingleTop = true
            }
        }
    }
}

private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(route = route, arguments = arguments, content = content)
}