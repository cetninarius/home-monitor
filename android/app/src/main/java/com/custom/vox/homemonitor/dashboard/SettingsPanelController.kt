package com.custom.vox.homemonitor.dashboard

import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.custom.vox.homemonitor.BuildConfig
import com.custom.vox.homemonitor.R
import com.custom.vox.homemonitor.admin.LocalAdminServer
import com.custom.vox.homemonitor.app
import com.custom.vox.homemonitor.data.appearance.AppearanceStore
import com.custom.vox.homemonitor.data.appearance.BackgroundImageStore
import com.custom.vox.homemonitor.data.appearance.BackgroundOption
import com.custom.vox.homemonitor.data.appearance.CardStyle
import com.custom.vox.homemonitor.data.secure.PendingSecretGate
import com.custom.vox.homemonitor.data.services.UptimeConfigStore
import com.custom.vox.homemonitor.data.services.UptimeMonitorInfo
import com.custom.vox.homemonitor.diagnostics.CrashReporting
import com.custom.vox.homemonitor.media.SmartTubeState
import kotlin.concurrent.thread

/**
 * Settings hub sheet (Dashboard / Appearance / Integrations / About).
 * Explicit D-pad grid on every page — no geometric focus skipping.
 */
class SettingsPanelController(
    private val root: ViewGroup,
    private val onDashboardPrefsChanged: () -> Unit = {},
) {
    private val store: AppearanceStore = root.context.app().appearanceStore
    private val images: BackgroundImageStore = root.context.app().backgroundImageStore
    private val prefs = root.context.app().dashboardPrefs
    private val pending = root.context.app().pendingSecretGate
    private val uptimeConfig = root.context.app().uptimeConfig
    private val uptimeRepo = root.context.app().uptimeRepository
    private val httpStore = root.context.app().httpEndpointStore
    private val smartTube = root.context.app().smartTubeStatus
    private val adminAuth = root.context.app().adminAuth
    private val reclaimer = root.context.app().dashboardReclaimer

    private var panel: ViewGroup? = null
    private var sheet: ViewGroup? = null
    private var pageHub: View? = null
    private var pageDashboard: View? = null
    private var pageAppearance: View? = null
    private var pageIntegrations: View? = null
    private var pageAbout: View? = null
    private var titleView: TextView? = null
    private var backBtn: View? = null
    private var doneBtn: View? = null
    private var pendingBanner: View? = null
    private var pendingApprove: View? = null
    private var pendingReject: View? = null
    private var toggleServices: TextView? = null
    private var toggleNp: TextView? = null
    private var toggleWf1: TextView? = null
    private var toggleWf1Integrations: TextView? = null
    private var wf1Status: TextView? = null
    private var uptimeStatus: TextView? = null
    private var httpStatus: TextView? = null
    private var smartTubeStatus: TextView? = null
    private var integrationsSummary: TextView? = null
    private var integrationsHint: TextView? = null
    private var adminUrlBtn: TextView? = null
    private var adminShowTokenBtn: TextView? = null
    private var adminRegenTokenBtn: TextView? = null
    private var adminHint: TextView? = null
    private var urLoadBtn: TextView? = null
    private var urSaveBtn: TextView? = null
    private var urDisconnectBtn: TextView? = null
    private var urMonitorList: LinearLayout? = null
    private var aboutBody: TextView? = null
    private var toggleDiagnostics: TextView? = null
    private var openNls: View? = null
    private var openSmartTube: View? = null
    private var bgRow: LinearLayout? = null
    private var dimRow: LinearLayout? = null
    private var styleRow: LinearLayout? = null
    private var restoreBtn: View? = null
    private var dashboardContent: ViewGroup? = null
    private var open = false
    private var appearanceBuilt = false
    private var page = Page.HUB
    private var pendingId: String? = null

    private val bgChips = mutableListOf<View>()
    private val dimChips = mutableListOf<View>()
    private val styleChips = mutableListOf<View>()
    private val hubNav = mutableListOf<View>()
    private val urMonitorRows = mutableListOf<View>()
    private val draftSelectedIds = linkedSetOf<String>()
    private var listedMonitors: List<UptimeMonitorInfo> = emptyList()

    private enum class Page { HUB, DASHBOARD, APPEARANCE, INTEGRATIONS, ABOUT }

    fun attach() {
        if (panel != null) return
        dashboardContent = root.findViewById(R.id.dashboardContent)
        val view = LayoutInflater.from(root.context)
            .inflate(R.layout.view_settings_panel, root, false) as ViewGroup
        root.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        panel = view
        sheet = view.findViewById(R.id.settingsSheet)
        pageHub = view.findViewById(R.id.settingsPageHub)
        pageDashboard = view.findViewById(R.id.settingsPageDashboard)
        pageAppearance = view.findViewById(R.id.settingsPageAppearance)
        pageIntegrations = view.findViewById(R.id.settingsPageIntegrations)
        pageAbout = view.findViewById(R.id.settingsPageAbout)
        titleView = view.findViewById(R.id.settingsTitle)
        backBtn = view.findViewById(R.id.settingsBack)
        doneBtn = view.findViewById(R.id.settingsDone)
        pendingBanner = view.findViewById(R.id.settingsPendingBanner)
        pendingApprove = view.findViewById(R.id.settingsPendingApprove)
        pendingReject = view.findViewById(R.id.settingsPendingReject)
        toggleServices = view.findViewById(R.id.settingsToggleServices)
        toggleNp = view.findViewById(R.id.settingsToggleNowPlaying)
        toggleWf1 = view.findViewById(R.id.settingsToggleWf1)
        toggleWf1Integrations = view.findViewById(R.id.settingsToggleWf1Integrations)
        wf1Status = view.findViewById(R.id.settingsWf1Status)
        uptimeStatus = view.findViewById(R.id.settingsUptimeStatus)
        httpStatus = view.findViewById(R.id.settingsHttpStatus)
        smartTubeStatus = view.findViewById(R.id.settingsSmartTubeStatus)
        integrationsSummary = view.findViewById(R.id.settingsIntegrationsSummary)
        integrationsHint = view.findViewById(R.id.settingsIntegrationsHint)
        adminUrlBtn = view.findViewById(R.id.settingsAdminUrl)
        adminShowTokenBtn = view.findViewById(R.id.settingsAdminShowToken)
        adminRegenTokenBtn = view.findViewById(R.id.settingsAdminRegenToken)
        adminHint = view.findViewById(R.id.settingsAdminHint)
        urLoadBtn = view.findViewById(R.id.settingsUrLoadMonitors)
        urSaveBtn = view.findViewById(R.id.settingsUrSaveSelection)
        urDisconnectBtn = view.findViewById(R.id.settingsUrDisconnect)
        urMonitorList = view.findViewById(R.id.settingsUrMonitorList)
        aboutBody = view.findViewById(R.id.settingsAboutBody)
        toggleDiagnostics = view.findViewById(R.id.settingsToggleDiagnostics)
        openNls = view.findViewById(R.id.settingsOpenNls)
        openSmartTube = view.findViewById(R.id.settingsOpenSmartTube)
        bgRow = view.findViewById(R.id.appearanceBgRow)
        dimRow = view.findViewById(R.id.appearanceDimRow)
        styleRow = view.findViewById(R.id.appearanceStyleRow)
        restoreBtn = view.findViewById(R.id.appearanceRestore)

        // ScrollViews must not steal D-pad from chips / actions.
        listOf(pageAppearance, pageIntegrations).forEach { pageView ->
            (pageView as? ScrollView)?.apply {
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            }
        }

        hubNav.clear()
        listOf(
            R.id.settingsNavDashboard to Page.DASHBOARD,
            R.id.settingsNavAppearance to Page.APPEARANCE,
            R.id.settingsNavIntegrations to Page.INTEGRATIONS,
            R.id.settingsNavAbout to Page.ABOUT,
        ).forEach { (id, target) ->
            val v = view.findViewById<View>(id)
            ensureId(v)
            hubNav.add(v)
            bindActivate(v) { showPage(target) }
        }

        ensureId(backBtn)
        ensureId(doneBtn)
        ensureId(toggleServices)
        ensureId(toggleNp)
        listOf(
            integrationsSummary, adminUrlBtn, adminShowTokenBtn, adminRegenTokenBtn,
            urLoadBtn, urSaveBtn, urDisconnectBtn, openNls, openSmartTube,
            restoreBtn, pendingApprove, pendingReject, backBtn, doneBtn,
            toggleServices, toggleNp, toggleWf1, toggleWf1Integrations,
        ).forEach { ensureId(it) }

        bindActivate(doneBtn) {
            persistUrDraftAndRefresh()
            close()
        }
        bindActivate(backBtn) {
            if (page == Page.INTEGRATIONS) persistUrDraftAndRefresh()
            showPage(Page.HUB)
        }
        bindActivate(restoreBtn) {
            store.restoreDefaults()
            refreshAppearanceSelection()
        }
        bindActivate(toggleServices) {
            prefs.setServicesEnabled(!prefs.servicesEnabled())
            refreshDashboardToggles()
            onDashboardPrefsChanged()
        }
        bindActivate(toggleNp) {
            prefs.setNowPlayingEnabled(!prefs.nowPlayingEnabled())
            refreshDashboardToggles()
            onDashboardPrefsChanged()
        }
        val toggleWf1Action = {
            prefs.setWf1Enabled(!prefs.wf1Enabled())
            refreshDashboardToggles()
            refreshIntegrations()
            onDashboardPrefsChanged()
        }
        bindActivate(toggleWf1, toggleWf1Action)
        bindActivate(toggleWf1Integrations, toggleWf1Action)
        bindActivate(pendingApprove) { approvePending() }
        bindActivate(pendingReject) { rejectPending() }
        bindActivate(integrationsSummary) { refreshIntegrations() }
        bindActivate(adminUrlBtn) { refreshIntegrations() }
        bindActivate(adminShowTokenBtn) {
            adminAuth.setTokenVisibleOnTv(!adminAuth.isTokenVisibleOnTv())
            refreshIntegrations()
        }
        bindActivate(adminRegenTokenBtn) {
            adminAuth.regenerateToken("settings")
            adminAuth.setTokenVisibleOnTv(true)
            integrationsHint?.text = root.context.getString(R.string.settings_token_stable)
            refreshIntegrations()
        }
        bindActivate(urLoadBtn) { loadUrMonitors() }
        bindActivate(urSaveBtn) { saveUrSelection() }
        bindActivate(urDisconnectBtn) {
            uptimeConfig.disconnect()
            pending.cancel()
            listedMonitors = emptyList()
            draftSelectedIds.clear()
            renderUrMonitorRows()
            refreshIntegrations()
            integrationsHint?.text = "UptimeRobot disconnected."
        }
        bindActivate(openNls) {
            // Pause overlay only — keep HM session. BACK from Apps restores the dashboard.
            close()
            reclaimer.pauseOverlayForSettings("open-nls-settings")
            val msg = smartTube.openNotificationAccessSettings()
            android.util.Log.i("SettingsPanel", "NLS: $msg")
        }
        bindActivate(openSmartTube) {
            val egg = !prefs.smartTubeEasterEggConsumed()
            if (egg) {
                // Consume before launch so retries never re-fire.
                prefs.markSmartTubeEasterEggConsumed()
                reclaimer.markUserOpenedSmartTube()
            }
            val err = smartTube.openSmartTube(playEggVideo = egg)
            integrationsHint?.text = when {
                err != null -> err
                egg -> "Opened once-only surprise. Later launches open SmartTube normally."
                else ->
                    "SmartTube warmed underneath. Play from the phone — Now Playing stays on this dashboard."
            }
            if (err != null) {
                android.util.Log.w("SettingsPanel", "SmartTube open failed: $err")
            }
        }

        view.isClickable = true
        view.isFocusable = false
        view.setOnClickListener { /* absorb veil */ }
        sheet?.apply {
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        view.visibility = View.GONE
        aboutBody?.text = "Home Monitor ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
            "BF3 beta — optional WF1, hardened LAN admin\n" +
            "Admin is cleartext HTTP on your LAN — secrets require TV confirmation."
        bindActivate(toggleDiagnostics) {
            val diag = root.context.app().diagnosticsPrefs
            diag.setEnabled(!diag.isEnabled())
            CrashReporting.syncWithPrefs(root.context)
            refreshAbout()
        }
        refreshAbout()
    }

    private fun refreshAbout() {
        val enabled = root.context.app().diagnosticsPrefs.isEnabled()
        toggleDiagnostics?.text = root.context.getString(
            if (enabled) R.string.settings_diagnostics_on else R.string.settings_diagnostics_off,
        )
    }

    fun isOpen(): Boolean = open

    fun open() {
        attach()
        showPage(Page.HUB)
        refreshPendingBanner()
        panel?.visibility = View.VISIBLE
        open = true
        setDashboardFocusBlocked(true)
        focusFirstOnPage()
    }

    fun close() {
        if (!open) return
        open = false
        panel?.visibility = View.GONE
        setDashboardFocusBlocked(false)
        root.findViewById<View>(R.id.appearanceEntry)?.requestFocus()
    }

    fun toggle() {
        if (open) close() else open()
    }

    /** BACK: subpage → hub, hub → close. */
    fun handleBack(): Boolean {
        if (!open) return false
        if (page != Page.HUB) {
            if (page == Page.INTEGRATIONS) persistUrDraftAndRefresh()
            showPage(Page.HUB)
            return true
        }
        persistUrDraftAndRefresh()
        close()
        return true
    }

    /** Poll while open so LAN-submitted keys surface promptly. */
    fun tick() {
        if (!open) return
        val wasPending = pendingBanner?.visibility == View.VISIBLE
        refreshPendingBanner()
        val nowPending = pendingBanner?.visibility == View.VISIBLE
        if (wasPending != nowPending) {
            wireFocusForPage()
        }
    }

    private fun showPage(target: Page) {
        page = target
        pageHub?.visibility = if (target == Page.HUB) View.VISIBLE else View.GONE
        pageDashboard?.visibility = if (target == Page.DASHBOARD) View.VISIBLE else View.GONE
        pageAppearance?.visibility = if (target == Page.APPEARANCE) View.VISIBLE else View.GONE
        pageIntegrations?.visibility = if (target == Page.INTEGRATIONS) View.VISIBLE else View.GONE
        pageAbout?.visibility = if (target == Page.ABOUT) View.VISIBLE else View.GONE
        backBtn?.visibility = if (target == Page.HUB) View.GONE else View.VISIBLE

        // Keep Gone footer out of focus search.
        backBtn?.isFocusable = target != Page.HUB
        backBtn?.isClickable = target != Page.HUB

        titleView?.setText(
            when (target) {
                Page.HUB -> R.string.settings_title
                Page.DASHBOARD -> R.string.settings_dashboard
                Page.APPEARANCE -> R.string.appearance_title
                Page.INTEGRATIONS -> R.string.settings_integrations
                Page.ABOUT -> R.string.settings_about
            },
        )
        when (target) {
            Page.DASHBOARD -> refreshDashboardToggles()
            Page.APPEARANCE -> {
                ensureAppearanceBuilt()
                refreshAppearanceSelection()
            }
            Page.INTEGRATIONS -> refreshIntegrations()
            Page.ABOUT -> refreshAbout()
            else -> Unit
        }
        wireFocusForPage()
        focusFirstOnPage()
    }

    private fun focusFirstOnPage() {
        sheet?.post {
            val target = when (page) {
                Page.HUB -> hubNav.firstOrNull()
                Page.DASHBOARD -> toggleServices
                Page.APPEARANCE -> bgChips.firstOrNull { it.isSelected } ?: bgChips.firstOrNull()
                Page.INTEGRATIONS -> integrationsSummary
                Page.ABOUT -> backBtn?.takeIf { it.visibility == View.VISIBLE } ?: doneBtn
            }
            val pendingFirst = pendingApprove?.takeIf {
                pendingBanner?.visibility == View.VISIBLE
            }
            (pendingFirst ?: target ?: doneBtn)?.requestFocus()
        }
    }

    private fun refreshDashboardToggles() {
        toggleServices?.text = toggleLabel(
            root.context.getString(R.string.settings_services_toggle),
            prefs.servicesEnabled(),
        )
        toggleNp?.text = toggleLabel(
            root.context.getString(R.string.settings_np_toggle),
            prefs.nowPlayingEnabled(),
        )
        val wf1Label = toggleLabel(
            root.context.getString(R.string.settings_wf1_toggle),
            prefs.wf1Enabled(),
        )
        toggleWf1?.text = wf1Label
        toggleWf1Integrations?.text = wf1Label
    }

    private fun toggleLabel(name: String, on: Boolean): String =
        "$name — ${if (on) "On" else "Off"}"

    private fun adminBaseUrl(): String {
        val ip = LocalAdminServer.guessLanIpv4() ?: "192.168.x.x"
        return "http://$ip:${BuildConfig.ADMIN_PORT}"
    }

    private fun refreshIntegrations() {
        val cfg = uptimeConfig.publicConfig()
        val ids = uptimeConfig.selectedMonitorIds()
        if (draftSelectedIds.isEmpty() && ids.isNotEmpty()) {
            draftSelectedIds.addAll(ids)
        }
        val urLine = when {
            cfg["apiKeySet"] == true && ids.isNotEmpty() ->
                "Connected · showing ${ids.size} monitor(s) on the dashboard"
            cfg["apiKeySet"] == true ->
                "API key on device · load monitors and select which to show"
            else ->
                "Not connected — set key in web admin, then Approve on TV"
        }
        val eps = httpStore.list()
        val httpLine = if (eps.isEmpty()) {
            "No Direct HTTP checks yet. Add them in web admin (max 5)."
        } else {
            eps.joinToString("\n") { ep ->
                val on = if (ep.enabled) "on" else "off"
                "• ${ep.name} ($on)\n  ${ep.url}"
            }
        }
        val st = smartTube.snapshot()
        val wf1 = root.context.app().wf1Config
        val wf1Line = when {
            !prefs.wf1Enabled() -> "Disabled — enable to show the experimental queue widget"
            !wf1.isConfigured() -> "Enabled · no engine configured (set URL + token in web admin)"
            else -> "Enabled · proxy ${wf1.baseUrl()}"
        }
        val url = adminBaseUrl()
        integrationsSummary?.text =
            "Web admin: $url\nUptimeRobot: ${if (cfg["apiKeySet"] == true) "key set" else "no key"} · ${ids.size} selected\n" +
                "HTTP checks: ${eps.size}\nWF1: ${if (prefs.wf1Enabled()) "on" else "off"}\n" +
                "SmartTube: ${st.state.name.replace('_', ' ')}"

        adminUrlBtn?.text = "Admin: $url  (port ${BuildConfig.ADMIN_PORT})"
        val token = adminAuth.getOrCreateToken()
        adminShowTokenBtn?.text = if (adminAuth.isTokenVisibleOnTv()) {
            "Token: $token  (OK to hide)"
        } else {
            "Show admin token (needed to sign in from phone)"
        }
        adminHint?.text = root.context.getString(R.string.settings_token_stable) + "\n" +
            root.context.getString(R.string.settings_port_hint, BuildConfig.ADMIN_PORT) + "\n" +
            "LAN admin uses cleartext HTTP — treat your home network as the trust boundary."

        uptimeStatus?.text = urLine + "\n" + root.context.getString(R.string.settings_ur_key_hint)
        httpStatus?.text = httpLine + "\n" + root.context.getString(R.string.settings_http_hint)
        wf1Status?.text = wf1Line + "\n" + root.context.getString(R.string.settings_wf1_hint)
        smartTubeStatus?.text = "${st.detail}\nInstalled=${st.installed} · NLS=${st.notificationAccess}"
        integrationsHint?.text = when (st.state) {
            SmartTubeState.NOT_INSTALLED ->
                "Install SmartTube (org.smarttube.stable), then Open SmartTube here."
            SmartTubeState.PERMISSION_REQUIRED ->
                root.context.getString(R.string.media_diag_grant_hint)
            else ->
                "Background playback: SmartTube → Settings → General → Only audio (HOME/BACK)."
        }
        if (listedMonitors.isEmpty() && ids.isNotEmpty()) {
            // Show currently selected IDs as rows until a full list is loaded.
            listedMonitors = ids.map { id ->
                UptimeMonitorInfo(id = id, name = uptimeConfig.labelFor(id), status = com.custom.vox.homemonitor.data.services.ServiceStatus.UNKNOWN)
            }
            renderUrMonitorRows()
        }
    }

    private fun loadUrMonitors() {
        if (uptimeConfig.apiKey().isNullOrBlank()) {
            integrationsHint?.text = root.context.getString(R.string.settings_ur_key_hint)
            return
        }
        urLoadBtn?.text = "Loading monitors…"
        thread {
            val result = uptimeRepo.listMonitors()
            root.post {
                urLoadBtn?.setText(R.string.settings_ur_load_monitors)
                result.fold(
                    onSuccess = { list ->
                        listedMonitors = list
                        if (draftSelectedIds.isEmpty()) {
                            draftSelectedIds.addAll(uptimeConfig.selectedMonitorIds())
                        }
                        renderUrMonitorRows()
                        wireFocusForPage()
                        integrationsHint?.text = "Loaded ${list.size} monitors. OK to toggle, then Save selection."
                    },
                    onFailure = { e ->
                        integrationsHint?.text = "Could not list monitors: ${e.message}"
                    },
                )
            }
        }
    }

    private fun saveUrSelection() {
        val ok = persistUrDraftAndRefresh(forceMessage = true)
        if (!ok && uptimeConfig.apiKey().isNullOrBlank()) {
            integrationsHint?.text = root.context.getString(R.string.settings_ur_key_hint)
        } else if (!ok && draftSelectedIds.isEmpty()) {
            integrationsHint?.text = "Select 1–2 monitors, then Save (or Done)."
        }
    }

    /**
     * Draft selection used to live only in memory — Done closed Settings without
     * writing prefs, so the Services cards never changed. Persist on Save / Done / Back.
     */
    private fun persistUrDraftAndRefresh(forceMessage: Boolean = false): Boolean {
        if (uptimeConfig.apiKey().isNullOrBlank()) return false
        val selected = draftSelectedIds.toList().take(UptimeConfigStore.MAX_SELECTED)
        if (selected.isEmpty()) return false
        val labels = LinkedHashMap<String, String>()
        listedMonitors.filter { selected.contains(it.id) }.forEach { labels[it.id] = it.name }
        // Keep any existing labels for IDs not in the loaded list.
        uptimeConfig.labelsMap().forEach { (id, label) ->
            if (id in selected) labels.putIfAbsent(id, label)
        }
        val previous = uptimeConfig.selectedMonitorIds()
        uptimeConfig.saveSelection(selected, labels)
        draftSelectedIds.clear()
        draftSelectedIds.addAll(selected)
        if (forceMessage || previous != selected) {
            integrationsHint?.text = "Saved ${selected.size}/2 — updating Services cards…"
            refreshIntegrations()
        }
        forceServicesRefresh()
        return true
    }

    private fun forceServicesRefresh() {
        val hub = root.context.app().serviceMonitorHub
        // Poke first so the widget's wait-loop wakes, then refresh for fresh data.
        hub.requestRefresh()
        thread {
            try {
                hub.refresh()
            } finally {
                hub.requestRefresh()
                root.post {
                    // Second poke in case the first was consumed before refresh finished.
                    hub.requestRefresh()
                }
            }
        }
    }

    private fun renderUrMonitorRows() {
        val host = urMonitorList ?: return
        host.removeAllViews()
        urMonitorRows.clear()
        val maxHint = panel?.findViewById<TextView>(R.id.settingsUrMaxHint)
        maxHint?.text = root.context.getString(R.string.settings_ur_max_selected) +
            "  (${draftSelectedIds.size}/${UptimeConfigStore.MAX_SELECTED})"
        listedMonitors.forEachIndexed { index, m ->
            val row = TextView(root.context).apply {
                val selected = draftSelectedIds.contains(m.id)
                text = "${if (selected) "✓" else "○"}  ${m.name}  (${m.id})"
                setTextColor(ContextCompat.getColor(context, R.color.hm_text_primary))
                textSize = 15f
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                background = ContextCompat.getDrawable(context, R.drawable.bg_appearance_option)
                val density = resources.displayMetrics.density
                setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (8 * density).toInt() }
                isFocusable = true
                isClickable = true
                tag = m.id
            }
            ensureId(row)
            bindActivate(row) {
                if (draftSelectedIds.contains(m.id)) {
                    draftSelectedIds.remove(m.id)
                } else if (draftSelectedIds.size >= UptimeConfigStore.MAX_SELECTED) {
                    integrationsHint?.text = root.context.getString(R.string.settings_ur_max_selected)
                    return@bindActivate
                } else {
                    draftSelectedIds.add(m.id)
                }
                renderUrMonitorRows()
                wireFocusForPage()
                urMonitorRows.getOrNull(index)?.requestFocus()
            }
            urMonitorRows.add(row)
            host.addView(row)
        }
    }

    private fun refreshPendingBanner() {
        val pub = pending.peekPublic()
        val isPending = pub["pending"] == true
        pendingBanner?.visibility = if (isPending) View.VISIBLE else View.GONE
        pendingApprove?.isFocusable = isPending
        pendingReject?.isFocusable = isPending
        pendingId = pub["id"] as? String
    }

    private fun approvePending() {
        val id = pendingId ?: return
        val confirmed = pending.confirm(id) ?: return
        when (confirmed.kind) {
            PendingSecretGate.Pending.Kind.UPTIMEROBOT_API_KEY -> {
                uptimeConfig.setApiKey(confirmed.secret)
            }
            PendingSecretGate.Pending.Kind.WF1_PROXY_TOKEN -> {
                root.context.app().wf1Config.setToken(confirmed.secret)
                confirmed.meta["baseUrl"]?.let { root.context.app().wf1Config.setBaseUrl(it) }
                onDashboardPrefsChanged()
            }
        }
        refreshPendingBanner()
        if (page == Page.INTEGRATIONS) {
            refreshIntegrations()
            if (confirmed.kind == PendingSecretGate.Pending.Kind.UPTIMEROBOT_API_KEY) {
                loadUrMonitors()
            }
        }
        wireFocusForPage()
    }

    private fun rejectPending() {
        pending.cancel(pendingId)
        refreshPendingBanner()
        wireFocusForPage()
    }

    private fun ensureAppearanceBuilt() {
        if (appearanceBuilt) {
            rebuildBackgroundRow()
            return
        }
        rebuildBackgroundRow()
        buildDimRow()
        buildStyleRow()
        appearanceBuilt = true
    }

    private fun rebuildBackgroundRow() {
        val row = bgRow ?: return
        row.removeAllViews()
        bgChips.clear()
        images.allOptions().forEach { opt ->
            val chip = makeBgChip(opt)
            bgChips.add(chip)
            row.addView(chip)
        }
    }

    private fun buildDimRow() {
        val row = dimRow ?: return
        row.removeAllViews()
        dimChips.clear()
        listOf(
            0 to R.string.appearance_dim_0,
            20 to R.string.appearance_dim_20,
            40 to R.string.appearance_dim_40,
            60 to R.string.appearance_dim_60,
        ).forEach { (value, labelRes) ->
            val chip = makeTextChip(root.context.getString(labelRes), weight = 1f) {
                store.setDimPercent(value)
                refreshAppearanceSelection()
            }
            chip.tag = value
            dimChips.add(chip)
            row.addView(chip)
        }
    }

    private fun buildStyleRow() {
        val row = styleRow ?: return
        row.removeAllViews()
        styleChips.clear()
        listOf(
            CardStyle.GLASS to R.string.appearance_style_glass,
            CardStyle.SOLID to R.string.appearance_style_solid,
            CardStyle.MINIMAL to R.string.appearance_style_minimal,
        ).forEach { (style, labelRes) ->
            val chip = makeTextChip(root.context.getString(labelRes), weight = 1f) {
                store.setCardStyle(style)
                refreshAppearanceSelection()
            }
            chip.tag = style
            styleChips.add(chip)
            row.addView(chip)
        }
    }

    private fun refreshAppearanceSelection() {
        val state = store.snapshot()
        bgChips.forEach { it.isSelected = it.tag == state.backgroundId }
        dimChips.forEach { it.isSelected = it.tag == state.dimPercent }
        styleChips.forEach { it.isSelected = it.tag == state.cardStyle }
    }

    private fun makeBgChip(opt: BackgroundOption): View {
        val ctx = root.context
        val density = ctx.resources.displayMetrics.density
        val chip = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isClickable = true
            tag = opt.id
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_appearance_option)
            val pad = (8 * density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginEnd = (8 * density).toInt() }
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        ensureId(chip)
        val thumb = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (56 * density).toInt(),
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            isFocusable = false
            contentDescription = opt.label
        }
        val bundled = images.bundledRes(opt.id)
        if (bundled != 0) {
            thumb.setImageResource(bundled)
        } else {
            images.loadDisplayBitmap(opt.id, 320, 180)?.let { thumb.setImageBitmap(it) }
        }
        val label = TextView(ctx).apply {
            text = opt.label
            setTextColor(ContextCompat.getColor(ctx, R.color.hm_text_secondary))
            textSize = 12f
            gravity = Gravity.CENTER
            isFocusable = false
            setPadding(0, (6 * density).toInt(), 0, 0)
            maxLines = 1
        }
        chip.addView(thumb)
        chip.addView(label)
        bindActivate(chip) {
            store.setBackgroundId(opt.id)
            refreshAppearanceSelection()
        }
        return chip
    }

    private fun makeTextChip(text: String, weight: Float, onClick: () -> Unit): TextView {
        val ctx = root.context
        val density = root.resources.displayMetrics.density
        return TextView(ctx).apply {
            this.text = text
            isFocusable = true
            isClickable = true
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(ctx, R.color.hm_text_primary))
            textSize = 15f
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_appearance_option)
            layoutParams = LinearLayout.LayoutParams(0, (52 * density).toInt(), weight).apply {
                marginEnd = (8 * density).toInt()
            }
            ensureId(this)
            bindActivate(this, onClick)
        }
    }

    private fun wireFocusForPage() {
        val footer = footerViews()
        val pendingRow = pendingViews()
        linkRow(footer)
        footer.forEach { pinDown(it) }

        when (page) {
            Page.HUB -> wireVerticalPage(hubNav, pendingRow, footer)
            Page.DASHBOARD -> wireVerticalPage(
                listOfNotNull(toggleServices, toggleNp, toggleWf1),
                pendingRow,
                footer,
            )
            Page.INTEGRATIONS -> wireVerticalPage(
                listOfNotNull(
                    integrationsSummary,
                    adminUrlBtn,
                    adminShowTokenBtn,
                    adminRegenTokenBtn,
                    urLoadBtn,
                ) + urMonitorRows + listOfNotNull(
                    urSaveBtn,
                    urDisconnectBtn,
                    toggleWf1Integrations,
                    openNls,
                    openSmartTube,
                ),
                pendingRow,
                footer,
            )
            Page.APPEARANCE -> wireAppearancePage(pendingRow, footer)
            Page.ABOUT -> wireAboutPage(pendingRow, footer)
        }
    }

    /**
     * Vertical menu pages (Hub / Dashboard / Integrations):
     * item1 ↔ item2 ↔ … ↔ last → footer. Never skip middle items.
     */
    private fun wireVerticalPage(
        items: List<View>,
        pendingRow: List<View>,
        footer: List<View>,
    ) {
        linkColumn(items)
        if (pendingRow.isNotEmpty()) {
            linkRow(pendingRow)
            pendingRow.forEach { pinUp(it) }
            // Only the first content item moves UP into pending.
            bridgeEdges(pendingRow, listOf(items.first()))
        }
        if (items.isNotEmpty() && footer.isNotEmpty()) {
            // Only the last content item moves DOWN into footer.
            bridgeEdges(listOf(items.last()), footer)
        } else if (items.isEmpty() && footer.isNotEmpty()) {
            if (pendingRow.isNotEmpty()) bridgeEdges(pendingRow, footer)
            else footer.forEach { pinUp(it) }
        }
    }

    /**
     * Appearance grid: bg row → dim row → style row → restore → footer.
     * Horizontal within each chip row; vertical only between adjacent rows.
     */
    private fun wireAppearancePage(pendingRow: List<View>, footer: List<View>) {
        linkRow(bgChips)
        linkRow(dimChips)
        linkRow(styleChips)
        bridgeRows(bgChips, dimChips)
        bridgeRows(dimChips, styleChips)
        val restore = listOfNotNull(restoreBtn)
        if (restore.isNotEmpty()) {
            bridgeRows(styleChips, restore)
            bridgeEdges(restore, footer)
        } else {
            bridgeEdges(styleChips.takeLast(1), footer)
        }
        if (pendingRow.isNotEmpty()) {
            linkRow(pendingRow)
            pendingRow.forEach { pinUp(it) }
            bridgeEdges(pendingRow, bgChips.take(1))
            // Remaining bg chips keep UP pinned within the row top.
            bgChips.drop(1).forEach { pinUp(it) }
        } else {
            bgChips.forEach { pinUp(it) }
        }
    }

    private fun wireAboutPage(pendingRow: List<View>, footer: List<View>) {
        wireVerticalPage(
            listOfNotNull(toggleDiagnostics),
            pendingRow,
            footer,
        )
    }

    private fun pendingViews(): List<View> {
        if (pendingBanner?.visibility != View.VISIBLE) return emptyList()
        return listOfNotNull(pendingApprove, pendingReject)
    }

    private fun footerViews(): List<View> {
        val back = backBtn?.takeIf { it.visibility == View.VISIBLE }
        return listOfNotNull(back, doneBtn)
    }

    /**
     * Connect only the boundary between two groups:
     * - every [upper] DOWN → mapped [lower]
     * - every [lower] UP → mapped [upper]
     * Used for adjacent chip rows (same width) or single-edge column↔footer.
     */
    private fun bridgeRows(upper: List<View>, lower: List<View>) {
        if (upper.isEmpty() || lower.isEmpty()) return
        upper.forEach { ensureId(it) }
        lower.forEach { ensureId(it) }
        upper.forEachIndexed { i, chip ->
            val target = lower.getOrNull(i.coerceAtMost(lower.lastIndex)) ?: lower.last()
            chip.nextFocusDownId = target.id
        }
        lower.forEachIndexed { i, chip ->
            val target = upper.getOrNull(i.coerceAtMost(upper.lastIndex)) ?: upper.last()
            chip.nextFocusUpId = target.id
        }
    }

    /**
     * Edge-only bridge for column→footer / pending→first item.
     * Does not rewrite DOWN/UP on non-boundary siblings.
     */
    private fun bridgeEdges(upper: List<View>, lower: List<View>) {
        if (upper.isEmpty() || lower.isEmpty()) return
        upper.forEach { ensureId(it) }
        lower.forEach { ensureId(it) }
        // Upper items (usually a single last-of-column) go down into lower.
        upper.forEachIndexed { i, chip ->
            val target = lower.getOrNull(i.coerceAtMost(lower.lastIndex)) ?: lower.last()
            chip.nextFocusDownId = target.id
        }
        // Lower items (footer) go up into upper.
        lower.forEachIndexed { i, chip ->
            val target = upper.getOrNull(i.coerceAtMost(upper.lastIndex)) ?: upper.last()
            chip.nextFocusUpId = target.id
        }
    }

    private fun setDashboardFocusBlocked(blocked: Boolean) {
        val content = dashboardContent ?: return
        content.descendantFocusability = if (blocked) {
            ViewGroup.FOCUS_BLOCK_DESCENDANTS
        } else {
            ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        root.findViewById<View>(R.id.appearanceEntry)?.isFocusable = !blocked
    }

    private fun bindActivate(view: View?, onClick: () -> Unit) {
        if (view == null) return
        view.isClickable = true
        view.isFocusable = true
        view.setOnClickListener { onClick() }
        view.setOnKeyListener { v, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                v.performClick()
                return@setOnKeyListener true
            }
            false
        }
    }

    /** Vertical list: UP/DOWN between items; LEFT/RIGHT stay put. */
    private fun linkColumn(items: List<View>) {
        if (items.isEmpty()) return
        items.forEach { ensureId(it) }
        items.forEachIndexed { i, item ->
            item.nextFocusLeftId = item.id
            item.nextFocusRightId = item.id
            item.nextFocusUpId = items.getOrNull(i - 1)?.id ?: item.id
            item.nextFocusDownId = items.getOrNull(i + 1)?.id ?: item.id
        }
    }

    /** Horizontal row: LEFT/RIGHT between chips. */
    private fun linkRow(chips: List<View>) {
        if (chips.isEmpty()) return
        chips.forEach { ensureId(it) }
        chips.forEachIndexed { i, chip ->
            chip.nextFocusLeftId = chips.getOrNull(i - 1)?.id ?: chip.id
            chip.nextFocusRightId = chips.getOrNull(i + 1)?.id ?: chip.id
        }
    }

    private fun pinUp(v: View) {
        ensureId(v)
        v.nextFocusUpId = v.id
    }

    private fun pinDown(v: View) {
        ensureId(v)
        v.nextFocusDownId = v.id
    }

    private fun ensureId(view: View?) {
        if (view == null) return
        if (view.id == View.NO_ID) {
            view.id = ViewCompat.generateViewId()
        }
    }
}
