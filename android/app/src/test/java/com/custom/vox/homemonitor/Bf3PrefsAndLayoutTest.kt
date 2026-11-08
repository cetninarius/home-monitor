package com.custom.vox.homemonitor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.custom.vox.homemonitor.admin.AdminAuth
import com.custom.vox.homemonitor.dashboard.DashboardController
import com.custom.vox.homemonitor.dashboard.DashboardLayoutMode
import com.custom.vox.homemonitor.data.appearance.AppearanceStore
import com.custom.vox.homemonitor.data.secure.PendingSecretGate
import com.custom.vox.homemonitor.data.secure.SecretStore
import com.custom.vox.homemonitor.data.settings.DashboardPrefs
import com.custom.vox.homemonitor.data.wf1.Wf1ConfigStore
import com.custom.vox.homemonitor.media.SmartTubeIntegrationStatus
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
class Bf3PrefsAndLayoutTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("home_monitor_dashboard", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("home_monitor_wf1", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("home_monitor_admin", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("home_monitor_secrets_fallback", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun freshInstall_defaultsWf1OffAndEggAvailable() {
        val prefs = DashboardPrefs(context)
        val wf1 = Wf1ConfigStore(context, SecretStore(context))
        prefs.migrateBf3IfNeeded(wf1)
        assertFalse(prefs.smartTubeEasterEggConsumed())
        // Fresh installs keep WF1 disabled. BuildConfig may still seed URL/token
        // for owner/debug builds; beta builds with blank BuildConfig stay unconfigured.
        assertFalse(prefs.wf1Enabled())
        if (com.custom.vox.homemonitor.BuildConfig.WF1_PROXY_BASE_URL.isBlank()) {
            assertFalse(wf1.isConfigured())
        } else {
            assertTrue(wf1.isConfigured())
        }
    }

    @Test
    fun easterEgg_consumedOnce() {
        val prefs = DashboardPrefs(context)
        assertFalse(prefs.smartTubeEasterEggConsumed())
        prefs.markSmartTubeSettingsEggDoneCompat()
        assertTrue(prefs.smartTubeEasterEggConsumed())
    }

    @Test
    fun layoutMode_resolvesFromVisibilityPrefs() {
        // Idle NP preference alone stays on HOME / WORKFLOW — live media opens MEDIA.
        assertEquals(
            DashboardLayoutMode.HOME,
            DashboardController.LayoutPolicy.mode(
                nowPlayingEnabled = true, wf1Enabled = false, mediaActive = false,
            ),
        )
        assertEquals(
            DashboardLayoutMode.MEDIA,
            DashboardController.LayoutPolicy.mode(
                nowPlayingEnabled = true, wf1Enabled = false, mediaActive = true,
            ),
        )
        assertEquals(
            DashboardLayoutMode.WORKFLOW,
            DashboardController.LayoutPolicy.mode(
                nowPlayingEnabled = true, wf1Enabled = true, mediaActive = false,
            ),
        )
        assertEquals(
            DashboardLayoutMode.WORKFLOW_MEDIA,
            DashboardController.LayoutPolicy.mode(
                nowPlayingEnabled = true, wf1Enabled = true, mediaActive = true,
            ),
        )
        assertEquals(
            DashboardLayoutMode.HOME,
            DashboardController.LayoutPolicy.mode(
                nowPlayingEnabled = false, wf1Enabled = false, mediaActive = true,
            ),
        )
    }

    @Test
    fun wf1ConfigStore_roundTripWithoutTokenLeakInPublicConfig() {
        val store = Wf1ConfigStore(context, SecretStore(context))
        store.setBaseUrl("http://192.168.0.10:8792/")
        store.setToken("secret-token-value")
        assertTrue(store.isConfigured())
        assertEquals("http://192.168.0.10:8792", store.baseUrl())
        val pub = store.publicConfig()
        assertEquals(true, pub["configured"])
        assertEquals(true, pub["tokenSet"])
        assertFalse(pub.containsKey("token"))
        assertNull(pub["token"])
    }

    @Test
    fun appearanceCustomFileId_rejectsTraversal() {
        assertNull(AppearanceStore.customFileId("custom:../etc/passwd"))
        assertNull(AppearanceStore.customFileId("custom:foo/bar"))
        assertNull(AppearanceStore.customFileId("custom:foo\\bar"))
        assertEquals("abc-123_X", AppearanceStore.customFileId("custom:abc-123_X"))
        assertNull(AppearanceStore.customFileId("bundled:baghdad"))
    }

    @Test
    fun adminAuth_constantTimeEqualsAndNewTokenFormat() {
        assertTrue(AdminAuth.constantTimeEquals("ABCD-EFGH", "ABCD-EFGH"))
        assertFalse(AdminAuth.constantTimeEquals("ABCD-EFGH", "ABCD-EFGX"))
        val auth = AdminAuth(context)
        val token = auth.regenerateToken("test")
        assertTrue(token.matches(Regex("^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$")))
        assertTrue(auth.validateBearer("Bearer $token"))
        assertFalse(auth.validateBearer("Bearer wrong-token-value-xx"))
        val session = auth.loginWithToken(token)
        assertTrue(auth.validateSession(session))
    }

    @Test
    fun pendingSecretGate_supportsWf1TokenKind() {
        val gate = PendingSecretGate()
        val submitted = gate.submit(
            PendingSecretGate.Pending.Kind.WF1_PROXY_TOKEN,
            " proxy-token ",
            meta = mapOf("baseUrl" to "http://192.168.0.10:8792"),
        )
        assertEquals("", submitted.secret)
        val confirmed = gate.confirm(submitted.id)!!
        assertEquals("proxy-token", confirmed.secret)
        assertEquals("http://192.168.0.10:8792", confirmed.meta["baseUrl"])
    }

    @Test
    fun smartTubeEggUrl_isOfficialRickroll() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            SmartTubeIntegrationStatus.EASTER_EGG_VIDEO_URL,
        )
    }

    @Test
    fun placeholderTokenDetection() {
        assertTrue(Wf1ConfigStore.isPlaceholderToken("change-me-home-monitor-token"))
        assertTrue(Wf1ConfigStore.isPlaceholderToken(""))
        assertFalse(Wf1ConfigStore.isPlaceholderToken("real-token-xyz"))
    }
}

/** Test helper alias — production API is markSmartTubeEasterEggConsumed. */
private fun DashboardPrefs.markSmartTubeSettingsEggDoneCompat() {
    markSmartTubeEasterEggConsumed()
}
