package com.custom.vox.homemonitor.data.services

import android.content.Context
import com.custom.vox.homemonitor.data.secure.SecretStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * UptimeRobot configuration. API key lives in [SecretStore]; selection/labels in plain prefs.
 * Migrates legacy plaintext key + A/B monitor IDs once.
 */
class UptimeConfigStore(
    context: Context,
    private val secrets: SecretStore = SecretStore(context),
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        migrateFromLegacyIfNeeded()
    }

    fun isConfigured(): Boolean =
        !apiKey().isNullOrBlank() && selectedMonitorIds().isNotEmpty()

    fun apiKey(): String? = secrets.getString(SECRET_API_KEY)

    fun selectedMonitorIds(): List<String> {
        val raw = prefs.getString(KEY_SELECTED_IDS, null)
        if (!raw.isNullOrBlank()) {
            return parseIdList(raw)
        }
        // Legacy fallback during read before migration completed
        return listOfNotNull(prefs.getString(KEY_ID_A, null), prefs.getString(KEY_ID_B, null))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun labelFor(monitorId: String, fallback: String = monitorId): String {
        val map = labelsMap()
        return map[monitorId]?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun labelsMap(): Map<String, String> {
        val raw = prefs.getString(KEY_LABELS_JSON, null) ?: return legacyLabelsMap()
        return try {
            val obj = JSONObject(raw)
            val out = LinkedHashMap<String, String>()
            obj.keys().forEach { key ->
                val v = obj.optString(key)
                if (v.isNotBlank()) out[key] = v
            }
            out
        } catch (_: Exception) {
            legacyLabelsMap()
        }
    }

    fun setApiKey(key: String) {
        secrets.putString(SECRET_API_KEY, key.trim())
    }

    fun clearApiKey() {
        secrets.remove(SECRET_API_KEY)
    }

    fun saveSelection(ids: List<String>, labels: Map<String, String> = emptyMap()) {
        val cleaned = ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            .take(MAX_SELECTED)
        val labelObj = JSONObject()
        labels.forEach { (id, label) ->
            if (id !in cleaned) return@forEach
            val t = label.trim()
            if (t.isNotEmpty()) labelObj.put(id, t)
        }
        prefs.edit()
            .putString(KEY_SELECTED_IDS, JSONArray(cleaned).toString())
            .putString(KEY_LABELS_JSON, labelObj.toString())
            .remove(KEY_ID_A)
            .remove(KEY_ID_B)
            .remove(KEY_LABEL_A)
            .remove(KEY_LABEL_B)
            .commit()
    }

    /** Legacy 2-slot save used by older admin UI during transition. */
    fun save(
        apiKey: String,
        monitorIdA: String,
        monitorIdB: String,
        labelA: String?,
        labelB: String?,
    ) {
        setApiKey(apiKey)
        val labels = LinkedHashMap<String, String>()
        if (!labelA.isNullOrBlank()) labels[monitorIdA.trim()] = labelA.trim()
        if (!labelB.isNullOrBlank()) labels[monitorIdB.trim()] = labelB.trim()
        saveSelection(listOf(monitorIdA, monitorIdB), labels)
    }

    fun disconnect() {
        clearApiKey()
        prefs.edit().clear().commit()
    }

    fun clear() = disconnect()

    fun publicConfig(): Map<String, Any?> {
        val ids = selectedMonitorIds()
        val labels = labelsMap()
        return mapOf(
            "configured" to isConfigured(),
            "apiKeySet" to !apiKey().isNullOrBlank(),
            "selectedMonitorIds" to ids,
            "labels" to labels,
            // Legacy fields for older admin UI
            "monitorIdA" to ids.getOrNull(0),
            "monitorIdB" to ids.getOrNull(1),
            "labelA" to ids.getOrNull(0)?.let { labels[it] },
            "labelB" to ids.getOrNull(1)?.let { labels[it] },
        )
    }

    // Legacy accessors for transitional code
    fun monitorIdA(): String? = selectedMonitorIds().getOrNull(0)
    fun monitorIdB(): String? = selectedMonitorIds().getOrNull(1)
    fun labelA(): String = monitorIdA()?.let { labelFor(it, "Service A") } ?: "Service A"
    fun labelB(): String = monitorIdB()?.let { labelFor(it, "Service B") } ?: "Service B"

    private fun legacyLabelsMap(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val a = prefs.getString(KEY_ID_A, null)
        val b = prefs.getString(KEY_ID_B, null)
        val la = prefs.getString(KEY_LABEL_A, null)
        val lb = prefs.getString(KEY_LABEL_B, null)
        if (!a.isNullOrBlank() && !la.isNullOrBlank()) out[a] = la
        if (!b.isNullOrBlank() && !lb.isNullOrBlank()) out[b] = lb
        return out
    }

    private fun migrateFromLegacyIfNeeded() {
        val legacyKey = prefs.getString(KEY_API, null)?.takeIf { it.isNotBlank() }
        if (legacyKey != null && apiKey().isNullOrBlank()) {
            setApiKey(legacyKey)
            prefs.edit().remove(KEY_API).commit()
        }
        if (prefs.getString(KEY_SELECTED_IDS, null).isNullOrBlank()) {
            val a = prefs.getString(KEY_ID_A, null)
            val b = prefs.getString(KEY_ID_B, null)
            val ids = listOfNotNull(a, b).map { it.trim() }.filter { it.isNotEmpty() }
            if (ids.isNotEmpty()) {
                saveSelection(ids, legacyLabelsMap())
            }
        }
    }

    private fun parseIdList(raw: String): List<String> = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            arr.optString(i)?.trim()?.takeIf { it.isNotEmpty() }
        }
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        private const val PREFS = "home_monitor_uptime"
        private const val SECRET_API_KEY = "uptimerobot_api_key"
        private const val KEY_API = "api_key" // legacy plaintext — migrated away
        private const val KEY_ID_A = "monitor_id_a"
        private const val KEY_ID_B = "monitor_id_b"
        private const val KEY_LABEL_A = "label_a"
        private const val KEY_LABEL_B = "label_b"
        private const val KEY_SELECTED_IDS = "selected_monitor_ids"
        private const val KEY_LABELS_JSON = "monitor_labels_json"
        const val MAX_SELECTED = 2
    }
}
