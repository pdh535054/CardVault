package com.pdh.cardvault.data

import android.content.Context
import androidx.core.content.edit

interface StartupAuthenticationStore {
    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean)
}

class SharedPreferencesStartupAuthenticationStore(
    context: Context,
) : StartupAuthenticationStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        NON_SENSITIVE_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    override fun setEnabled(enabled: Boolean) {
        preferences.edit {
            putBoolean(KEY_ENABLED, enabled)
        }
    }

    private companion object {
        const val KEY_ENABLED = "startup_authentication_enabled"
    }
}
