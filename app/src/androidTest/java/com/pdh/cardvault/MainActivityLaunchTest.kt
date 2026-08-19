package com.pdh.cardvault

import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {
    @Test
    fun mainActivityStartsWithSecureWindow() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(
                    activity.window.attributes.flags and
                        WindowManager.LayoutParams.FLAG_SECURE != 0,
                )
                assertTrue(
                    activity.window.decorView.importantForAutofill ==
                        View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,
                )
                assertTrue(
                    activity.window.decorView.importantForContentCapture ==
                        View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS,
                )
            }
        }
    }

    @Test
    fun authenticatedForegroundSessionCanClearAndRestoreSecureWindowFlag() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setAuthenticatedScreenCaptureAllowed(true)
                assertFalse(
                    activity.window.attributes.flags and
                        WindowManager.LayoutParams.FLAG_SECURE != 0,
                )

                activity.setAuthenticatedScreenCaptureAllowed(false)
                assertTrue(
                    activity.window.attributes.flags and
                        WindowManager.LayoutParams.FLAG_SECURE != 0,
                )
            }
        }
    }

    @Test
    fun stoppedActivityDoesNotRestoreScreenCapturePermissionWhenResumed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setAuthenticatedScreenCaptureAllowed(true)
                assertFalse(
                    activity.window.attributes.flags and
                        WindowManager.LayoutParams.FLAG_SECURE != 0,
                )
            }

            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)

            scenario.onActivity { activity ->
                assertTrue(
                    activity.window.attributes.flags and
                        WindowManager.LayoutParams.FLAG_SECURE != 0,
                )
            }
        }
    }
}
