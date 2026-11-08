package com.custom.vox.homemonitor.media

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.custom.vox.homemonitor.MainActivity
import com.custom.vox.homemonitor.R

/**
 * Background Activity Launch is blocked on this TV (`isBgStartWhitelisted: false`).
 * Full-screen notification intents are an allowed path back to [MainActivity].
 * SYSTEM_ALERT_WINDOW also exempts BAL when the user has granted overlay.
 */
class ReclaimLauncher(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        ensureChannel()
    }

    fun bringDashboardToFront(reason: String) {
        val overlay = canDrawOverlays()
        Log.i(TAG, "bringDashboardToFront reason=$reason overlay=$overlay")
        if (overlay) {
            if (tryMoveTaskToFront()) return
            tryStartDirect()
            return
        }
        fireFullScreenIntent()
    }

    private fun tryMoveTaskToFront(): Boolean {
        return try {
            val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val tasks = am.appTasks
            if (tasks.isNullOrEmpty()) {
                Log.i(TAG, "no AppTasks to moveToFront")
                false
            } else {
                tasks[0].moveToFront()
                Log.i(TAG, "AppTask.moveToFront ok count=${tasks.size}")
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "moveToFront failed: ${e.message}")
            false
        }
    }

    fun canDrawOverlays(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(appContext)
        } else {
            true
        }

    fun overlaySettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${appContext.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun tryStartDirect() {
        try {
            appContext.startActivity(mainActivityIntent())
        } catch (e: Exception) {
            Log.w(TAG, "direct start failed: ${e.message}")
        }
    }

    private fun fireFullScreenIntent() {
        val contentPi = PendingIntent.getActivity(
            appContext,
            REQ_CONTENT,
            mainActivityIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val fullScreenPi = PendingIntent.getActivity(
            appContext,
            REQ_FULLSCREEN,
            mainActivityIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_np_play)
            .setContentTitle(appContext.getString(R.string.app_name))
            .setContentText(appContext.getString(R.string.reclaim_notification_text))
            .setContentIntent(contentPi)
            .setFullScreenIntent(fullScreenPi, true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(4_000L)
            .build()
        try {
            nm.notify(NOTIF_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "notify failed: ${e.message}")
        }
    }

    private fun mainActivityIntent(): Intent =
        Intent(appContext, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.reclaim_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = appContext.getString(R.string.reclaim_channel_desc)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ReclaimLauncher"
        private const val CHANNEL_ID = "hm_reclaim"
        private const val NOTIF_ID = 7101
        private const val REQ_CONTENT = 7102
        private const val REQ_FULLSCREEN = 7103
    }
}
