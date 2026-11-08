package com.custom.vox.homemonitor.data.media

import android.content.Context
import android.graphics.Bitmap
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Observes SmartTube MediaSession, enriches missing metadata via oEmbed,
 * and loads cached artwork.
 *
 * Visibility is gated by [NowPlayingGate]: residual BUFFERING/PAUSED sessions
 * never open the card; only real PLAYING does.
 */
class NowPlayingRepository(context: Context) {

    private val appContext = context.applicationContext
    private val reader = SessionMediaReader(appContext)
    private val oEmbed = YouTubeOEmbedClient()
    private val artwork = ArtworkCache(appContext)
    private val io = Executors.newSingleThreadExecutor()
    private val enrichIo = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val latest = AtomicReference(NowPlayingSnapshot.inactive(false))
    private val listeners = mutableSetOf<(NowPlayingSnapshot, Bitmap?) -> Unit>()

    private var running = false
    private var gapHoldUntilElapsed = 0L
    private var pauseStartedElapsed = 0L
    private var bufferingStartedElapsed = 0L
    private var sessionGoneSinceElapsed = 0L
    private var lastArtKey: String? = null
    private var lastBitmap: Bitmap? = null
    private var lastActiveSnap: NowPlayingSnapshot? = null
    /** Card is open in this UI session (opened by PLAYING). */
    @Volatile private var cardOpen = false
    /**
     * After dismiss / timeout, stay hidden until PLAYING is observed again.
     * BUFFERING alone must never clear this.
     */
    @Volatile private var suppressUntilNewPlay = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            io.execute { poll() }
            main.postDelayed(this, POLL_MS)
        }
    }

    fun start() {
        if (running) return
        running = true
        // Assume residual sessions until we prove PLAYING.
        // Prevents warmup BUFFERING from expanding the dashboard on launch.
        suppressUntilNewPlay = true
        cardOpen = false
        publish(NowPlayingSnapshot.inactive(reader.isNotificationListenerEnabled()), null)
        main.removeCallbacks(tick)
        tick.run()
    }

    fun stop() {
        running = false
        main.removeCallbacks(tick)
    }

    fun latest(): NowPlayingSnapshot = latest.get()

    fun addListener(listener: (NowPlayingSnapshot, Bitmap?) -> Unit) {
        listeners.add(listener)
        listener(latest.get(), lastBitmap)
    }

    fun removeListener(listener: (NowPlayingSnapshot, Bitmap?) -> Unit) {
        listeners.remove(listener)
    }

    fun play(): Boolean = reader.transport(SessionMediaReader.TransportAction.PLAY)
    fun pause(): Boolean = reader.transport(SessionMediaReader.TransportAction.PAUSE)
    fun next(): Boolean = reader.transport(SessionMediaReader.TransportAction.NEXT)
    fun previous(): Boolean = reader.transport(SessionMediaReader.TransportAction.PREVIOUS)

    /**
     * Hide Now Playing UI immediately. Pause transport if needed (never STOP —
     * STOP breaks SmartTube Lounge).
     */
    fun dismiss() {
        val snap = latest.get()
        val shouldPause = snap.showCard && (
            snap.state == NowPlayingPlaybackState.PLAYING ||
                snap.state == NowPlayingPlaybackState.BUFFERING ||
                snap.state == NowPlayingPlaybackState.PAUSED
            )
        endSession(listenerEnabled = reader.isNotificationListenerEnabled())
        if (shouldPause) {
            io.execute {
                reader.transport(SessionMediaReader.TransportAction.PAUSE)
            }
        }
    }

    private fun poll() {
        val enabled = reader.isNotificationListenerEnabled()
        if (!enabled) {
            suppressUntilNewPlay = false
            cardOpen = false
            publish(NowPlayingSnapshot.inactive(false, "Notification access required"), null)
            return
        }
        val raw = reader.readRaw()
        val now = SystemClock.elapsedRealtime()
        if (raw == null) {
            if (sessionGoneSinceElapsed == 0L) {
                sessionGoneSinceElapsed = now
            }
            if (suppressUntilNewPlay || !cardOpen || now - sessionGoneSinceElapsed >= SESSION_GONE_MS) {
                endSession(listenerEnabled = true)
            }
            return
        }
        sessionGoneSinceElapsed = 0L

        val mappedState = mapState(raw.stateCode)
        val bufferingFor = if (mappedState == NowPlayingPlaybackState.BUFFERING && cardOpen) {
            if (bufferingStartedElapsed == 0L) bufferingStartedElapsed = now
            now - bufferingStartedElapsed
        } else {
            bufferingStartedElapsed = 0L
            0L
        }
        val pausedFor = if (
            (mappedState == NowPlayingPlaybackState.PAUSED || mappedState == NowPlayingPlaybackState.OTHER) &&
            cardOpen
        ) {
            if (pauseStartedElapsed == 0L) pauseStartedElapsed = now
            now - pauseStartedElapsed
        } else {
            if (mappedState == NowPlayingPlaybackState.PLAYING) pauseStartedElapsed = 0L
            0L
        }

        when (
            NowPlayingGate.decide(
                NowPlayingGate.Input(
                    state = mappedState,
                    suppressUntilPlay = suppressUntilNewPlay,
                    cardOpen = cardOpen,
                    bufferingForMs = bufferingFor,
                    pausedForMs = pausedFor,
                ),
            )
        ) {
            NowPlayingGate.Action.HIDE -> {
                publish(NowPlayingSnapshot.inactive(true), null)
                return
            }
            NowPlayingGate.Action.END_SESSION -> {
                // Brief STOPPED hold between tracks if we still have a snap.
                if (mappedState == NowPlayingPlaybackState.STOPPED &&
                    cardOpen &&
                    now <= gapHoldUntilElapsed &&
                    lastActiveSnap != null
                ) {
                    publish(lastActiveSnap!!.copy(state = NowPlayingPlaybackState.STOPPED), lastBitmap)
                    return
                }
                endSession(listenerEnabled = true)
                return
            }
            NowPlayingGate.Action.SHOW -> {
                // proceed to publish live snap
            }
        }

        if (mappedState == NowPlayingPlaybackState.PLAYING) {
            suppressUntilNewPlay = false
            cardOpen = true
            pauseStartedElapsed = 0L
            bufferingStartedElapsed = 0L
            gapHoldUntilElapsed = now + TRACK_GAP_MS
        } else if (mappedState == NowPlayingPlaybackState.BUFFERING && cardOpen) {
            gapHoldUntilElapsed = now + TRACK_GAP_MS
        }

        var title = raw.title
        var channel = raw.channel
        var artUri = raw.artworkUri
        var metaSource = "mediasession"
        val mediaId = raw.mediaId

        // Cast / Lounge sessions often omit title/channel/art on every poll.
        // Keep last good display fields for the same mediaId so we don't wipe
        // oEmbed enrichment each second (visible card flicker).
        val prev = lastActiveSnap
        if (prev != null && prev.mediaId != null && prev.mediaId == mediaId) {
            if (title.isNullOrBlank()) title = prev.title
            if (channel.isNullOrBlank()) channel = prev.channel
            if (artUri.isNullOrBlank()) artUri = prev.artworkUri
            if (prev.metadataSource != "mediasession" &&
                (raw.title.isNullOrBlank() || raw.channel.isNullOrBlank() || raw.artworkUri.isNullOrBlank())
            ) {
                metaSource = prev.metadataSource
            }
        }

        // Fast path: publish immediately with session metadata. Enrich async so a
        // slow oEmbed/art fetch can never stall collapse / dismiss for minutes.
        val snap = NowPlayingSnapshot(
            active = true,
            listenerEnabled = true,
            mediaId = mediaId,
            title = title,
            channel = channel,
            artworkUri = artUri,
            durationMs = raw.durationMs,
            positionMs = raw.positionMs,
            positionSource = raw.positionSource,
            state = mappedState,
            canPlay = raw.canPlay,
            canPause = raw.canPause,
            canSkipNext = raw.canSkipNext,
            canSkipPrevious = raw.canSkipPrevious,
            metadataSource = metaSource,
        )
        lastActiveSnap = snap
        val cachedBmp = when {
            mediaId != null -> artwork.getCached(mediaId)
            artUri != null -> artwork.getCached(artUri)
            else -> null
        } ?: lastBitmap
        if (cachedBmp != null) lastBitmap = cachedBmp
        publish(snap, cachedBmp)

        // Cast sessions often omit title/artist but still carry a YouTube id —
        // always try oEmbed when identity is known and display fields are empty.
        val needsFallback = mediaId != null &&
            (title.isNullOrBlank() || channel.isNullOrBlank() || artUri.isNullOrBlank())
        val needsArt = (mediaId ?: artUri) != null &&
            (artUri != null || mediaId != null) &&
            (lastArtKey != (mediaId ?: artUri) || lastBitmap == null)
        if (needsFallback || needsArt) {
            val enrichId = mediaId
            val enrichArt = artUri ?: mediaId?.let { YouTubeOEmbedClient.thumbnailUrlFor(it) }
            enrichIo.execute {
                var eTitle = title
                var eChannel = channel
                var eArt = enrichArt
                var eSource = metaSource
                if (enrichId != null && (eTitle.isNullOrBlank() || eChannel.isNullOrBlank() || eArt.isNullOrBlank())) {
                    val oe = oEmbed.fetch(enrichId)
                    if (oe != null) {
                        if (eTitle.isNullOrBlank()) eTitle = oe.title
                        if (eChannel.isNullOrBlank()) eChannel = oe.authorName
                        if (eArt.isNullOrBlank()) {
                            eArt = oe.thumbnailUrl ?: YouTubeOEmbedClient.thumbnailUrlFor(enrichId)
                        }
                        eSource = "mediasession+oembed"
                    } else if (eArt.isNullOrBlank()) {
                        eArt = YouTubeOEmbedClient.thumbnailUrlFor(enrichId)
                        eSource = "mediasession+thumb"
                    }
                }
                var bmp = lastBitmap
                val artKey = enrichId ?: eArt
                if (artKey != null && eArt != null) {
                    bmp = artwork.getCached(artKey) ?: artwork.load(artKey, eArt)
                    lastArtKey = artKey
                    lastBitmap = bmp
                }
                // Drop enrichment if session already collapsed or media changed.
                val current = latest.get()
                if (!current.showCard) return@execute
                if (enrichId != null && current.mediaId != null && current.mediaId != enrichId) {
                    return@execute
                }
                val enriched = current.copy(
                    title = eTitle ?: current.title,
                    channel = eChannel ?: current.channel,
                    artworkUri = eArt ?: current.artworkUri,
                    metadataSource = eSource,
                )
                lastActiveSnap = enriched
                publish(enriched, bmp)
            }
        }
    }

    private fun endSession(listenerEnabled: Boolean) {
        if (suppressUntilNewPlay && !cardOpen && latest.get().let { !it.showCard }) {
            // Already collapsed under suppress — avoid listener spam.
            return
        }
        Log.i(TAG, "endSession suppress=true cardOpen was=$cardOpen")
        suppressUntilNewPlay = true
        cardOpen = false
        sessionGoneSinceElapsed = 0L
        pauseStartedElapsed = 0L
        bufferingStartedElapsed = 0L
        gapHoldUntilElapsed = 0L
        lastActiveSnap = null
        lastArtKey = null
        lastBitmap = null
        publish(NowPlayingSnapshot.inactive(listenerEnabled), null)
    }

    private fun publish(snap: NowPlayingSnapshot, bitmap: Bitmap?) {
        latest.set(snap)
        main.post {
            listeners.toList().forEach { it(snap, bitmap) }
        }
    }

    private fun mapState(code: Int): NowPlayingPlaybackState = when (code) {
        PlaybackState.STATE_NONE -> NowPlayingPlaybackState.NONE
        PlaybackState.STATE_STOPPED -> NowPlayingPlaybackState.STOPPED
        PlaybackState.STATE_PAUSED -> NowPlayingPlaybackState.PAUSED
        PlaybackState.STATE_PLAYING -> NowPlayingPlaybackState.PLAYING
        PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> NowPlayingPlaybackState.BUFFERING
        else -> NowPlayingPlaybackState.OTHER
    }

    companion object {
        private const val TAG = "NowPlayingRepo"
        private const val POLL_MS = 1_000L
        private const val TRACK_GAP_MS = 2_500L
        private const val SESSION_GONE_MS = 1_200L
    }
}
