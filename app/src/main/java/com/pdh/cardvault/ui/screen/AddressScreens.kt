package com.pdh.cardvault.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.pdh.cardvault.R
import com.pdh.cardvault.domain.validation.AddressValidationError
import com.pdh.cardvault.presentation.AddressDetailUiModel
import com.pdh.cardvault.presentation.AddressDetailUiState
import com.pdh.cardvault.presentation.AddressCopyPart
import com.pdh.cardvault.presentation.AddressEditUiState
import com.pdh.cardvault.presentation.AddressFormField
import com.pdh.cardvault.presentation.AddressFormUiState
import com.pdh.cardvault.presentation.AddressListItemUiModel
import com.pdh.cardvault.presentation.AddressOperationMessage
import com.pdh.cardvault.presentation.VaultFolderUiModel
import com.pdh.cardvault.ui.card.AddressWalletStack
import com.pdh.cardvault.ui.card.CardTemplatePicker
import com.pdh.cardvault.ui.card.CardTemplatePreview
import com.pdh.cardvault.ui.card.CardTemplateRegistry
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

@Composable
fun AddressListScreen(
    addresses: List<AddressListItemUiModel>,
    folders: List<VaultFolderUiModel>,
    folderOrder: List<UUID?>,
    sortingInProgress: Boolean,
    operationMessage: AddressOperationMessage,
    onBack: () -> Unit,
    onAddAddress: () -> Unit,
    onAddressSelected: (UUID) -> Unit,
    onAddressesReordered: (List<UUID>) -> Unit,
    onFoldersReordered: (List<UUID?>) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (UUID, String) -> Unit,
    onDeleteFolder: (UUID) -> Unit,
    onMoveAddressToFolder: (UUID, UUID?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFolderId by remember { mutableStateOf<UUID?>(null) }
    val folderTargets = remember { mutableMapOf<UUID?, Rect>() }
    LaunchedEffect(folders) {
        if (selectedFolderId != null && folders.none { it.id == selectedFolderId }) {
            selectedFolderId = null
        }
    }
    val visibleAddresses = addresses.filter { it.folderId == selectedFolderId }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item("address-header") {
                AddressHeader(addresses.size, onBack, onAddAddress)
            }
            if (operationMessage != AddressOperationMessage.None) {
                item("address-message") { AddressOperationNotice(operationMessage) }
            }
            item("address-folder-shelf") {
                VaultFolderShelf(
                    folders = folders,
                    unfiledCount = addresses.count { it.folderId == null },
                    folderOrder = folderOrder,
                    selectedFolderId = selectedFolderId,
                    onSelect = { selectedFolderId = it },
                    onCreate = onCreateFolder,
                    onRename = onRenameFolder,
                    onDelete = onDeleteFolder,
                    onReorder = onFoldersReordered,
                    onTargetBoundsChanged = { id, bounds -> folderTargets[id] = bounds },
                )
            }
            if (visibleAddresses.isEmpty()) {
                item("address-empty") { EmptyAddressState(onAddAddress) }
            } else {
                item("address-stack") {
                    AddressWalletStack(
                        addresses = visibleAddresses,
                        sortingInProgress = sortingInProgress,
                        openActionLabel = stringResource(R.string.action_open_address_detail),
                        moveUpActionLabel = stringResource(R.string.action_move_up),
                        moveDownActionLabel = stringResource(R.string.action_move_down),
                        onAddressOpened = onAddressSelected,
                        onAddressesReordered = { orderedVisibleIds ->
                            onAddressesReordered(mergeVisibleAddressOrder(addresses, orderedVisibleIds))
                        },
                        onAddressDropped = { addressId, position ->
                            val target = folderTargets.entries
                                .firstOrNull { (_, bounds) -> bounds.contains(position) }
                            val currentFolder = addresses.firstOrNull { it.id == addressId }?.folderId
                            if (target != null && target.key != currentFolder) {
                                onMoveAddressToFolder(addressId, target.key)
                                true
                            } else false
                        },
                    )
                }
            }
        }
    }
}

private fun mergeVisibleAddressOrder(
    allAddresses: List<AddressListItemUiModel>,
    orderedVisibleIds: List<UUID>,
): List<UUID> {
    val visible = orderedVisibleIds.iterator()
    val visibleSet = orderedVisibleIds.toSet()
    return allAddresses.map { address -> if (address.id in visibleSet) visible.next() else address.id }
}

@Composable
private fun AddressHeader(
    count: Int,
    onBack: () -> Unit,
    onAddAddress: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundBackButton(onBack)
            Column {
                Text(
                    text = stringResource(R.string.address_wallet_title),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                )
                if (count > 0) {
                    Text(
                        text = stringResource(R.string.address_wallet_count, count),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Surface(
            onClick = onAddAddress,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("＋", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
private fun EmptyAddressState(onAddAddress: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("⌖", style = MaterialTheme.typography.displaySmall)
            Text(
                text = stringResource(R.string.address_empty_heading),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.address_empty_detail),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAddAddress) {
                Text(stringResource(R.string.action_add_address))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAddressScreen(
    form: AddressFormUiState,
    onFieldChanged: (AddressFormField, String) -> Unit,
    onTemplateSelected: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AddressFormScreen(
        title = stringResource(R.string.address_add_title),
        submitLabel = stringResource(R.string.action_save_card),
        form = form,
        onFieldChanged = onFieldChanged,
        onTemplateSelected = onTemplateSelected,
        onSubmit = onSubmit,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAddressScreen(
    editState: AddressEditUiState,
    onFieldChanged: (AddressFormField, String) -> Unit,
    onTemplateSelected: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (editState) {
        is AddressEditUiState.Ready -> AddressFormScreen(
            title = stringResource(R.string.address_edit_title),
            submitLabel = stringResource(R.string.action_save_changes),
            form = editState.form,
            onFieldChanged = onFieldChanged,
            onTemplateSelected = onTemplateSelected,
            onSubmit = onSubmit,
            onBack = onBack,
            modifier = modifier,
        )
        AddressEditUiState.Hidden,
        AddressEditUiState.Loading,
        AddressEditUiState.Unavailable,
        -> Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.address_edit_title)) },
                    navigationIcon = { RoundBackButton(onBack) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
        ) { contentPadding ->
            Text(
                text = stringResource(
                    if (editState == AddressEditUiState.Unavailable) {
                        R.string.address_edit_unavailable
                    } else {
                        R.string.address_edit_loading
                    },
                ),
                modifier = Modifier.padding(contentPadding).padding(20.dp),
                color = if (editState == AddressEditUiState.Unavailable) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressFormScreen(
    title: String,
    submitLabel: String,
    form: AddressFormUiState,
    onFieldChanged: (AddressFormField, String) -> Unit,
    onTemplateSelected: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { RoundBackButton(onBack) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { contentPadding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
        ) {
            val layout = addressFormLayout(maxHeight.value)
            val previewVerticalPadding = layout.previewVerticalPaddingDp.dp
            val previewHeightLimit = layout.previewHeightDp.dp
            val previewWidthLimit = minOf(
                420.dp,
                previewHeightLimit * ADDRESS_CARD_ASPECT_RATIO,
            )

            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = previewVerticalPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CardTemplatePreview(
                        template = CardTemplateRegistry.findOrDefault(form.cardTemplateId),
                        nickname = form.nickname,
                        compactLayout = previewHeightLimit < 140.dp,
                        modifier = Modifier.widthIn(max = previewWidthLimit),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                AddressField(
                    value = form.nickname,
                    onValueChange = { onFieldChanged(AddressFormField.Nickname, it) },
                    label = stringResource(R.string.address_field_nickname),
                    error = addressFieldError(form, AddressValidationError.Nickname),
                )
                AddressField(
                    value = form.detailedAddress,
                    onValueChange = { onFieldChanged(AddressFormField.DetailedAddress, it) },
                    label = stringResource(R.string.address_field_detail),
                    error = addressFieldError(form, AddressValidationError.DetailedAddress),
                    minLines = 3,
                    maxLines = 5,
                )
                AddressField(
                    value = form.city,
                    onValueChange = { onFieldChanged(AddressFormField.City, it) },
                    label = stringResource(R.string.address_field_city),
                    error = addressFieldError(form, AddressValidationError.City),
                )
                AddressField(
                    value = form.other,
                    onValueChange = { onFieldChanged(AddressFormField.Other, it) },
                    label = stringResource(R.string.address_field_other),
                    error = addressFieldError(form, AddressValidationError.Other),
                    minLines = 2,
                    maxLines = 3,
                )
                AddressField(
                    value = form.postalCode,
                    onValueChange = { onFieldChanged(AddressFormField.PostalCode, it) },
                    label = stringResource(R.string.address_field_postal_code),
                    error = addressFieldError(form, AddressValidationError.PostalCode),
                )
                AddressField(
                    value = form.country,
                    onValueChange = { onFieldChanged(AddressFormField.Country, it) },
                    label = stringResource(R.string.address_field_country),
                    error = addressFieldError(form, AddressValidationError.Country),
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.card_template_heading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    CardTemplatePicker(
                        selectedTemplateId = form.cardTemplateId,
                        onTemplateSelected = onTemplateSelected,
                        nickname = form.nickname,
                        showPreview = false,
                    )
                    addressFieldError(form, AddressValidationError.Template)?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error)
                    }
                }
                Button(
                    onClick = onSubmit,
                    enabled = !form.submitting,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Text(
                        if (form.submitting) {
                            stringResource(R.string.action_saving)
                        } else {
                            submitLabel
                        },
                    )
                }
            }
        }
    }
}
}

@Composable
private fun AddressField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    minLines: Int = 1,
    maxLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        isError = error != null,
        supportingText = error?.let { message -> ({ Text(message) }) },
        minLines = minLines,
        maxLines = maxLines,
        singleLine = maxLines == 1,
    )
}

@Composable
private fun addressFieldError(
    form: AddressFormUiState,
    error: AddressValidationError,
): String? {
    if (error !in form.validationErrors) return null
    return stringResource(
        when (error) {
            AddressValidationError.Nickname -> R.string.address_error_nickname
            AddressValidationError.DetailedAddress -> R.string.address_error_detail
            AddressValidationError.City -> R.string.address_error_city
            AddressValidationError.Other -> R.string.address_error_other
            AddressValidationError.PostalCode -> R.string.address_error_postal_code
            AddressValidationError.Country -> R.string.address_error_country
            AddressValidationError.Template -> R.string.address_error_template
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressDetailScreen(
    state: AddressDetailUiState,
    operationMessage: AddressOperationMessage,
    onCopy: (UUID, AddressCopyPart) -> Unit,
    onEdit: (UUID) -> Unit,
    onDelete: (UUID) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleteCandidate by remember { mutableStateOf<UUID?>(null) }
    deleteCandidate?.let { recordId ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(stringResource(R.string.address_delete_confirmation_title)) },
            text = { Text(stringResource(R.string.address_delete_confirmation_detail)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteCandidate = null
                        onDelete(recordId)
                    },
                ) { Text(stringResource(R.string.action_delete_permanently)) }
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
                title = { Text(stringResource(R.string.address_detail_title)) },
                navigationIcon = { RoundBackButton(onBack) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state) {
                AddressDetailUiState.Hidden,
                AddressDetailUiState.Loading,
                -> Text(stringResource(R.string.address_detail_loading))
                AddressDetailUiState.NotFound -> Text(
                    stringResource(R.string.address_detail_not_found),
                    color = MaterialTheme.colorScheme.error,
                )
                is AddressDetailUiState.Loaded -> {
                    var showBack by remember(state.recordId) { mutableStateOf(false) }
                    LaunchedEffect(state.recordId) { showBack = true }
                    FlippableAddressDetail(
                        address = state.address,
                        showBack = showBack,
                        onCopy = { part -> onCopy(state.recordId, part) },
                        onShowBackChanged = { showBack = it },
                    )
                    AddressOperationNotice(operationMessage)
                    AnimatedVisibility(
                        visible = showBack,
                        enter = fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 120)),
                        exit = fadeOut(animationSpec = tween(durationMillis = 120)),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = { onEdit(state.recordId) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.address_edit))
                            }
                            OutlinedButton(
                                onClick = { deleteCandidate = state.recordId },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = stringResource(R.string.address_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlippableAddressDetail(
    address: AddressDetailUiModel,
    showBack: Boolean,
    onCopy: (AddressCopyPart) -> Unit,
    onShowBackChanged: (Boolean) -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (showBack) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 320f),
        label = "address-detail-flip",
    )
    val density = LocalDensity.current.density
    val cardShape = MaterialTheme.shapes.extraLarge
    val template = CardTemplateRegistry.findOrDefault(address.cardTemplateId)
    val flipScale = 0.96f + 0.04f * abs(cos(rotation / 180f * PI)).toFloat()
    val toggleLabel = stringResource(
        if (showBack) R.string.card_flip_to_front else R.string.card_flip_to_back,
    )
    val toggle = { onShowBackChanged(!showBack) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ADDRESS_CARD_ASPECT_RATIO)
            .graphicsLayer {
                rotationY = rotation
                scaleX = flipScale
                scaleY = flipScale
                cameraDistance = 28f * density
                shadowElevation = (12f + 8f * flipScale).dp.toPx()
                transformOrigin = TransformOrigin.Center
                shape = cardShape
                clip = false
            }
            .clickable(onClick = toggle)
            .semantics {
                contentDescription = toggleLabel
                onClick(label = toggleLabel) {
                    toggle()
                    true
                }
            },
    ) {
        if (rotation <= 90f) {
            CardTemplatePreview(
                template = template,
                nickname = address.nickname,
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 0.dp,
            )
        } else {
            AddressBackCard(
                address = address,
                onCopy = onCopy,
                modifier = Modifier.graphicsLayer { rotationY = 180f },
            )
        }
    }
}

@Composable
private fun AddressBackCard(
    address: AddressDetailUiModel,
    onCopy: (AddressCopyPart) -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = CardTemplateRegistry.findOrDefault(address.cardTemplateId)
    val textColor = template.nicknameColor.color ?: template.foreground
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(listOf(template.gradientStart, template.gradientEnd)),
            )
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Surface(
            onClick = { onCopy(AddressCopyPart.Complete) },
            modifier = Modifier.align(Alignment.TopEnd),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.18f),
            contentColor = textColor,
        ) {
            Text(
                text = stringResource(R.string.address_copy_all),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            AddressCopyLine(
                text = address.detailedAddress,
                copyLabel = stringResource(R.string.address_copy_detail),
                textColor = textColor,
                emphasized = true,
                maxLines = 3,
                onClick = { onCopy(AddressCopyPart.DetailedAddress) },
            )
            address.other.takeIf(String::isNotBlank)?.let { other ->
                AddressCopyLine(
                    text = other,
                    copyLabel = stringResource(R.string.address_copy_other),
                    textColor = textColor,
                    maxLines = 2,
                    onClick = { onCopy(AddressCopyPart.Other) },
                )
            }
            AddressCopyLine(
                text = address.city,
                copyLabel = stringResource(R.string.address_copy_city),
                textColor = textColor,
                onClick = { onCopy(AddressCopyPart.City) },
            )
            AddressCopyLine(
                text = address.postalCode,
                copyLabel = stringResource(R.string.address_copy_postal_code),
                textColor = textColor,
                onClick = { onCopy(AddressCopyPart.PostalCode) },
            )
            address.country.takeIf(String::isNotBlank)?.let { country ->
                AddressCopyLine(
                    text = country,
                    copyLabel = stringResource(R.string.address_copy_country),
                    textColor = textColor,
                    onClick = { onCopy(AddressCopyPart.Country) },
                )
            }
        }
    }
}

@Composable
private fun AddressCopyLine(
    text: String,
    copyLabel: String,
    textColor: Color,
    onClick: () -> Unit,
    emphasized: Boolean = false,
    maxLines: Int = 1,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = copyLabel
                onClick(label = copyLabel) {
                    onClick()
                    true
                }
            }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = textColor,
            style = if (emphasized) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "⧉",
            color = textColor.copy(alpha = 0.58f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun AddressOperationNotice(message: AddressOperationMessage) {
    val text = when (message) {
        AddressOperationMessage.None,
        AddressOperationMessage.RecordDeleted,
        -> return
        AddressOperationMessage.StorageFailed -> stringResource(R.string.address_storage_failed)
        AddressOperationMessage.AddressCopied -> stringResource(R.string.address_copied)
        AddressOperationMessage.ClipboardFailed -> stringResource(R.string.address_clipboard_failed)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            color = if (message == AddressOperationMessage.StorageFailed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun RoundBackButton(onBack: () -> Unit) {
    val label = stringResource(R.string.action_back)
    Surface(
        onClick = onBack,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(40.dp)
            .clearAndSetSemantics {
                contentDescription = label
                onClick(label = label) {
                    onBack()
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
}

private const val ADDRESS_CARD_ASPECT_RATIO = 1.586f

internal data class AddressFormLayout(
    val previewVerticalPaddingDp: Float,
    val previewHeightDp: Float,
)

internal fun addressFormLayout(maxHeightDp: Float): AddressFormLayout {
    val safeHeight = maxHeightDp.coerceAtLeast(0f)
    val verticalPadding = if (safeHeight < 360f) 4f else 10f
    val reservedEditorHeight = minOf(180f, safeHeight * 0.55f)
    val previewHeight = (safeHeight - reservedEditorHeight - verticalPadding * 2f)
        .coerceIn(0f, 240f)
    return AddressFormLayout(verticalPadding, previewHeight)
}
