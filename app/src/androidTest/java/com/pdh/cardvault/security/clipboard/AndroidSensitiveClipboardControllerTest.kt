package com.pdh.cardvault.security.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pdh.cardvault.MainActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSensitiveClipboardControllerTest {
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var controller: AndroidSensitiveClipboardController
    private lateinit var activityScenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val launchIntent = Intent.makeMainActivity(
            ComponentName(targetContext, MainActivity::class.java),
        )
        activityScenario = ActivityScenario.launch(launchIntent)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        clipboardManager = context.getSystemService(ClipboardManager::class.java)
        clipboardManager.clearPrimaryClip()
        controller = AndroidSensitiveClipboardController(context)
    }

    @After
    fun tearDown() {
        if (::controller.isInitialized) controller.clearCardNumberIfOwned()
        if (::clipboardManager.isInitialized) clipboardManager.clearPrimaryClip()
        if (::activityScenario.isInitialized) activityScenario.close()
    }

    @Test
    fun copiedCardNumberIsMarkedSensitiveAndOwnedContentCanBeCleared() {
        val syntheticNumber = "9".repeat(12)

        assertTrue(controller.copyCardNumber(syntheticNumber))
        assertTrue(
            clipboardManager.primaryClipDescription
                ?.extras
                ?.getBoolean("android.content.extra.IS_SENSITIVE") == true,
        )

        controller.clearCardNumberIfOwned()

        assertNull(clipboardManager.primaryClip)
    }

    @Test
    fun clearDoesNotDeleteClipboardContentThatReplacedCardVaultValue() {
        assertTrue(controller.copyCardNumber("8".repeat(12)))
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Other content", "unrelated"))

        controller.clearCardNumberIfOwned()

        assertEquals(
            "unrelated",
            clipboardManager.primaryClip?.getItemAt(0)?.text?.toString(),
        )
    }

    @Test
    fun ownedCardNumberIsAutomaticallyClearedAfterConfiguredDeadline() {
        val mainHandler = Handler(Looper.getMainLooper())
        val clipboardGateway = InMemoryCardNumberClipboardGateway()
        val fastController = AndroidSensitiveClipboardController(
            clipboardGateway = clipboardGateway,
            handler = mainHandler,
            ownerTokenFactory = { "synthetic-owner-token" },
            clearDelayMillis = 50L,
            clearRetryDelayMillis = 25L,
        )
        assertTrue(fastController.copyCardNumber("7".repeat(12)))

        val mainQueueReached = CountDownLatch(1)
        mainHandler.postDelayed(mainQueueReached::countDown, 250L)
        assertTrue(
            "Main thread did not process the clipboard-clear deadline",
            mainQueueReached.await(5L, TimeUnit.SECONDS),
        )

        assertFalse(clipboardGateway.containsClip)
    }

    @Test
    fun temporarilyUnavailableClipboardRetainsOwnershipAndRetriesConditionalClear() {
        val mainHandler = Handler(Looper.getMainLooper())
        val clipboardGateway = InMemoryCardNumberClipboardGateway()
        val fastController = AndroidSensitiveClipboardController(
            clipboardGateway = clipboardGateway,
            handler = mainHandler,
            ownerTokenFactory = { "retry-owner-token" },
            clearDelayMillis = 20L,
            clearRetryDelayMillis = 30L,
        )
        assertTrue(fastController.copyCardNumber("6".repeat(12)))
        clipboardGateway.unavailableObservationsRemaining = 1

        val retriesCompleted = CountDownLatch(1)
        mainHandler.postDelayed(retriesCompleted::countDown, 150L)
        assertTrue(
            "Main thread did not process the clipboard-clear retry",
            retriesCompleted.await(5L, TimeUnit.SECONDS),
        )

        assertFalse(clipboardGateway.containsClip)
    }
}

private class InMemoryCardNumberClipboardGateway : CardNumberClipboardGateway {
    var containsClip: Boolean = false
        private set
    var unavailableObservationsRemaining: Int = 0
    private var ownerToken: String? = null

    override fun writeCardNumber(normalizedCardNumber: String, ownerToken: String): Boolean {
        containsClip = true
        this.ownerToken = ownerToken
        return true
    }

    override fun observeOwnership(): ClipboardOwnershipObservation {
        if (unavailableObservationsRemaining > 0) {
            unavailableObservationsRemaining -= 1
            return ClipboardOwnershipObservation.Unavailable
        }
        return if (containsClip) {
            ClipboardOwnershipObservation.Present(ownerToken)
        } else {
            ClipboardOwnershipObservation.Unavailable
        }
    }

    override fun clear(): Boolean {
        containsClip = false
        ownerToken = null
        return true
    }
}
