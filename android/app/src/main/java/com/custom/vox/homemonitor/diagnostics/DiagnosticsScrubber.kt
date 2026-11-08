package com.custom.vox.homemonitor.diagnostics

import java.util.regex.Pattern

/**
 * Client-side scrubbing for diagnostic events before any network send.
 * Drops or redacts calendar/todo content, tokens, private URLs, and WF1 payloads.
 */
object DiagnosticsScrubber {

    private val PRIVATE_IPV4 = Pattern.compile(
        """\b(?:10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[0-1])\.\d{1,3}\.\d{1,3})\b""",
    )
    private val BEARER = Pattern.compile("""(?i)(Bearer\s+)[A-Za-z0-9._\-+=/]{6,}""")
    private val ADMIN_TOKEN = Pattern.compile("""\b[A-Z0-9]{4}-[A-Z0-9]{4}(?:-[A-Z0-9]{4})?\b""")
    private val URL_WITH_CREDS = Pattern.compile("""(?i)(https?://)[^/\s:@]+:[^/\s@]+@""")
    private val QUERY_SECRET = Pattern.compile(
        """(?i)([?&](?:token|api[_-]?key|auth|password|secret)=)[^&\s]+""",
    )

    /** Messages that represent expected optional-integration states — never report. */
    private val EXPECTED_MESSAGE_FRAGMENTS = listOf(
        "not_configured",
        "wf1 not configured",
        "no wf1",
        "smarttube not installed",
        "notification access",
        "permission_required",
        "unable to refresh wf1",
        "empty body",
        "unknownhost",
        "failed to connect",
        "network is unreachable",
        "software caused connection abort",
        "timeout",
        "socket closed",
    )

    fun isExpectedNoise(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val lower = message.lowercase()
        return EXPECTED_MESSAGE_FRAGMENTS.any { lower.contains(it) }
    }

    fun scrubText(input: String?): String? {
        if (input == null) return null
        var out: CharSequence = input
        out = BEARER.matcher(out).replaceAll("$1[REDACTED]")
        out = URL_WITH_CREDS.matcher(out).replaceAll("$1[REDACTED]@")
        out = QUERY_SECRET.matcher(out).replaceAll("$1[REDACTED]")
        out = PRIVATE_IPV4.matcher(out).replaceAll("[REDACTED_IP]")
        out = ADMIN_TOKEN.matcher(out).replaceAll("[REDACTED_TOKEN]")
        return out.toString()
    }

    fun scrubMap(values: MutableMap<String, Any?>) {
        val keys = values.keys.toList()
        for (key in keys) {
            val lower = key.lowercase()
            if (SENSITIVE_KEYS.any { lower.contains(it) }) {
                values[key] = "[REDACTED]"
                continue
            }
            when (val v = values[key]) {
                is String -> values[key] = scrubText(v)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val copy = (v as Map<String, Any?>).toMutableMap()
                    scrubMap(copy)
                    values[key] = copy
                }
            }
        }
    }

    private val SENSITIVE_KEYS = listOf(
        "token", "password", "secret", "apikey", "api_key", "authorization",
        "cookie", "title", "description", "event", "task", "todo", "calendar",
        "wf1", "payload", "artwork", "media", "metadata", "baseurl", "url",
    )
}
