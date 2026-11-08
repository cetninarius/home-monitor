package com.custom.vox.homemonitor.data.media

enum class NowPlayingPlaybackState {
    NONE,
    STOPPED,
    PAUSED,
    PLAYING,
    BUFFERING,
    OTHER,
}

data class NowPlayingSnapshot(
    val active: Boolean,
    val listenerEnabled: Boolean,
    val mediaId: String?,
    val title: String?,
    val channel: String?,
    val artworkUri: String?,
    val durationMs: Long,
    val positionMs: Long,
    val positionSource: String,
    val state: NowPlayingPlaybackState,
    val canPlay: Boolean,
    val canPause: Boolean,
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
    val metadataSource: String,
    val error: String? = null,
) {
    /**
     * Visible while repository marks the session active. Opening the card is gated
     * upstream ([NowPlayingGate]) so residual BUFFERING never becomes a card alone.
     */
    val showCard: Boolean
        get() = active && state != NowPlayingPlaybackState.NONE

    companion object {
        fun inactive(listenerEnabled: Boolean, error: String? = null) = NowPlayingSnapshot(
            active = false,
            listenerEnabled = listenerEnabled,
            mediaId = null,
            title = null,
            channel = null,
            artworkUri = null,
            durationMs = -1L,
            positionMs = -1L,
            positionSource = "none",
            state = NowPlayingPlaybackState.NONE,
            canPlay = false,
            canPause = false,
            canSkipNext = false,
            canSkipPrevious = false,
            metadataSource = "none",
            error = error,
        )
    }
}

data class OEmbedResult(
    val title: String?,
    val authorName: String?,
    val thumbnailUrl: String?,
)
