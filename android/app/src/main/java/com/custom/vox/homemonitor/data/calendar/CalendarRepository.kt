package com.custom.vox.homemonitor.data.calendar

import com.custom.vox.homemonitor.data.local.CalendarEventDao
import com.custom.vox.homemonitor.data.local.CalendarEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class CalendarRepository(
    private val dao: CalendarEventDao,
) {
    fun observeAll(): Flow<List<CalendarEvent>> = dao.observeAll().map { list ->
        list.map { it.toModel() }
    }

    fun observeDisplayState(): Flow<CalendarDisplayState> = observeAll().map { all ->
        CalendarLogic.buildDisplayState(all)
    }

    suspend fun listAll(): List<CalendarEvent> = dao.listAll().map { it.toModel() }

    suspend fun get(id: String): CalendarEvent? = dao.getById(id)?.toModel()

    suspend fun create(
        title: String,
        date: String,
        startTime: String? = null,
        endTime: String? = null,
        allDay: Boolean = false,
        description: String? = null,
    ): CalendarEvent {
        validate(title, date, startTime, endTime, allDay)
        val now = System.currentTimeMillis()
        val entity = CalendarEventEntity(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            date = date,
            startTime = if (allDay) null else startTime?.takeIf { it.isNotBlank() },
            endTime = if (allDay) null else endTime?.takeIf { it.isNotBlank() },
            allDay = allDay,
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = now,
            updatedAt = now,
        )
        dao.insert(entity)
        return entity.toModel()
    }

    suspend fun update(
        id: String,
        title: String? = null,
        date: String? = null,
        startTime: String? = null,
        endTime: String? = null,
        allDay: Boolean? = null,
        description: String? = null,
        clearDescription: Boolean = false,
    ): CalendarEvent {
        val existing = dao.getById(id) ?: throw NoSuchElementException("Event not found")
        val nextAllDay = allDay ?: existing.allDay
        val nextDate = date ?: existing.date
        val nextTitle = title?.trim() ?: existing.title
        val nextStart = when {
            nextAllDay -> null
            startTime != null -> startTime.takeIf { it.isNotBlank() }
            else -> existing.startTime
        }
        val nextEnd = when {
            nextAllDay -> null
            endTime != null -> endTime.takeIf { it.isNotBlank() }
            else -> existing.endTime
        }
        validate(nextTitle, nextDate, nextStart, nextEnd, nextAllDay)
        val updated = existing.copy(
            title = nextTitle,
            date = nextDate,
            startTime = nextStart,
            endTime = nextEnd,
            allDay = nextAllDay,
            description = when {
                clearDescription -> null
                description != null -> description.trim().takeIf { it.isNotEmpty() }
                else -> existing.description
            },
            updatedAt = System.currentTimeMillis(),
        )
        dao.update(updated)
        return updated.toModel()
    }

    suspend fun delete(id: String) {
        if (dao.getById(id) == null) throw NoSuchElementException("Event not found")
        dao.delete(id)
    }

    private fun validate(
        title: String,
        date: String,
        startTime: String?,
        endTime: String?,
        allDay: Boolean,
    ) {
        if (title.isBlank()) throw IllegalArgumentException("Title is required")
        if (!CalendarLogic.isValidDate(date)) throw IllegalArgumentException("Invalid date")
        if (!allDay) {
            if (!CalendarLogic.isValidTime(startTime)) throw IllegalArgumentException("Invalid start time")
            if (!CalendarLogic.isValidTime(endTime)) throw IllegalArgumentException("Invalid end time")
        }
    }
}

fun CalendarEventEntity.toModel() = CalendarEvent(
    id = id,
    title = title,
    date = date,
    startTime = startTime,
    endTime = endTime,
    allDay = allDay,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
