package com.custom.vox.homemonitor.data.services

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class HttpEndpoint(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
)

class HttpEndpointStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(): List<HttpEndpoint> {
        val raw = prefs.getString(KEY_ENDPOINTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                HttpEndpoint(
                    id = o.getString("id"),
                    name = o.optString("name").ifBlank { o.optString("url") },
                    url = o.getString("url"),
                    enabled = o.optBoolean("enabled", true),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun get(id: String): HttpEndpoint? = list().firstOrNull { it.id == id }

    fun upsert(endpoint: HttpEndpoint): HttpEndpoint {
        HttpUrlValidator.validate(endpoint.url).getOrElse { throw IllegalArgumentException(it.message) }
        val current = list().toMutableList()
        val idx = current.indexOfFirst { it.id == endpoint.id }
        if (idx >= 0) {
            current[idx] = endpoint
        } else {
            require(current.size < MAX_ENDPOINTS) { "max_endpoints" }
            current.add(endpoint)
        }
        persist(current)
        return endpoint
    }

    fun add(name: String, url: String, enabled: Boolean = true): HttpEndpoint {
        require(list().size < MAX_ENDPOINTS) { "max_endpoints" }
        HttpUrlValidator.validate(url).getOrElse { throw IllegalArgumentException(it.message) }
        val ep = HttpEndpoint(
            id = UUID.randomUUID().toString().replace("-", "").take(12),
            name = name.trim().ifBlank { url.trim() },
            url = url.trim(),
            enabled = enabled,
        )
        return upsert(ep)
    }

    fun delete(id: String): Boolean {
        val next = list().filterNot { it.id == id }
        if (next.size == list().size) return false
        persist(next)
        return true
    }

    fun clear() {
        prefs.edit().remove(KEY_ENDPOINTS).commit()
    }

    private fun persist(items: List<HttpEndpoint>) {
        val arr = JSONArray()
        items.forEach { ep ->
            arr.put(
                JSONObject()
                    .put("id", ep.id)
                    .put("name", ep.name)
                    .put("url", ep.url)
                    .put("enabled", ep.enabled),
            )
        }
        prefs.edit().putString(KEY_ENDPOINTS, arr.toString()).commit()
    }

    companion object {
        private const val PREFS = "home_monitor_http_monitors"
        private const val KEY_ENDPOINTS = "endpoints"
        const val MAX_ENDPOINTS = 5
    }
}
