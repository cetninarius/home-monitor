package com.custom.vox.homemonitor.data.media

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * YouTube metadata without an API key.
 * Order: oEmbed → Innertube WEB player → thumbnail-only fallback.
 * Cast MediaSessions often omit title/artist; oEmbed is frequently 401.
 */
class YouTubeOEmbedClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) {
    private val memory = ConcurrentHashMap<String, OEmbedResult>()
    private val failures = ConcurrentHashMap<String, Long>()

    fun fetch(videoId: String): OEmbedResult? {
        if (!VIDEO_ID.matches(videoId)) return null
        memory[videoId]?.let { return it }
        val failedAt = failures[videoId]
        if (failedAt != null && System.currentTimeMillis() - failedAt < FAIL_COOLDOWN_MS) {
            return null
        }
        val result = fetchOEmbed(videoId) ?: fetchInnertube(videoId)
        if (result == null) {
            failures[videoId] = System.currentTimeMillis()
            return null
        }
        val normalized = result.copy(
            thumbnailUrl = result.thumbnailUrl ?: thumbnailUrlFor(videoId),
        )
        memory[videoId] = normalized
        failures.remove(videoId)
        return normalized
    }

    private fun fetchOEmbed(videoId: String): OEmbedResult? {
        val watch = "https://www.youtube.com/watch?v=$videoId"
        val encoded = URLEncoder.encode(watch, StandardCharsets.UTF_8.name())
        return fetchJsonGet(
            "https://www.youtube.com/oembed?format=json&url=$encoded",
        )?.let { json ->
            OEmbedResult(
                title = json.optString("title").ifBlank { null },
                authorName = json.optString("author_name").ifBlank { null },
                thumbnailUrl = json.optString("thumbnail_url").ifBlank { null },
            ).takeIf { it.title != null || it.authorName != null }
        }
    }

    private fun fetchInnertube(videoId: String): OEmbedResult? {
        return try {
            val body = JSONObject()
                .put(
                    "context",
                    JSONObject().put(
                        "client",
                        JSONObject()
                            .put("clientName", "WEB")
                            .put("clientVersion", "2.20241101.00.00")
                            .put("hl", "en")
                            .put("gl", "US"),
                    ),
                )
                .put("videoId", videoId)
                .toString()
                .toRequestBody(JSON_MEDIA)
            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .post(body)
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "innertube HTTP ${resp.code}")
                    return null
                }
                val raw = resp.body?.string().orEmpty()
                if (raw.isBlank()) return null
                val details = JSONObject(raw).optJSONObject("videoDetails") ?: return null
                val title = details.optString("title").ifBlank { null }
                val author = details.optString("author").ifBlank { null }
                if (title == null && author == null) return null
                OEmbedResult(
                    title = title,
                    authorName = author,
                    thumbnailUrl = thumbnailUrlFor(videoId),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "innertube failed: ${e.message}")
            null
        }
    }

    private fun fetchJsonGet(url: String): JSONObject? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "HTTP ${resp.code} for $url")
                    return null
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank() || body.equals("Unauthorized", ignoreCase = true)) return null
                JSONObject(body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "GET failed $url: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "YouTubeOEmbed"
        private const val FAIL_COOLDOWN_MS = 60_000L
        private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{6,20}$")
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 9; TV) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        fun thumbnailUrlFor(videoId: String): String =
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }
}
