package com.custom.vox.homemonitor.media

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.custom.vox.homemonitor.data.media.SessionMediaReader

enum class SmartTubeState {
    NOT_INSTALLED,
    PERMISSION_REQUIRED,
    READY_NO_SESSION,
    ACTIVE_SESSION,
    PLAYBACK_AVAILABLE,
    ERROR,
}

data class SmartTubeStatusSnapshot(
    val state: SmartTubeState,
    val installed: Boolean,
    val notificationAccess: Boolean,
    val hasSession: Boolean,
    val playing: Boolean,
    val detail: String,
)

class SmartTubeIntegrationStatus(
    private val context: Context,
) {
    fun snapshot(): SmartTubeStatusSnapshot {
        val installed = isInstalled()
        val reader = SessionMediaReader(context)
        val nls = reader.isNotificationListenerEnabled()
        val raw = try {
            reader.readRaw()
        } catch (e: Exception) {
            return SmartTubeStatusSnapshot(
                state = SmartTubeState.ERROR,
                installed = installed,
                notificationAccess = nls,
                hasSession = false,
                playing = false,
                detail = e.message ?: "error",
            )
        }
        val hasSession = raw != null
        val playing = raw?.stateCode == android.media.session.PlaybackState.STATE_PLAYING
        val state = when {
            !installed -> SmartTubeState.NOT_INSTALLED
            !nls -> SmartTubeState.PERMISSION_REQUIRED
            playing -> SmartTubeState.PLAYBACK_AVAILABLE
            hasSession -> SmartTubeState.ACTIVE_SESSION
            else -> SmartTubeState.READY_NO_SESSION
        }
        val detail = when (state) {
            SmartTubeState.NOT_INSTALLED -> "SmartTube (org.smarttube.stable) is not installed"
            SmartTubeState.PERMISSION_REQUIRED -> "Grant Notification access for MediaSession"
            SmartTubeState.READY_NO_SESSION -> "Ready — start a video in SmartTube"
            SmartTubeState.ACTIVE_SESSION -> "Session detected (state=${raw?.stateCode})"
            SmartTubeState.PLAYBACK_AVAILABLE -> "Playback active"
            SmartTubeState.ERROR -> "Diagnostic error"
        }
        return SmartTubeStatusSnapshot(
            state = state,
            installed = installed,
            notificationAccess = nls,
            hasSession = hasSession,
            playing = playing,
            detail = detail,
        )
    }

    fun isInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(SessionMediaReader.TARGET_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * VOX / TitanOS does not resolve the phone-style Notification Listener settings
     * intents (they crash or ActivityNotFound). Open TV Apps settings instead and
     * tell the user the path.
     *
     * @return human message for the Settings UI
     */
    fun openNotificationAccessSettings(): String {
        val pm = context.packageManager
        val candidates = listOf(
            // Leanback Apps list — from here: Special app access → Notification access.
            Intent().setClassName(
                "com.android.tv.settings",
                "com.android.tv.settings.device.apps.AppsActivity",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(Settings.ACTION_APPLICATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        for (intent in candidates) {
            try {
                if (intent.resolveActivity(pm) == null) continue
                context.startActivity(intent)
                return NLS_PATH_HINT
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "NLS settings candidate missing: ${intent.component ?: intent.action}")
            } catch (e: Exception) {
                Log.w(TAG, "NLS settings candidate failed: ${intent.component ?: intent.action}", e)
            }
        }
        return "Could not open Settings. Manually: $NLS_PATH_HINT"
    }

    /**
     * Launch SmartTube. When [playEggVideo] is true, prefer opening the official
     * Rick Astley video URL through SmartTube; fall back to a normal launch.
     *
     * @return null on success; otherwise an error/status message for the UI.
     */
    fun openSmartTube(playEggVideo: Boolean = false): String? {
        val pm = context.packageManager
        if (playEggVideo) {
            val view = Intent(Intent.ACTION_VIEW, Uri.parse(EASTER_EGG_VIDEO_URL)).apply {
                setPackage(SessionMediaReader.TARGET_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                if (view.resolveActivity(pm) != null) {
                    context.startActivity(view)
                    return null
                }
            } catch (e: ActivityNotFoundException) {
                Log.i(TAG, "Egg video intent unsupported; falling back to normal launch")
            } catch (e: Exception) {
                Log.w(TAG, "Egg video launch failed; falling back", e)
            }
        }

        val launch = pm.getLeanbackLaunchIntentForPackage(SessionMediaReader.TARGET_PACKAGE)
            ?: pm.getLaunchIntentForPackage(SessionMediaReader.TARGET_PACKAGE)
        if (launch == null) {
            return "SmartTube not installed (org.smarttube.stable)"
        }
        return try {
            launch.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            )
            context.startActivity(launch)
            null
        } catch (e: ActivityNotFoundException) {
            "SmartTube not installed (org.smarttube.stable)"
        } catch (e: Exception) {
            Log.w(TAG, "Open SmartTube failed", e)
            e.message ?: "Could not open SmartTube"
        }
    }

    fun publicConfig(): Map<String, Any?> {
        val s = snapshot()
        return mapOf(
            "state" to s.state.name,
            "installed" to s.installed,
            "notificationAccess" to s.notificationAccess,
            "hasSession" to s.hasSession,
            "playing" to s.playing,
            "detail" to s.detail,
            "package" to SessionMediaReader.TARGET_PACKAGE,
            "setupSteps" to listOf(
                "Install SmartTube (org.smarttube.stable) from its official source",
                "SmartTube → Settings → General → Background playback → Only audio (HOME or BACK)",
                NLS_PATH_HINT,
                "Play a video in SmartTube, return here, confirm Now Playing",
            ),
        )
    }

    companion object {
        private const val TAG = "SmartTubeStatus"
        const val NLS_PATH_HINT =
            "Settings → Apps → Special app access → Notification access → Home Monitor"
        const val EASTER_EGG_VIDEO_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    }
}
