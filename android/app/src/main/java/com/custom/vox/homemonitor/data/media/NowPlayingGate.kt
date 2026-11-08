package com.custom.vox.homemonitor.data.media

/**
 * Pure rules for when the Now Playing card may be visible.
 *
 * Cold / residual SmartTube sessions often sit in BUFFERING or PAUSED for a long
 * time without any real playback. Those must **not** open the card. The card
 * opens only on PLAYING; BUFFERING/PAUSED may keep an already-open card briefly.
 */
object NowPlayingGate {

    /** Max time to keep an already-open card while stuck in BUFFERING. */
    const val MAX_BUFFERING_HOLD_MS = 4_000L

    /** Keep card after user pauses, then collapse. */
    const val HOLD_AFTER_PAUSE_MS = 8_000L

    data class Input(
        val state: NowPlayingPlaybackState,
        /** True after dismiss / pause-timeout until PLAYING is seen again. */
        val suppressUntilPlay: Boolean,
        /** Card is currently considered open in the UI session. */
        val cardOpen: Boolean,
        /** Elapsed ms spent continuously in BUFFERING while cardOpen (0 if not). */
        val bufferingForMs: Long,
        /** Elapsed ms spent continuously in PAUSED while cardOpen (0 if not). */
        val pausedForMs: Long,
    )

    enum class Action {
        /** Show / refresh the live card. */
        SHOW,
        /** Keep hidden; wait for real PLAYING. */
        HIDE,
        /** Collapse an open card and arm suppress-until-play. */
        END_SESSION,
    }

    fun decide(input: Input): Action {
        if (input.suppressUntilPlay) {
            return if (input.state == NowPlayingPlaybackState.PLAYING) {
                Action.SHOW
            } else {
                Action.HIDE
            }
        }

        return when (input.state) {
            NowPlayingPlaybackState.PLAYING -> Action.SHOW

            NowPlayingPlaybackState.BUFFERING -> when {
                !input.cardOpen -> Action.HIDE
                input.bufferingForMs >= MAX_BUFFERING_HOLD_MS -> Action.END_SESSION
                else -> Action.SHOW
            }

            NowPlayingPlaybackState.PAUSED,
            NowPlayingPlaybackState.OTHER,
            -> when {
                !input.cardOpen -> Action.HIDE
                input.pausedForMs >= HOLD_AFTER_PAUSE_MS -> Action.END_SESSION
                else -> Action.SHOW
            }

            NowPlayingPlaybackState.STOPPED,
            NowPlayingPlaybackState.NONE,
            -> when {
                !input.cardOpen -> Action.HIDE
                else -> Action.END_SESSION
            }
        }
    }
}
