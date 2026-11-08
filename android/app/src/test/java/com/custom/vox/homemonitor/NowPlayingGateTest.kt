package com.custom.vox.homemonitor

import com.custom.vox.homemonitor.data.media.NowPlayingGate
import com.custom.vox.homemonitor.data.media.NowPlayingPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingGateTest {

    @Test
    fun coldBuffering_staysHidden() {
        assertEquals(
            NowPlayingGate.Action.HIDE,
            NowPlayingGate.decide(
                NowPlayingGate.Input(
                    state = NowPlayingPlaybackState.BUFFERING,
                    suppressUntilPlay = true,
                    cardOpen = false,
                    bufferingForMs = 30_000L,
                    pausedForMs = 0L,
                ),
            ),
        )
        assertEquals(
            NowPlayingGate.Action.HIDE,
            NowPlayingGate.decide(
                NowPlayingGate.Input(
                    state = NowPlayingPlaybackState.BUFFERING,
                    suppressUntilPlay = false,
                    cardOpen = false,
                    bufferingForMs = 0L,
                    pausedForMs = 0L,
                ),
            ),
        )
    }

    @Test
    fun playing_opensEvenAfterSuppress() {
        assertEquals(
            NowPlayingGate.Action.SHOW,
            NowPlayingGate.decide(
                NowPlayingGate.Input(
                    state = NowPlayingPlaybackState.PLAYING,
                    suppressUntilPlay = true,
                    cardOpen = false,
                    bufferingForMs = 0L,
                    pausedForMs = 0L,
                ),
            ),
        )
    }

    @Test
    fun dismissSuppress_ignoresBufferingAndPaused() {
        for (state in listOf(
            NowPlayingPlaybackState.BUFFERING,
            NowPlayingPlaybackState.PAUSED,
            NowPlayingPlaybackState.STOPPED,
        )) {
            assertEquals(
                state.name,
                NowPlayingGate.Action.HIDE,
                NowPlayingGate.decide(
                    NowPlayingGate.Input(
                        state = state,
                        suppressUntilPlay = true,
                        cardOpen = false,
                        bufferingForMs = 5_000L,
                        pausedForMs = 5_000L,
                    ),
                ),
            )
        }
    }

    @Test
    fun openCard_briefBufferingKept_thenEnded() {
        assertEquals(
            NowPlayingGate.Action.SHOW,
            NowPlayingGate.decide(
                NowPlayingGate.Input(
                    state = NowPlayingPlaybackState.BUFFERING,
                    suppressUntilPlay = false,
                    cardOpen = true,
                    bufferingForMs = 1_000L,
                    pausedForMs = 0L,
                ),
            ),
        )
        assertEquals(
            NowPlayingGate.Action.END_SESSION,
            NowPlayingGate.decide(
                NowPlayingGate.Input(
                    state = NowPlayingPlaybackState.BUFFERING,
                    suppressUntilPlay = false,
                    cardOpen = true,
                    bufferingForMs = NowPlayingGate.MAX_BUFFERING_HOLD_MS,
                    pausedForMs = 0L,
                ),
            ),
        )
    }

    @Test
    fun coldPaused_staysHidden() {
        assertEquals(
            NowPlayingGate.Action.HIDE,
            NowPlayingGate.decide(
                NowPlayingGate.Input(
                    state = NowPlayingPlaybackState.PAUSED,
                    suppressUntilPlay = false,
                    cardOpen = false,
                    bufferingForMs = 0L,
                    pausedForMs = 0L,
                ),
            ),
        )
    }
}
