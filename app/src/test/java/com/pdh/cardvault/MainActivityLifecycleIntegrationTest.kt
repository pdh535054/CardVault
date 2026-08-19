package com.pdh.cardvault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLifecycleIntegrationTest {
    @Test
    fun activityDirectlyDrivesMainVaultForegroundAndBackgroundTransitions() {
        val activitySource = TestProjectFiles.resolve(
            "src/main/java/com/pdh/cardvault/MainActivity.kt",
        ).readText()
        val composeSource = TestProjectFiles.resolve(
            "src/main/java/com/pdh/cardvault/CardVaultApp.kt",
        ).readText()

        assertTrue(activitySource.contains("override fun onStart()"))
        assertTrue(activitySource.contains("mainViewModel.onAppForegrounded()"))
        assertTrue(activitySource.contains("override fun onStop()"))
        assertTrue(activitySource.contains("mainViewModel.onAppBackgrounded()"))
        assertFalse(activitySource.contains("recordBackgroundedAt("))
        assertFalse(
            composeSource.contains(
                "Lifecycle.Event.ON_START -> mainViewModel.onAppForegrounded()",
            ),
        )
    }
}
