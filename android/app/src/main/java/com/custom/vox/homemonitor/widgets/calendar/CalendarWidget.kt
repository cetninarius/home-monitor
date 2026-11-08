package com.custom.vox.homemonitor.widgets.calendar

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.custom.vox.homemonitor.R
import com.custom.vox.homemonitor.app
import com.custom.vox.homemonitor.data.calendar.CalendarDisplayState
import com.custom.vox.homemonitor.data.calendar.CalendarEvent
import com.custom.vox.homemonitor.data.calendar.CalendarLogic
import com.custom.vox.homemonitor.widgets.DashboardWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CalendarWidget(
    private val context: Context,
) : DashboardWidget {

    override val id: String = "calendar"

    private var emptyView: TextView? = null
    private var eventsContainer: LinearLayout? = null
    private var nextUpView: TextView? = null
    private var compactMode = false
    private var lastState: CalendarDisplayState? = null
    private var scope: CoroutineScope? = null
    private var collectJob: Job? = null

    override fun attach(parent: ViewGroup): View {
        val view = LayoutInflater.from(context).inflate(R.layout.widget_calendar, parent, false)
        emptyView = view.findViewById(R.id.calendarEmpty)
        eventsContainer = view.findViewById(R.id.calendarEvents)
        nextUpView = view.findViewById(R.id.calendarNextUp)
        parent.addView(view)
        return view
    }

    fun setCompact(compact: Boolean) {
        val changed = compactMode != compact
        compactMode = compact
        if (changed || lastState != null) lastState?.let { bind(it) }
    }

    override fun onStart() {
        if (scope == null) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        collectJob?.cancel()
        collectJob = scope?.launch {
            context.app().calendarRepository.observeDisplayState().collectLatest { bind(it) }
        }
    }

    override fun onStop() {
        collectJob?.cancel()
        collectJob = null
        scope?.cancel()
        scope = null
    }

    private fun bind(state: CalendarDisplayState) {
        lastState = state
        val container = eventsContainer ?: return
        container.removeAllViews()

        if (compactMode) {
            bindCompact(state)
            return
        }

        when {
            !state.hasAnyEvents -> {
                emptyView?.visibility = View.VISIBLE
                emptyView?.setText(R.string.calendar_nothing_scheduled)
                nextUpView?.visibility = View.GONE
            }
            state.todayEvents.isEmpty() -> {
                emptyView?.visibility = View.VISIBLE
                emptyView?.setText(R.string.calendar_no_events_today)
                bindNextUp(state.nextUpcoming)
            }
            else -> {
                emptyView?.visibility = View.GONE
                val inflater = LayoutInflater.from(context)
                state.todayEvents.forEach { event ->
                    container.addView(inflateEvent(inflater, container, event))
                }
                bindNextUp(state.nextUpcoming)
            }
        }
    }

    private fun bindCompact(state: CalendarDisplayState) {
        eventsContainer?.visibility = View.GONE
        nextUpView?.visibility = View.GONE
        val next = state.nextUpcoming ?: state.todayEvents.firstOrNull()
        if (next == null) {
            emptyView?.visibility = View.VISIBLE
            emptyView?.setText(R.string.calendar_nothing_scheduled)
            return
        }
        emptyView?.visibility = View.GONE
        eventsContainer?.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(context)
        eventsContainer?.addView(inflateEvent(inflater, eventsContainer!!, next))
    }

    private fun bindNextUp(event: CalendarEvent?) {
        val view = nextUpView ?: return
        if (event == null) {
            view.visibility = View.GONE
            return
        }
        view.visibility = View.VISIBLE
        view.text = context.getString(
            R.string.calendar_next_up,
            CalendarLogic.formatNextUpLabel(event),
        )
    }

    private fun inflateEvent(
        inflater: LayoutInflater,
        parent: ViewGroup,
        event: CalendarEvent,
    ): View {
        val row = inflater.inflate(R.layout.item_calendar_event, parent, false)
        val time = row.findViewById<TextView>(R.id.eventTime)
        val title = row.findViewById<TextView>(R.id.eventTitle)
        val secondary = row.findViewById<TextView>(R.id.eventSecondary)
        time.text = when {
            event.allDay || event.startTime.isNullOrBlank() ->
                context.getString(R.string.calendar_all_day)
            event.endTime.isNullOrBlank() -> event.startTime
            else -> "${event.startTime}–${event.endTime}"
        }
        title.text = event.title
        if (!compactMode && !event.description.isNullOrBlank()) {
            secondary.visibility = View.VISIBLE
            secondary.text = event.description
        } else {
            secondary.visibility = View.GONE
        }
        return row
    }
}
