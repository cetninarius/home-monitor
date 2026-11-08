package com.custom.vox.homemonitor.data.calendar

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object CalendarLogic {
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun today(zone: ZoneId = zone()): String = LocalDate.now(zone).format(dateFmt)

    fun nowTime(zone: ZoneId = zone()): String = LocalTime.now(zone).format(timeFmt)

    fun parseDate(value: String): LocalDate = LocalDate.parse(value, dateFmt)

    fun isValidDate(value: String): Boolean = try {
        parseDate(value)
        true
    } catch (_: Exception) {
        false
    }

    fun isValidTime(value: String?): Boolean {
        if (value.isNullOrBlank()) return true
        return try {
            LocalTime.parse(value, timeFmt)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun eventsForDate(all: List<CalendarEvent>, date: String): List<CalendarEvent> {
        return all.filter { it.date == date }
            .sortedWith(eventComparator())
    }

    fun nextUpcoming(
        all: List<CalendarEvent>,
        today: String,
        nowTime: String,
    ): CalendarEvent? {
        val todayDate = parseDate(today)
        return all
            .filter { event ->
                val d = parseDate(event.date)
                when {
                    d.isAfter(todayDate) -> true
                    d.isBefore(todayDate) -> false
                    event.allDay || event.startTime.isNullOrBlank() -> true
                    else -> event.startTime >= nowTime
                }
            }
            .sortedWith(eventComparator())
            .firstOrNull()
    }

    fun buildDisplayState(
        all: List<CalendarEvent>,
        today: String = today(),
        nowTime: String = nowTime(),
    ): CalendarDisplayState {
        val todayEvents = eventsForDate(all, today).take(3)
        val next = nextUpcoming(all, today, nowTime)
        // Prefer a next-up that is not already shown in today's list when today has events
        // and next is later today — still show it as "next" only if after now and useful.
        val nextUp = when {
            next == null -> null
            todayEvents.isEmpty() -> next
            next.date != today -> next
            todayEvents.any { it.id == next.id } && todayEvents.size >= all.count { it.date == today } -> null
            todayEvents.any { it.id == next.id } -> {
                // Show next upcoming after the last shown today, or tomorrow+
                all.filter { e ->
                    val d = parseDate(e.date)
                    when {
                        d.isAfter(parseDate(today)) -> true
                        d.isEqual(parseDate(today)) && !todayEvents.any { it.id == e.id } -> {
                            e.allDay || e.startTime.isNullOrBlank() || e.startTime >= nowTime
                        }
                        else -> false
                    }
                }.sortedWith(eventComparator()).firstOrNull()
            }
            else -> next
        }
        return CalendarDisplayState(
            todayDate = today,
            todayEvents = todayEvents,
            nextUpcoming = nextUp,
            hasAnyEvents = all.isNotEmpty(),
        )
    }

    fun formatNextUpLabel(event: CalendarEvent, locale: Locale = Locale.getDefault()): String {
        val day = parseDate(event.date)
        val dayName = day.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        val timePart = when {
            event.allDay || event.startTime.isNullOrBlank() -> "All day"
            else -> event.startTime
        }
        return "$dayName, $timePart — ${event.title}"
    }

    fun eventComparator(): Comparator<CalendarEvent> =
        compareBy<CalendarEvent> { it.date }
            .thenByDescending { it.allDay }
            .thenBy { it.startTime ?: "" }
            .thenBy { it.title }
}
