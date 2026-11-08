package com.custom.vox.homemonitor.media

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * SmartTube's own boot receiver usually starts Lounge, but force-stop disables it
 * until something launches the app. We warm SmartTube under an HM overlay so the
 * user never sees BrowseActivity when opening Home Monitor from Plume.
 *
 * Important: [ActivityManager.getRunningAppProcesses] on modern Android often
 * omits other apps, so it cannot be trusted alone — otherwise every onResume
 * relaunches SmartTube and thrashs the MediaSession (Now Playing dies).
 */
object SmartTubeWarmup {
    private const val TAG = "SmartTubeWarmup"
    const val PACKAGE = "org.smarttube.stable"

    private val main = Handler(Looper.getMainLooper())

    /** After we successfully warm (or observe) SmartTube, skip re-warm for a while. */
    @Volatile
    private var warmLatchUntilElapsed: Long = 0L

    @Volatile
    private var warmInFlight: Boolean = false

    fun isProcessRunning(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return try {
            @Suppress("DEPRECATION")
            am.runningAppProcesses?.any { it.processName == PACKAGE } == true
        } catch (_: Exception) {
            false
        }
    }

    /** True when we should kick SmartTube for Lounge. */
    fun needsWarmup(context: Context): Boolean {
        if (warmInFlight) return false
        if (isProcessRunning(context)) {
            markWarmed("process-visible")
            return false
        }
        if (SystemClock.elapsedRealtime() < warmLatchUntilElapsed) return false
        return true
    }

    /**
     * @param returnToHome after launching ST, go to the TV launcher (boot path).
     * @param returnToPackage after launching ST, bring this package back (HM path).
     * @param onComplete always invoked on the main thread when warmup finishes
     *   (already running, start failed, or return delay elapsed).
     */
    fun ensureRunning(
        context: Context,
        returnToHome: Boolean = false,
        returnToPackage: String? = null,
        onComplete: (() -> Unit)? = null,
    ) {
        if (!needsWarmup(context)) {
            Log.i(TAG, "skip warmup (already warm / latch)")
            onComplete?.let { main.post(it) }
            return
        }
        val app = context.applicationContext
        val launch = leanbackLaunchIntent(app)
        if (launch == null) {
            onComplete?.let { main.post(it) }
            return
        }
        warmInFlight = true
        try {
            app.startActivity(launch)
            markWarmed("started")
            Log.i(TAG, "started SmartTube for Lounge warmup")
        } catch (e: Exception) {
            warmInFlight = false
            Log.w(TAG, "start failed: ${e.message}")
            onComplete?.let { main.post(it) }
            return
        }
        when {
            returnToPackage != null -> {
                main.postDelayed({
                    warmInFlight = false
                    try {
                        val back = app.packageManager.getLeanbackLaunchIntentForPackage(returnToPackage)
                            ?: app.packageManager.getLaunchIntentForPackage(returnToPackage)
                        if (back != null) {
                            back.addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
                            )
                            app.startActivity(back)
                            Log.i(TAG, "returned to $returnToPackage")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "return failed: ${e.message}")
                    }
                    onComplete?.invoke()
                }, RETURN_DELAY_MS)
            }
            returnToHome -> {
                main.postDelayed({
                    warmInFlight = false
                    try {
                        app.startActivity(
                            Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                        )
                        Log.i(TAG, "returned HOME after warmup")
                    } catch (e: Exception) {
                        Log.w(TAG, "HOME failed: ${e.message}")
                    }
                    onComplete?.invoke()
                }, RETURN_DELAY_MS)
            }
            else -> {
                warmInFlight = false
                onComplete?.let { main.post(it) }
            }
        }
    }

    private fun markWarmed(reason: String) {
        warmLatchUntilElapsed = SystemClock.elapsedRealtime() + WARM_LATCH_MS
        Log.i(TAG, "warm latch ${WARM_LATCH_MS}ms ($reason)")
    }

    private fun leanbackLaunchIntent(context: Context): Intent? {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(PACKAGE)
            ?: pm.getLaunchIntentForPackage(PACKAGE)
            ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    private const val RETURN_DELAY_MS = 2_200L
    private const val WARM_LATCH_MS = 10 * 60_000L
}
