package com.custom.vox.homemonitor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.custom.vox.homemonitor.data.services.HttpEndpointStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HttpEndpointStoreTest {
    private lateinit var store: HttpEndpointStore

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("home_monitor_http_monitors", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = HttpEndpointStore(ctx)
    }

    @Test
    fun addEnableDisableAndCap() {
        val a = store.add("Router", "http://192.168.0.1/")
        assertTrue(a.enabled)
        store.upsert(a.copy(enabled = false))
        assertFalse(store.get(a.id)!!.enabled)

        repeat(4) { i ->
            store.add("E$i", "http://192.168.0.${10 + i}/")
        }
        assertEquals(5, store.list().size)
        try {
            store.add("Overflow", "http://192.168.0.99/")
            throw AssertionError("expected max_endpoints")
        } catch (e: IllegalArgumentException) {
            assertEquals("max_endpoints", e.message)
        }
    }

    @Test
    fun rejectsBlockedUrl() {
        try {
            store.add("Meta", "http://169.254.169.254/")
            throw AssertionError("expected blocked")
        } catch (e: IllegalArgumentException) {
            assertEquals("blocked_host", e.message)
        }
    }
}
