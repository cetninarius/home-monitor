package com.custom.vox.homemonitor.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.provider.Settings
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Reads SmartTube MediaSession metadata when notification listener access is granted.
 */
class SmartTubeSessionReader(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private var lastGoodPositionMs: Long = -1L
    private var lastGoodPositionAtElapsed: Long = 0L
    private var lastArtUri: String? = null
    private var lastArtReachable: Boolean? = null
    private var lastArtStatus: String? = null

    fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val cn = ComponentName(context, MediaSessionDiagService::class.java).flattenToString()
        return flat.split(':').any { it.equals(cn, ignoreCase = true) }
    }

    fun read(): SmartTubeSessionSnapshot {
        val enabled = isNotificationListenerEnabled()
        if (!enabled) {
            return SmartTubeSessionSnapshot(
                listenerEnabled = false,
                sessionFound = false,
                playbackState = "NO_LISTENER",
                playbackStateCode = -1,
                title = null,
                artist = null,
                durationMs = -1L,
                reportedPositionMs = -1L,
                estimatedPositionMs = null,
                positionSource = "none",
                albumArtUri = null,
                artUriReachable = null,
                artUriStatus = null,
                error = "Enable notification access for Home Monitor",
            )
        }

        return try {
            val mgr = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val listener = ComponentName(context, MediaSessionDiagService::class.java)
            val sessions = mgr.getActiveSessions(listener)
            val controller = sessions.firstOrNull { it.packageName == TARGET_PACKAGE }
            if (controller == null) {
                resetEstimate()
                SmartTubeSessionSnapshot(
                    listenerEnabled = true,
                    sessionFound = false,
                    playbackState = "NO_SESSION",
                    playbackStateCode = -1,
                    title = null,
                    artist = null,
                    durationMs = -1L,
                    reportedPositionMs = -1L,
                    estimatedPositionMs = null,
                    positionSource = "none",
                    albumArtUri = null,
                    artUriReachable = null,
                    artUriStatus = null,
                )
            } else {
                snapshotFrom(controller)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "getActiveSessions denied", e)
            SmartTubeSessionSnapshot(
                listenerEnabled = enabled,
                sessionFound = false,
                playbackState = "SECURITY",
                playbackStateCode = -1,
                title = null,
                artist = null,
                durationMs = -1L,
                reportedPositionMs = -1L,
                estimatedPositionMs = null,
                positionSource = "none",
                albumArtUri = null,
                artUriReachable = null,
                artUriStatus = null,
                error = e.message,
            )
        }
    }

    fun probeArtUri(uri: String?): Pair<Boolean?, String?> {
        if (uri.isNullOrBlank()) return null to null
        if (uri == lastArtUri && lastArtStatus != null) {
            return lastArtReachable to lastArtStatus
        }
        return try {
            val req = Request.Builder().url(uri).head().build()
            http.newCall(req).execute().use { resp ->
                val ok = resp.isSuccessful || resp.code in 200..399
                val status = "HTTP ${resp.code}"
                lastArtUri = uri
                lastArtReachable = ok
                lastArtStatus = status
                ok to status
            }
        } catch (e: Exception) {
            // Some CDNs reject HEAD — try GET range
            try {
                val req = Request.Builder()
                    .url(uri)
                    .header("Range", "bytes=0-0")
                    .get()
                    .build()
                http.newCall(req).execute().use { resp ->
                    val ok = resp.isSuccessful || resp.code == 206
                    val status = "GET ${resp.code}"
                    lastArtUri = uri
                    lastArtReachable = ok
                    lastArtStatus = status
                    ok to status
                }
            } catch (e2: Exception) {
                lastArtUri = uri
                lastArtReachable = false
                lastArtStatus = e2.javaClass.simpleName + ": " + (e2.message ?: "")
                false to lastArtStatus
            }
        }
    }

    private fun snapshotFrom(controller: MediaController): SmartTubeSessionSnapshot {
        val state = controller.playbackState
        val meta = controller.metadata
        val stateCode = state?.state ?: -1
        val stateName = stateName(stateCode)
        val reported = state?.position ?: PlaybackState.PLAYBACK_POSITION_UNKNOWN
        val duration = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val artUri = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)

        if (meta != null) {
            val keys = meta.keySet().sorted()
            Log.i(TAG, "metadataKeys=$keys")
            for (key in keys) {
                val asString = meta.getString(key)
                val line = when {
                    asString != null -> asString
                    meta.getBitmap(key) != null -> "(bitmap)"
                    else -> "long=${meta.getLong(key)}"
                }
                Log.i(TAG, "meta[$key]=$line")
            }
        }

        val (est, source) = estimatePosition(
            reported = reported,
            playing = stateCode == PlaybackState.STATE_PLAYING,
            speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            updateTime = state?.lastPositionUpdateTime ?: 0L,
        )

        val (reachable, status) = probeArtUri(artUri)

        Log.i(
            TAG,
            "state=$stateName title=$title artist=$artist dur=$duration pos=$reported est=$est art=$artUri",
        )

        return SmartTubeSessionSnapshot(
            listenerEnabled = true,
            sessionFound = true,
            playbackState = stateName,
            playbackStateCode = stateCode,
            title = title,
            artist = artist,
            durationMs = duration,
            reportedPositionMs = reported,
            estimatedPositionMs = est,
            positionSource = source,
            albumArtUri = artUri,
            artUriReachable = reachable,
            artUriStatus = status,
        )
    }

    /**
     * SmartTube often reports position 0 or negative. Prefer framework-adjusted position
     * when playing; otherwise extrapolate from the last sane sample.
     */
    private fun estimatePosition(
        reported: Long,
        playing: Boolean,
        speed: Float,
        updateTime: Long,
    ): Pair<Long?, String> {
        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        val frameworkAdjusted = if (playing && reported >= 0 && updateTime > 0L) {
            val delta = nowElapsed - updateTime
            (reported + (delta * speed).toLong()).coerceAtLeast(0L)
        } else {
            null
        }

        val saneReported = reported >= 0 &&
            (lastGoodPositionMs < 0 || reported >= lastGoodPositionMs - 2_000L)

        if (saneReported && reported > 0) {
            lastGoodPositionMs = reported
            lastGoodPositionAtElapsed = nowElapsed
            if (frameworkAdjusted != null && kotlin.math.abs(frameworkAdjusted - reported) < 2_000L) {
                return frameworkAdjusted to "framework+reported"
            }
            return reported to "reported"
        }

        if (frameworkAdjusted != null && reported == 0L) {
            // position stuck at 0 — still try framework clock if updateTime advances
            if (updateTime != lastGoodPositionAtElapsed || lastGoodPositionMs < 0) {
                // keep estimate from wall clock against last good
            }
        }

        if (playing && lastGoodPositionMs >= 0) {
            val delta = nowElapsed - lastGoodPositionAtElapsed
            val est = (lastGoodPositionMs + (delta * speed).toLong()).coerceAtLeast(0L)
            return est to "extrapolated"
        }

        if (reported < 0) {
            return null to "invalid_reported"
        }

        return reported to "reported_zero"
    }

    private fun resetEstimate() {
        lastGoodPositionMs = -1L
        lastGoodPositionAtElapsed = 0L
    }

    private fun stateName(code: Int): String = when (code) {
        PlaybackState.STATE_NONE -> "NONE"
        PlaybackState.STATE_STOPPED -> "STOPPED"
        PlaybackState.STATE_PAUSED -> "PAUSED"
        PlaybackState.STATE_PLAYING -> "PLAYING"
        PlaybackState.STATE_FAST_FORWARDING -> "FF"
        PlaybackState.STATE_REWINDING -> "REW"
        PlaybackState.STATE_BUFFERING -> "BUFFERING"
        PlaybackState.STATE_ERROR -> "ERROR"
        PlaybackState.STATE_CONNECTING -> "CONNECTING"
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "SKIP_PREV"
        PlaybackState.STATE_SKIPPING_TO_NEXT -> "SKIP_NEXT"
        else -> "OTHER_$code"
    }

    companion object {
        const val TARGET_PACKAGE = "org.smarttube.stable"
        private const val TAG = "MediaSessionDiag"

        fun notificationListenerSettingsIntent(context: Context): android.content.Intent {
            return if (Build.VERSION.SDK_INT >= 30) {
                android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                    putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        ComponentName(context, MediaSessionDiagService::class.java).flattenToString(),
                    )
                }
            } else {
                android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
        }
    }
}
