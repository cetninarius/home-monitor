package com.custom.vox.homemonitor.data.calendar

data class CalendarEvent(
    val id: String,
    val title: String,
    val date: String,
    val startTime: String?,
    val endTime: String?,
    val allDay: Boolean,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class CalendarDisplayState(
    val todayDate: String,
    val todayEvents: List<CalendarEvent>,
    val nextUpcoming: CalendarEvent?,
    val hasAnyEvents: Boolean,
)
