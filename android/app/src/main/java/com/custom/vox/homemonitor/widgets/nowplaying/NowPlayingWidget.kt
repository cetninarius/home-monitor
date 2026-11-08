package com.custom.vox.homemonitor.widgets.nowplaying

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.custom.vox.homemonitor.R
import com.custom.vox.homemonitor.app
import com.custom.vox.homemonitor.data.media.NowPlayingPlaybackState
import com.custom.vox.homemonitor.data.media.NowPlayingSnapshot
import com.custom.vox.homemonitor.data.media.SessionMediaReader
import com.custom.vox.homemonitor.widgets.DashboardWidget
import java.util.Locale
import java.util.concurrent.TimeUnit

class NowPlayingWidget(
    private val context: Context,
) : DashboardWidget {

    override val id: String = "now-playing"

    private var root: View? = null
    private var content: LinearLayout? = null
    private var artFrame: FrameLayout? = null
    private var meta: FrameLayout? = null
    private var metaBody: LinearLayout? = null
    private var art: ImageView? = null
    private var title: TextView? = null
    private var channel: TextView? = null
    private var progress: ProgressBar? = null
    private var elapsed: TextView? = null
    private var duration: TextView? = null
    private var stateLabel: TextView? = null
    private var prev: ImageButton? = null
    private var playPause: ImageButton? = null
    private var next: ImageButton? = null
    private var expand: ImageButton? = null
    private var close: ImageButton? = null

    private var lastSnap: NowPlayingSnapshot? = null
    private var lastShowCard: Boolean? = null
    private var lastBoundBitmap: Bitmap? = null
    private var lastTitleBound: String? = null
    private var lastChannelBound: String? = null
    private var lastStateBound: NowPlayingPlaybackState? = null
    private var lastPlayPauseRes: Int? = null
    private var lastPrevVisible: Int? = null
    private var lastNextVisible: Int? = null
    private var lastPlayVisible: Int? = null
    private var layoutModeListener: ((Boolean) -> Unit)? = null
    /** MEDIA mode uses a vertical rail; WF1 modes keep the classic horizontal hero. */
    private var railPresentation = false

    private val onUpdate: (NowPlayingSnapshot, Bitmap?) -> Unit = { snap, bmp ->
        bind(snap, bmp)
    }

    fun setLayoutModeListener(listener: (Boolean) -> Unit) {
        layoutModeListener = listener
    }

    fun setRailPresentation(vertical: Boolean) {
        if (railPresentation == vertical && root != null) return
        railPresentation = vertical
        applyPresentation()
    }

    override fun attach(parent: ViewGroup): View {
        val view = LayoutInflater.from(context).inflate(R.layout.widget_now_playing, parent, false)
        root = view
        content = view.findViewById(R.id.nowPlayingContent)
        artFrame = view.findViewById(R.id.nowPlayingArtFrame)
        meta = view.findViewById(R.id.nowPlayingMeta)
        metaBody = view.findViewById(R.id.nowPlayingMetaBody)
        art = view.findViewById(R.id.nowPlayingArt)
        title = view.findViewById(R.id.nowPlayingTitle)
        channel = view.findViewById(R.id.nowPlayingChannel)
        progress = view.findViewById(R.id.nowPlayingProgress)
        elapsed = view.findViewById(R.id.nowPlayingElapsed)
        duration = view.findViewById(R.id.nowPlayingDuration)
        stateLabel = view.findViewById(R.id.nowPlayingState)
        prev = view.findViewById(R.id.nowPlayingPrev)
        playPause = view.findViewById(R.id.nowPlayingPlayPause)
        next = view.findViewById(R.id.nowPlayingNext)
        expand = view.findViewById(R.id.nowPlayingExpand)
        close = view.findViewById(R.id.nowPlayingClose)
        applyPresentation()

        val repo = context.app().nowPlayingRepository
        listOf(prev, playPause, next, expand, close).forEach { btn ->
            btn?.isFocusable = true
            btn?.isFocusableInTouchMode = true
        }
        prev?.setOnClickListener { repo.previous() }
        next?.setOnClickListener { repo.next() }
        playPause?.setOnClickListener {
            val s = lastSnap ?: return@setOnClickListener
            // Only PAUSE while actually playing. Stuck BUFFERING / CONNECTING must
            // send PLAY again — treating buffering as "playing" made the button
            // pause a session that never started.
            if (s.state == NowPlayingPlaybackState.PLAYING) {
                repo.pause()
            } else {
                repo.play()
            }
        }
        expand?.setOnClickListener { openSmartTube(lastSnap?.mediaId) }
        close?.setOnClickListener { repo.dismiss() }

        parent.removeAllViews()
        parent.addView(view)
        return view
    }

    override fun onStart() {
        context.app().nowPlayingRepository.addListener(onUpdate)
    }

    override fun onStop() {
        context.app().nowPlayingRepository.removeListener(onUpdate)
    }

    override fun preferredFocusView(): View? = playPause?.takeIf { it.visibility == View.VISIBLE }

    private fun applyPresentation() {
        val content = content ?: return
        val artFrame = artFrame ?: return
        val meta = meta ?: return
        val density = context.resources.displayMetrics.density
        if (railPresentation) {
            content.orientation = LinearLayout.VERTICAL
            content.gravity = android.view.Gravity.CENTER_HORIZONTAL
            artFrame.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
            ).also { it.bottomMargin = (12 * density).toInt(); it.marginEnd = 0 }
            meta.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            title?.textSize = 22f
        } else {
            content.orientation = LinearLayout.HORIZONTAL
            content.gravity = android.view.Gravity.CENTER_VERTICAL
            artFrame.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 38f)
                .also { it.marginEnd = (16 * density).toInt(); it.bottomMargin = 0 }
            meta.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 62f)
                .also { it.gravity = android.view.Gravity.CENTER_VERTICAL }
            title?.textSize = 26f
        }
        content.requestLayout()
    }

    private fun bind(snap: NowPlayingSnapshot, bitmap: Bitmap?) {
        lastSnap = snap
        val enabled = context.app().dashboardPrefs.nowPlayingEnabled()
        val show = enabled && snap.showCard
        // Only notify layout shell when visibility actually changes — avoids
        // re-pinning the whole dashboard on every 1s progress tick.
        if (lastShowCard != show) {
            lastShowCard = show
            layoutModeListener?.invoke(show)
        }

        if (!show) {
            lastBoundBitmap = null
            lastTitleBound = null
            lastChannelBound = null
            lastStateBound = null
            return
        }

        val resolvedTitle = snap.title?.takeIf { it.isNotBlank() }
        val resolvedChannel = snap.channel?.takeIf { it.isNotBlank() }
        val waitingOnMeta = !snap.mediaId.isNullOrBlank() && resolvedTitle == null
        val titleText = when {
            resolvedTitle != null -> resolvedTitle
            waitingOnMeta -> context.getString(R.string.now_playing_label)
            else -> context.getString(R.string.now_playing_unknown_title)
        }
        val channelText = when {
            resolvedChannel != null -> resolvedChannel
            waitingOnMeta -> "YouTube"
            resolvedTitle != null -> context.getString(R.string.now_playing_unknown_channel)
            else -> ""
        }
        if (titleText != lastTitleBound) {
            lastTitleBound = titleText
            title?.text = titleText
        }
        if (channelText != lastChannelBound) {
            lastChannelBound = channelText
            channel?.text = channelText
        }

        // Re-binding the same Bitmap every poll flickers on some Android TV GPUs.
        if (bitmap !== lastBoundBitmap) {
            lastBoundBitmap = bitmap
            if (bitmap != null) {
                art?.setImageBitmap(bitmap)
            } else {
                art?.setImageDrawable(null)
                art?.setBackgroundColor(ContextCompat.getColor(context, R.color.hm_card_bg))
            }
        }

        val dur = snap.durationMs.coerceAtLeast(0L)
        val pos = snap.positionMs.coerceAtLeast(0L).let { if (dur > 0) it.coerceAtMost(dur) else it }
        progress?.max = 1000
        progress?.progress = if (dur > 0) ((pos * 1000L) / dur).toInt() else 0
        elapsed?.text = formatTime(pos)
        val durText = if (dur > 0) formatTime(dur) else "—"
        if (duration?.text?.toString() != durText) {
            duration?.text = durText
        }

        if (lastStateBound != snap.state) {
            lastStateBound = snap.state
            stateLabel?.text = when (snap.state) {
                NowPlayingPlaybackState.PLAYING -> context.getString(R.string.now_playing_state_playing)
                NowPlayingPlaybackState.PAUSED -> context.getString(R.string.now_playing_state_paused)
                NowPlayingPlaybackState.BUFFERING -> context.getString(R.string.now_playing_state_buffering)
                else -> snap.state.name
            }
        }

        val playRes =
            if (snap.state == NowPlayingPlaybackState.PLAYING || snap.state == NowPlayingPlaybackState.BUFFERING) {
                R.drawable.ic_np_pause
            } else {
                R.drawable.ic_np_play
            }
        if (playRes != lastPlayPauseRes) {
            lastPlayPauseRes = playRes
            playPause?.setImageResource(playRes)
        }
        val playVis = if (snap.canPlay || snap.canPause) View.VISIBLE else View.GONE
        val prevVis = if (snap.canSkipPrevious) View.VISIBLE else View.GONE
        val nextVis = if (snap.canSkipNext) View.VISIBLE else View.GONE
        if (playVis != lastPlayVisible) {
            lastPlayVisible = playVis
            playPause?.visibility = playVis
        }
        if (prevVis != lastPrevVisible) {
            lastPrevVisible = prevVis
            prev?.visibility = prevVis
        }
        if (nextVis != lastNextVisible) {
            lastNextVisible = nextVis
            next?.visibility = nextVis
        }
        expand?.visibility = View.VISIBLE
    }

    private fun openSmartTube(mediaId: String?) {
        context.app().dashboardReclaimer.markUserOpenedSmartTube()
        val pm = context.packageManager
        try {
            if (!mediaId.isNullOrBlank()) {
                val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$mediaId")).apply {
                    setPackage(SessionMediaReader.TARGET_PACKAGE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (view.resolveActivity(pm) != null) {
                    context.startActivity(view)
                    return
                }
            }
            val launch = pm.getLaunchIntentForPackage(SessionMediaReader.TARGET_PACKAGE)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            if (launch != null) {
                context.startActivity(launch)
            }
        } catch (_: ActivityNotFoundException) {
            // SmartTube missing — ignore
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSec = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0L))
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%d:%02d", m, s)
        }
    }
}
