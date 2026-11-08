package com.custom.vox.homemonitor.data.services

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Merges UptimeRobot + Direct HTTP into one snapshot for the Services widget.
 */
class ServiceMonitorHub(
    private val uptime: UptimeRepository,
    private val http: HttpMonitorRepository,
    private val uptimeConfig: UptimeConfigStore,
    private val httpStore: HttpEndpointStore,
) {
    @Volatile
    var lastSnapshot: ServicesSnapshot? = null
        private set

    private val poke = AtomicBoolean(false)

    /** Ask the Services widget to refresh immediately (e.g. after monitor selection). */
    fun requestRefresh() {
        poke.set(true)
    }

    fun consumeRefreshRequest(): Boolean = poke.getAndSet(false)

    fun refresh(): ServicesSnapshot {
        val urConfigured = uptimeConfig.isConfigured()
        val httpConfigured = httpStore.list().any { it.enabled }
        val ur = if (urConfigured) uptime.refresh() else ServicesSnapshot(emptyList(), null, false)
        val httpSnap = if (httpConfigured) http.refresh() else ServicesSnapshot(emptyList(), null, false)
        val monitors = ur.monitors + httpSnap.monitors
        val errors = listOfNotNull(ur.error, httpSnap.error)
        val snap = ServicesSnapshot(
            monitors = monitors,
            refreshedAtMs = listOfNotNull(ur.refreshedAtMs, httpSnap.refreshedAtMs).maxOrNull(),
            configured = urConfigured || httpConfigured || httpStore.list().isNotEmpty(),
            error = errors.firstOrNull(),
        )
        lastSnapshot = snap
        return snap
    }

    fun hasAnyConfiguration(): Boolean =
        uptimeConfig.isConfigured() || httpStore.list().isNotEmpty()
}
