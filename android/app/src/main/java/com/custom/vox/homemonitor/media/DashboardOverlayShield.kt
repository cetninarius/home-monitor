package com.custom.vox.homemonitor.media

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import com.custom.vox.homemonitor.R
import com.custom.vox.homemonitor.dashboard.DashboardController

/**
 * Live fullscreen overlay hosting a real [DashboardController] so Now Playing
 * keeps updating and D-pad focus works, while SmartTube stays underneath for audio.
 */
class DashboardOverlayShield(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())

    private var overlayRoot: View? = null
    private var controller: DashboardController? = null
    private var showing = false
    private var onExitRequest: (() -> Unit)? = null

    fun canDrawOverlays(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(appContext)
        } else {
            true
        }

    fun isShowing(): Boolean = showing

    fun setExitHandler(handler: (() -> Unit)?) {
        onExitRequest = handler
    }

    fun show(reason: String) {
        if (!canDrawOverlays()) {
            Log.w(TAG, "cannot show — no overlay permission ($reason)")
            return
        }
        val run = Runnable { showNow(reason) }
        if (Looper.myLooper() == Looper.getMainLooper()) run.run() else main.post(run)
    }

    private fun showNow(reason: String) {
        if (showing && overlayRoot != null) {
            Log.i(TAG, "already showing ($reason)")
            // Keep focus on the live dashboard if something stole it.
            overlayRoot?.requestFocus()
            return
        }
        try {
            forceRemoveOverlay()
            val themed = ContextThemeWrapper(appContext, R.style.Theme_HomeMonitor)
            val content = LayoutInflater.from(themed)
                .inflate(R.layout.activity_dashboard, null, false)
            val dash = DashboardController(content)
            dash.setup()
            dash.start()

            // Wrapper owns key dispatch so BACK works even when a child (player/todo)
            // has focus — View.OnKeyListener on the content root only fires when focused.
            val root = object : FrameLayout(themed) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    val keyCode = event.keyCode
                    if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                        if (event.action == KeyEvent.ACTION_UP) {
                            if (dash.closeAppearance()) {
                                Log.i(TAG, "BACK closed appearance panel")
                            } else {
                                Log.i(TAG, "BACK/exit on live overlay")
                                onExitRequest?.invoke()
                            }
                        }
                        return true
                    }
                    if (event.action == KeyEvent.ACTION_DOWN &&
                        (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS)
                    ) {
                        dash.toggleAppearance()
                        return true
                    }
                    if (event.action == KeyEvent.ACTION_DOWN && isFocusEntryKey(keyCode)) {
                        if (dash.isAppearanceOpen()) {
                            return super.dispatchKeyEvent(event)
                        }
                        val focused = findFocus()
                        if (focused == null || focused === this || focused === content) {
                            val target = dash.preferredFocus()
                            if (target != null && target.requestFocus()) {
                                Log.i(TAG, "D-pad entered focus → ${target.javaClass.simpleName}")
                                return true
                            }
                        }
                    }
                    return super.dispatchKeyEvent(event)
                }
            }
            root.isClickable = true
            root.isFocusable = true
            root.isFocusableInTouchMode = true
            root.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            root.addView(
                content,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            wm.addView(root, overlayParams())
            overlayRoot = root
            controller = dash
            showing = true
            // Park focus on wrapper with no control highlight until the user presses D-pad.
            root.post { root.requestFocus() }
            Log.i(TAG, "live dashboard shown reason=$reason")
        } catch (e: Exception) {
            Log.e(TAG, "show failed: ${e.message}", e)
            forceRemoveOverlay()
        }
    }

    fun hide(reason: String) {
        val run = Runnable {
            if (!showing && overlayRoot == null) return@Runnable
            Log.i(TAG, "hide reason=$reason")
            forceRemoveOverlay()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run.run() else main.post(run)
    }

    private fun forceRemoveOverlay() {
        val dash = controller
        controller = null
        val root = overlayRoot
        overlayRoot = null
        showing = false
        try {
            dash?.stop()
        } catch (_: Exception) {
        }
        if (root != null) {
            try {
                wm.removeView(root)
            } catch (_: Exception) {
            }
        }
    }

    private fun overlayParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        // Focusable + touchable so D-pad moves focus on the live dashboard.
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "HomeMonitorLive"
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun isFocusEntryKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

    companion object {
        private const val TAG = "DashboardOverlayShield"
    }
}
