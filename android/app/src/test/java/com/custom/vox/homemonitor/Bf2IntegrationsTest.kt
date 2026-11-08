package com.custom.vox.homemonitor

import com.custom.vox.homemonitor.data.secure.PendingSecretGate
import com.custom.vox.homemonitor.data.services.HttpUrlValidator
import com.custom.vox.homemonitor.data.services.ServiceStatus
import com.custom.vox.homemonitor.data.services.UptimeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Bf2IntegrationsTest {

    @Test
    fun uptimeStatusMapping() {
        assertEquals(ServiceStatus.UP, UptimeRepository.mapStatusCode(2))
        assertEquals(ServiceStatus.DOWN, UptimeRepository.mapStatusCode(8))
        assertEquals(ServiceStatus.DOWN, UptimeRepository.mapStatusCode(9))
        assertEquals(ServiceStatus.PAUSED, UptimeRepository.mapStatusCode(0))
        assertEquals(ServiceStatus.UNKNOWN, UptimeRepository.mapStatusCode(1))
        assertEquals(ServiceStatus.UNKNOWN, UptimeRepository.mapStatusCode(99))
    }

    @Test
    fun httpUrlValidator_acceptsLanAndRejectsMetadata() {
        assertTrue(HttpUrlValidator.validate("https://example.com/health").isSuccess)
        assertTrue(HttpUrlValidator.validate("http://192.168.0.1/").isSuccess)
        assertTrue(HttpUrlValidator.validate("http://10.0.0.5:8080/ping").isSuccess)

        assertEquals(
            "unsupported_scheme",
            HttpUrlValidator.validate("ftp://192.168.0.1/").exceptionOrNull()?.message,
        )
        assertEquals(
            "embedded_credentials",
            HttpUrlValidator.validate("http://user:pass@192.168.0.1/").exceptionOrNull()?.message,
        )
        assertEquals(
            "blocked_host",
            HttpUrlValidator.validate("http://169.254.169.254/latest/meta-data/").exceptionOrNull()?.message,
        )
        assertEquals(
            "blocked_host",
            HttpUrlValidator.validate("http://metadata.google.internal/").exceptionOrNull()?.message,
        )
        assertEquals(
            "blocked_host",
            HttpUrlValidator.validate("http://127.0.0.1:8765/").exceptionOrNull()?.message,
        )
    }

    @Test
    fun httpUrlValidator_redirectTargetUsesSameRules() {
        assertTrue(HttpUrlValidator.validateRedirectTarget("https://status.example/").isSuccess)
        assertTrue(HttpUrlValidator.validateRedirectTarget("http://169.254.169.254/").isFailure)
    }

    @Test
    fun pendingSecretGate_requiresConfirmAndNeverReturnsSecretOnSubmit() {
        val gate = PendingSecretGate()
        val submitted = gate.submit(
            PendingSecretGate.Pending.Kind.UPTIMEROBOT_API_KEY,
            " secret-key-value ",
        )
        assertEquals("", submitted.secret)
        assertTrue(gate.hasPending())
        val pub = gate.peekPublic()
        assertEquals(true, pub["pending"])
        assertFalse(pub.containsKey("secret"))
        assertNull(gate.confirm("wrong-id"))
        val confirmed = gate.confirm(submitted.id)
        assertNotNull(confirmed)
        assertEquals("secret-key-value", confirmed!!.secret)
        assertFalse(gate.hasPending())
    }

    @Test
    fun pendingSecretGate_cancelClears() {
        val gate = PendingSecretGate()
        val p = gate.submit(PendingSecretGate.Pending.Kind.UPTIMEROBOT_API_KEY, "abc")
        assertTrue(gate.cancel(p.id))
        assertFalse(gate.hasPending())
    }
}
