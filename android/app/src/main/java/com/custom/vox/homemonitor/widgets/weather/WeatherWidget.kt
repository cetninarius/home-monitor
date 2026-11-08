package com.custom.vox.homemonitor.widgets.weather

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.custom.vox.homemonitor.R
import com.custom.vox.homemonitor.app
import com.custom.vox.homemonitor.data.weather.WeatherCodes
import com.custom.vox.homemonitor.data.weather.WeatherRepository
import com.custom.vox.homemonitor.data.weather.WeatherSnapshot
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
import kotlin.math.roundToInt

class WeatherWidget(
    private val context: Context,
) : DashboardWidget {

    override val id: String = "weather"

    private var expanded: View? = null
    private var compact: View? = null
    private var iconView: ImageView? = null
    private var tempView: TextView? = null
    private var descView: TextView? = null
    private var rainView: TextView? = null
    private var statusView: TextView? = null
    private var iconCompact: ImageView? = null
    private var tempCompact: TextView? = null
    private var compactMode = false
    private var lastSnap: WeatherSnapshot? = null
    private var scope: CoroutineScope? = null
    private var job: Job? = null

    override fun attach(parent: ViewGroup): View {
        val view = LayoutInflater.from(context).inflate(R.layout.widget_weather, parent, false)
        expanded = view.findViewById(R.id.weatherExpanded)
        compact = view.findViewById(R.id.weatherCompact)
        iconView = view.findViewById(R.id.weatherIcon)
        tempView = view.findViewById(R.id.weatherTemp)
        descView = view.findViewById(R.id.weatherDesc)
        rainView = view.findViewById(R.id.weatherRain)
        statusView = view.findViewById(R.id.weatherStatus)
        iconCompact = view.findViewById(R.id.weatherIconCompact)
        tempCompact = view.findViewById(R.id.weatherTempCompact)
        parent.addView(view)
        applyCompactVisibility()
        return view
    }

    fun setCompact(compact: Boolean) {
        val changed = compactMode != compact
        compactMode = compact
        applyCompactVisibility()
        if (changed) lastSnap?.let { bind(it) }
    }

    override fun onStart() {
        if (scope == null) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        job?.cancel()
        job = scope?.launch {
            while (isActive) {
                val result = withContext(Dispatchers.IO) { context.app().weatherRepository.refresh() }
                result.onSuccess { bind(it) }
                    .onFailure {
                        if (!compactMode) {
                            statusView?.visibility = View.VISIBLE
                            statusView?.setText(R.string.weather_unavailable)
                        }
                        tempCompact?.text = "—"
                    }
                delay(WeatherRepository.REFRESH_MS)
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

    private fun bind(snap: WeatherSnapshot) {
        lastSnap = snap
        val temp = "${snap.temperatureC.roundToInt()}°C"
        val icon = when (WeatherCodes.iconKind(snap.weatherCode)) {
            "clear" -> R.drawable.ic_weather_clear
            "rain", "storm" -> R.drawable.ic_weather_rain
            else -> R.drawable.ic_weather_cloud
        }

        tempView?.text = temp
        tempCompact?.text = temp
        iconView?.setImageResource(icon)
        iconCompact?.setImageResource(icon)

        if (compactMode) return

        descView?.text = snap.description
        val rain = snap.rainProbabilityNextHours
        if (rain != null) {
            rainView?.visibility = View.VISIBLE
            rainView?.text = context.getString(R.string.weather_rain, rain)
        } else {
            rainView?.visibility = View.GONE
        }
        if (snap.stale || System.currentTimeMillis() - snap.refreshedAtMs > WeatherRepository.STALE_AFTER_MS) {
            statusView?.visibility = View.VISIBLE
            statusView?.setText(R.string.weather_stale)
        } else {
            statusView?.visibility = View.GONE
        }
    }
}
