package com.custom.vox.homemonitor.data.settings

import android.content.Context
import android.content.pm.PackageManager
import com.custom.vox.homemonitor.BuildConfig
import com.custom.vox.homemonitor.data.wf1.Wf1ConfigStore

/**
 * Widget visibility and integration enable flags.
 * Fresh installs default WF1 off; upgrades migrate owner WF1 + easter-egg state once.
 */
class DashboardPrefs(context: Context) {
    private val appContext = context.applicationContext
    private val prefs =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun servicesEnabled(): Boolean = prefs.getBoolean(KEY_SERVICES, true)

    fun nowPlayingEnabled(): Boolean = prefs.getBoolean(KEY_NOW_PLAYING, true)

    fun wf1Enabled(): Boolean = prefs.getBoolean(KEY_WF1, false)

    fun smartTubeEasterEggConsumed(): Boolean =
        prefs.getBoolean(KEY_ST_EGG_CONSUMED, false)

    fun setServicesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICES, enabled).commit()
    }

    fun setNowPlayingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOW_PLAYING, enabled).commit()
    }

    fun setWf1Enabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WF1, enabled).commit()
    }

    fun markSmartTubeEasterEggConsumed() {
        prefs.edit().putBoolean(KEY_ST_EGG_CONSUMED, true).commit()
    }

    /**
     * One-shot BF3 migration. Safe to call on every cold start.
     */
    fun migrateBf3IfNeeded(wf1Config: Wf1ConfigStore) {
        if (prefs.getInt(KEY_SCHEMA, 0) >= SCHEMA_BF3) return

        val upgrade = isUpgradeInstall()
        val seeded = wf1Config.seedFromBuildConfigIfEmpty()
        val buildUrl = BuildConfig.WF1_PROXY_BASE_URL.trim()
        val hasBuildUrl = buildUrl.isNotBlank()
        val shouldEnableWf1 = upgrade && (seeded || wf1Config.isConfigured() || hasBuildUrl)

        val ed = prefs.edit()
        if (!prefs.contains(KEY_WF1)) {
            ed.putBoolean(KEY_WF1, shouldEnableWf1)
        }
        if (upgrade && !prefs.contains(KEY_ST_EGG_CONSUMED)) {
            ed.putBoolean(KEY_ST_EGG_CONSUMED, true)
        }
        ed.putInt(KEY_SCHEMA, SCHEMA_BF3)
        ed.commit()
    }

    fun publicConfig(): Map<String, Any?> = mapOf(
        "servicesEnabled" to servicesEnabled(),
        "nowPlayingEnabled" to nowPlayingEnabled(),
        "wf1Enabled" to wf1Enabled(),
    )

    private fun isUpgradeInstall(): Boolean {
        return try {
            val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            info.lastUpdateTime > info.firstInstallTime
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val PREFS = "home_monitor_dashboard"
        private const val KEY_SERVICES = "widget_services_enabled"
        private const val KEY_NOW_PLAYING = "widget_now_playing_enabled"
        private const val KEY_WF1 = "widget_wf1_enabled"
        private const val KEY_ST_EGG_CONSUMED = "smarttube_easter_egg_consumed"
        private const val KEY_SCHEMA = "dashboard_prefs_schema"
        const val SCHEMA_BF3 = 1
    }
}
