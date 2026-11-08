package com.custom.vox.homemonitor.data.services

enum class ServiceStatus {
    UP,
    DOWN,
    PAUSED,
    UNKNOWN,
    STALE,
    UNCONFIGURED,
    CHECKING,
}

enum class ServiceProvider {
    UPTIMEROBOT,
    HTTP,
}

data class ServiceMonitor(
    val id: String,
    val name: String,
    val status: ServiceStatus,
    val provider: ServiceProvider = ServiceProvider.UPTIMEROBOT,
    val uptimeRatio30d: Double? = null,
    val responseTimeMs: Long? = null,
    val lastSuccessAtMs: Long? = null,
    val lastAttemptAtMs: Long? = null,
    val error: String? = null,
    /** @deprecated use lastSuccessAtMs */
    val lastCheckAtMs: Long? = lastSuccessAtMs,
)

data class ServicesSnapshot(
    val monitors: List<ServiceMonitor>,
    val refreshedAtMs: Long?,
    val configured: Boolean,
    val error: String? = null,
)

data class UptimeMonitorInfo(
    val id: String,
    val name: String,
    val status: ServiceStatus,
)
