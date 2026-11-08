package com.custom.vox.homemonitor

import com.custom.vox.homemonitor.admin.JsonUtil
import com.custom.vox.homemonitor.data.calendar.CalendarEvent
import com.custom.vox.homemonitor.data.todo.TodoTask
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminJsonUtilTest {
    @Test
    fun errorAndOkPayloads() {
        val err = JSONObject(JsonUtil.error("unauthorized"))
        assertFalse(err.getBoolean("ok"))
        assertEquals("unauthorized", err.getString("error"))

        val ok = JSONObject(JsonUtil.ok(JSONObject().put("authenticated", true)))
        assertTrue(ok.getBoolean("ok"))
        assertTrue(ok.getBoolean("authenticated"))
    }

    @Test
    fun serializesTaskAndEvent() {
        val task = TodoTask(
            id = "t1",
            title = "Renew domain",
            description = null,
            status = TodoTask.STATUS_PENDING,
            priority = TodoTask.PRIORITY_HIGH,
            dueDate = null,
            sortOrder = 1,
            createdAt = 1,
            updatedAt = 1,
            completedAt = null,
        )
        val event = CalendarEvent(
            id = "e1",
            title = "Dentist",
            date = "2026-11-12",
            startTime = "15:00",
            endTime = null,
            allDay = false,
            description = null,
            createdAt = 1,
            updatedAt = 1,
        )
        assertEquals("Renew domain", JsonUtil.taskJson(task).getString("title"))
        assertEquals("2026-11-12", JsonUtil.eventJson(event).getString("date"))
    }
}
