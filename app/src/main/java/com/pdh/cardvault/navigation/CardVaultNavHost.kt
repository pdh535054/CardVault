package com.pdh.cardvault.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.pdh.cardvault.presentation.CardFormField
import com.pdh.cardvault.presentation.AddressFormField
import com.pdh.cardvault.presentation.AddressNavigationEvent
import com.pdh.cardvault.presentation.AddressesUiState
import com.pdh.cardvault.presentation.CardEditUiState
import com.pdh.cardvault.presentation.CardNavigationEvent
import com.pdh.cardvault.presentation.CardsUiState
import com.pdh.cardvault.presentation.VaultTransferUiState
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
fun CardVaultNavHost(
    navController: NavHostController,
    showSecurityNoticeAtLaunch: Boolean,
    onSecurityNoticeAcknowledged: () -> Unit,
    startupAuthenticationEnabled: Boolean,
    authenticationInProgress: Boolean,
    authenticationOutcome: AuthenticationOutcome,
    onStartupAuthenticationChanged: (Boolean) -> Unit,
    vaultTransferUiState: VaultTransferUiState,
    onPrepareVaultExport: () -> Unit,
    onPrepareNewDevicePairing: () -> Unit,
    onRotateVaultSyncKey: () -> Unit,
    onSharePreparedVaultExport: () -> Unit,
    onVaultImportUriSelected: (String) -> Unit,
    onVaultPairingCodeChanged: (String) -> Unit,
    onConfirmVaultPairingImport: () -> Unit,
    cardsUiState: CardsUiState,
    addressesUiState: AddressesUiState,
    onAddressFormFieldChanged: (AddressFormField, String) -> Unit,
    onAddressTemplateSelected: (String) -> Unit,
    onSubmitAddress: () -> Unit,
    onClearAddressForm: () -> Unit,
    onLoadAddressDetail: (String?) -> Unit,
    onClearAddressDetail: () -> Unit,
    onCopyAddress: (UUID) -> Unit,
    onDeleteAddress: (UUID) -> Unit,
    onAddressesReordered: (List<UUID>) -> Unit,
    onLoadAddressEdit: (String?) -> Unit,
    onAddressEditFieldChanged: (AddressFormField, String) -> Unit,
    onAddressEditTemplateSelected: (String) -> Unit,
    onSubmitAddressEdit: () -> Unit,
    onClearAddressEditDraft: () -> Unit,
    onAddressNavigationEventHandled: (AddressNavigationEvent) -> Unit,
    onCardFormFieldChanged: (CardFormField, String) -> Unit,
    onConfirmSaveCvvRisk: () -> Unit,
    onDismissSaveCvvRisk: () -> Unit,
    onTemplateSelected: (String) -> Unit,
    onSubmitCard: () -> Unit,
    onClearCardForm: () -> Unit,
    onLoadCardDetail: (String?) -> Unit,
    onClearCardDetail: () -> Unit,
    onCardsReordered: (List<UUID>) -> Unit,
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
            fadeIn(animationSpec = tween(durationMillis = 190)) +
                slideInHorizontally(
                    animationSpec = tween(durationMillis = 240),
                    initialOffsetX = { fullWidth -> fullWidth / 12 },
                )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(durationMillis = 130)) +
                scaleOut(
                    animationSpec = tween(durationMillis = 180),
                    targetScale = 0.985f,
                )
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(durationMillis = 180)) +
                scaleIn(
                    animationSpec = tween(durationMillis = 210),
                    initialScale = 0.985f,
                )
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(durationMillis = 150)) +
                slideOutHorizontally(
                    animationSpec = tween(durationMillis = 220),
                    targetOffsetX = { fullWidth -> fullWidth / 14 },
                )
        },
    ) {
        composable(CardVaultDestination.SecurityNotice.route) {
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
        composable(CardVaultDestination.Home.route) {
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
            )
        }
        composable(CardVaultDestination.Cards.route) {
            CardListScreen(
                cards = cardsUiState.cards,
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
                onCardsReordered = onCardsReordered,
            )
        }
        composable(CardVaultDestination.AddCard.route) {
            val leaveForm = {
                onClearCardForm()
                navController.popBackStack()
                Unit
            }
            AddCardScreen(
                form = cardsUiState.form,
                onFieldChanged = onCardFormFieldChanged,
                onConfirmSaveCvvRisk = onConfirmSaveCvvRisk,
                onDismissSaveCvvRisk = onDismissSaveCvvRisk,
                onTemplateSelected = onTemplateSelected,
                onSubmit = {
                    onSubmitCard()
                },
                onBack = leaveForm,
            )
        }
        composable(
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
                onDeleteConfirmed = { id ->
                    onRequestCardAuthentication(AuthenticationAction.DeleteCard, id)
                },
                onBack = navController::popBackStack,
            )
        }
        composable(
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
                )
            } else {
                EditCardStatusScreen(
                    loading = cardsUiState.edit == CardEditUiState.Loading,
                    onBack = navController::popBackStack,
                )
            }
        }
        composable(CardVaultDestination.Settings.route) {
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
        composable(CardVaultDestination.VaultTransfer.route) {
            VaultTransferScreen(
                state = vaultTransferUiState,
                onBack = navController::popBackStack,
                onPrepareExport = onPrepareVaultExport,
                onPrepareNewDevicePairing = onPrepareNewDevicePairing,
                onRotateSyncKey = onRotateVaultSyncKey,
                onSharePreparedExport = onSharePreparedVaultExport,
                onImportUriSelected = onVaultImportUriSelected,
                onPairingCodeChanged = onVaultPairingCodeChanged,
                onConfirmPairingImport = onConfirmVaultPairingImport,
            )
        }
        composable(CardVaultDestination.AboutPrivacy.route) {
            AboutPrivacyScreen(onBack = navController::popBackStack)
        }
        composable(CardVaultDestination.TemplateGallery.route) {
            TemplateGalleryScreen(onBack = navController::popBackStack)
        }
        composable(CardVaultDestination.Addresses.route) {
            AddressListScreen(
                addresses = addressesUiState.addresses,
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
            )
        }
        composable(CardVaultDestination.AddAddress.route) {
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
        composable(
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
        composable(
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
}
