package com.pdh.cardvault.presentation

import com.pdh.cardvault.data.FirstRunStateStore
import com.pdh.cardvault.data.StartupAuthenticationStore
import com.pdh.cardvault.security.auth.AuthenticationAction
import com.pdh.cardvault.security.auth.AuthenticationScope
import com.pdh.cardvault.security.auth.DeviceSecurityChecker
import com.pdh.cardvault.security.auth.DeviceSecurityStatus
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelTest {
    @Test
    fun deviceWithoutSecureLockScreenCannotUseVault() {
        val fixture = fixture(deviceStatus = DeviceSecurityStatus.NoSecureLockScreen)

        fixture.viewModel.onUiReady(activityRecreated = false)

        assertEquals(
            VaultLockState.SecurityUnavailable(DeviceSecurityStatus.NoSecureLockScreen),
            fixture.viewModel.uiState.value.vaultLockState,
        )
        assertFalse(fixture.viewModel.uiState.value.canRenderMaskedContent)
        assertNull(fixture.viewModel.uiState.value.authenticationRequest)
    }

    @Test
    fun legacyStartupAuthenticationIsDisabledAndColdStartShowsMaskedVault() {
        val fixture = fixture(startupEnabled = true)

        fixture.viewModel.onUiReady(activityRecreated = false)

        assertFalse(fixture.startupStore.enabledValue)
        assertFalse(fixture.viewModel.uiState.value.startupAuthenticationEnabled)
        assertEquals(VaultLockState.UnlockedMasked, fixture.viewModel.uiState.value.vaultLockState)
        assertTrue(fixture.viewModel.uiState.value.canRenderMaskedContent)
        assertNull(fixture.viewModel.uiState.value.authenticationRequest)
    }

    @Test
    fun startupAuthenticationCannotBeReenabled() {
        val fixture = unlockedFixture()

        fixture.viewModel.requestStartupAuthenticationChange(enabled = true)

        assertFalse(fixture.startupStore.enabledValue)
        assertFalse(fixture.viewModel.uiState.value.startupAuthenticationEnabled)
        assertNull(fixture.viewModel.uiState.value.authenticationRequest)
        assertEquals(VaultLockState.UnlockedMasked, fixture.viewModel.uiState.value.vaultLockState)
    }

    @Test
    fun backgroundAndForegroundNeverRequestEntryAuthentication() {
        val fixture = fixture(startupEnabled = true)
        fixture.viewModel.onUiReady(activityRecreated = false)

        fixture.viewModel.onAppBackgrounded()
        assertEquals(VaultLockState.UnlockedMasked, fixture.viewModel.uiState.value.vaultLockState)
        assertTrue(fixture.viewModel.uiState.value.canRenderMaskedContent)
        assertNull(fixture.viewModel.uiState.value.authenticationRequest)
        fixture.viewModel.onAppForegrounded()
        assertEquals(VaultLockState.UnlockedMasked, fixture.viewModel.uiState.value.vaultLockState)
        assertTrue(fixture.viewModel.uiState.value.canRenderMaskedContent)
        assertNull(fixture.viewModel.uiState.value.authenticationRequest)
        assertFalse(fixture.viewModel.uiState.value.screenCaptureAllowed)
    }

    @Test
    fun activityOrProcessRecreationShowsMaskedVaultWithoutAuthentication() {
        val fixture = fixture(startupEnabled = true)
        fixture.viewModel.onUiReady(activityRecreated = true)

        assertEquals(VaultLockState.UnlockedMasked, fixture.viewModel.uiState.value.vaultLockState)
        assertTrue(fixture.viewModel.uiState.value.canRenderMaskedContent)
        assertNull(fixture.viewModel.uiState.value.authenticationRequest)
    }

    @Test
    fun successfulRecordPromptLifecycleResultIsAppliedOnlyAfterForegroundReturn() {
        val fixture = unlockedFixture()
        val recordId = UUID.randomUUID()
        assertTrue(
            fixture.viewModel.requestOneTimeAuthentication(
                AuthenticationAction.EditCard,
                recordId,
            ),
        )
        val request = requireNotNull(fixture.viewModel.uiState.value.authenticationRequest)

        fixture.viewModel.onAppBackgrounded()
        fixture.viewModel.onAuthenticationResult(request.requestId, authenticated = true)
        assertEquals(
            VaultLockState.Authenticating(request.scope),
            fixture.viewModel.uiState.value.vaultLockState,
        )
        assertFalse(fixture.viewModel.uiState.value.canRenderMaskedContent)
        assertFalse(fixture.viewModel.uiState.value.screenCaptureAllowed)
        assertNull(fixture.viewModel.uiState.value.authenticationRequest)

        fixture.viewModel.onAppForegrounded()
        assertEquals(VaultLockState.UnlockedMasked, fixture.viewModel.uiState.value.vaultLockState)
        assertTrue(fixture.viewModel.uiState.value.canRenderMaskedContent)
        assertTrue(fixture.viewModel.uiState.value.screenCaptureAllowed)
    }

    @Test
    fun returningWhilePromptIsPendingKeepsMaskedContentHidden() {
        val fixture = unlockedFixture()
        assertTrue(
            fixture.viewModel.requestOneTimeAuthentication(
                AuthenticationAction.RevealCardSecrets,
                UUID.randomUUID(),
            ),
        )
        val request = requireNotNull(fixture.viewModel.uiState.value.authenticationRequest)

        fixture.viewModel.onAppBackgrounded()
        fixture.viewModel.onAppForegrounded()

        assertEquals(VaultLockState.Authenticating(request.scope), fixture.viewModel.uiState.value.vaultLockState)
        assertFalse(fixture.viewModel.uiState.value.canRenderMaskedContent)

        fixture.viewModel.onAuthenticationResult(request.requestId, authenticated = true)
        assertTrue(fixture.viewModel.uiState.value.canRenderMaskedContent)
    }

    @Test
    fun oneTimeAuthorizationMatchesOneActionAndRecordAndIsConsumedOnce() {
        val fixture = unlockedFixture()
        val recordId = UUID.randomUUID()

        assertTrue(
            fixture.viewModel.requestOneTimeAuthentication(
                AuthenticationAction.RevealCardSecrets,
                recordId,
            ),
        )
        authenticateActiveRequest(fixture)

        assertTrue(
            fixture.viewModel.consumeOneTimeAuthorization(
                AuthenticationAction.RevealCardSecrets,
                recordId,
            ),
        )
        assertFalse(
            fixture.viewModel.consumeOneTimeAuthorization(
                AuthenticationAction.RevealCardSecrets,
                recordId,
            ),
        )
    }

    @Test
    fun vaultTransferAuthorizationMatchesOneActionAndIsConsumedOnce() {
        val fixture = unlockedFixture()

        assertTrue(
            fixture.viewModel.requestVaultTransferAuthentication(
                AuthenticationAction.ExportVault,
            ),
        )
        authenticateActiveRequest(fixture)

        assertTrue(
            fixture.viewModel.consumeVaultTransferAuthorization(
                AuthenticationAction.ExportVault,
            ),
        )
        assertFalse(
            fixture.viewModel.consumeVaultTransferAuthorization(
                AuthenticationAction.ExportVault,
            ),
        )
    }

    @Test
    fun vaultTransferAuthorizationCannotCrossImportAndExportActions() {
        val fixture = unlockedFixture()
        fixture.viewModel.requestVaultTransferAuthentication(AuthenticationAction.ExportVault)
        authenticateActiveRequest(fixture)

        assertFalse(
            fixture.viewModel.consumeVaultTransferAuthorization(
                AuthenticationAction.ImportVault,
            ),
        )
        assertFalse(
            fixture.viewModel.consumeVaultTransferAuthorization(
                AuthenticationAction.ExportVault,
            ),
        )
    }

    @Test
    fun recordAuthenticationSurvivesOnlyItsSystemPromptLifecycleTransition() {
        val fixture = unlockedFixture()
        val recordId = UUID.randomUUID()
        assertTrue(
            fixture.viewModel.requestOneTimeAuthentication(
                AuthenticationAction.RevealCardSecrets,
                recordId,
            ),
        )
        val request = requireNotNull(fixture.viewModel.uiState.value.authenticationRequest)

        fixture.viewModel.onAppBackgrounded()
        fixture.viewModel.onAuthenticationResult(request.requestId, authenticated = true)
        assertEquals(
            VaultLockState.Authenticating(request.scope),
            fixture.viewModel.uiState.value.vaultLockState,
        )
        assertFalse(fixture.viewModel.uiState.value.canRenderMaskedContent)
        assertNull(fixture.viewModel.uiState.value.authenticationRequest)

        fixture.viewModel.onAppForegrounded()

        assertEquals(VaultLockState.UnlockedMasked, fixture.viewModel.uiState.value.vaultLockState)
        assertTrue(
            fixture.viewModel.consumeOneTimeAuthorization(
                AuthenticationAction.RevealCardSecrets,
                recordId,
            ),
        )
        assertFalse(
            fixture.viewModel.consumeOneTimeAuthorization(
                AuthenticationAction.RevealCardSecrets,
                recordId,
            ),
        )
    }

    @Test
    fun wrongRecordAttemptConsumesOneTimeAuthorization() {
        val fixture = unlockedFixture()
        val authorizedRecordId = UUID.randomUUID()
        val otherRecordId = UUID.randomUUID()
        fixture.viewModel.requestOneTimeAuthentication(
            AuthenticationAction.RevealCardSecrets,
            authorizedRecordId,
        )
        authenticateActiveRequest(fixture)

        assertFalse(
            fixture.viewModel.consumeOneTimeAuthorization(
                AuthenticationAction.RevealCardSecrets,
                otherRecordId,
            ),
        )
        assertFalse(
            fixture.viewModel.consumeOneTimeAuthorization(
                AuthenticationAction.RevealCardSecrets,
                authorizedRecordId,
            ),
        )
    }

    @Test
    fun authorizationCannotCrossActions() {
        val fixture = unlockedFixture()
        val recordId = UUID.randomUUID()
        fixture.viewModel.requestOneTimeAuthentication(AuthenticationAction.EditCard, recordId)
        authenticateActiveRequest(fixture)

        assertFalse(
            fixture.viewModel.consumeOneTimeAuthorization(
                AuthenticationAction.DeleteCard,
                recordId,
            ),
        )
    }

    @Test
    fun revealCardSecretsAuthorizationCannotAuthorizeEdit() {
        val fixture = unlockedFixture()
        val recordId = UUID.randomUUID()
        fixture.viewModel.requestOneTimeAuthentication(
            AuthenticationAction.RevealCardSecrets,
            recordId,
        )
        authenticateActiveRequest(fixture)

        assertFalse(
            fixture.viewModel.consumeOneTimeAuthorization(
                AuthenticationAction.EditCard,
                recordId,
            ),
        )
        assertFalse(
            fixture.viewModel.consumeOneTimeAuthorization(
                AuthenticationAction.RevealCardSecrets,
                recordId,
            ),
        )
    }

    @Test
    fun backgroundClearsUnconsumedOneTimeAuthorization() {
        val fixture = unlockedFixture()
        val recordId = UUID.randomUUID()
        fixture.viewModel.requestOneTimeAuthentication(AuthenticationAction.EditCard, recordId)
        authenticateActiveRequest(fixture)

        fixture.viewModel.onAppBackgrounded()
        fixture.viewModel.onAppForegrounded()

        assertFalse(
            fixture.viewModel.consumeOneTimeAuthorization(
                AuthenticationAction.EditCard,
                recordId,
            ),
        )
    }

    @Test
    fun screenCaptureIsAllowedOnlyAfterSuccessfulAuthenticationInCurrentForegroundSession() {
        val fixture = unlockedFixture()
        val recordId = UUID.randomUUID()
        assertFalse(fixture.viewModel.uiState.value.screenCaptureAllowed)

        fixture.viewModel.requestOneTimeAuthentication(
            AuthenticationAction.RevealCardSecrets,
            recordId,
        )
        assertFalse(fixture.viewModel.uiState.value.screenCaptureAllowed)

        authenticateActiveRequest(fixture)
        assertTrue(fixture.viewModel.uiState.value.screenCaptureAllowed)

        fixture.viewModel.onAppBackgrounded()
        assertFalse(fixture.viewModel.uiState.value.screenCaptureAllowed)
    }

    @Test
    fun cancelledAuthenticationNeverAllowsScreenCapture() {
        val fixture = unlockedFixture()
        fixture.viewModel.requestOneTimeAuthentication(
            AuthenticationAction.RevealCardSecrets,
            UUID.randomUUID(),
        )
        val request = requireNotNull(fixture.viewModel.uiState.value.authenticationRequest)

        fixture.viewModel.onAuthenticationResult(request.requestId, authenticated = false)

        assertFalse(fixture.viewModel.uiState.value.screenCaptureAllowed)
    }

    @Test
    fun startingAnotherAuthenticationImmediatelyRevokesScreenCapturePermission() {
        val fixture = unlockedFixture()
        fixture.viewModel.requestOneTimeAuthentication(
            AuthenticationAction.RevealCardSecrets,
            UUID.randomUUID(),
        )
        authenticateActiveRequest(fixture)
        assertTrue(fixture.viewModel.uiState.value.screenCaptureAllowed)

        fixture.viewModel.requestOneTimeAuthentication(
            AuthenticationAction.EditCard,
            UUID.randomUUID(),
        )

        assertFalse(fixture.viewModel.uiState.value.screenCaptureAllowed)
    }

    @Test
    fun vaultOrDeviceSecurityFailureRevokesScreenCapturePermission() {
        val vaultFailure = unlockedFixture()
        vaultFailure.viewModel.requestOneTimeAuthentication(
            AuthenticationAction.RevealCardSecrets,
            UUID.randomUUID(),
        )
        authenticateActiveRequest(vaultFailure)
        vaultFailure.viewModel.onVaultAccessFailed()
        assertFalse(vaultFailure.viewModel.uiState.value.screenCaptureAllowed)

        val deviceFailure = unlockedFixture()
        deviceFailure.viewModel.requestOneTimeAuthentication(
            AuthenticationAction.RevealCardSecrets,
            UUID.randomUUID(),
        )
        authenticateActiveRequest(deviceFailure)
        deviceFailure.deviceChecker.status = DeviceSecurityStatus.AuthenticationUnavailable
        deviceFailure.viewModel.retryDeviceSecurityCheck()
        assertFalse(deviceFailure.viewModel.uiState.value.screenCaptureAllowed)
    }

    @Test
    fun activityRecreationRestoresMaskedStateWithoutAuthentication() {
        val fixture = fixture(startupEnabled = false)

        fixture.viewModel.onUiReady(activityRecreated = true)

        assertEquals(VaultLockState.UnlockedMasked, fixture.viewModel.uiState.value.vaultLockState)
        assertTrue(fixture.viewModel.uiState.value.canRenderMaskedContent)
        assertNull(fixture.viewModel.uiState.value.authenticationRequest)
    }

    @Test
    fun authenticationModelsRedactRequestAndRecordIdentifiers() {
        val recordId = UUID.randomUUID()
        val scope = AuthenticationScope(AuthenticationAction.DeleteCard, recordId)
        val request = com.pdh.cardvault.security.auth.SystemAuthenticationRequest(
            UUID.randomUUID(),
            scope,
        )

        assertFalse(scope.toString().contains(recordId.toString()))
        assertFalse(request.toString().contains(recordId.toString()))
        assertFalse(request.toString().contains(request.requestId.toString()))
    }

    private fun unlockedFixture(): Fixture = fixture().also { fixture ->
        fixture.viewModel.onUiReady(activityRecreated = false)
        assertEquals(VaultLockState.UnlockedMasked, fixture.viewModel.uiState.value.vaultLockState)
    }

    private fun authenticateActiveRequest(fixture: Fixture) {
        val request = requireNotNull(fixture.viewModel.uiState.value.authenticationRequest)
        fixture.viewModel.onAuthenticationResult(request.requestId, authenticated = true)
    }

    private fun fixture(
        startupEnabled: Boolean = false,
        deviceStatus: DeviceSecurityStatus = DeviceSecurityStatus.Available,
    ): Fixture {
        val firstRunStore = FakeFirstRunStateStore()
        val startupStore = FakeStartupAuthenticationStore(startupEnabled)
        val deviceChecker = FakeDeviceSecurityChecker(deviceStatus)
        return Fixture(
            viewModel = MainViewModel(
                firstRunStore,
                startupStore,
                deviceChecker,
            ),
            startupStore = startupStore,
            deviceChecker = deviceChecker,
        )
    }

    private data class Fixture(
        val viewModel: MainViewModel,
        val startupStore: FakeStartupAuthenticationStore,
        val deviceChecker: FakeDeviceSecurityChecker,
    )

    private class FakeFirstRunStateStore : FirstRunStateStore {
        private var acknowledged = true

        override fun isSecurityNoticeAcknowledged(): Boolean = acknowledged

        override fun acknowledgeSecurityNotice() {
            acknowledged = true
        }
    }

    private class FakeStartupAuthenticationStore(
        var enabledValue: Boolean,
    ) : StartupAuthenticationStore {
        override fun isEnabled(): Boolean = enabledValue

        override fun setEnabled(enabled: Boolean) {
            enabledValue = enabled
        }
    }

    private class FakeDeviceSecurityChecker(
        var status: DeviceSecurityStatus,
    ) : DeviceSecurityChecker {
        override fun check(): DeviceSecurityStatus = status
    }
}
