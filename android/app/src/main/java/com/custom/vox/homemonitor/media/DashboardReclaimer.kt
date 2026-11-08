package com.custom.vox.homemonitor.media

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.custom.vox.homemonitor.HomeMonitorApp
import com.custom.vox.homemonitor.data.media.SessionMediaReader

/**
 * While Home Monitor is open (launched from Plume), UI is a live overlay.
 * Overlay is **not** permanent system-wide — only for that session.
 * BACK / exit tears it down and returns straight to Plume home.
 */
class DashboardReclaimer(
    private val app: HomeMonitorApp,
) {
    private val launcher = ReclaimLauncher(app)
    private val shield = DashboardOverlayShield(app)

    private var sessionActive = false
    private var suppressUntilElapsed = 0L
    /** Overlay hidden so TV Settings / Apps / SmartTube is visible; restore on next HM resume. */
    private var overlayPausedForExternal = false

    init {
        shield.setExitHandler { requestExit() }
    }

    fun attachSnapshotSource(@Suppress("UNUSED_PARAMETER") view: android.view.View) {
        // Unused — UI lives in the overlay.
    }

    /** User opened HM from Plume — show overlay for this session only. */
    fun enterPersistent(reason: String) {
        // Fresh launcher entry always wins over a temporary SmartTube suppress —
        // otherwise MainActivity paints black with no overlay.
        if (reason == "launch") {
            suppressUntilElapsed = 0L
        } else if (isSuppressed()) {
            Log.i(TAG, "enterSession suppressed ($reason)")
            return
        }
        overlayPausedForExternal = false
        sessionActive = true
        ensureOverlay(reason)
        app.nowPlayingRepository.start()
        Log.i(TAG, "session overlay on reason=$reason")
    }

    fun beginWarmupCover() {
        enterPersistent("warmup")
    }

    fun endWarmupCover() {
        if (sessionActive && !overlayPausedForExternal) {
            ensureOverlay("warmup-done-keep")
        }
        Log.i(TAG, "warmup finished — session=$sessionActive showing=${shield.isShowing()}")
    }

    fun onDashboardResumed() {
        if (overlayPausedForExternal) {
            if (isPackageLikelyForeground(SessionMediaReader.TARGET_PACKAGE)) {
                Log.i(TAG, "skip overlay restore — SmartTube still foreground")
                return
            }
            // Coming back from Apps / notification-access / SmartTube expand.
            overlayPausedForExternal = false
            sessionActive = true
            ensureOverlay("return-from-external")
            app.nowPlayingRepository.start()
            Log.i(TAG, "restored overlay after external app")
            return
        }
        if (isSuppressed()) {
            if (isPackageLikelyForeground(SessionMediaReader.TARGET_PACKAGE)) {
                Log.i(TAG, "resume suppressed — SmartTube foreground")
                return
            }
            // User is back on HM without SmartTube on top — clear stale suppress.
            Log.i(TAG, "clearing stale SmartTube suppress on resume")
            suppressUntilElapsed = 0L
        }
        enterPersistent("activity-resumed")
    }

    fun onDashboardPaused(finishing: Boolean = false) {
        if (finishing) {
            Log.i(TAG, "activity finishing under session=$sessionActive")
            return
        }
        if (overlayPausedForExternal) {
            // Stay down while user is in TV Settings / Apps / SmartTube.
            return
        }
        if (isSuppressed()) {
            dropOverlay("suppressed-pause")
            return
        }
        if (sessionActive) {
            ensureOverlay("activity-paused")
        }
    }

    fun onDashboardFinished() {
        Log.i(TAG, "activity finished — session=$sessionActive")
    }

    fun markUserOpenedSmartTube() {
        // Keep the HM session; only hide the overlay so SmartTube is usable.
        // Using leaveForExternalApp() previously left a black trampoline for 20s
        // when the user returned to Home Monitor.
        pauseOverlayForSettings("user-opened-smarttube")
    }

    /**
     * Temporarily hide the overlay so Apps / Settings / SmartTube is usable.
     * Keeps the HM session — [onDashboardResumed] brings the dashboard back.
     * Does **not** go HOME and does **not** suppress re-entry.
     */
    fun pauseOverlayForSettings(reason: String) {
        overlayPausedForExternal = true
        dropOverlay(reason)
        Log.i(TAG, "overlay paused for settings ($reason) — will restore on resume")
    }

    /**
     * Longer leave. Suppresses auto overlay briefly while another app is on top.
     * Explicit HM launcher entry ([enterPersistent] reason=launch) clears this.
     */
    fun leaveForExternalApp(reason: String) {
        overlayPausedForExternal = false
        suppressUntilElapsed = SystemClock.elapsedRealtime() + SUPPRESS_MS
        sessionActive = false
        dropOverlay(reason)
        Log.i(TAG, "left for external app — suppress ${SUPPRESS_MS}ms ($reason)")
    }

    /** BACK / exit: remove overlay and go to Plume immediately. */
    fun requestExit() {
        Log.i(TAG, "exit → Plume home")
        overlayPausedForExternal = false
        sessionActive = false
        app.nowPlayingRepository.stop()
        dropOverlay("user-exit")
        try {
            app.startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } catch (e: Exception) {
            Log.w(TAG, "HOME failed: ${e.message}")
        }
        try {
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.appTasks.toList().forEach { task ->
                try {
                    task.finishAndRemoveTask()
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "finishAndRemoveTask failed: ${e.message}")
        }
    }

    fun needsOverlayPermission(): Boolean = !shield.canDrawOverlays()

    fun openOverlaySettings() {
        try {
            app.startActivity(launcher.overlaySettingsIntent())
        } catch (e: Exception) {
            Log.w(TAG, "overlay settings failed: ${e.message}")
        }
    }

    private fun ensureOverlay(reason: String) {
        if (!shield.canDrawOverlays()) {
            Log.w(TAG, "no overlay permission ($reason)")
            return
        }
        if (!shield.isShowing()) {
            shield.show(reason)
        }
    }

    private fun dropOverlay(reason: String) {
        if (shield.isShowing()) {
            shield.hide(reason)
        }
    }

    private fun isSuppressed(): Boolean =
        SystemClock.elapsedRealtime() < suppressUntilElapsed

    /** Best-effort top-app check (API 28 TV). Used to avoid covering SmartTube. */
    private fun isPackageLikelyForeground(packageName: String): Boolean {
        return try {
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            val tasks = am.getRunningTasks(1)
            val top = tasks.firstOrNull()?.topActivity?.packageName
            top == packageName
        } catch (e: Exception) {
            Log.w(TAG, "foreground check failed: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "DashboardReclaimer"
        private const val SUPPRESS_MS = 20_000L
    }
}
