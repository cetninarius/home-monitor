package com.custom.vox.homemonitor

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.custom.vox.homemonitor.admin.LocalAdminServer
import com.custom.vox.homemonitor.media.SmartTubeWarmup

/**
 * Trampoline from Plume. Real UI is the persistent [DashboardOverlayShield].
 * Keeping a blank activity alive anchors the task; the overlay never tears down
 * on phone disconnect (that flicker is gone).
 */
class MainActivity : AppCompatActivity() {

    private var adminServer: LocalAdminServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersive()
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                keepScreenOn = true
            },
        )
        app().adminAuth.getOrCreateToken()
        // Overlay first — before SmartTube warmup can paint.
        app().dashboardReclaimer.enterPersistent("launch")
        maybePromptOverlayForShield()
        window.decorView.post { warmSmartTubeUnderOverlay() }
    }

    override fun onResume() {
        super.onResume()
        enterImmersive()
        app().dashboardReclaimer.onDashboardResumed()
        startAdminServer()
        maybePromptOverlayForShield()
        if (SmartTubeWarmup.needsWarmup(this)) {
            warmSmartTubeUnderOverlay()
        }
    }

    private fun warmSmartTubeUnderOverlay() {
        val reclaimer = app().dashboardReclaimer
        reclaimer.beginWarmupCover()
        if (!SmartTubeWarmup.needsWarmup(this)) {
            reclaimer.endWarmupCover()
            return
        }
        SmartTubeWarmup.ensureRunning(
            context = this,
            returnToPackage = packageName,
            onComplete = { reclaimer.endWarmupCover() },
        )
    }

    private fun maybePromptOverlayForShield() {
        val reclaimer = app().dashboardReclaimer
        if (!reclaimer.needsOverlayPermission()) return
        val prefs = getSharedPreferences(PREFS_RECLAIM, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_OVERLAY_PROMPTED, false)) return
        window.decorView.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            if (!reclaimer.needsOverlayPermission()) return@postDelayed
            prefs.edit().putBoolean(KEY_OVERLAY_PROMPTED, true).apply()
            Log.i(TAG, "Opening overlay permission settings")
            reclaimer.openOverlaySettings()
        }, 1_000L)
    }

    override fun onPause() {
        app().dashboardReclaimer.onDashboardPaused(finishing = isFinishing)
        stopAdminServer()
        super.onPause()
    }

    override fun onDestroy() {
        if (isFinishing) {
            app().dashboardReclaimer.onDashboardFinished()
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            app().dashboardReclaimer.requestExit()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun startAdminServer() {
        if (adminServer?.isAlive == true) return
        val app = app()
        val server = LocalAdminServer.create(
            context = this,
            auth = app.adminAuth,
            calendarRepository = app.calendarRepository,
            todoRepository = app.todoRepository,
            uptimeConfig = app.uptimeConfig,
            uptimeRepository = app.uptimeRepository,
            pendingSecretGate = app.pendingSecretGate,
            httpEndpointStore = app.httpEndpointStore,
            httpMonitorRepository = app.httpMonitorRepository,
            dashboardPrefs = app.dashboardPrefs,
            wf1Config = app.wf1Config,
            smartTubeStatus = app.smartTubeStatus,
            appearanceStore = app.appearanceStore,
            backgroundImages = app.backgroundImageStore,
        )
        try {
            server.start()
            adminServer = server
            Log.i(TAG, "Admin URL: ${server.publicBaseUrl()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start admin server", e)
            adminServer = null
        }
    }

    private fun stopAdminServer() {
        try {
            adminServer?.stop()
        } catch (_: Exception) {
        }
        adminServer = null
    }

    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    companion object {
        private const val TAG = "HomeMonitorAdmin"
        private const val PREFS_RECLAIM = "hm_reclaim"
        private const val KEY_OVERLAY_PROMPTED = "overlay_prompted"
    }
}
