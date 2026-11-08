package com.custom.vox.homemonitor.widgets.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.custom.vox.homemonitor.R
import com.custom.vox.homemonitor.data.network.NetworkSnapshot
import com.custom.vox.homemonitor.data.network.NetworkStatusReader
import com.custom.vox.homemonitor.widgets.DashboardWidget

/**
 * Top-right status strip — same visual family as the clock, not a glass card.
 */
class NetworkStripWidget(
    private val context: Context,
) : DashboardWidget {

    override val id: String = "network-strip"

    private var statusView: TextView? = null
    private var detailView: TextView? = null
    private var routerView: TextView? = null
    private val reader = NetworkStatusReader(context)
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            bind(reader.read())
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    fun bindViews(status: TextView, detail: TextView, router: TextView) {
        statusView = status
        detailView = detail
        routerView = router
    }

    override fun attach(parent: ViewGroup): View = parent

    override fun onStart() {
        handler.removeCallbacks(tick)
        tick.run()
    }

    override fun onStop() {
        handler.removeCallbacks(tick)
    }

    private fun bind(snap: NetworkSnapshot) {
        val status = statusView ?: return
        val detail = detailView ?: return
        val router = routerView ?: return

        when {
            snap.validatedInternet -> {
                status.setText(R.string.network_online)
                status.setTextColor(ContextCompat.getColor(context, R.color.hm_status_up))
            }
            snap.online -> {
                status.setText(R.string.network_local)
                status.setTextColor(ContextCompat.getColor(context, R.color.hm_connection_stale))
            }
            else -> {
                status.setText(R.string.network_offline)
                status.setTextColor(ContextCompat.getColor(context, R.color.hm_accent_red))
            }
        }

        val transport = when (snap.transportLabel) {
            "wifi" -> context.getString(R.string.network_wifi)
            "ethernet" -> context.getString(R.string.network_ethernet)
            else -> "—"
        }
        val ip = snap.ipv4 ?: context.getString(R.string.network_ip_unknown)
        detail.text = context.getString(R.string.network_detail_fmt, transport, ip)

        val gw = snap.gateway?.takeIf { it.isNotBlank() && it != "0.0.0.0" }
        if (gw == null) {
            router.visibility = View.GONE
        } else {
            router.visibility = View.VISIBLE
            router.text = context.getString(R.string.network_router_fmt, gw)
        }
    }

    companion object {
        private const val REFRESH_MS = 15_000L
    }
}
