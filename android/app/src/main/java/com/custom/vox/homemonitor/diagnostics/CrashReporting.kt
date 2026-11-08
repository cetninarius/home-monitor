package com.custom.vox.homemonitor.diagnostics

import android.content.Context
import android.util.Log
import com.custom.vox.homemonitor.BuildConfig
import com.custom.vox.homemonitor.data.settings.DiagnosticsPrefs
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.protocol.User

/**
 * Minimal opt-in crash reporting via Sentry Android SDK.
 * No session replay, screenshots, product analytics, or user identification.
 */
object CrashReporting {
    private const val TAG = "CrashReporting"

    @Volatile
    private var initialized = false

    fun syncWithPrefs(context: Context) {
        val prefs = DiagnosticsPrefs(context)
        if (prefs.isEnabled()) {
            ensureStarted(context.applicationContext)
        } else {
            stop()
        }
    }

    fun ensureStarted(appContext: Context) {
        val dsn = BuildConfig.SENTRY_DSN.trim()
        if (dsn.isEmpty()) {
            Log.w(TAG, "Diagnostics enabled but SENTRY_DSN is empty; not starting")
            return
        }
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            SentryAndroid.init(appContext) { options ->
                options.dsn = dsn
                options.environment = "beta"
                options.release = "home-monitor@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
                options.isSendDefaultPii = false
                options.isEnableUserInteractionTracing = false
                options.isAttachScreenshot = false
                options.isAttachViewHierarchy = false
                options.tracesSampleRate = null
                options.profilesSampleRate = null
                options.sessionReplay.sessionSampleRate = 0.0
                options.sessionReplay.onErrorSampleRate = 0.0
                options.isEnableUncaughtExceptionHandler = true
                options.isAnrEnabled = true
                options.setDiagnosticLevel(SentryLevel.ERROR)
                options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                    filterAndScrub(event)
                }
            }
            // Explicitly clear any user identity the SDK might attach.
            Sentry.setUser(null)
            initialized = true
            Log.i(TAG, "Crash reporting started (env=beta release=${BuildConfig.VERSION_NAME})")
        }
    }

    fun stop() {
        if (!initialized) return
        synchronized(this) {
            if (!initialized) return
            try {
                Sentry.close()
            } catch (e: Exception) {
                Log.w(TAG, "Sentry.close failed: ${e.message}")
            }
            initialized = false
            Log.i(TAG, "Crash reporting stopped")
        }
    }

    fun isActive(): Boolean = initialized

    /**
     * Debug/CI-only path for verifying scrubbing + transport. Never exposed in UI.
     * Returns false when reporting is inactive or not a debug build.
     */
    fun captureVerificationExceptionIfDebug(message: String = "home-monitor diagnostics verification"): Boolean {
        if (!BuildConfig.DEBUG || !initialized) return false
        Sentry.captureException(IllegalStateException(message))
        return true
    }

    internal fun filterAndScrub(event: SentryEvent): SentryEvent? {
        val message = event.message?.formatted
            ?: event.exceptions?.firstOrNull()?.value
            ?: event.throwable?.message
        if (DiagnosticsScrubber.isExpectedNoise(message)) {
            return null
        }
        event.message?.let { msg ->
            msg.message = DiagnosticsScrubber.scrubText(msg.message)
            msg.formatted = DiagnosticsScrubber.scrubText(msg.formatted)
        }
        event.exceptions?.forEach { ex ->
            ex.value = DiagnosticsScrubber.scrubText(ex.value)
            ex.type = DiagnosticsScrubber.scrubText(ex.type)
        }
        event.fingerprints = event.fingerprints?.mapNotNull { DiagnosticsScrubber.scrubText(it) }
        event.user = null
        event.request?.let { req ->
            req.url = DiagnosticsScrubber.scrubText(req.url)
            req.cookies = null
            req.headers?.let { headers ->
                val scrubbed = headers.toMutableMap()
                scrubbed.keys.toList().forEach { key ->
                    if (key.equals("Authorization", ignoreCase = true) ||
                        key.equals("Cookie", ignoreCase = true)
                    ) {
                        scrubbed[key] = "[REDACTED]"
                    } else {
                        scrubbed[key] = DiagnosticsScrubber.scrubText(scrubbed[key]) ?: ""
                    }
                }
                req.headers = scrubbed
            }
            req.data = null
        }
        event.breadcrumbs?.forEach { crumb ->
            crumb.message = DiagnosticsScrubber.scrubText(crumb.message)
            crumb.category = DiagnosticsScrubber.scrubText(crumb.category)
            crumb.data?.let { data ->
                val copy = data.toMutableMap()
                DiagnosticsScrubber.scrubMap(copy)
                data.clear()
                data.putAll(copy)
            }
        }
        event.extras?.let { extras ->
            val copy = extras.toMutableMap()
            DiagnosticsScrubber.scrubMap(copy)
            event.extras = copy
        }
        event.tags?.let { tags ->
            val copy = tags.toMutableMap()
            copy.keys.toList().forEach { k ->
                copy[k] = DiagnosticsScrubber.scrubText(copy[k]) ?: ""
            }
            // Drop tags that look like personal config
            listOf("wf1", "admin", "token", "url").forEach { bad ->
                copy.keys.filter { it.lowercase().contains(bad) }.forEach { copy.remove(it) }
            }
            event.tags = copy
        }
        // Never send a synthetic user object.
        event.user = User()
        event.user = null
        return event
    }
}
