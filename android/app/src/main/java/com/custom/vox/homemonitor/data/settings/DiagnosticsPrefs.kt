package com.custom.vox.homemonitor.data.settings

import android.content.Context

/**
 * Opt-in crash/diagnostic reporting. Off by default for beta privacy.
 */
class DiagnosticsPrefs(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).commit()
    }

    companion object {
        private const val PREFS = "home_monitor_diagnostics"
        private const val KEY_ENABLED = "crash_reporting_enabled"
    }
}
