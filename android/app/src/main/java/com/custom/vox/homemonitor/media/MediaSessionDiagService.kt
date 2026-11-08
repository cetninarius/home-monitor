package com.custom.vox.homemonitor.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Empty listener — required so [android.media.session.MediaSessionManager.getActiveSessions]
 * can return third-party sessions after the user enables notification access.
 */
class MediaSessionDiagService : NotificationListenerService() {

    override fun onListenerConnected() {
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        Log.i(TAG, "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = Unit

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit

    companion object {
        private const val TAG = "MediaSessionDiag"
    }
}
