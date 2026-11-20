package com.pdh.cardvault

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.graphics.Color
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.pdh.cardvault.data.SharedPreferencesFirstRunStateStore
import com.pdh.cardvault.data.SharedPreferencesStartupAuthenticationStore
import com.pdh.cardvault.presentation.MainViewModel
import com.pdh.cardvault.security.auth.AndroidDeviceSecurityChecker
import com.pdh.cardvault.sync.extractInboundTransferUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** An encrypted transfer file handed to CardVault via "Open with" or "Share". */
internal data class InboundTransferRequest(
    val id: Long,
    val uri: String,
) {
    override fun toString(): String = "InboundTransferRequest(id=$id, uri=redacted)"
}

class MainActivity : FragmentActivity() {
    private var authenticatedScreenCaptureRequested = false
    private var resumed = false
    private var inboundTransferSerial = 0L
    private val pendingInboundTransfer = MutableStateFlow<InboundTransferRequest?>(null)
    internal val inboundTransferRequest: StateFlow<InboundTransferRequest?> =
        pendingInboundTransfer.asStateFlow()
    private val mainViewModel: MainViewModel by lazy {
        ViewModelProvider(
            this,
            MainViewModel.Factory(
                firstRunStateStore = SharedPreferencesFirstRunStateStore(applicationContext),
                startupAuthenticationStore =
                    SharedPreferencesStartupAuthenticationStore(applicationContext),
                deviceSecurityChecker = AndroidDeviceSecurityChecker(applicationContext),
            ),
        )[MainViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        protectScreenContent()
        super.onCreate(savedInstanceState)

        // A recreated activity must not replay the file that launched it originally.
        if (savedInstanceState == null) {
            enqueueInboundTransfer(intent)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
        window.decorView.apply {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            importantForContentCapture =
                View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            CardVaultApp(
                activityRecreated = savedInstanceState != null,
                mainViewModel = mainViewModel,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        enqueueInboundTransfer(intent)
    }

    override fun onStart() {
        super.onStart()
        mainViewModel.onAppForegrounded()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        applyAuthenticatedScreenCapturePolicy()
    }

    override fun onPause() {
        resumed = false
        protectScreenContent()
        super.onPause()
    }

    override fun onStop() {
        mainViewModel.onAppBackgrounded()
        authenticatedScreenCaptureRequested = false
        protectScreenContent()
        super.onStop()
    }

    internal fun setAuthenticatedScreenCaptureAllowed(allowed: Boolean) {
        authenticatedScreenCaptureRequested = allowed
        applyAuthenticatedScreenCapturePolicy()
    }

    /** Marks the request as handled; returns false if a newer request replaced it meanwhile. */
    internal fun consumeInboundTransfer(requestId: Long): Boolean {
        val current = pendingInboundTransfer.value ?: return false
        if (current.id != requestId) return false
        pendingInboundTransfer.value = null
        return true
    }

    private fun enqueueInboundTransfer(source: Intent?) {
        val uri = extractInboundTransferUri(source) ?: return
        inboundTransferSerial += 1L
        pendingInboundTransfer.value = InboundTransferRequest(
            id = inboundTransferSerial,
            uri = uri.toString(),
        )
    }

    private fun applyAuthenticatedScreenCapturePolicy() {
        if (authenticatedScreenCaptureRequested && resumed) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            protectScreenContent()
        }
    }

    private fun protectScreenContent() {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
