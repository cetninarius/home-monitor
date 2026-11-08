package com.custom.vox.homemonitor.admin

import com.custom.vox.homemonitor.data.calendar.CalendarEvent
import com.custom.vox.homemonitor.data.todo.TodoTask
import org.json.JSONArray
import org.json.JSONObject

object JsonUtil {
    fun eventJson(e: CalendarEvent): JSONObject = JSONObject()
        .put("id", e.id)
        .put("title", e.title)
        .put("date", e.date)
        .put("startTime", e.startTime)
        .put("endTime", e.endTime)
        .put("allDay", e.allDay)
        .put("description", e.description)
        .put("createdAt", e.createdAt)
        .put("updatedAt", e.updatedAt)

    fun taskJson(t: TodoTask): JSONObject = JSONObject()
        .put("id", t.id)
        .put("title", t.title)
        .put("description", t.description)
        .put("status", t.status)
        .put("priority", t.priority)
        .put("dueDate", t.dueDate)
        .put("sortOrder", t.sortOrder)
        .put("createdAt", t.createdAt)
        .put("updatedAt", t.updatedAt)
        .put("completedAt", t.completedAt)

    fun eventsArray(list: List<CalendarEvent>): JSONArray {
        val arr = JSONArray()
        list.forEach { arr.put(eventJson(it)) }
        return arr
    }

    fun tasksArray(list: List<TodoTask>): JSONArray {
        val arr = JSONArray()
        list.forEach { arr.put(taskJson(it)) }
        return arr
    }

    fun error(message: String): String =
        JSONObject().put("ok", false).put("error", message).toString()

    fun ok(payload: JSONObject = JSONObject()): String =
        payload.put("ok", true).toString()

    fun toJsonValue(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Number, is Boolean, is String -> value
        is Map<*, *> -> {
            val obj = JSONObject()
            value.forEach { (k, v) -> obj.put(k.toString(), toJsonValue(v)) }
            obj
        }
        is Iterable<*> -> {
            val arr = JSONArray()
            value.forEach { arr.put(toJsonValue(it)) }
            arr
        }
        is IntArray -> {
            val arr = JSONArray()
            value.forEach { arr.put(it) }
            arr
        }
        else -> value.toString()
    }
}
