package com.custom.vox.homemonitor

import com.custom.vox.homemonitor.data.calendar.CalendarEvent
import com.custom.vox.homemonitor.data.calendar.CalendarLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarLogicTest {
    private fun event(
        id: String,
        date: String,
        title: String,
        start: String? = null,
        allDay: Boolean = false,
    ) = CalendarEvent(
        id = id,
        title = title,
        date = date,
        startTime = start,
        endTime = null,
        allDay = allDay,
        description = null,
        createdAt = 1,
        updatedAt = 1,
    )

    @Test
    fun todayEventsSortedChronologically() {
        val all = listOf(
            event("2", "2026-11-07", "Later", "15:00"),
            event("1", "2026-11-07", "Morning", "09:00"),
            event("3", "2026-11-07", "All day", allDay = true),
            event("4", "2026-11-08", "Tomorrow", "10:00"),
        )
        val today = CalendarLogic.eventsForDate(all, "2026-11-07")
        assertEquals(listOf("3", "1", "2"), today.map { it.id })
    }

    @Test
    fun nextUpcomingSkipsPastToday() {
        val all = listOf(
            event("1", "2026-11-07", "Past", "08:00"),
            event("2", "2026-11-07", "Soon", "18:00"),
            event("3", "2026-11-08", "Tomorrow", "09:00"),
        )
        val next = CalendarLogic.nextUpcoming(all, "2026-11-07", "12:00")
        assertEquals("2", next?.id)
    }

    @Test
    fun emptyStateWhenNothingScheduled() {
        val state = CalendarLogic.buildDisplayState(emptyList(), "2026-11-07", "12:00")
        assertFalse(state.hasAnyEvents)
        assertTrue(state.todayEvents.isEmpty())
        assertNull(state.nextUpcoming)
    }

    @Test
    fun validatesDateAndTime() {
        assertTrue(CalendarLogic.isValidDate("2026-11-12"))
        assertFalse(CalendarLogic.isValidDate("12-11-2026"))
        assertTrue(CalendarLogic.isValidTime("15:00"))
        assertFalse(CalendarLogic.isValidTime("25:00"))
        assertTrue(CalendarLogic.isValidTime(null))
    }

    @Test
    fun formatNextUpLabel() {
        val e = event("1", "2026-11-10", "Dentist", "15:00")
        val label = CalendarLogic.formatNextUpLabel(e, java.util.Locale.ENGLISH)
        assertTrue(label.contains("Dentist"))
        assertTrue(label.contains("15:00"))
    }
}
