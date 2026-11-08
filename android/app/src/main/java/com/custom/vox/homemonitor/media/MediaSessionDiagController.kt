package com.custom.vox.homemonitor.media

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import com.custom.vox.homemonitor.BuildConfig
import com.custom.vox.homemonitor.R
import java.util.concurrent.Executors

/**
 * Feature-flagged overlay that polls SmartTube MediaSession without altering dashboard widgets.
 */
class MediaSessionDiagController(
    private val activity: Activity,
    private val root: View,
) {
    private val reader = SmartTubeSessionReader(activity)
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var panel: View? = null
    private var text: TextView? = null
    private var hint: TextView? = null
    private var promptedSettings = false
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            io.execute {
                val snap = reader.read()
                handler.post { render(snap) }
            }
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    fun start() {
        if (!BuildConfig.MEDIA_SESSION_DIAG) return
        panel = root.findViewById(R.id.mediaSessionDiagPanel)
        text = root.findViewById(R.id.mediaSessionDiagText)
        hint = root.findViewById(R.id.mediaSessionDiagHint)
        panel?.visibility = View.VISIBLE
        running = true
        handler.removeCallbacks(tick)
        tick.run()
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    private fun render(snap: SmartTubeSessionSnapshot) {
        text?.text = snap.toDiagText()
        Log.i(TAG, snap.toDiagText().replace("\n", " | "))
        when {
            !snap.listenerEnabled -> {
                hint?.text = activity.getString(R.string.media_diag_grant_hint)
                maybeOpenSettings()
            }
            !snap.sessionFound -> {
                hint?.text = activity.getString(R.string.media_diag_waiting)
            }
            else -> {
                hint?.text = "ok · source=${snap.positionSource}"
            }
        }
    }

    private fun maybeOpenSettings() {
        if (promptedSettings) return
        promptedSettings = true
        // VOX TV Settings does not resolve ACTION_NOTIFICATION_LISTENER_SETTINGS.
        // Guide the user through the leanback Apps → Special app access path instead of
        // silently writing secure settings.
        hint?.text = activity.getString(R.string.media_diag_grant_hint) +
            " Path: Settings → Apps → Special app access → Notification access → Home Monitor."
        Log.i(TAG, "Notification access not granted — waiting for user approval via TV Settings")
        try {
            activity.startActivity(
                android.content.Intent().setClassName(
                    "com.android.tv.settings",
                    "com.android.tv.settings.device.apps.AppsActivity",
                ),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not open TV Apps settings", e)
        }
    }

    companion object {
        private const val TAG = "MediaSessionDiag"
        private const val INTERVAL_MS = 1_000L
    }
}
