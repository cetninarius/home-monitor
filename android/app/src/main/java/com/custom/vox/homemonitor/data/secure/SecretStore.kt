package com.custom.vox.homemonitor.data.secure

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Keystore-backed preferences for secrets. Falls back to private prefs only if
 * encryption cannot be initialized (should be rare); callers still treat values as secrets.
 */
class SecretStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = createPrefs(appContext)

    fun getString(key: String): String? =
        prefs.getString(key, null)?.takeIf { it.isNotBlank() }

    fun putString(key: String, value: String?) {
        prefs.edit().apply {
            if (value.isNullOrBlank()) remove(key) else putString(key, value)
        }.commit()
    }

    fun remove(key: String) {
        prefs.edit().remove(key).commit()
    }

    companion object {
        private const val TAG = "SecretStore"
        private const val FILE = "home_monitor_secrets"

        private fun createPrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (e: Exception) {
                Log.e(TAG, "EncryptedSharedPreferences unavailable; using private prefs fallback")
                context.getSharedPreferences("${FILE}_fallback", Context.MODE_PRIVATE)
            }
        }
    }
}
