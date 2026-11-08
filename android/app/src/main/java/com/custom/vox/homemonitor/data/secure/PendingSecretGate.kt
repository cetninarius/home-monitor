package com.custom.vox.homemonitor.data.secure

import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * In-memory pending secret from LAN admin. Must be confirmed on the TV remote
 * before it is written to [SecretStore]. Compensates for cleartext HTTP admin.
 */
class PendingSecretGate {
    data class Pending(
        val id: String,
        val kind: Kind,
        val secret: String,
        val createdAtElapsed: Long,
        val expiresAtElapsed: Long,
        val meta: Map<String, String> = emptyMap(),
    ) {
        enum class Kind { UPTIMEROBOT_API_KEY, WF1_PROXY_TOKEN }
    }

    private val pending = AtomicReference<Pending?>(null)

    fun submit(
        kind: Pending.Kind,
        secret: String,
        meta: Map<String, String> = emptyMap(),
        ttlMs: Long = DEFAULT_TTL_MS,
    ): Pending {
        val now = SystemClock.elapsedRealtime()
        val p = Pending(
            id = UUID.randomUUID().toString().take(8),
            kind = kind,
            secret = secret.trim(),
            createdAtElapsed = now,
            expiresAtElapsed = now + ttlMs,
            meta = meta,
        )
        pending.set(p)
        return p.copy(secret = "") // never return secret to callers of submit response shape
    }

    /** Public status without the secret material. */
    fun peekPublic(): Map<String, Any?> {
        val p = pending.get() ?: return mapOf("pending" to false)
        if (SystemClock.elapsedRealtime() > p.expiresAtElapsed) {
            pending.compareAndSet(p, null)
            return mapOf("pending" to false, "expired" to true)
        }
        return mapOf(
            "pending" to true,
            "id" to p.id,
            "kind" to p.kind.name,
            "expiresInMs" to (p.expiresAtElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0),
            "meta" to p.meta,
        )
    }

    fun hasPending(): Boolean = peekPublic()["pending"] == true

    fun confirm(id: String): Pending? {
        val p = pending.get() ?: return null
        if (p.id != id) return null
        if (SystemClock.elapsedRealtime() > p.expiresAtElapsed) {
            pending.compareAndSet(p, null)
            return null
        }
        return if (pending.compareAndSet(p, null)) p else null
    }

    fun cancel(id: String? = null): Boolean {
        val p = pending.get() ?: return false
        if (id != null && p.id != id) return false
        return pending.compareAndSet(p, null)
    }

    /** For tests only — never log the secret. */
    internal fun peekForTest(): Pending? = pending.get()

    companion object {
        const val DEFAULT_TTL_MS = 120_000L
    }
}
