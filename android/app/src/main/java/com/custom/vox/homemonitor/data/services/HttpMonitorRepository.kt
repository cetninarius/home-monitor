package com.custom.vox.homemonitor.data.services

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight reachability checks from the TV. Success = HTTP 2xx.
 * Does not compute historical uptime.
 */
class HttpMonitorRepository(
    private val store: HttpEndpointStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
) {
    @Volatile
    var lastSnapshot: ServicesSnapshot? = null
        private set

    private val inFlight = AtomicBoolean(false)

    fun refresh(): ServicesSnapshot {
        val endpoints = store.list().filter { it.enabled }
        if (endpoints.isEmpty()) {
            val snap = ServicesSnapshot(emptyList(), null, configured = store.list().isNotEmpty())
            lastSnapshot = snap
            return snap
        }
        if (!inFlight.compareAndSet(false, true)) {
            return lastSnapshot ?: ServicesSnapshot(emptyList(), null, true, error = "Busy")
        }
        return try {
            val now = System.currentTimeMillis()
            val monitors = endpoints.map { checkOne(it, now) }
            val snap = ServicesSnapshot(
                monitors = monitors,
                refreshedAtMs = now,
                configured = true,
            )
            lastSnapshot = snap
            snap
        } finally {
            inFlight.set(false)
        }
    }

    fun test(url: String): Result<ServiceMonitor> = runCatching {
        val validated = HttpUrlValidator.validate(url).getOrThrow()
        checkOne(
            HttpEndpoint(id = "test", name = "Test", url = validated.toString(), enabled = true),
            System.currentTimeMillis(),
        )
    }

    private fun checkOne(ep: HttpEndpoint, now: Long): ServiceMonitor {
        val attemptAt = now
        val urlResult = HttpUrlValidator.validate(ep.url)
        if (urlResult.isFailure) {
            return ServiceMonitor(
                id = "http:${ep.id}",
                name = ep.name,
                status = ServiceStatus.UNKNOWN,
                provider = ServiceProvider.HTTP,
                lastAttemptAtMs = attemptAt,
                error = urlResult.exceptionOrNull()?.message ?: "invalid_url",
            )
        }
        val url = urlResult.getOrThrow()
        val started = System.nanoTime()
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "HomeMonitor/1.7")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                // Validate final URL after redirects
                val finalUrl = response.request.url.toString()
                HttpUrlValidator.validateRedirectTarget(finalUrl).getOrElse {
                    return ServiceMonitor(
                        id = "http:${ep.id}",
                        name = ep.name,
                        status = ServiceStatus.UNKNOWN,
                        provider = ServiceProvider.HTTP,
                        lastAttemptAtMs = attemptAt,
                        error = "redirect_blocked",
                    )
                }
                // Bound body read
                response.body?.source()?.request(MAX_BODY_PEEK)
                response.body?.close()
                val elapsed = (System.nanoTime() - started) / 1_000_000L
                val code = response.code
                val status = when {
                    code in 200..299 -> ServiceStatus.UP
                    code in 400..599 -> ServiceStatus.DOWN
                    else -> ServiceStatus.UNKNOWN
                }
                ServiceMonitor(
                    id = "http:${ep.id}",
                    name = ep.name,
                    status = status,
                    provider = ServiceProvider.HTTP,
                    responseTimeMs = elapsed,
                    lastSuccessAtMs = if (status == ServiceStatus.UP) now else null,
                    lastAttemptAtMs = attemptAt,
                    error = if (status != ServiceStatus.UP) "HTTP $code" else null,
                )
            }
        } catch (e: Exception) {
            ServiceMonitor(
                id = "http:${ep.id}",
                name = ep.name,
                status = ServiceStatus.UNKNOWN,
                provider = ServiceProvider.HTTP,
                lastAttemptAtMs = attemptAt,
                error = e.message ?: "error",
            )
        }
    }

    companion object {
        const val REFRESH_MS = 60_000L
        private const val MAX_BODY_PEEK = 8_192L
    }
}
