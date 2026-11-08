package com.custom.vox.homemonitor

import com.custom.vox.homemonitor.diagnostics.CrashReporting
import com.custom.vox.homemonitor.diagnostics.DiagnosticsScrubber
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.SentryException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsScrubberTest {
    @Test
    fun redactsPrivateIpsTokensAndBearers() {
        val raw = "Proxy http://192.168.0.63:8792 Bearer abcdefghijklmnop token A356-Q49G-K2M7"
        val scrubbed = DiagnosticsScrubber.scrubText(raw)!!
        assertFalse(scrubbed.contains("192.168"))
        assertFalse(scrubbed.contains("abcdefghijklmnop"))
        assertFalse(scrubbed.contains("A356-Q49G-K2M7"))
        assertTrue(scrubbed.contains("[REDACTED_IP]"))
        assertTrue(scrubbed.contains("[REDACTED]"))
    }

    @Test
    fun expectedNoiseIsFiltered() {
        assertTrue(DiagnosticsScrubber.isExpectedNoise("WF1 not configured"))
        assertTrue(DiagnosticsScrubber.isExpectedNoise("UnknownHostException: proxy.local"))
        assertFalse(DiagnosticsScrubber.isExpectedNoise("NullPointerException in DashboardController"))
    }

    @Test
    fun beforeSendDropsExpectedAndScrubsCrash() {
        val noise = SentryEvent().apply {
            message = Message().also { it.formatted = "not_configured" }
        }
        assertNull(CrashReporting.filterAndScrub(noise))

        val crash = SentryEvent().apply {
            message = Message().also {
                it.formatted = "boom at http://10.0.0.5/admin"
            }
            exceptions = listOf(
                SentryException().also {
                    it.value = "failed near 192.168.1.10"
                    it.type = "IllegalStateException"
                },
            )
        }
        val kept = CrashReporting.filterAndScrub(crash)!!
        assertEquals("boom at http://[REDACTED_IP]/admin", kept.message?.formatted)
        assertEquals("failed near [REDACTED_IP]", kept.exceptions?.first()?.value)
        assertNull(kept.user)
    }
}
