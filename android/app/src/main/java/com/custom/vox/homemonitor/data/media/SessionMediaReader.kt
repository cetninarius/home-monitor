package com.custom.vox.homemonitor.data.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.custom.vox.homemonitor.media.MediaSessionDiagService

/**
 * Low-level SmartTube MediaSession reader used by [NowPlayingRepository].
 */
class SessionMediaReader(private val context: Context) {

    private var lastGoodPositionMs: Long = -1L
    private var lastGoodPositionAtElapsed: Long = 0L
    private var wasPlaying: Boolean = false
    private var cachedController: MediaController? = null

    fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val cn = ComponentName(context, MediaSessionDiagService::class.java).flattenToString()
        return flat.split(':').any { it.equals(cn, ignoreCase = true) }
    }

    fun readRaw(): RawSession? {
        if (!isNotificationListenerEnabled()) return null
        return try {
            val mgr = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val listener = ComponentName(context, MediaSessionDiagService::class.java)
            val sessions = mgr.getActiveSessions(listener)
            val controller = sessions.firstOrNull { it.packageName == TARGET_PACKAGE }
            cachedController = controller
            controller?.let { fromController(it) }
        } catch (e: SecurityException) {
            Log.w(TAG, "getActiveSessions denied", e)
            cachedController = null
            null
        }
    }

    fun controller(): MediaController? = cachedController

    fun transport(action: TransportAction): Boolean {
        // Refresh controller if the last poll missed (common right after SmartTube opens).
        var c = cachedController
        if (c == null) {
            readRaw()
            c = cachedController
        }
        if (c == null) {
            Log.w(TAG, "transport $action skipped — no SmartTube session")
            return false
        }
        val controls = c.transportControls
        return try {
            when (action) {
                TransportAction.PLAY -> controls.play()
                TransportAction.PAUSE -> controls.pause()
                TransportAction.STOP -> controls.stop()
                TransportAction.NEXT -> controls.skipToNext()
                TransportAction.PREVIOUS -> controls.skipToPrevious()
            }
            Log.i(TAG, "transport $action ok")
            true
        } catch (e: Exception) {
            Log.w(TAG, "transport $action failed: ${e.message}")
            false
        }
    }

    private fun fromController(controller: MediaController): RawSession {
        val state = controller.playbackState
        val meta = controller.metadata
        val stateCode = state?.state ?: PlaybackState.STATE_NONE
        val actions = state?.actions ?: 0L
        val reported = state?.position ?: PlaybackState.PLAYBACK_POSITION_UNKNOWN
        val (pos, source) = estimatePosition(
            reported = reported,
            playing = stateCode == PlaybackState.STATE_PLAYING,
            speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            updateTime = state?.lastPositionUpdateTime ?: 0L,
        )
        val title = meta.stringOrNull(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta.stringOrNull(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: meta.stringOrNull(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)
        val subtitle = meta.stringOrNull(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val channel = meta.stringOrNull(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.stringOrNull(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta.stringOrNull(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: meta.stringOrNull(MediaMetadata.METADATA_KEY_WRITER)
            ?: meta.stringOrNull(MediaMetadata.METADATA_KEY_ALBUM)
            ?: subtitle
        val art = meta.stringOrNull(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: meta.stringOrNull(MediaMetadata.METADATA_KEY_ART_URI)
            ?: meta.stringOrNull(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        val mediaUri = meta.stringOrNull(MediaMetadata.METADATA_KEY_MEDIA_URI)
        val rawId = meta.stringOrNull(MediaMetadata.METADATA_KEY_MEDIA_ID)
        val mediaId = extractYoutubeVideoId(rawId)
            ?: extractYoutubeVideoId(mediaUri)
            ?: extractYoutubeVideoId(art)
            ?: rawId
        val duration = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L

        return RawSession(
            stateCode = stateCode,
            mediaId = mediaId,
            title = title,
            channel = channel,
            subtitle = subtitle,
            artworkUri = art,
            durationMs = duration,
            positionMs = pos ?: -1L,
            positionSource = source,
            canPlay = actions and PlaybackState.ACTION_PLAY != 0L,
            canPause = actions and PlaybackState.ACTION_PAUSE != 0L,
            canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canSkipPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
        )
    }

    private fun estimatePosition(
        reported: Long,
        playing: Boolean,
        speed: Float,
        updateTime: Long,
    ): Pair<Long?, String> {
        val nowElapsed = SystemClock.elapsedRealtime()

        // Paused / non-playing: never extrapolate. SmartTube's lastPositionUpdateTime
        // often stays stuck across the pause, so framework-adjust would add the whole
        // pause duration on the first playing tick (progress jumps ahead of the phone).
        if (!playing) {
            wasPlaying = false
            if (reported >= 0L) {
                lastGoodPositionMs = reported
                lastGoodPositionAtElapsed = nowElapsed
                return reported to "reported"
            }
            if (lastGoodPositionMs >= 0L) {
                return lastGoodPositionMs to "held"
            }
            return null to "invalid"
        }

        val justResumed = !wasPlaying
        wasPlaying = true

        // On resume, trust the session position for one sample — do not apply a
        // possibly stale lastPositionUpdateTime delta.
        if (justResumed && reported >= 0L) {
            lastGoodPositionMs = reported
            lastGoodPositionAtElapsed = nowElapsed
            return reported to "reported-resume"
        }

        // Only clock-adjust when the session refreshed position recently.
        val updateAge = if (updateTime > 0L) nowElapsed - updateTime else Long.MAX_VALUE
        val frameworkAdjusted =
            if (reported >= 0L && updateAge in 0L..FRESH_UPDATE_MAX_MS) {
                (reported + (updateAge * speed).toLong()).coerceAtLeast(0L)
            } else {
                null
            }

        val saneReported = reported >= 0L &&
            (lastGoodPositionMs < 0L || reported >= lastGoodPositionMs - 5_000L)

        if (saneReported && reported >= 0L) {
            val pos = frameworkAdjusted ?: reported
            lastGoodPositionMs = pos
            lastGoodPositionAtElapsed = nowElapsed
            return pos to if (frameworkAdjusted != null) "framework+reported" else "reported"
        }

        if (lastGoodPositionMs >= 0L) {
            val delta = nowElapsed - lastGoodPositionAtElapsed
            val est = (lastGoodPositionMs + (delta * speed).toLong()).coerceAtLeast(0L)
            // Do not write est back into lastGood — that would compound every poll.
            return est to "extrapolated"
        }

        if (reported < 0L) return null to "invalid"
        return reported to "reported"
    }

    private fun MediaMetadata?.stringOrNull(key: String): String? {
        if (this == null) return null
        val v = getString(key) ?: return null
        return v.trim().takeIf { it.isNotEmpty() }
    }

    data class RawSession(
        val stateCode: Int,
        val mediaId: String?,
        val title: String?,
        val channel: String?,
        val subtitle: String?,
        val artworkUri: String?,
        val durationMs: Long,
        val positionMs: Long,
        val positionSource: String,
        val canPlay: Boolean,
        val canPause: Boolean,
        val canSkipNext: Boolean,
        val canSkipPrevious: Boolean,
    )

    enum class TransportAction { PLAY, PAUSE, STOP, NEXT, PREVIOUS }

    companion object {
        const val TARGET_PACKAGE = "org.smarttube.stable"
        private const val TAG = "SessionMediaReader"
        private const val FRESH_UPDATE_MAX_MS = 2_000L
        private val YT_ID = Regex("""(?:v=|/vi?/|youtu\.be/|embed/|shorts/|yt:video:)?([A-Za-z0-9_-]{11})(?:[?&#/]|$)""")

        /** Normalize cast / SmartTube media ids and thumbnail URLs to an 11-char video id. */
        fun extractYoutubeVideoId(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val t = raw.trim()
            if (t.matches(Regex("^[A-Za-z0-9_-]{11}$"))) return t
            return YT_ID.find(t)?.groupValues?.getOrNull(1)
        }
    }
}
