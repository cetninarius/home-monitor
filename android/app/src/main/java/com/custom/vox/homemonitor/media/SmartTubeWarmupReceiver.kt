package com.custom.vox.homemonitor.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Backup for SmartTube's own boot/screen-on remote receiver. After a force-stop,
 * SmartTube will not auto-start until something launches it — we do that here,
 * then return to the TV launcher so BrowseActivity does not stay on screen.
 */
class SmartTubeWarmupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "warmup trigger action=$action")
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> SmartTubeWarmup.ensureRunning(context, returnToHome = true)
        }
    }

    companion object {
        private const val TAG = "SmartTubeWarmupRx"
    }
}
