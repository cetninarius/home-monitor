package com.custom.vox.homemonitor.dashboard

/**
 * Adaptive dashboard compositions.
 *
 * [mediaShowing] is true only when Now Playing is enabled **and** a live
 * playback card is active — idle NP preference alone stays on HOME / WORKFLOW.
 */
enum class DashboardLayoutMode {
    /** No WF1, no live playback — Weather+Services rail, Calendar, To-do. */
    HOME,

    /** No WF1, live playback — compact home grid + vertical Now Playing rail. */
    MEDIA,

    /** WF1 on, no live playback — classic 66/34 grid + WF1. */
    WORKFLOW,

    /** WF1 on + live playback — classic left NP hero + WF1 column. */
    WORKFLOW_MEDIA,
    ;

    companion object {
        fun resolve(mediaShowing: Boolean, wf1Enabled: Boolean): DashboardLayoutMode =
            when {
                mediaShowing && wf1Enabled -> WORKFLOW_MEDIA
                mediaShowing -> MEDIA
                wf1Enabled -> WORKFLOW
                else -> HOME
            }
    }
}
