package com.custom.vox.homemonitor.data.services

import java.net.InetAddress
import java.net.URI
import java.net.URL

/**
 * Validates user-supplied monitor URLs. Allows public + RFC1918 home LAN;
 * blocks metadata endpoints and non-http(s) schemes.
 */
object HttpUrlValidator {
    private val BLOCKED_HOSTS = setOf(
        "metadata.google.internal",
        "metadata.google.com",
        "kubernetes.default",
        "kubernetes.default.svc",
    )

    fun validate(raw: String): Result<URL> = runCatching {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "empty_url" }
        val uri = URI(trimmed)
        val scheme = uri.scheme?.lowercase() ?: error("missing_scheme")
        require(scheme == "http" || scheme == "https") { "unsupported_scheme" }
        require(uri.userInfo.isNullOrEmpty()) { "embedded_credentials" }
        val host = uri.host?.lowercase() ?: error("missing_host")
        require(host !in BLOCKED_HOSTS) { "blocked_host" }
        if (isBlockedIpLiteral(host)) error("blocked_host")
        val url = uri.toURL()
        // Resolve and re-check after DNS for metadata-like addresses
        try {
            val addrs = InetAddress.getAllByName(host)
            if (addrs.any { isBlockedAddress(it) }) error("blocked_host")
        } catch (_: Exception) {
            // DNS failure is OK at config time; runtime check will surface UNKNOWN
        }
        url
    }

    fun validateRedirectTarget(raw: String): Result<URL> = validate(raw)

    fun isBlockedIpLiteral(host: String): Boolean {
        val h = host.removePrefix("[").removeSuffix("]")
        return try {
            isBlockedAddress(InetAddress.getByName(h))
        } catch (_: Exception) {
            false
        }
    }

    fun isBlockedAddress(addr: InetAddress): Boolean {
        val host = addr.hostAddress ?: return false
        // Link-local / cloud metadata / loopback (avoid TV local admin via localhost)
        if (host == "169.254.169.254" || host.startsWith("169.254.")) return true
        if (host.startsWith("127.") || host == "::1" || addr.isLoopbackAddress) return true
        if (host == "0.0.0.0" || host == "::") return true
        return false
    }
}
