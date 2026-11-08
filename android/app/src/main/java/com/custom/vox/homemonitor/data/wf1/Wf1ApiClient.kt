package com.custom.vox.homemonitor.data.wf1

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class Wf1ApiClient(
    private val config: Wf1ConfigStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build(),
) {

    fun fetchQueue(): Wf1QueueSnapshot {
        val baseUrl = config.baseUrl()
            ?: throw Wf1NotConfiguredException("WF1 proxy URL not configured")
        val token = config.token()
            ?: throw Wf1NotConfiguredException("WF1 proxy token not configured")

        val request = Request.Builder()
            .url("$baseUrl/v1/wf1/queue")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Wf1ApiException("HTTP ${response.code}")
            }
            val body = response.body?.string()
                ?: throw Wf1ApiException("Empty body")
            return parseSnapshot(JSONObject(body))
        }
    }

    private fun parseSnapshot(json: JSONObject): Wf1QueueSnapshot {
        val connection = when (json.optString("connection", "down").lowercase()) {
            "ok" -> ConnectionState.OK
            "stale" -> ConnectionState.STALE
            else -> ConnectionState.DOWN
        }
        val countsJson = json.optJSONObject("counts") ?: JSONObject()
        val counts = Wf1Counts(
            running = countsJson.optInt("running", 0),
            queued = countsJson.optInt("queued", 0),
            blocked = countsJson.optInt("blocked", 0),
            readyForReview = countsJson.optInt("readyForReview", 0),
        )
        val activeJson = json.optJSONObject("active")
        val active = if (activeJson != null && !activeJson.optBoolean("idle", false)) {
            Wf1ActiveTask(
                issueNumber = activeJson.optIntOrNull("issueNumber"),
                title = activeJson.optStringOrNull("title"),
                status = activeJson.optString("status", "unconfirmed"),
                showSpinner = activeJson.optBoolean("showSpinner", false),
                stage = activeJson.optStringOrNull("stage"),
                elapsedMs = if (activeJson.has("elapsedMs") && !activeJson.isNull("elapsedMs")) {
                    activeJson.optLong("elapsedMs")
                } else null,
                errorSummary = activeJson.optStringOrNull("errorSummary"),
                statusConfirmed = activeJson.optBoolean("statusConfirmed", false),
            )
        } else null

        return Wf1QueueSnapshot(
            refreshedAt = json.optStringOrNull("refreshedAt"),
            connection = connection,
            counts = counts,
            active = active,
            queued = parseItems(json.optJSONArray("queued")),
            blocked = parseItems(json.optJSONArray("blocked")),
            readyForReview = parseItems(json.optJSONArray("readyForReview")),
            recentDone = parseItems(json.optJSONArray("recentDone")),
        )
    }

    private fun parseItems(array: JSONArray?): List<Wf1TaskItem> {
        if (array == null) return emptyList()
        val out = ArrayList<Wf1TaskItem>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            out.add(
                Wf1TaskItem(
                    issueNumber = o.optIntOrNull("issueNumber") ?: o.optIntOrNull("number"),
                    title = o.optStringOrNull("title"),
                    status = o.optString("status", "queued"),
                    priority = o.optStringOrNull("priority"),
                    queuePosition = o.optIntOrNull("queuePosition"),
                    detail = o.optStringOrNull("detail"),
                    errorSummary = o.optStringOrNull("errorSummary"),
                ),
            )
        }
        return out
    }
}

class Wf1ApiException(message: String) : Exception(message)

class Wf1NotConfiguredException(message: String) : Exception(message)

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val v = optString(key)
    return v.ifBlank { null }
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return optInt(key)
}
