package com.custom.vox.homemonitor.data.services

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UptimeRobot v2 getMonitors — read-only use of the user-supplied API key.
 */
class UptimeRepository(
    private val config: UptimeConfigStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build(),
) {
    @Volatile
    var lastSnapshot: ServicesSnapshot? = null
        private set

    private val inFlight = AtomicBoolean(false)
    @Volatile
    private var backoffUntilMs: Long = 0L

    fun refresh(): ServicesSnapshot {
        if (!config.isConfigured()) {
            val snap = ServicesSnapshot(
                monitors = emptyList(),
                refreshedAtMs = null,
                configured = false,
            )
            lastSnapshot = snap
            return snap
        }
        if (System.currentTimeMillis() < backoffUntilMs) {
            return lastSnapshot?.copy(error = "Backing off after provider error")
                ?: ServicesSnapshot(emptyList(), null, true, error = "Backing off")
        }
        if (!inFlight.compareAndSet(false, true)) {
            return lastSnapshot ?: ServicesSnapshot(emptyList(), null, true, error = "Busy")
        }
        return try {
            doRefresh()
        } finally {
            inFlight.set(false)
        }
    }

    fun testConnection(apiKey: String): Result<Int> = runCatching {
        val body = FormBody.Builder()
            .add("api_key", apiKey.trim())
            .add("format", "json")
            .add("logs", "0")
            .build()
        val request = Request.Builder().url(API_URL).post(body).build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                error("unauthorized")
            }
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val json = JSONObject(response.body?.string() ?: "{}")
            if (json.optString("stat") != "ok") {
                error(json.optJSONObject("error")?.optString("message") ?: "API error")
            }
            json.optJSONArray("monitors")?.length() ?: 0
        }
    }

    fun listMonitors(apiKey: String? = config.apiKey()): Result<List<UptimeMonitorInfo>> = runCatching {
        val key = apiKey?.trim()?.takeIf { it.isNotEmpty() } ?: error("api_key_required")
        val body = FormBody.Builder()
            .add("api_key", key)
            .add("format", "json")
            .add("logs", "0")
            .build()
        val request = Request.Builder().url(API_URL).post(body).build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) error("unauthorized")
            if (response.code == 429) {
                backoffUntilMs = System.currentTimeMillis() + 60_000L
                error("rate_limited")
            }
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val json = JSONObject(response.body?.string() ?: "{}")
            if (json.optString("stat") != "ok") {
                error(json.optJSONObject("error")?.optString("message") ?: "API error")
            }
            val arr = json.optJSONArray("monitors") ?: return@use emptyList()
            (0 until arr.length()).map { i ->
                val m = arr.getJSONObject(i)
                UptimeMonitorInfo(
                    id = m.getString("id"),
                    name = m.optString("friendly_name").ifBlank { m.getString("id") },
                    status = mapStatus(m.optInt("status", 0)),
                )
            }
        }
    }

    private fun doRefresh(): ServicesSnapshot {
        val ids = config.selectedMonitorIds()
        if (ids.isEmpty()) {
            val snap = ServicesSnapshot(emptyList(), null, false)
            lastSnapshot = snap
            return snap
        }
        val attemptAt = System.currentTimeMillis()
        return try {
            val body = FormBody.Builder()
                .add("api_key", config.apiKey()!!)
                .add("format", "json")
                .add("monitors", ids.joinToString("-"))
                .add("custom_uptime_ratios", "30")
                .build()
            val request = Request.Builder().url(API_URL).post(body).build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    401, 403 -> return staleOrError("unauthorized", attemptAt)
                    429 -> {
                        backoffUntilMs = System.currentTimeMillis() + 60_000L
                        return staleOrError("rate_limited", attemptAt)
                    }
                }
                if (!response.isSuccessful) {
                    return staleOrError("HTTP ${response.code}", attemptAt)
                }
                val json = JSONObject(response.body?.string() ?: "{}")
                if (json.optString("stat") != "ok") {
                    return staleOrError(
                        json.optJSONObject("error")?.optString("message") ?: "API error",
                        attemptAt,
                    )
                }
                val arr = json.optJSONArray("monitors") ?: return staleOrError("No monitors", attemptAt)
                val byId = HashMap<String, JSONObject>()
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    byId[m.getString("id")] = m
                }
                val now = System.currentTimeMillis()
                val monitors = ids.map { id ->
                    mapMonitor(id, config.labelFor(id), byId, now, attemptAt)
                }
                val snap = ServicesSnapshot(
                    monitors = monitors,
                    refreshedAtMs = now,
                    configured = true,
                )
                lastSnapshot = snap
                snap
            }
        } catch (e: Exception) {
            staleOrError(e.message ?: "error", attemptAt)
        }
    }

    private fun mapMonitor(
        id: String,
        fallbackName: String,
        byId: Map<String, JSONObject>,
        now: Long,
        attemptAt: Long,
    ): ServiceMonitor {
        val m = byId[id]
        if (m == null) {
            return ServiceMonitor(
                id = id,
                name = fallbackName,
                status = ServiceStatus.UNKNOWN,
                provider = ServiceProvider.UPTIMEROBOT,
                lastAttemptAtMs = attemptAt,
                error = "Monitor not found",
            )
        }
        val status = mapStatus(m.optInt("status", 0))
        val ratioRaw = m.opt("custom_uptime_ratio")
        val ratio = when (ratioRaw) {
            is Number -> ratioRaw.toDouble()
            is String -> ratioRaw.split('-', ',').firstOrNull()?.toDoubleOrNull()
            else -> null
        } ?: m.optString("all_time_uptime_ratio", "").toDoubleOrNull()
        val name = m.optString("friendly_name").ifBlank { fallbackName }
        return ServiceMonitor(
            id = id,
            name = config.labelFor(id, name),
            status = status,
            provider = ServiceProvider.UPTIMEROBOT,
            uptimeRatio30d = ratio,
            lastSuccessAtMs = now,
            lastAttemptAtMs = attemptAt,
        )
    }

    private fun mapStatus(code: Int): ServiceStatus = mapStatusCode(code)

    private fun staleOrError(message: String, attemptAt: Long): ServicesSnapshot {
        val prev = lastSnapshot
        if (prev != null && prev.configured && prev.monitors.isNotEmpty()) {
            val aged = prev.copy(
                monitors = prev.monitors.map {
                    val status = if (it.status == ServiceStatus.UP) ServiceStatus.STALE else it.status
                    it.copy(status = status, lastAttemptAtMs = attemptAt, error = message)
                },
                error = message,
            )
            lastSnapshot = aged
            return aged
        }
        val snap = ServicesSnapshot(
            monitors = config.selectedMonitorIds().map { id ->
                ServiceMonitor(
                    id = id,
                    name = config.labelFor(id),
                    status = ServiceStatus.UNKNOWN,
                    provider = ServiceProvider.UPTIMEROBOT,
                    lastAttemptAtMs = attemptAt,
                    error = message,
                )
            },
            refreshedAtMs = prev?.refreshedAtMs,
            configured = config.isConfigured(),
            error = message,
        )
        lastSnapshot = snap
        return snap
    }

    companion object {
        const val API_URL = "https://api.uptimerobot.com/v2/getMonitors"
        const val REFRESH_MS = 60_000L
        const val STALE_AFTER_MS = 3 * 60_000L

        /** UptimeRobot monitor status codes → widget status. Failures use UNKNOWN/STALE upstream. */
        fun mapStatusCode(code: Int): ServiceStatus = when (code) {
            2 -> ServiceStatus.UP
            8, 9 -> ServiceStatus.DOWN
            0 -> ServiceStatus.PAUSED
            else -> ServiceStatus.UNKNOWN
        }
    }
}
