package com.custom.vox.homemonitor.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "calendar_events",
    indices = [Index(value = ["date"])],
)
data class CalendarEventEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** Local civil date YYYY-MM-DD */
    val date: String,
    /** Local HH:mm or null when all-day / unset */
    val startTime: String?,
    val endTime: String?,
    val allDay: Boolean,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
