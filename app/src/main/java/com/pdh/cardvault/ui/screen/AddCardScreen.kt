@file:Suppress("DEPRECATION")

package com.pdh.cardvault.ui.screen

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.pdh.cardvault.R
import com.pdh.cardvault.domain.validation.BankCardValidationError
import com.pdh.cardvault.domain.validation.ExpiryDateTools
import com.pdh.cardvault.presentation.CardFormField
import com.pdh.cardvault.presentation.CardFormUiState
import com.pdh.cardvault.ui.card.CardTemplatePicker
import com.pdh.cardvault.ui.card.CardTemplatePreview
import com.pdh.cardvault.ui.card.CardTemplateRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCardScreen(
    form: CardFormUiState,
    onFieldChanged: (CardFormField, String) -> Unit,
    onConfirmSaveCvvRisk: () -> Unit,
    onDismissSaveCvvRisk: () -> Unit,
    onTemplateSelected: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = BankCardFormScreen(
    title = stringResource(R.string.add_card_title),
    saveButtonLabel = stringResource(R.string.action_save_card),
    form = form,
    onFieldChanged = onFieldChanged,
    onConfirmSaveCvvRisk = onConfirmSaveCvvRisk,
    onDismissSaveCvvRisk = onDismissSaveCvvRisk,
    onTemplateSelected = onTemplateSelected,
    onSubmit = onSubmit,
    onBack = onBack,
    modifier = modifier,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCardScreen(
    form: CardFormUiState,
    onFieldChanged: (CardFormField, String) -> Unit,
    onConfirmSaveCvvRisk: () -> Unit,
    onDismissSaveCvvRisk: () -> Unit,
    onTemplateSelected: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = BankCardFormScreen(
    title = stringResource(R.string.edit_card_title),
    saveButtonLabel = stringResource(R.string.action_save_changes),
    form = form,
    onFieldChanged = onFieldChanged,
    onConfirmSaveCvvRisk = onConfirmSaveCvvRisk,
    onDismissSaveCvvRisk = onDismissSaveCvvRisk,
    onTemplateSelected = onTemplateSelected,
    onSubmit = onSubmit,
    onBack = onBack,
    modifier = modifier,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankCardFormScreen(
    title: String,
    saveButtonLabel: String,
    form: CardFormUiState,
    onFieldChanged: (CardFormField, String) -> Unit,
    onConfirmSaveCvvRisk: () -> Unit,
    onDismissSaveCvvRisk: () -> Unit,
    onTemplateSelected: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler {
        if (!form.submitting) onBack()
    }
    DisableAutofillForCurrentComposeView()

    if (form.showCvvRiskConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissSaveCvvRisk,
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(stringResource(R.string.card_cvv_risk_title)) },
            text = { Text(stringResource(R.string.card_cvv_risk_detail)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirmSaveCvvRisk()
                        onSubmit()
                    },
                ) {
                    Text(stringResource(R.string.card_cvv_risk_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissSaveCvvRisk) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    val errors = form.validationErrors
    val nicknameError = when {
        BankCardValidationError.NICKNAME_REQUIRED in errors ->
            stringResource(R.string.card_error_nickname_required)
        BankCardValidationError.NICKNAME_TOO_LONG in errors ->
            stringResource(R.string.card_error_nickname_length)
        else -> null
    }
    val cardNumberError = when {
        BankCardValidationError.CARD_NUMBER_ILLEGAL_CHARACTER in errors ->
            stringResource(R.string.card_error_number_characters)
        BankCardValidationError.CARD_NUMBER_LENGTH in errors ->
            stringResource(R.string.card_error_number_length)
        else -> null
    }
    val expiryMonthError = if (BankCardValidationError.EXPIRY_MONTH_OUT_OF_RANGE in errors) {
        stringResource(R.string.card_error_expiry_month)
    } else {
        null
    }
    val expiryYearError = if (
        BankCardValidationError.EXPIRY_YEAR_OUT_OF_SUPPORTED_RANGE in errors
    ) {
        stringResource(R.string.card_error_expiry_year)
    } else {
        null
    }
    val cvvError = when {
        BankCardValidationError.CVV_REQUIRED_WHEN_ENABLED in errors ->
            stringResource(R.string.card_error_cvv_required)
        BankCardValidationError.CVV_FORMAT in errors ->
            stringResource(R.string.card_error_cvv_format)
        else -> null
    }
    val templateError = if (BankCardValidationError.UNKNOWN_TEMPLATE_ID in errors) {
        stringResource(R.string.card_error_template)
    } else {
        null
    }
    val notesError = if (BankCardValidationError.NOTES_TOO_LONG in errors) {
        stringResource(R.string.card_error_notes_length)
    } else {
        null
    }
    val cvvFocusRequester = remember { FocusRequester() }

    ClipboardBlocked {
        Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    navigationIcon = {
                        TextButton(onClick = onBack, enabled = !form.submitting) {
                            Text(stringResource(R.string.action_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Button(
                            onClick = onSubmit,
                            enabled = !form.submitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                        ) {
                            Text(
                                if (form.submitting) {
                                    stringResource(R.string.action_saving)
                                } else {
                                    saveButtonLabel
                                },
                            )
                        }
                    }
                }
            },
        ) { contentPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                val previewVerticalPadding = if (maxHeight < 360.dp) 4.dp else 10.dp
                val reservedEditorHeight = minOf(180.dp, maxHeight * 0.55f)
                val previewHeightLimit = (
                    maxHeight - reservedEditorHeight - previewVerticalPadding * 2
                    ).coerceAtLeast(0.dp).coerceAtMost(240.dp)
                val previewWidthLimit = minOf(420.dp, previewHeightLimit * 1.586f)

                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 20.dp,
                                vertical = previewVerticalPadding,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        CardTemplatePreview(
                            template = CardTemplateRegistry.findOrDefault(form.cardTemplateId),
                            nickname = form.nickname,
                            cardNetwork = form.cardNetwork,
                            compactLayout = previewHeightLimit < 140.dp,
                            modifier = Modifier.widthIn(max = previewWidthLimit),
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                OutlinedTextField(
                    value = form.nickname,
                    onValueChange = { value -> onFieldChanged(CardFormField.Nickname, value) },
                    label = { Text(stringResource(R.string.card_field_nickname)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .redactValueFromSemantics(
                            stringResource(R.string.card_field_nickname),
                        ),
                    singleLine = true,
                    isError = nicknameError != null,
                    supportingText = nicknameError.asSupportingText(),
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(
                    value = form.cardNumber,
                    onValueChange = { value -> onFieldChanged(CardFormField.CardNumber, value) },
                    label = { Text(stringResource(R.string.card_field_number)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .redactValueFromSemantics(
                            stringResource(R.string.card_field_number),
                        ),
                    singleLine = true,
                    isError = cardNumberError != null,
                    supportingText = cardNumberError.asSupportingText(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    visualTransformation = GroupedCardNumberVisualTransformation,
                    shape = RoundedCornerShape(16.dp),
                )
                if (form.showLuhnWarning) {
                    Text(
                        text = stringResource(R.string.card_warning_luhn),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }

                val expiryError = expiryMonthError ?: expiryYearError
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = form.expiryDigits,
                        onValueChange = { value ->
                            val normalized = ExpiryDateTools.normalizeInputDigits(value)
                            val justCompleted = shouldAdvanceExpiryFocusToCvv(
                                previousDigits = form.expiryDigits,
                                normalizedDigits = normalized,
                            )
                            onFieldChanged(CardFormField.ExpiryDate, normalized)
                            if (justCompleted) cvvFocusRequester.requestFocus()
                        },
                        label = { Text(stringResource(R.string.card_field_expiry_date)) },
                        placeholder = {
                            Text(stringResource(R.string.card_field_expiry_placeholder))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .redactValueFromSemantics(
                                stringResource(R.string.card_field_expiry_date),
                            ),
                        singleLine = true,
                        isError = expiryError != null,
                        supportingText = expiryError.asSupportingText(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { cvvFocusRequester.requestFocus() },
                        ),
                        visualTransformation = ExpiryDateVisualTransformation,
                        shape = RoundedCornerShape(16.dp),
                    )
                    OutlinedTextField(
                        value = form.cvv,
                        onValueChange = { value -> onFieldChanged(CardFormField.Cvv, value) },
                        label = { Text(stringResource(R.string.card_field_cvv)) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(cvvFocusRequester)
                            .redactValueFromSemantics(
                                stringResource(R.string.card_field_cvv),
                            ),
                        singleLine = true,
                        isError = cvvError != null,
                        supportingText = cvvError.asSupportingText(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(16.dp),
                    )
                }
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { value -> onFieldChanged(CardFormField.Notes, value) },
                    label = { Text(stringResource(R.string.card_field_notes_optional)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .redactValueFromSemantics(
                            stringResource(R.string.card_field_notes_optional),
                        ),
                    minLines = 2,
                    maxLines = 4,
                    isError = notesError != null,
                    supportingText = notesError.asSupportingText(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(16.dp),
                )
                    }
                }

                Text(
                    text = stringResource(R.string.card_template_heading),
                    style = MaterialTheme.typography.titleMedium,
                )
                CardTemplatePicker(
                    selectedTemplateId = form.cardTemplateId,
                    onTemplateSelected = onTemplateSelected,
                    nickname = form.nickname,
                    cardNetwork = form.cardNetwork,
                    showPreview = false,
                )
                if (templateError != null) {
                    Text(
                        text = templateError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (form.submissionFailed) {
                    Text(
                        text = stringResource(R.string.card_error_save_generic),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCardStatusScreen(
    loading: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_card_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(
                    if (loading) {
                        R.string.card_edit_loading
                    } else {
                        R.string.card_edit_unavailable
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun DisableAutofillForCurrentComposeView() {
    val view = LocalView.current
    DisposableEffect(view) {
        val previousMode = view.importantForAutofill
        view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        onDispose {
            view.importantForAutofill = previousMode
        }
    }
}

@Composable
private fun ClipboardBlocked(content: @Composable () -> Unit) {
    val platformClipboard = LocalClipboard.current
    val disabledClipboard = remember(platformClipboard) {
        DisabledClipboard(platformClipboard)
    }
    CompositionLocalProvider(
        LocalClipboard provides disabledClipboard,
        LocalClipboardManager provides DisabledClipboardManager,
        content = content,
    )
}

private class DisabledClipboard(
    private val platformClipboard: Clipboard,
) : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? = null

    override suspend fun setClipEntry(clipEntry: ClipEntry?) = Unit

    override val nativeClipboard
        get() = platformClipboard.nativeClipboard
}

private object DisabledClipboardManager : ClipboardManager {
    override fun getText(): AnnotatedString? = null

    override fun setText(annotatedString: AnnotatedString) = Unit
}

private object GroupedCardNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val grouped = text.text.chunked(4).joinToString(separator = " ")
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val safeOffset = offset.coerceIn(0, text.length)
                return (safeOffset + safeOffset / 4).coerceAtMost(grouped.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val safeOffset = offset.coerceIn(0, grouped.length)
                return (safeOffset - safeOffset / 5).coerceAtMost(text.length)
            }
        }
        return TransformedText(AnnotatedString(grouped), mapping)
    }
}

private object ExpiryDateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = ExpiryDateTools.normalizeInputDigits(text.text)
        val formatted = ExpiryDateTools.formatInputDigits(digits)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val safeOffset = offset.coerceIn(0, digits.length)
                return when {
                    digits.length < 2 || safeOffset < 2 -> safeOffset
                    else -> safeOffset + 1
                }.coerceAtMost(formatted.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val safeOffset = offset.coerceIn(0, formatted.length)
                return when {
                    digits.length < 2 || safeOffset <= 2 -> safeOffset
                    else -> safeOffset - 1
                }.coerceAtMost(digits.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

private fun String?.asSupportingText(): (@Composable () -> Unit)? =
    this?.let { message ->
        { Text(message) }
    }

private fun Modifier.redactValueFromSemantics(description: String): Modifier =
    clearAndSetSemantics {
        contentDescription = description
    }

internal fun shouldAdvanceExpiryFocusToCvv(
    previousDigits: String,
    normalizedDigits: String,
): Boolean = previousDigits.length < 4 && normalizedDigits.length == 4
