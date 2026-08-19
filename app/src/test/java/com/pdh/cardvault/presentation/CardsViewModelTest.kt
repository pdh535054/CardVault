package com.pdh.cardvault.presentation

import com.pdh.cardvault.data.room.PersistentBankCardRepository
import com.pdh.cardvault.data.room.PersistentCardDetail
import com.pdh.cardvault.data.room.PersistentCardListItem
import com.pdh.cardvault.data.room.PersistentCardSecrets
import com.pdh.cardvault.domain.model.BankCardInput
import com.pdh.cardvault.domain.model.CardNetwork
import com.pdh.cardvault.domain.validation.BankCardValidationError
import com.pdh.cardvault.domain.validation.BankCardValidator
import com.pdh.cardvault.domain.validation.CardNumberTools
import com.pdh.cardvault.domain.validation.CardNetworkDetector
import com.pdh.cardvault.security.auth.AuthenticationAction
import com.pdh.cardvault.security.auth.AuthenticationScope
import com.pdh.cardvault.security.auth.DeviceSecurityChecker
import com.pdh.cardvault.security.auth.DeviceSecurityStatus
import com.pdh.cardvault.security.clipboard.SensitiveClipboardController
import com.pdh.cardvault.ui.card.CardTemplateRegistry
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardsViewModelTest {
    @Test
    fun vaultStartsLockedAndLoadsOnlyFrontSafeProjectionsAfterUnlock() {
        val fixture = fixture(unlock = false)
        val id = fixture.repository.seed(validInput())

        assertEquals(VaultContentState.Locked, fixture.viewModel.uiState.value.vaultContentState)
        assertTrue(fixture.viewModel.uiState.value.cards.isEmpty())

        fixture.viewModel.onVaultAccessAllowed {}

        val item = fixture.viewModel.uiState.value.cards.single()
        assertEquals(VaultContentState.Ready, fixture.viewModel.uiState.value.vaultContentState)
        assertEquals(id, item.id)
        val fieldNames = CardListItemUiModel::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(fieldNames.none { name -> "cardnumber" in name })
        assertTrue(fieldNames.none { name -> "expiry" in name })
        assertTrue(fieldNames.none { name -> "cvv" in name })
        assertFalse(item.toString().contains(fixture.repository.input(id).cardNumber))
    }

    @Test
    fun cvvStorageRequiresExplicitRiskConfirmationAndDefaultsOff() {
        val fixture = fixture()

        assertFalse(fixture.viewModel.uiState.value.form.saveCvv)
        fixture.viewModel.setSaveCvv(true)
        assertFalse(fixture.viewModel.uiState.value.form.saveCvv)
        assertTrue(fixture.viewModel.uiState.value.form.showCvvRiskConfirmation)

        fixture.viewModel.dismissSaveCvvRisk()
        assertFalse(fixture.viewModel.uiState.value.form.saveCvv)
        fixture.viewModel.setSaveCvv(true)
        fixture.viewModel.confirmSaveCvvRisk()
        assertTrue(fixture.viewModel.uiState.value.form.saveCvv)
        assertTrue(fixture.viewModel.uiState.value.form.cvvRiskAcknowledged)

        fixture.viewModel.updateField(CardFormField.Cvv, "8".repeat(4))
        fixture.viewModel.setSaveCvv(false)
        assertFalse(fixture.viewModel.uiState.value.form.saveCvv)
        assertTrue(fixture.viewModel.uiState.value.form.cvv.isEmpty())
        assertFalse(fixture.viewModel.uiState.value.form.cvvRiskAcknowledged)
    }

    @Test
    fun expiryDateKeepsRawDigitsInTypingOrderWhileFormattingOnlyForDisplay() {
        val fixture = fixture()

        fixture.viewModel.updateField(CardFormField.ExpiryDate, "0")
        assertEquals("0", fixture.viewModel.uiState.value.form.expiryDateText)
        fixture.viewModel.updateField(CardFormField.ExpiryDate, "06")
        assertEquals("06/", fixture.viewModel.uiState.value.form.expiryDateText)
        fixture.viewModel.updateField(CardFormField.ExpiryDate, "062")
        assertEquals("06/2", fixture.viewModel.uiState.value.form.expiryDateText)
        fixture.viewModel.updateField(CardFormField.ExpiryDate, "0629")
        assertEquals("06/29", fixture.viewModel.uiState.value.form.expiryDateText)

        fixture.viewModel.updateField(CardFormField.ExpiryDate, "062")
        assertEquals("06/2", fixture.viewModel.uiState.value.form.expiryDateText)
    }

    @Test
    fun selectingACoverStyleDoesNotChangeHiddenIssuerData() {
        val fixture = fixture()
        val templateId = "custom:jade:waves"

        fixture.viewModel.selectTemplate(templateId)

        val form = fixture.viewModel.uiState.value.form
        assertEquals(templateId, form.cardTemplateId)
        assertEquals("CardVault", form.issuerName)
    }

    @Test
    fun typingCvvEnablesEncryptedStorageAndSavesWithoutASecondDialog() {
        val fixture = fixture()
        fillValidAddForm(fixture.viewModel)

        fixture.viewModel.updateField(CardFormField.Cvv, "8".repeat(4))

        assertTrue(fixture.viewModel.uiState.value.form.saveCvv)
        assertFalse(fixture.viewModel.uiState.value.form.cvvRiskAcknowledged)
        assertTrue(fixture.viewModel.submitCard())
        assertFalse(fixture.viewModel.uiState.value.form.showCvvRiskConfirmation)
        assertEquals(1, fixture.repository.size)
    }

    @Test
    fun validAddPersistsRefreshesListAndClearsEntireForm() {
        val fixture = fixture()
        fillValidAddForm(fixture.viewModel)

        assertTrue(fixture.viewModel.submitCard())

        val state = fixture.viewModel.uiState.value
        assertEquals(1, fixture.repository.size)
        assertEquals(1, state.cards.size)
        assertTrue(state.form.nickname.isEmpty())
        assertTrue(state.form.cardNumber.isEmpty())
        assertFalse(state.form.saveCvv)
        assertTrue(state.form.cvv.isEmpty())
        assertTrue(state.form.notes.isEmpty())
        assertEquals("Clearly synthetic local-only note", fixture.repository.input(state.cards.single().id).notes)
        assertEquals(CardNavigationEvent.AddSaved, state.navigationEvent)
    }

    @Test
    fun cardNetworkUpdatesFromTypedNumberAndFlowsToTheCardList() {
        val fixture = fixture()
        val syntheticVisaNumber = "4" + "0".repeat(15)

        fixture.viewModel.updateField(CardFormField.CardNumber, syntheticVisaNumber)
        assertEquals(CardNetwork.Visa, fixture.viewModel.uiState.value.form.cardNetwork)

        fillValidAddForm(fixture.viewModel, cardNumber = syntheticVisaNumber)
        assertTrue(fixture.viewModel.submitCard())
        assertEquals(CardNetwork.Visa, fixture.viewModel.uiState.value.cards.single().cardNetwork)
        assertNull(fixture.viewModel.uiState.value.form.cardNetwork)
    }

    @Test
    fun invalidFieldsBlockAddWhileLuhnWarningRemainsNonBlocking() {
        val fixture = fixture()

        assertFalse(fixture.viewModel.submitCard())
        assertTrue(
            BankCardValidationError.NICKNAME_REQUIRED in
                fixture.viewModel.uiState.value.form.validationErrors,
        )
        assertEquals(0, fixture.repository.size)

        fillValidAddForm(fixture.viewModel, cardNumber = "${"0".repeat(11)}1")
        assertTrue(fixture.viewModel.uiState.value.form.showLuhnWarning)
        assertTrue(fixture.viewModel.submitCard())
        assertEquals(1, fixture.repository.size)
    }

    @Test
    fun insecureDeviceBlocksAddBeforeRepositoryWrite() {
        val fixture = fixture(deviceStatus = DeviceSecurityStatus.NoSecureLockScreen)
        fillValidAddForm(fixture.viewModel)

        assertFalse(fixture.viewModel.submitCard())

        assertEquals(0, fixture.repository.size)
        assertEquals(
            CardOperationMessage.DeviceSecurityRequired,
            fixture.viewModel.uiState.value.operationMessage,
        )
    }

    @Test
    fun detailUsesMaskedProjectionAndContainsNoFullNumberOrCvvField() {
        val fixture = fixture()
        val fullNumber = "7".repeat(12)
        val cvv = "8".repeat(4)
        val id = fixture.repository.seed(
            validInput(cardNumber = fullNumber, saveCvv = true, cvv = cvv),
        )
        fixture.viewModel.onVaultAccessRevoked()
        fixture.viewModel.onVaultAccessAllowed {}

        fixture.viewModel.loadCardDetail(id.toString())

        val loaded = fixture.viewModel.uiState.value.detail as CardDetailUiState.Loaded
        assertEquals(id, loaded.recordId)
        assertFalse(loaded.card.maskedCardNumber.contains(fullNumber))
        assertEquals(CvvSaveStatusUi.Saved, loaded.card.cvvSaveStatus)
        val fields = CardDetailUiModel::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse("cardnumber" in fields)
        assertFalse("cvv" in fields)
        assertTrue(fields.none { name -> "expiry" in name })
        assertTrue(fields.none { name -> "expired" in name })
        assertFalse(loaded.toString().contains(fullNumber))
        assertFalse(loaded.toString().contains(cvv))
    }

    @Test
    fun authorizationMustMatchPendingActionAndRecordAndIsConsumedOnce() {
        val fixture = fixtureWithDetail(saveCvv = true)
        val id = fixture.recordId
        assertTrue(fixture.viewModel.prepareAuthentication(AuthenticationAction.RevealCardSecrets, id))
        val expected = requireNotNull(fixture.viewModel.pendingAuthenticationScope())
        val wrong = AuthenticationScope(AuthenticationAction.EditCard, id)

        assertFalse(fixture.viewModel.onAuthorizationGranted(wrong))
        assertTrue(fixture.viewModel.onAuthorizationGranted(expected))
        assertTrue(
            fixture.viewModel.uiState.value.revealedSecrets is
                RevealedCardSecretsUiState.Visible,
        )
        assertFalse(fixture.viewModel.onAuthorizationGranted(expected))
        assertNull(fixture.viewModel.pendingAuthenticationScope())
    }

    @Test
    fun oneAuthenticationRevealsCardNumberAndSavedCvvForOneFifteenSecondLease() {
        val fixture = fixtureWithDetail(saveCvv = true)
        fixture.clock.now = 10_000L

        authorize(fixture, AuthenticationAction.RevealCardSecrets)

        val visible = fixture.viewModel.uiState.value.revealedSecrets as
            RevealedCardSecretsUiState.Visible
        val stored = fixture.repository.input(fixture.recordId)
        assertEquals(25_000L, visible.expiresAtElapsedRealtime)
        assertEquals(stored.cardNumber, visible.cardNumber)
        assertEquals(stored.expiryMonth, visible.expiryMonth)
        assertEquals(stored.expiryYear, visible.expiryYear)
        assertFalse(visible.expired)
        assertEquals(stored.cvv, visible.cvv)
        assertFalse(visible.toString().contains(stored.cardNumber))
        assertFalse(visible.toString().contains(requireNotNull(stored.cvv)))
        assertTrue(fixture.viewModel.copyRevealedCardNumber(fixture.recordId))

        fixture.clock.now = 24_999L
        fixture.scheduler.triggerNext()
        assertTrue(
            fixture.viewModel.uiState.value.revealedSecrets is
                RevealedCardSecretsUiState.Visible,
        )
        fixture.clock.now = 25_000L
        fixture.scheduler.triggerNext()
        assertEquals(
            RevealedCardSecretsUiState.Hidden,
            fixture.viewModel.uiState.value.revealedSecrets,
        )
        assertNull(fixture.clipboard.copiedCardNumber)

        authorize(fixture, AuthenticationAction.RevealCardSecrets)
        fixture.viewModel.hideRevealedCardSecrets()
        assertEquals(
            RevealedCardSecretsUiState.Hidden,
            fixture.viewModel.uiState.value.revealedSecrets,
        )
    }

    @Test
    fun revealWithoutStoredCvvPublishesNoCvvValue() {
        val fixture = fixtureWithDetail(saveCvv = false)

        authorize(fixture, AuthenticationAction.RevealCardSecrets)

        val visible = fixture.viewModel.uiState.value.revealedSecrets as
            RevealedCardSecretsUiState.Visible
        assertNull(visible.cvv)
    }

    @Test
    fun activeRevealAllowsOneTapCopyWithoutASecondAuthentication() {
        val fixture = fixtureWithDetail()
        authorize(fixture, AuthenticationAction.RevealCardSecrets)

        assertTrue(fixture.viewModel.copyRevealedCardNumber(fixture.recordId))

        assertEquals(
            fixture.repository.input(fixture.recordId).cardNumber,
            fixture.clipboard.copiedCardNumber,
        )
        assertEquals(1, fixture.clipboard.copyCount)
        assertTrue(
            fixture.viewModel.uiState.value.revealedSecrets is
                RevealedCardSecretsUiState.Visible,
        )
        assertEquals(
            CardOperationMessage.CardNumberCopied,
            fixture.viewModel.uiState.value.operationMessage,
        )
    }

    @Test
    fun copyFailsClosedWithoutAnActiveUnexpiredReveal() {
        val fixture = fixtureWithDetail()

        assertFalse(fixture.viewModel.copyRevealedCardNumber(fixture.recordId))
        assertEquals(0, fixture.clipboard.copyCount)

        fixture.clock.now = 5_000L
        authorize(fixture, AuthenticationAction.RevealCardSecrets)
        fixture.clock.now = 20_000L

        assertFalse(fixture.viewModel.copyRevealedCardNumber(fixture.recordId))
        assertEquals(0, fixture.clipboard.copyCount)
        assertEquals(
            RevealedCardSecretsUiState.Hidden,
            fixture.viewModel.uiState.value.revealedSecrets,
        )
    }

    @Test
    fun copyCannotCrossRecordsAndInvalidatesTheCurrentReveal() {
        val fixture = fixtureWithDetail()
        authorize(fixture, AuthenticationAction.RevealCardSecrets)

        assertFalse(fixture.viewModel.copyRevealedCardNumber(UUID.randomUUID()))

        assertEquals(0, fixture.clipboard.copyCount)
        assertEquals(
            RevealedCardSecretsUiState.Hidden,
            fixture.viewModel.uiState.value.revealedSecrets,
        )
    }

    @Test
    fun hideLeaveAndBackgroundInvalidateAnInFlightAuthenticatedReveal() {
        fun verify(reset: (DetailFixture) -> Unit) {
            val fixture = fixtureWithDetail(saveCvv = true)
            val readGate = CompletableDeferred<Unit>()
            fixture.repository.secretsReadGate = readGate

            authorize(fixture, AuthenticationAction.RevealCardSecrets)
            assertEquals(
                RevealedCardSecretsUiState.Hidden,
                fixture.viewModel.uiState.value.revealedSecrets,
            )

            reset(fixture)
            readGate.complete(Unit)

            assertEquals(
                RevealedCardSecretsUiState.Hidden,
                fixture.viewModel.uiState.value.revealedSecrets,
            )
        }

        verify { fixture -> fixture.viewModel.hideRevealedCardSecrets() }
        verify { fixture -> fixture.viewModel.clearCardDetail() }
        verify { fixture -> fixture.viewModel.onAppBackgrounded() }
    }

    @Test
    fun systemAuthenticationLifecyclePreservesPendingScopeWithoutRelockingVault() {
        val fixture = fixtureWithDetail(saveCvv = true)
        assertTrue(
            fixture.viewModel.prepareAuthentication(
                AuthenticationAction.RevealCardSecrets,
                fixture.recordId,
            ),
        )
        val scope = requireNotNull(fixture.viewModel.pendingAuthenticationScope())

        fixture.viewModel.onAppBackgrounded(preservePendingAuthentication = true)
        fixture.viewModel.clearCardDetail(preservePendingAuthentication = true)

        assertEquals(scope, fixture.viewModel.pendingAuthenticationScope())
        assertEquals(VaultContentState.Ready, fixture.viewModel.uiState.value.vaultContentState)
        assertEquals(
            RevealedCardSecretsUiState.Hidden,
            fixture.viewModel.uiState.value.revealedSecrets,
        )

        assertTrue(fixture.viewModel.onAuthorizationGranted(scope))
        fixture.viewModel.onVaultAccessAllowed {}

        val visible = fixture.viewModel.uiState.value.revealedSecrets as
            RevealedCardSecretsUiState.Visible
        assertEquals(fixture.recordId, visible.recordId)
        assertEquals(
            fixture.repository.input(fixture.recordId).cardNumber,
            visible.cardNumber,
        )
    }

    @Test
    fun failedCopyAndBackgroundClearClipboardWithoutExposingValueInMessages() {
        val fixture = fixtureWithDetail()
        authorize(fixture, AuthenticationAction.RevealCardSecrets)
        fixture.clipboard.failWrites = true

        assertFalse(fixture.viewModel.copyRevealedCardNumber(fixture.recordId))

        assertNull(fixture.clipboard.copiedCardNumber)
        assertEquals(
            CardOperationMessage.ClipboardFailed,
            fixture.viewModel.uiState.value.operationMessage,
        )

        fixture.clipboard.failWrites = false
        assertTrue(fixture.viewModel.copyRevealedCardNumber(fixture.recordId))
        fixture.viewModel.onAppBackgrounded()

        assertNull(fixture.clipboard.copiedCardNumber)
        assertTrue(fixture.clipboard.clearCount > 0)
    }

    @Test
    fun backgroundHidesRevealedValuesButPreservesAuthenticatedEditDraft() {
        val fixture = fixtureWithDetail(saveCvv = true)
        authorize(fixture, AuthenticationAction.RevealCardSecrets)

        fixture.viewModel.clearCardDetail()
        assertEquals(
            RevealedCardSecretsUiState.Hidden,
            fixture.viewModel.uiState.value.revealedSecrets,
        )

        fixture.viewModel.loadCardDetail(fixture.recordId.toString())
        authorize(fixture, AuthenticationAction.EditCard)
        assertTrue(fixture.viewModel.uiState.value.edit is CardEditUiState.Ready)
        fixture.viewModel.updateEditField(CardFormField.Notes, "Unsaved synthetic edit")

        fixture.viewModel.onAppBackgrounded()

        val state = fixture.viewModel.uiState.value
        assertEquals(VaultContentState.Ready, state.vaultContentState)
        assertTrue(state.cards.isNotEmpty())
        assertTrue(state.detail is CardDetailUiState.Loaded)
        val edit = state.edit as CardEditUiState.Ready
        assertEquals("Unsaved synthetic edit", edit.form.notes)
        assertEquals(RevealedCardSecretsUiState.Hidden, state.revealedSecrets)
        assertTrue(fixture.repository.unlocked)
    }

    @Test
    fun backgroundPreservesUnsavedAddForm() {
        val fixture = fixture(unlock = true)
        fixture.viewModel.updateField(CardFormField.Nickname, "Unsaved synthetic alias")
        fixture.viewModel.updateField(CardFormField.CardNumber, "7".repeat(12))

        fixture.viewModel.onAppBackgrounded()

        val state = fixture.viewModel.uiState.value
        assertEquals(VaultContentState.Ready, state.vaultContentState)
        assertEquals("Unsaved synthetic alias", state.form.nickname)
        assertEquals("7".repeat(12), state.form.cardNumber)
        assertTrue(fixture.repository.unlocked)
    }

    @Test
    fun editFieldsAreNotDecryptedBeforeMatchingAuthorization() {
        val fixture = fixtureWithDetail(saveCvv = true)

        assertEquals(0, fixture.repository.editReadCount)
        assertTrue(fixture.viewModel.prepareAuthentication(AuthenticationAction.EditCard, fixture.recordId))
        assertEquals(0, fixture.repository.editReadCount)

        val scope = requireNotNull(fixture.viewModel.pendingAuthenticationScope())
        fixture.viewModel.onAuthorizationGranted(scope)

        assertEquals(1, fixture.repository.editReadCount)
        val ready = fixture.viewModel.uiState.value.edit as CardEditUiState.Ready
        assertEquals(fixture.recordId, ready.recordId)
        assertEquals(fixture.repository.input(fixture.recordId).nickname, ready.form.nickname)
        assertEquals(CardNavigationEvent.EditReady(fixture.recordId), fixture.viewModel.uiState.value.navigationEvent)
    }

    @Test
    fun editCanChangeAllFieldsAndDisablingCvvRemovesItBeforeUpdate() {
        val fixture = fixtureWithDetail(saveCvv = true)
        authorize(fixture, AuthenticationAction.EditCard)
        fixture.viewModel.onNavigationEventHandled(CardNavigationEvent.EditReady(fixture.recordId))
        fixture.viewModel.updateEditField(CardFormField.Nickname, "Synthetic edited alias")
        fixture.viewModel.updateEditField(CardFormField.IssuerName, "Synthetic edited issuer")
        fixture.viewModel.updateEditField(CardFormField.CardNumber, "6".repeat(12))
        fixture.viewModel.updateEditField(CardFormField.ExpiryDate, "01/98")
        fixture.viewModel.setEditSaveCvv(false)
        fixture.viewModel.selectEditTemplate("custom:midnight:orbits")
        fixture.viewModel.updateEditField(CardFormField.Notes, "Synthetic edited note")

        assertTrue(fixture.viewModel.submitEdit())

        val stored = fixture.repository.input(fixture.recordId)
        assertEquals("Synthetic edited alias", stored.nickname)
        assertEquals("Synthetic edited issuer", stored.issuerName)
        assertEquals("6".repeat(12), stored.cardNumber)
        assertEquals(1, stored.expiryMonth)
        assertEquals(2_098, stored.expiryYear)
        assertFalse(stored.saveCvv)
        assertNull(stored.cvv)
        assertEquals("custom:midnight:orbits", stored.cardTemplateId)
        assertEquals("Synthetic edited note", stored.notes)
        assertEquals(CardNavigationEvent.EditSaved, fixture.viewModel.uiState.value.navigationEvent)
    }

    @Test
    fun cancellingEditDoesNotModifyRepositoryAndReentryNeedsFreshAuthorization() {
        val fixture = fixtureWithDetail()
        val before = fixture.repository.input(fixture.recordId)
        authorize(fixture, AuthenticationAction.EditCard)
        fixture.viewModel.updateEditField(CardFormField.Nickname, "Discarded synthetic edit")

        fixture.viewModel.clearEditDraft()

        assertEquals(before, fixture.repository.input(fixture.recordId))
        assertEquals(CardEditUiState.Hidden, fixture.viewModel.uiState.value.edit)
        assertEquals(1, fixture.repository.editReadCount)
        authorize(fixture, AuthenticationAction.EditCard)
        assertEquals(2, fixture.repository.editReadCount)
    }

    @Test
    fun deleteDoesNothingUntilDedicatedAuthorizationThenPermanentlyRemovesRecord() {
        val fixture = fixtureWithDetail()

        assertEquals(1, fixture.repository.size)
        assertEquals(0, fixture.repository.deleteCount)
        assertTrue(fixture.viewModel.prepareAuthentication(AuthenticationAction.DeleteCard, fixture.recordId))
        assertEquals(0, fixture.repository.deleteCount)
        val scope = requireNotNull(fixture.viewModel.pendingAuthenticationScope())

        fixture.viewModel.onAuthorizationGranted(scope)

        assertEquals(1, fixture.repository.deleteCount)
        assertEquals(0, fixture.repository.size)
        assertTrue(fixture.viewModel.uiState.value.cards.isEmpty())
        assertEquals(
            CardNavigationEvent.DeleteCompleted,
            fixture.viewModel.uiState.value.navigationEvent,
        )
        assertFalse(fixture.viewModel.onAuthorizationGranted(scope))
    }

    @Test
    fun explicitOrderingPersistsAndHonorsFirstAndLastBoundaries() {
        val fixture = fixture(unlock = false)
        val first = fixture.repository.seed(validInput(nickname = "Synthetic first"))
        val second = fixture.repository.seed(validInput(nickname = "Synthetic second"))
        val third = fixture.repository.seed(validInput(nickname = "Synthetic third"))
        fixture.viewModel.onVaultAccessAllowed {}
        assertEquals(listOf(third, second, first), fixture.viewModel.uiState.value.cards.map { it.id })
        fixture.viewModel.enterSortMode()

        assertFalse(fixture.viewModel.moveCardUp(third))
        assertFalse(fixture.viewModel.moveCardDown(first))
        assertTrue(fixture.viewModel.moveCardDown(third))

        assertEquals(listOf(second, third, first), fixture.repository.orderedIds())
        assertEquals(listOf(second, third, first), fixture.viewModel.uiState.value.cards.map { it.id })
        assertEquals(1, fixture.repository.reorderCount)
    }

    @Test
    fun dragOrderingPersistsOneCompletePermutationInASingleRepositoryWrite() {
        val fixture = fixture(unlock = false)
        val first = fixture.repository.seed(validInput(nickname = "Synthetic first"))
        val second = fixture.repository.seed(validInput(nickname = "Synthetic second"))
        val third = fixture.repository.seed(validInput(nickname = "Synthetic third"))
        fixture.viewModel.onVaultAccessAllowed {}

        assertTrue(fixture.viewModel.reorderCards(listOf(first, third, second)))

        assertEquals(listOf(first, third, second), fixture.repository.orderedIds())
        assertEquals(
            listOf(first, third, second),
            fixture.viewModel.uiState.value.cards.map(CardListItemUiModel::id),
        )
        assertEquals(1, fixture.repository.reorderCount)
        assertFalse(fixture.viewModel.reorderCards(listOf(first, third, second)))
        assertEquals(1, fixture.repository.reorderCount)
    }

    @Test
    fun failedOrCancelledAuthenticationNeverExecutesPendingOperation() {
        val fixture = fixtureWithDetail(saveCvv = true)
        assertTrue(
            fixture.viewModel.prepareAuthentication(
                AuthenticationAction.RevealCardSecrets,
                fixture.recordId,
            ),
        )

        fixture.viewModel.onAuthenticationFailed()

        assertNull(fixture.viewModel.pendingAuthenticationScope())
        assertEquals(
            RevealedCardSecretsUiState.Hidden,
            fixture.viewModel.uiState.value.revealedSecrets,
        )
        assertEquals(
            CardOperationMessage.AuthenticationFailed,
            fixture.viewModel.uiState.value.operationMessage,
        )
    }

    @Test
    fun vaultFailureFailsClosedAndInvokesSafeLockCallback() {
        val fixture = fixture(unlock = false)
        fixture.repository.failUnlock = true
        var failureCalled = false

        fixture.viewModel.onVaultAccessAllowed { failureCalled = true }

        assertTrue(failureCalled)
        assertEquals(VaultContentState.Unavailable, fixture.viewModel.uiState.value.vaultContentState)
        assertTrue(fixture.viewModel.uiState.value.cards.isEmpty())
        assertFalse(fixture.repository.unlocked)
    }

    @Test
    fun vaultFailureInvalidatesPreviouslyGrantedRecordOperation() {
        val detail = fixtureWithDetail()
        assertTrue(
            detail.viewModel.prepareAuthentication(
                AuthenticationAction.RevealCardSecrets,
                detail.recordId,
            ),
        )
        val scope = requireNotNull(detail.viewModel.pendingAuthenticationScope())

        detail.viewModel.onVaultAccessRevoked(preservePendingAuthentication = true)
        assertTrue(detail.viewModel.onAuthorizationGranted(scope))
        detail.repository.failUnlock = true
        detail.viewModel.onVaultAccessAllowed {}

        detail.repository.failUnlock = false
        detail.viewModel.onVaultAccessAllowed {}

        assertEquals(
            RevealedCardSecretsUiState.Hidden,
            detail.viewModel.uiState.value.revealedSecrets,
        )
        assertNull(detail.viewModel.pendingAuthenticationScope())
    }

    @Test
    fun uiModelsExceptionsAndRepositoryStringsDoNotExposeSensitiveValues() {
        val fixture = fixtureWithDetail(saveCvv = true)
        authorize(fixture, AuthenticationAction.RevealCardSecrets)
        val input = fixture.repository.input(fixture.recordId)
        val output = listOf(
            fixture.viewModel.uiState.value.toString(),
            fixture.viewModel.uiState.value.detail.toString(),
            fixture.viewModel.uiState.value.revealedSecrets.toString(),
            fixture.repository.toString(),
        ).joinToString()

        assertFalse(output.contains(input.cardNumber))
        assertFalse(output.contains(requireNotNull(input.cvv)))
        assertFalse(output.contains(input.nickname))
        val forbiddenStem = listOf("hold", "er").joinToString(separator = "")
        val fields = listOf(
            CardFormUiState::class.java,
            CardListItemUiModel::class.java,
            CardDetailUiModel::class.java,
            CardsUiState::class.java,
        ).flatMap { type -> type.declaredFields.map { field -> field.name.lowercase() } }
        assertTrue(fields.none { field -> forbiddenStem in field })
    }

    private fun authorize(fixture: DetailFixture, action: AuthenticationAction) {
        assertTrue(fixture.viewModel.prepareAuthentication(action, fixture.recordId))
        val scope = requireNotNull(fixture.viewModel.pendingAuthenticationScope())
        assertTrue(fixture.viewModel.onAuthorizationGranted(scope))
    }

    private fun fixtureWithDetail(saveCvv: Boolean = false): DetailFixture {
        val fixture = fixture(unlock = false)
        val id = fixture.repository.seed(
            validInput(
                saveCvv = saveCvv,
                cvv = "8".repeat(4).takeIf { saveCvv },
            ),
        )
        fixture.viewModel.onVaultAccessAllowed {}
        fixture.viewModel.loadCardDetail(id.toString())
        return DetailFixture(fixture, id)
    }

    private fun fixture(
        unlock: Boolean = true,
        deviceStatus: DeviceSecurityStatus = DeviceSecurityStatus.Available,
    ): Fixture {
        val repository = FakePersistentBankCardRepository()
        val clipboard = FakeSensitiveClipboardController()
        val clock = FakeMonotonicClock()
        val scheduler = FakeExpirationScheduler(clock)
        val validator = BankCardValidator { templateId ->
            CardTemplateRegistry.findById(templateId) != null
        }
        val viewModel = CardsViewModel(
            repository = repository,
            validator = validator,
            defaultTemplateId = CardTemplateRegistry.DEFAULT_TEMPLATE_ID,
            templateIssuerLabel = CardTemplateRegistry::issuerLabelFor,
            deviceSecurityChecker = DeviceSecurityChecker { deviceStatus },
            sensitiveClipboardController = clipboard,
            externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            timeSource = clock,
            expirationScheduler = scheduler,
        )
        return Fixture(viewModel, repository, clock, scheduler, clipboard).also { fixture ->
            if (unlock) fixture.viewModel.onVaultAccessAllowed {}
        }
    }

    private fun fillValidAddForm(
        viewModel: CardsViewModel,
        cardNumber: String = "0".repeat(12),
    ) {
        viewModel.updateField(CardFormField.Nickname, "Synthetic vault alias")
        viewModel.updateField(CardFormField.IssuerName, "Synthetic offline issuer")
        viewModel.updateField(CardFormField.CardNumber, cardNumber)
        viewModel.updateField(CardFormField.ExpiryDate, "12/99")
        viewModel.selectTemplate(CardTemplateRegistry.DEFAULT_TEMPLATE_ID)
        viewModel.updateField(CardFormField.Notes, "Clearly synthetic local-only note")
    }

    private fun validInput(
        nickname: String = "Synthetic persisted alias",
        issuerName: String = "Synthetic persisted issuer",
        cardNumber: String = "0".repeat(12),
        expiryMonth: Int = 12,
        expiryYear: Int = 2_099,
        saveCvv: Boolean = false,
        cvv: String? = null,
        cardTemplateId: String = CardTemplateRegistry.DEFAULT_TEMPLATE_ID,
        notes: String = "Clearly synthetic encrypted note",
    ): BankCardInput = BankCardInput(
        nickname = nickname,
        issuerName = issuerName,
        cardNumber = cardNumber,
        expiryMonth = expiryMonth,
        expiryYear = expiryYear,
        saveCvv = saveCvv,
        cvv = cvv,
        cardTemplateId = cardTemplateId,
        notes = notes,
    )

    private data class Fixture(
        val viewModel: CardsViewModel,
        val repository: FakePersistentBankCardRepository,
        val clock: FakeMonotonicClock,
        val scheduler: FakeExpirationScheduler,
        val clipboard: FakeSensitiveClipboardController,
    )

    private data class DetailFixture(
        val fixture: Fixture,
        val recordId: UUID,
    ) {
        val viewModel: CardsViewModel
            get() = fixture.viewModel
        val repository: FakePersistentBankCardRepository
            get() = fixture.repository
        val clock: FakeMonotonicClock
            get() = fixture.clock
        val scheduler: FakeExpirationScheduler
            get() = fixture.scheduler
        val clipboard: FakeSensitiveClipboardController
            get() = fixture.clipboard
    }
}

private class FakeSensitiveClipboardController : SensitiveClipboardController {
    var copiedCardNumber: String? = null
        private set
    var copyCount: Int = 0
        private set
    var clearCount: Int = 0
        private set
    var failWrites: Boolean = false

    override fun copyCardNumber(normalizedCardNumber: String): Boolean {
        if (failWrites) return false
        copiedCardNumber = normalizedCardNumber
        copyCount += 1
        return true
    }

    override fun clearCardNumberIfOwned() {
        copiedCardNumber = null
        clearCount += 1
    }

    override fun toString(): String = "FakeSensitiveClipboardController(contents=redacted)"
}

private class FakePersistentBankCardRepository : PersistentBankCardRepository {
    private data class StoredCard(
        val id: UUID,
        val input: BankCardInput,
    )

    private val cards = mutableListOf<StoredCard>()
    var unlocked: Boolean = false
        private set
    var failUnlock: Boolean = false
    var editReadCount: Int = 0
        private set
    var deleteCount: Int = 0
        private set
    var reorderCount: Int = 0
        private set
    var secretsReadGate: CompletableDeferred<Unit>? = null
    val size: Int
        get() = cards.size

    override suspend fun unlockOrCreateVault() {
        if (failUnlock) throw IllegalStateException("Synthetic vault unavailable")
        unlocked = true
    }

    override fun lock() {
        unlocked = false
    }

    override fun isUnlocked(): Boolean = unlocked

    override suspend fun add(input: BankCardInput): UUID {
        requireUnlocked()
        val id = UUID.randomUUID()
        cards.add(0, StoredCard(id, input.copy(cvv = input.cvv.takeIf { input.saveCvv })))
        return id
    }

    override suspend fun getList(): List<PersistentCardListItem> {
        requireUnlocked()
        return cards.map { stored ->
            val input = stored.input
            PersistentCardListItem(
                id = stored.id,
                nickname = input.nickname,
                issuerName = input.issuerName,
                cardTemplateId = input.cardTemplateId,
                cardNetwork = CardNetworkDetector.detect(input.cardNumber),
            )
        }
    }

    override suspend fun getMaskedDetail(id: UUID): PersistentCardDetail? {
        requireUnlocked()
        return cards.firstOrNull { it.id == id }?.input?.let { input ->
            PersistentCardDetail(
                nickname = input.nickname,
                issuerName = input.issuerName,
                maskedCardNumber = requireNotNull(CardNumberTools.mask(input.cardNumber)),
                cardTemplateId = input.cardTemplateId,
                notes = input.notes,
                cvvSaved = input.saveCvv,
                cardNetwork = CardNetworkDetector.detect(input.cardNumber),
            )
        }
    }

    override suspend fun getSecretsForAuthenticatedUse(id: UUID): PersistentCardSecrets? {
        requireUnlocked()
        secretsReadGate?.await()
        return cards.firstOrNull { it.id == id }?.input?.let { input ->
            PersistentCardSecrets(
                cardNumber = input.cardNumber,
                expiryMonth = input.expiryMonth,
                expiryYear = input.expiryYear,
                cvv = input.cvv.takeIf { input.saveCvv },
            )
        }
    }

    override suspend fun getEditInputForAuthenticatedUse(id: UUID): BankCardInput? {
        requireUnlocked()
        editReadCount += 1
        return cards.firstOrNull { it.id == id }?.input
    }

    override suspend fun update(id: UUID, input: BankCardInput): Boolean {
        requireUnlocked()
        val index = cards.indexOfFirst { it.id == id }
        if (index < 0) return false
        cards[index] = StoredCard(id, input.copy(cvv = input.cvv.takeIf { input.saveCvv }))
        return true
    }

    override suspend fun delete(id: UUID): Boolean {
        requireUnlocked()
        deleteCount += 1
        return cards.removeAll { it.id == id }
    }

    override suspend fun reorder(orderedIds: List<UUID>) {
        requireUnlocked()
        require(orderedIds.size == cards.size && orderedIds.toSet() == cards.map { it.id }.toSet())
        val byId = cards.associateBy(StoredCard::id)
        cards.clear()
        cards += orderedIds.map(byId::getValue)
        reorderCount += 1
    }

    fun seed(input: BankCardInput): UUID {
        val id = UUID.randomUUID()
        cards.add(0, StoredCard(id, input))
        return id
    }

    fun input(id: UUID): BankCardInput = requireNotNull(cards.firstOrNull { it.id == id }).input

    fun orderedIds(): List<UUID> = cards.map(StoredCard::id)

    private fun requireUnlocked() {
        check(unlocked) { "Synthetic repository is locked" }
    }

    override fun toString(): String = "FakePersistentBankCardRepository(contents=redacted)"
}

private class FakeMonotonicClock : MonotonicTimeSource {
    var now: Long = 0L

    override fun elapsedRealtime(): Long = now
}

private class FakeExpirationScheduler(
    private val clock: FakeMonotonicClock,
) : SensitiveExpirationScheduler {
    private data class Scheduled(
        val dueAt: Long,
        val job: CompletableJob,
        val action: () -> Unit,
    )

    private val scheduled = mutableListOf<Scheduled>()

    override fun schedule(
        scope: CoroutineScope,
        delayMillis: Long,
        onExpired: () -> Unit,
    ): Job {
        val job = Job()
        scheduled += Scheduled(clock.now + delayMillis, job, onExpired)
        return job
    }

    fun triggerNext() {
        val next = scheduled.firstOrNull { it.job.isActive } ?: return
        scheduled.remove(next)
        next.action()
        next.job.complete()
    }

    fun triggerAllDue() {
        while (true) {
            val next = scheduled.firstOrNull { it.job.isActive && it.dueAt <= clock.now } ?: return
            scheduled.remove(next)
            next.action()
            next.job.complete()
        }
    }
}
