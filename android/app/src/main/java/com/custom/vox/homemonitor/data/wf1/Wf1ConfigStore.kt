package com.custom.vox.homemonitor.data.wf1

import android.content.Context
import com.custom.vox.homemonitor.BuildConfig
import com.custom.vox.homemonitor.data.secure.SecretStore

/**
 * Runtime WF1 proxy URL + token. BuildConfig values are only used as a one-time
 * upgrade seed — never as the live source of truth after migration.
 */
class Wf1ConfigStore(
    context: Context,
    private val secrets: SecretStore = SecretStore(context),
) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isConfigured(): Boolean =
        !baseUrl().isNullOrBlank() && !token().isNullOrBlank()

    fun baseUrl(): String? =
        prefs.getString(KEY_BASE_URL, null)?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }

    fun token(): String? = secrets.getString(SECRET_TOKEN)

    fun setBaseUrl(url: String?) {
        val cleaned = url?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
        prefs.edit().apply {
            if (cleaned == null) remove(KEY_BASE_URL) else putString(KEY_BASE_URL, cleaned)
        }.commit()
    }

    fun setToken(token: String?) {
        secrets.putString(SECRET_TOKEN, token?.trim()?.takeIf { it.isNotBlank() })
    }

    fun clear() {
        setBaseUrl(null)
        setToken(null)
    }

    /**
     * One-time seed from compile-time BuildConfig for upgrades that still
     * ship owner LAN values. Returns true if anything was written.
     *
     * Placeholder tokens are still seeded when a URL is present — the owner
     * proxy may legitimately use the same default shared secret.
     */
    fun seedFromBuildConfigIfEmpty(): Boolean {
        if (isConfigured()) return false
        val url = BuildConfig.WF1_PROXY_BASE_URL.trim().trimEnd('/')
        val tok = BuildConfig.WF1_PROXY_TOKEN.trim()
        if (url.isBlank()) return false
        var wrote = false
        if (baseUrl().isNullOrBlank()) {
            setBaseUrl(url)
            wrote = true
        }
        if (token().isNullOrBlank() && tok.isNotBlank()) {
            setToken(tok)
            wrote = true
        }
        return wrote
    }

    fun publicConfig(): Map<String, Any?> = mapOf(
        "configured" to isConfigured(),
        "baseUrlSet" to !baseUrl().isNullOrBlank(),
        "tokenSet" to !token().isNullOrBlank(),
        "baseUrl" to baseUrl(), // URL is not secret; token never returned
    )

    companion object {
        private const val PREFS = "home_monitor_wf1"
        private const val KEY_BASE_URL = "proxy_base_url"
        private const val SECRET_TOKEN = "wf1_proxy_token"

        fun isPlaceholderToken(token: String): Boolean {
            val t = token.trim()
            return t.isEmpty() ||
                t.equals("change-me-home-monitor-token", ignoreCase = true) ||
                t.equals("changeme", ignoreCase = true)
        }
    }
}
