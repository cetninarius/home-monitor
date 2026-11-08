package com.custom.vox.homemonitor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.custom.vox.homemonitor.data.secure.SecretStore
import com.custom.vox.homemonitor.data.services.UptimeConfigStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UptimeConfigStoreTest {
    private lateinit var ctx: Context
    private lateinit var secrets: SecretStore
    private lateinit var store: UptimeConfigStore

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("home_monitor_uptime", Context.MODE_PRIVATE).edit().clear().commit()
        secrets = SecretStore(ctx)
        secrets.remove("uptimerobot_api_key")
        store = UptimeConfigStore(ctx, secrets)
    }

    @Test
    fun migratesLegacyPlaintextKeyAndAbIds() {
        ctx.getSharedPreferences("home_monitor_uptime", Context.MODE_PRIVATE).edit()
            .putString("api_key", "legacy-secret")
            .putString("monitor_id_a", "111")
            .putString("monitor_id_b", "222")
            .putString("label_a", "Sudoku")
            .putString("label_b", "CRM")
            .commit()

        val migrated = UptimeConfigStore(ctx, secrets)
        assertEquals("legacy-secret", migrated.apiKey())
        assertEquals(listOf("111", "222"), migrated.selectedMonitorIds())
        assertEquals("Sudoku", migrated.labelFor("111"))
        assertNull(
            ctx.getSharedPreferences("home_monitor_uptime", Context.MODE_PRIVATE)
                .getString("api_key", null),
        )
        val pub = migrated.publicConfig()
        assertTrue(pub["apiKeySet"] as Boolean)
        assertFalse(pub.containsKey("apiKey"))
    }

    @Test
    fun disconnectWipesKeyAndSelection() {
        store.setApiKey("k")
        store.saveSelection(listOf("1", "2"), mapOf("1" to "A"))
        assertTrue(store.isConfigured())
        store.disconnect()
        assertNull(store.apiKey())
        assertTrue(store.selectedMonitorIds().isEmpty())
        assertFalse(store.isConfigured())
    }
}
