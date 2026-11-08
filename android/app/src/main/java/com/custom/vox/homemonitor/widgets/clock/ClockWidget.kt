package com.custom.vox.homemonitor.widgets.clock

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.custom.vox.homemonitor.R
import com.custom.vox.homemonitor.widgets.DashboardWidget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local 24-hour clock / date. Updates once per second while started; no network.
 */
class ClockWidget(
    private val context: Context,
) : DashboardWidget {

    override val id: String = "clock"

    private var clockView: TextView? = null
    private var dateView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())

    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            clockView?.text = timeFormat.format(now)
            dateView?.text = dateFormat.format(now)
            val delay = 1000L - (System.currentTimeMillis() % 1000L)
            handler.postDelayed(this, delay)
        }
    }

    /**
     * Binds to existing views in the dashboard left column rather than inflating a subtree.
     */
    fun bind(clock: TextView, date: TextView) {
        clockView = clock
        dateView = date
    }

    override fun attach(parent: ViewGroup): View {
        // Clock lives in activity_dashboard left slot; attach is a no-op for Phase 1.
        return parent
    }

    override fun onStart() {
        handler.removeCallbacks(tick)
        tick.run()
    }

    override fun onStop() {
        handler.removeCallbacks(tick)
    }
}
