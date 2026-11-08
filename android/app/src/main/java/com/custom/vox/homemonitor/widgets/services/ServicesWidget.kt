package com.custom.vox.homemonitor.widgets.services

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.custom.vox.homemonitor.R
import com.custom.vox.homemonitor.app
import com.custom.vox.homemonitor.data.services.ServiceMonitor
import com.custom.vox.homemonitor.data.services.ServiceProvider
import com.custom.vox.homemonitor.data.services.ServiceStatus
import com.custom.vox.homemonitor.data.services.ServicesSnapshot
import com.custom.vox.homemonitor.data.services.UptimeRepository
import com.custom.vox.homemonitor.widgets.DashboardWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ServicesWidget(
    private val context: Context,
) : DashboardWidget {

    override val id: String = "services"

    private var expanded: View? = null
    private var compact: View? = null
    private var messageView: TextView? = null
    private var row: LinearLayout? = null
    private var hScroll: ViewGroup? = null
    private var vScroll: ViewGroup? = null
    private var compactDots: LinearLayout? = null
    private var compactMode = false
    /** HOME left rail: keep monitors in one horizontal row with tighter card widths. */
    private var railNarrow = false
    private var lastSnap: ServicesSnapshot? = null
    private var scope: CoroutineScope? = null
    private var job: Job? = null
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun attach(parent: ViewGroup): View {
        val view = LayoutInflater.from(context).inflate(R.layout.widget_services, parent, false)
        expanded = view.findViewById(R.id.servicesExpanded)
        compact = view.findViewById(R.id.servicesCompact)
        messageView = view.findViewById(R.id.servicesMessage)
        row = view.findViewById(R.id.servicesRow)
        hScroll = view.findViewById(R.id.servicesHScroll)
        vScroll = view.findViewById(R.id.servicesVScroll)
        compactDots = view.findViewById(R.id.servicesCompactDots)
        parent.addView(view)
        applyCompactVisibility()
        applyRailHost()
        return view
    }

    fun setCompact(compact: Boolean) {
        val changed = compactMode != compact
        compactMode = compact
        applyCompactVisibility()
        if (changed) lastSnap?.let { bind(it) }
    }

    fun setRailNarrow(narrow: Boolean) {
        if (railNarrow == narrow) return
        railNarrow = narrow
        applyRailHost()
        lastSnap?.let { bind(it) }
    }

    private fun applyRailHost() {
        val row = row ?: return
        val hScroll = hScroll ?: return
        val vScroll = vScroll ?: return
        // Always horizontal — HOME rail just uses narrower side-by-side cards.
        if (row.parent !== hScroll) {
            (row.parent as? ViewGroup)?.removeView(row)
            hScroll.addView(
                row,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        vScroll.visibility = View.GONE
        hScroll.visibility = View.VISIBLE
        (hScroll as? android.widget.HorizontalScrollView)?.isFillViewport = railNarrow
    }

    override fun onStart() {
        if (scope == null) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        job?.cancel()
        job = scope?.launch {
            while (isActive) {
                if (!context.app().dashboardPrefs.servicesEnabled()) {
                    bind(
                        ServicesSnapshot(
                            monitors = emptyList(),
                            refreshedAtMs = null,
                            configured = false,
                            error = null,
                        ),
                    )
                    delay(UptimeRepository.REFRESH_MS)
                    continue
                }
                val snap = withContext(Dispatchers.IO) {
                    context.app().serviceMonitorHub.refresh()
                }
                bind(snap)
                // Wake early when Settings saves a new monitor selection.
                val hub = context.app().serviceMonitorHub
                var waited = 0L
                while (isActive && waited < UptimeRepository.REFRESH_MS) {
                    delay(1_000L)
                    waited += 1_000L
                    if (hub.consumeRefreshRequest()) break
                }
            }
        }
    }

    override fun onStop() {
        job?.cancel()
        job = null
        scope?.cancel()
        scope = null
    }

    private fun applyCompactVisibility() {
        expanded?.visibility = if (compactMode) View.GONE else View.VISIBLE
        compact?.visibility = if (compactMode) View.VISIBLE else View.GONE
    }

    private fun bind(snap: ServicesSnapshot) {
        lastSnap = snap
        val row = this.row ?: return
        row.removeAllViews()
        compactDots?.removeAllViews()

        if (!snap.configured || snap.monitors.isEmpty()) {
            messageView?.visibility = View.VISIBLE
            messageView?.setText(
                if (!context.app().serviceMonitorHub.hasAnyConfiguration()) {
                    R.string.services_setup_hint
                } else {
                    R.string.services_none_selected
                },
            )
            paintCompactEmpty()
            return
        }

        if (snap.error != null) {
            messageView?.visibility = View.VISIBLE
            val refreshed = snap.refreshedAtMs?.let { timeFmt.format(Date(it)) }
            messageView?.text = if (refreshed != null) {
                context.getString(R.string.services_stale, refreshed)
            } else {
                context.getString(R.string.services_error)
            }
        } else {
            messageView?.visibility = View.GONE
        }

        val density = context.resources.displayMetrics.density
        applyRailHost()
        row.orientation = LinearLayout.HORIZONTAL
        val monitors = snap.monitors.take(MAX_VISIBLE)
        val gap = (8 * density).toInt()
        row.layoutParams = row.layoutParams.apply {
            // fillViewport + MATCH_PARENT lets weighted cards share the rail width.
            width = if (railNarrow) ViewGroup.LayoutParams.MATCH_PARENT
            else ViewGroup.LayoutParams.WRAP_CONTENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        val wideCard = (140 * density).toInt()
        monitors.forEachIndexed { index, monitor ->
            val card = LayoutInflater.from(context)
                .inflate(R.layout.item_service_monitor, row, false)
            val lp = if (railNarrow) {
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            } else {
                LinearLayout.LayoutParams(wideCard, LinearLayout.LayoutParams.MATCH_PARENT)
            }
            if (index > 0) lp.marginStart = gap
            card.layoutParams = lp
            bindCard(card, monitor, snap)
            row.addView(card)
            addCompactDot(index, monitor, snap, density)
        }
    }

    private fun addCompactDot(
        index: Int,
        monitor: ServiceMonitor,
        snap: ServicesSnapshot,
        density: Float,
    ) {
        val dot = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (10 * density).toInt(),
                (10 * density).toInt(),
            ).apply {
                if (index > 0) marginStart = (8 * density).toInt()
            }
        }
        paintDot(dot, effectiveStatus(monitor, snap))
        compactDots?.addView(dot)
    }

    private fun paintCompactEmpty() {
        val density = context.resources.displayMetrics.density
        val dot = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (10 * density).toInt(),
                (10 * density).toInt(),
            )
        }
        paintDot(dot, ServiceStatus.UNKNOWN)
        compactDots?.addView(dot)
    }

    private fun effectiveStatus(monitor: ServiceMonitor, snap: ServicesSnapshot): ServiceStatus {
        var effective = monitor.status
        val age = snap.refreshedAtMs?.let { System.currentTimeMillis() - it } ?: Long.MAX_VALUE
        if (effective == ServiceStatus.UP && age > UptimeRepository.STALE_AFTER_MS) {
            effective = ServiceStatus.STALE
        }
        return effective
    }

    private fun paintDot(dot: View?, status: ServiceStatus) {
        if (dot == null) return
        val colorRes = when (status) {
            ServiceStatus.UP -> R.color.hm_status_up
            ServiceStatus.DOWN -> R.color.hm_accent_red
            ServiceStatus.PAUSED -> R.color.hm_status_paused
            ServiceStatus.STALE, ServiceStatus.CHECKING -> R.color.hm_connection_stale
            else -> R.color.hm_status_unknown
        }
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(ContextCompat.getColor(context, colorRes))
        dot.background = g
    }

    private fun bindCard(root: View, monitor: ServiceMonitor, snap: ServicesSnapshot) {
        val name = root.findViewById<TextView>(R.id.serviceName)
        val status = root.findViewById<TextView>(R.id.serviceStatus)
        val uptime = root.findViewById<TextView>(R.id.serviceUptime)
        val dot = root.findViewById<View>(R.id.serviceDot)
        val card = root.findViewById<View>(R.id.serviceCard) ?: root

        val effective = effectiveStatus(monitor, snap)
        name.text = monitor.name
        val (labelRes, colorRes, down) = when (effective) {
            ServiceStatus.UP -> Triple(R.string.services_up, R.color.hm_status_up, false)
            ServiceStatus.DOWN -> Triple(R.string.services_down, R.color.hm_accent_red, true)
            ServiceStatus.PAUSED -> Triple(R.string.services_paused, R.color.hm_status_paused, false)
            ServiceStatus.STALE -> Triple(R.string.services_stale_label, R.color.hm_connection_stale, false)
            ServiceStatus.CHECKING -> Triple(R.string.services_checking, R.color.hm_connection_stale, false)
            else -> Triple(R.string.services_unknown, R.color.hm_status_unknown, false)
        }
        status.setText(labelRes)
        val color = ContextCompat.getColor(context, colorRes)
        status.setTextColor(color)
        paintDot(dot, effective)
        card.setBackgroundResource(
            if (down) R.drawable.bg_service_down else R.drawable.bg_task_card,
        )

        when {
            monitor.provider == ServiceProvider.UPTIMEROBOT && monitor.uptimeRatio30d != null -> {
                uptime.visibility = View.VISIBLE
                uptime.text = String.format(Locale.US, "%.2f%% · 30d", monitor.uptimeRatio30d)
            }
            monitor.responseTimeMs != null -> {
                uptime.visibility = View.VISIBLE
                uptime.text = context.getString(R.string.services_response_ms, monitor.responseTimeMs)
            }
            else -> uptime.visibility = View.GONE
        }
    }

    companion object {
        private const val MAX_VISIBLE = 8
    }
}
