package com.pdh.cardvault

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.pdh.cardvault.data.room.CardVaultPersistentGraph
import com.pdh.cardvault.navigation.CardVaultNavHost
import com.pdh.cardvault.presentation.CardsViewModel
import com.pdh.cardvault.presentation.AddressViewModel
import com.pdh.cardvault.presentation.VaultContentState
import com.pdh.cardvault.presentation.MainViewModel
import com.pdh.cardvault.presentation.VaultLockState
import com.pdh.cardvault.presentation.VaultTransferViewModel
import com.pdh.cardvault.security.auth.AndroidDeviceSecurityChecker
import com.pdh.cardvault.security.auth.AuthenticationAction
import com.pdh.cardvault.security.auth.AuthenticationOutcome
import com.pdh.cardvault.security.auth.AuthenticationScope
import com.pdh.cardvault.security.auth.SystemBiometricPrompt
import com.pdh.cardvault.security.clipboard.AndroidSensitiveClipboardController
import com.pdh.cardvault.ui.card.CardTemplateRegistry
import com.pdh.cardvault.ui.screen.LockedScreen
import com.pdh.cardvault.ui.screen.SecurityUnavailableScreen
import com.pdh.cardvault.ui.screen.VaultUnavailableScreen
import com.pdh.cardvault.ui.theme.CardVaultTheme
import com.pdh.cardvault.sync.AndroidSyncFileExchange

internal fun shouldPreserveRecordAuthenticationOnContentDisposal(
    canRenderMaskedContent: Boolean,
    vaultLockState: VaultLockState,
): Boolean =
    !canRenderMaskedContent &&
        (vaultLockState as? VaultLockState.Authenticating)
            ?.scope
            ?.action
            ?.requiresRecordId == true

private object ProcessPersistentGraphHolder {
    @Volatile
    private var graph: CardVaultPersistentGraph? = null

    fun get(context: android.content.Context): CardVaultPersistentGraph =
        graph ?: synchronized(this) {
            graph ?: CardVaultPersistentGraph(
                context = context.applicationContext,
                templateIdLookup = { templateId ->
                    CardTemplateRegistry.findById(templateId) != null
                },
            ).also { created -> graph = created }
        }
}

@Composable
fun CardVaultApp(
    activityRecreated: Boolean,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vaultTransferShareChooserTitle = stringResource(
        R.string.vault_transfer_share_chooser_title,
    )
    val applicationContext = context.applicationContext
    val activity = context as? MainActivity
        ?: error("CardVault requires its MainActivity host.")
    val deviceSecurityChecker = remember(applicationContext) {
        AndroidDeviceSecurityChecker(applicationContext)
    }
    val persistentGraph = remember(applicationContext) {
        ProcessPersistentGraphHolder.get(applicationContext)
    }
    val sensitiveClipboardController = remember(applicationContext) {
        AndroidSensitiveClipboardController(applicationContext)
    }
    val syncFileExchange = remember(applicationContext) {
        AndroidSyncFileExchange(applicationContext)
    }
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val biometricPrompt = remember(activity, mainViewModel) {
        SystemBiometricPrompt(activity, mainViewModel::onAuthenticationResult)
    }
    val cardsFactory = remember(
        persistentGraph,
        deviceSecurityChecker,
        sensitiveClipboardController,
    ) {
        CardsViewModel.Factory(
            repository = persistentGraph.repository,
            validator = persistentGraph.validator,
            defaultTemplateId = CardTemplateRegistry.DEFAULT_TEMPLATE_ID,
            templateIssuerLabel = CardTemplateRegistry::issuerLabelFor,
            deviceSecurityChecker = deviceSecurityChecker,
            sensitiveClipboardController = sensitiveClipboardController,
        )
    }
    val cardsViewModel: CardsViewModel = viewModel(factory = cardsFactory)
    val cardsUiState by cardsViewModel.uiState.collectAsStateWithLifecycle()
    val addressFactory = remember(
        persistentGraph,
        sensitiveClipboardController,
    ) {
        AddressViewModel.Factory(
            repository = persistentGraph.repository,
            validator = persistentGraph.addressValidator,
            defaultTemplateId = CardTemplateRegistry.DEFAULT_TEMPLATE_ID,
            sensitiveClipboardController = sensitiveClipboardController,
        )
    }
    val addressViewModel: AddressViewModel = viewModel(factory = addressFactory)
    val addressesUiState by addressViewModel.uiState.collectAsStateWithLifecycle()
    val transferFactory = remember(persistentGraph, syncFileExchange) {
        VaultTransferViewModel.Factory(
            coordinator = persistentGraph.syncCoordinator,
            fileExchange = syncFileExchange,
        )
    }
    val vaultTransferViewModel: VaultTransferViewModel = viewModel(factory = transferFactory)
    val vaultTransferUiState by vaultTransferViewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()

    LaunchedEffect(mainViewModel, activityRecreated) {
        mainViewModel.onUiReady(activityRecreated)
    }

    LaunchedEffect(uiState.authenticationRequest?.requestId) {
        uiState.authenticationRequest?.let(biometricPrompt::authenticate)
    }

    DisposableEffect(activity, uiState.screenCaptureAllowed) {
        activity.setAuthenticatedScreenCaptureAllowed(uiState.screenCaptureAllowed)
        onDispose {
            activity.setAuthenticatedScreenCaptureAllowed(false)
        }
    }

    LaunchedEffect(uiState.canRenderMaskedContent, uiState.vaultLockState) {
        if (uiState.canRenderMaskedContent) {
            cardsViewModel.onVaultAccessAllowed(mainViewModel::onVaultAccessFailed)
            if (cardsUiState.vaultContentState == VaultContentState.Ready) {
                vaultTransferViewModel.onVaultAccessAllowed()
            }
        } else {
            val preservePendingAuthentication =
                (uiState.vaultLockState as? VaultLockState.Authenticating)
                    ?.scope
                    ?.action
                    ?.requiresRecordId == true
            cardsViewModel.onVaultAccessRevoked(preservePendingAuthentication)
            addressViewModel.onVaultAccessRevoked()
            val preserveVaultTransferAuthentication =
                (uiState.vaultLockState as? VaultLockState.Authenticating)
                    ?.scope
                    ?.action
                    ?.isVaultTransferAction == true
            vaultTransferViewModel.onAppBackgrounded(
                preservePendingAuthentication = preserveVaultTransferAuthentication,
            )
        }
    }

    LaunchedEffect(
        uiState.canRenderMaskedContent,
        cardsUiState.vaultContentState,
    ) {
        if (
            uiState.canRenderMaskedContent &&
            cardsUiState.vaultContentState == VaultContentState.Ready
        ) {
            addressViewModel.onVaultAccessAllowed()
            vaultTransferViewModel.onVaultAccessAllowed()
        }
    }

    LaunchedEffect(
        vaultTransferUiState.authenticationRequest?.requestId,
        uiState.canRenderMaskedContent,
        cardsUiState.vaultContentState,
    ) {
        val request = vaultTransferUiState.authenticationRequest ?: return@LaunchedEffect
        if (
            !uiState.canRenderMaskedContent ||
            cardsUiState.vaultContentState != VaultContentState.Ready
        ) {
            return@LaunchedEffect
        }
        val action = vaultTransferViewModel.consumeAuthenticationRequest(request.requestId)
            ?: return@LaunchedEffect
        if (!mainViewModel.requestVaultTransferAuthentication(action)) {
            vaultTransferViewModel.onAuthenticationDispatchRejected(action)
        }
    }

    LaunchedEffect(vaultTransferUiState.importedSnapshotRevision) {
        if (vaultTransferUiState.importedSnapshotRevision > 0L) {
            cardsViewModel.refreshAfterExternalImport()
            addressViewModel.refreshAfterExternalImport()
        }
    }

    LaunchedEffect(uiState.authenticationOutcome, cardsUiState.vaultContentState) {
        when (uiState.authenticationOutcome) {
            AuthenticationOutcome.Succeeded -> {
                val transferAction = vaultTransferViewModel.pendingAuthenticationAction()
                if (transferAction != null) {
                    if (cardsUiState.vaultContentState != VaultContentState.Ready) {
                        return@LaunchedEffect
                    }
                    if (mainViewModel.consumeVaultTransferAuthorization(transferAction)) {
                        vaultTransferViewModel.onAuthorizationGranted(transferAction)
                    } else {
                        vaultTransferViewModel.onAuthenticationFailed()
                    }
                    return@LaunchedEffect
                }
                val scope = cardsViewModel.pendingAuthenticationScope() ?: return@LaunchedEffect
                val recordId = requireNotNull(scope.recordId)
                if (
                    mainViewModel.consumeOneTimeAuthorization(scope.action, recordId)
                ) {
                    cardsViewModel.onAuthorizationGranted(scope)
                } else {
                    cardsViewModel.onAuthenticationFailed()
                }
            }

            AuthenticationOutcome.FailedOrCancelled -> {
                cardsViewModel.onAuthenticationFailed()
                vaultTransferViewModel.onAuthenticationFailed()
            }
            AuthenticationOutcome.None -> Unit
        }
    }

    DisposableEffect(
        lifecycleOwner,
        mainViewModel,
        cardsViewModel,
        addressViewModel,
        vaultTransferViewModel,
    ) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val pendingSystemRequest =
                        mainViewModel.uiState.value.authenticationRequest?.scope
                    val preservePendingAuthentication =
                        pendingSystemRequest?.action?.let { action ->
                            action.requiresRecordId || action.isVaultTransferAction
                        } == true
                    cardsViewModel.onAppBackgrounded(
                        preservePendingAuthentication =
                            pendingSystemRequest?.action?.requiresRecordId == true,
                    )
                    addressViewModel.onAppBackgrounded()
                    vaultTransferViewModel.onAppBackgrounded(preservePendingAuthentication)
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(biometricPrompt) {
        onDispose(biometricPrompt::cancel)
    }

    val showSecurityNoticeAtLaunch = remember { uiState.showSecurityNotice }
    val openSecuritySettings = remember(context) {
        {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            }
            Unit
        }
    }
    val requestCardAuthentication = remember(mainViewModel, cardsViewModel) {
        { action: AuthenticationAction, recordId: java.util.UUID ->
            val scope = AuthenticationScope(action, recordId)
            if (cardsViewModel.prepareAuthentication(action, recordId)) {
                if (!mainViewModel.requestOneTimeAuthentication(action, recordId)) {
                    cardsViewModel.cancelPreparedAuthentication(scope)
                }
            }
        }
    }

    CardVaultTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            when (val lockState = uiState.vaultLockState) {
                is VaultLockState.SecurityUnavailable -> SecurityUnavailableScreen(
                    status = lockState.status,
                    onOpenSecuritySettings = openSecuritySettings,
                    onRetry = mainViewModel::retryDeviceSecurityCheck,
                )

                VaultLockState.VaultUnavailable -> VaultUnavailableScreen(
                    onRetry = mainViewModel::retryVaultAccess,
                )

                else -> if (
                    uiState.canRenderMaskedContent &&
                    cardsUiState.vaultContentState == VaultContentState.Ready &&
                    addressesUiState.vaultContentState == VaultContentState.Ready
                ) {
                    CardVaultNavHost(
                        navController = navController,
                        showSecurityNoticeAtLaunch = showSecurityNoticeAtLaunch,
                        onSecurityNoticeAcknowledged = mainViewModel::acknowledgeSecurityNotice,
                        startupAuthenticationEnabled = uiState.startupAuthenticationEnabled,
                        authenticationInProgress =
                            lockState is VaultLockState.Authenticating,
                        authenticationOutcome = uiState.authenticationOutcome,
                        onStartupAuthenticationChanged =
                            mainViewModel::requestStartupAuthenticationChange,
                        vaultTransferUiState = vaultTransferUiState,
                        onPrepareVaultExport = vaultTransferViewModel::requestExport,
                        onPrepareNewDevicePairing =
                            vaultTransferViewModel::requestNewDevicePairing,
                        onRotateVaultSyncKey =
                            vaultTransferViewModel::requestSyncKeyRotation,
                        onSharePreparedVaultExport = {
                            val shareIntent = vaultTransferViewModel.preparedShareIntent()
                            if (shareIntent != null) {
                                runCatching {
                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            vaultTransferShareChooserTitle,
                                        ),
                                    )
                                }.onFailure {
                                    vaultTransferViewModel.onShareDispatchFailed()
                                }
                            } else {
                                vaultTransferViewModel.onShareDispatchFailed()
                            }
                        },
                        onVaultImportUriSelected = vaultTransferViewModel::onImportUriSelected,
                        onVaultPairingCodeChanged = vaultTransferViewModel::updatePairingCode,
                        onConfirmVaultPairingImport =
                            vaultTransferViewModel::confirmPairingImport,
                        cardsUiState = cardsUiState,
                        addressesUiState = addressesUiState,
                        onAddressFormFieldChanged = addressViewModel::updateField,
                        onAddressTemplateSelected = addressViewModel::selectTemplate,
                        onSubmitAddress = { addressViewModel.submitAddress() },
                        onClearAddressForm = addressViewModel::clearForm,
                        onLoadAddressDetail = addressViewModel::loadDetail,
                        onClearAddressDetail = addressViewModel::clearDetail,
                        onCopyAddress = { id -> addressViewModel.copyAddress(id) },
                        onDeleteAddress = { id -> addressViewModel.deleteAddress(id) },
                        onAddressesReordered = addressViewModel::reorderAddresses,
                        onLoadAddressEdit = addressViewModel::loadEdit,
                        onAddressEditFieldChanged = addressViewModel::updateEditField,
                        onAddressEditTemplateSelected = addressViewModel::selectEditTemplate,
                        onSubmitAddressEdit = { addressViewModel.submitEdit() },
                        onClearAddressEditDraft = addressViewModel::clearEditDraft,
                        onAddressNavigationEventHandled =
                            addressViewModel::onNavigationEventHandled,
                        onCardFormFieldChanged = cardsViewModel::updateField,
                        onConfirmSaveCvvRisk = cardsViewModel::confirmSaveCvvRisk,
                        onDismissSaveCvvRisk = cardsViewModel::dismissSaveCvvRisk,
                        onTemplateSelected = cardsViewModel::selectTemplate,
                        onSubmitCard = { cardsViewModel.submitCard() },
                        onClearCardForm = cardsViewModel::clearForm,
                        onLoadCardDetail = cardsViewModel::loadCardDetail,
                        onClearCardDetail = {
                            val preservePendingAuthentication =
                                mainViewModel.uiState.value.let { current ->
                                    shouldPreserveRecordAuthenticationOnContentDisposal(
                                        canRenderMaskedContent = current.canRenderMaskedContent,
                                        vaultLockState = current.vaultLockState,
                                    )
                                }
                            cardsViewModel.clearCardDetail(preservePendingAuthentication)
                        },
                        onCardsReordered = { orderedIds ->
                            cardsViewModel.reorderCards(orderedIds)
                        },
                        onRequestCardAuthentication = requestCardAuthentication,
                        onHideCardSecrets = cardsViewModel::hideRevealedCardSecrets,
                        onCopyRevealedCardNumber = { id ->
                            cardsViewModel.copyRevealedCardNumber(id)
                        },
                        onEditFormFieldChanged = cardsViewModel::updateEditField,
                        onConfirmEditSaveCvvRisk = cardsViewModel::confirmEditSaveCvvRisk,
                        onDismissEditSaveCvvRisk = cardsViewModel::dismissEditSaveCvvRisk,
                        onEditTemplateSelected = cardsViewModel::selectEditTemplate,
                        onSubmitEdit = { cardsViewModel.submitEdit() },
                        onClearEditDraft = cardsViewModel::clearEditDraft,
                        onNavigationEventHandled = cardsViewModel::onNavigationEventHandled,
                    )
                } else {
                    LockedScreen(
                        authenticating = lockState is VaultLockState.Authenticating ||
                            lockState == VaultLockState.Initializing ||
                            cardsUiState.vaultContentState == VaultContentState.Loading ||
                            addressesUiState.vaultContentState == VaultContentState.Loading,
                        authenticationFailed =
                            uiState.authenticationOutcome == AuthenticationOutcome.FailedOrCancelled,
                        onUnlock = mainViewModel::requestUnlock,
                    )
                }
            }
        }
    }
}
