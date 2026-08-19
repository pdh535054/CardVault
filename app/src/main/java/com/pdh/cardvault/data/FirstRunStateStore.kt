package com.pdh.cardvault.data

import android.content.Context
import androidx.core.content.edit

interface FirstRunStateStore {
    fun isSecurityNoticeAcknowledged(): Boolean

    fun acknowledgeSecurityNotice()
}

class SharedPreferencesFirstRunStateStore(
    context: Context,
) : FirstRunStateStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        NON_SENSITIVE_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun isSecurityNoticeAcknowledged(): Boolean =
        preferences.getBoolean(KEY_SECURITY_NOTICE_ACKNOWLEDGED, false)

    override fun acknowledgeSecurityNotice() {
        preferences.edit {
            putBoolean(KEY_SECURITY_NOTICE_ACKNOWLEDGED, true)
        }
    }

    private companion object {
        const val KEY_SECURITY_NOTICE_ACKNOWLEDGED = "security_notice_acknowledged"
    }
}

internal const val NON_SENSITIVE_PREFERENCES_NAME = "cardvault_non_sensitive_state"
