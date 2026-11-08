package com.custom.vox.homemonitor.widgets

import android.content.Context
import com.custom.vox.homemonitor.widgets.calendar.CalendarWidget
import com.custom.vox.homemonitor.widgets.clock.ClockWidget
import com.custom.vox.homemonitor.widgets.network.NetworkStripWidget
import com.custom.vox.homemonitor.widgets.nowplaying.NowPlayingWidget
import com.custom.vox.homemonitor.widgets.services.ServicesWidget
import com.custom.vox.homemonitor.widgets.todo.TodoWidget
import com.custom.vox.homemonitor.widgets.weather.WeatherWidget
import com.custom.vox.homemonitor.widgets.wf1.Wf1QueueWidget

object WidgetRegistry {
    fun createClock(context: Context): ClockWidget = ClockWidget(context)
    fun createNetworkStrip(context: Context): NetworkStripWidget = NetworkStripWidget(context)
    fun createNowPlaying(context: Context): NowPlayingWidget = NowPlayingWidget(context)
    fun createWf1(context: Context): Wf1QueueWidget = Wf1QueueWidget(context)
    fun createCalendar(context: Context): CalendarWidget = CalendarWidget(context)
    fun createTodo(context: Context): TodoWidget = TodoWidget(context)
    fun createServices(context: Context): ServicesWidget = ServicesWidget(context)
    fun createWeather(context: Context): WeatherWidget = WeatherWidget(context)
}
