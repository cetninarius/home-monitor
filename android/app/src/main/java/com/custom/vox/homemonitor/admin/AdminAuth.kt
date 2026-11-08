package com.custom.vox.homemonitor.admin

import android.content.Context
import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Admin token + short-lived session store.
 * Token is generated once and never embedded in static HTML/JS.
 */
class AdminAuth(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val sessions = ConcurrentHashMap<String, Long>()
    private val rateBucket = ConcurrentHashMap<String, MutableList<Long>>()
    private val random = SecureRandom()

    fun getOrCreateToken(): String {
        val existing = prefs.getString(KEY_TOKEN, null)
        if (!existing.isNullOrBlank() && isReadableToken(existing)) return existing
        // Migrate legacy long hex tokens to short TV-friendly form.
        // Existing readable tokens (including older 4-4) are preserved on upgrade.
        return regenerateToken(reason = if (existing.isNullOrBlank()) "first-run" else "migrate-format")
    }

    /**
     * Token is stable until regenerated. Regenerating invalidates all web sessions —
     * use when the old token was lost or leaked.
     *
     * Format: `A356-Q49G-K2M7` — three groups of 4 chars (new tokens).
     * Older `XXXX-XXXX` tokens remain valid until the user regenerates.
     */
    fun regenerateToken(reason: String = "manual"): String {
        val token = buildReadableToken()
        sessions.clear()
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putBoolean(KEY_TOKEN_REVEALED, true)
            .putBoolean(KEY_HINT_DISMISSED, false)
            .putLong(KEY_CREATED_AT, System.currentTimeMillis())
            .apply()
        Log.i(TAG, "Admin token regenerated ($reason)")
        return token
    }

    private fun buildReadableToken(): String {
        val chars = CHARSET
        fun group(n: Int): String = buildString(n) {
            repeat(n) { append(chars[random.nextInt(chars.length)]) }
        }
        return "${group(4)}-${group(4)}-${group(4)}"
    }

    private fun isReadableToken(token: String): Boolean =
        token.matches(READABLE_TOKEN_REGEX)

    fun shouldRevealTokenOnTv(): Boolean =
        prefs.getBoolean(KEY_TOKEN_REVEALED, false) && !isHintDismissed()

    fun isTokenVisibleOnTv(): Boolean = prefs.getBoolean(KEY_TOKEN_VISIBLE, false)

    fun setTokenVisibleOnTv(visible: Boolean) {
        prefs.edit().putBoolean(KEY_TOKEN_VISIBLE, visible).apply()
    }

    fun markTokenSeenOnTv() {
        prefs.edit().putBoolean(KEY_TOKEN_REVEALED, false).apply()
    }

    fun isHintDismissed(): Boolean = prefs.getBoolean(KEY_HINT_DISMISSED, false)

    fun dismissHint() {
        prefs.edit()
            .putBoolean(KEY_HINT_DISMISSED, true)
            .putBoolean(KEY_TOKEN_REVEALED, false)
            .apply()
    }

    fun validateBearer(header: String?): Boolean {
        if (header.isNullOrBlank()) return false
        val prefix = "Bearer "
        if (!header.startsWith(prefix)) return false
        return constantTimeEquals(header.substring(prefix.length), getOrCreateToken())
    }

    fun loginWithToken(token: String?): String? {
        if (token.isNullOrBlank()) return null
        if (!constantTimeEquals(token, getOrCreateToken())) return null
        val sessionId = newId()
        sessions[sessionId] = System.currentTimeMillis() + SESSION_TTL_MS
        return sessionId
    }

    fun validateSession(sessionId: String?): Boolean {
        if (sessionId.isNullOrBlank()) return false
        val exp = sessions[sessionId] ?: return false
        if (exp < System.currentTimeMillis()) {
            sessions.remove(sessionId)
            return false
        }
        // sliding expiry
        sessions[sessionId] = System.currentTimeMillis() + SESSION_TTL_MS
        return true
    }

    fun logout(sessionId: String?) {
        if (!sessionId.isNullOrBlank()) sessions.remove(sessionId)
    }

    fun allowRequest(clientKey: String, now: Long = System.currentTimeMillis()): Boolean {
        val window = rateBucket.getOrPut(clientKey) { mutableListOf() }
        synchronized(window) {
            window.removeAll { it < now - RATE_WINDOW_MS }
            if (window.size >= RATE_LIMIT) return false
            window.add(now)
            return true
        }
    }

    private fun newId(): String {
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "HomeMonitorAdmin"
        private const val PREFS = "home_monitor_admin"
        private const val KEY_TOKEN = "admin_token"
        private const val KEY_TOKEN_REVEALED = "token_reveal_on_tv"
        private const val KEY_TOKEN_VISIBLE = "token_visible_on_tv"
        private const val KEY_HINT_DISMISSED = "hint_dismissed"
        private const val KEY_CREATED_AT = "token_created_at"
        private const val SESSION_TTL_MS = 12 * 60 * 60 * 1000L
        private const val RATE_WINDOW_MS = 60_000L
        private const val RATE_LIMIT = 120
        private const val CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no I/O/0/1 — easier to read on TV
        // Accept legacy 2-group and new 3-group readable tokens.
        private val READABLE_TOKEN_REGEX = Regex("^[A-Z0-9]{4}-[A-Z0-9]{4}(-[A-Z0-9]{4})?$")

        fun constantTimeEquals(a: String, b: String): Boolean {
            val left = a.toByteArray(Charsets.UTF_8)
            val right = b.toByteArray(Charsets.UTF_8)
            // MessageDigest.isEqual is constant-time for equal-length arrays;
            // unequal lengths still return false without short-circuiting content.
            if (left.size != right.size) {
                // Still compare against self-sized buffer to avoid trivial timing on length.
                MessageDigest.isEqual(left, left)
                return false
            }
            return MessageDigest.isEqual(left, right)
        }
    }
}
