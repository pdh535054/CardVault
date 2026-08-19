package com.pdh.cardvault

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

class MainActivity : FragmentActivity() {
    private var authenticatedScreenCaptureRequested = false
    private var resumed = false
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
