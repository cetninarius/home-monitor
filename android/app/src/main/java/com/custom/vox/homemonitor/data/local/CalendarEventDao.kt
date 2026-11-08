package com.custom.vox.homemonitor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events ORDER BY date ASC, allDay DESC, startTime ASC, title ASC")
    fun observeAll(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events ORDER BY date ASC, allDay DESC, startTime ASC, title ASC")
    suspend fun listAll(): List<CalendarEventEntity>

    @Query(
        """
        SELECT * FROM calendar_events
        WHERE date = :date
        ORDER BY allDay DESC, startTime ASC, title ASC
        """,
    )
    fun observeForDate(date: String): Flow<List<CalendarEventEntity>>

    @Query(
        """
        SELECT * FROM calendar_events
        WHERE date > :date OR (date = :date AND (allDay = 1 OR startTime IS NULL OR startTime >= :timeOrEmpty))
        ORDER BY date ASC, allDay DESC, startTime ASC, title ASC
        LIMIT 1
        """,
    )
    suspend fun nextUpcoming(date: String, timeOrEmpty: String): CalendarEventEntity?

    @Query("SELECT * FROM calendar_events WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CalendarEventEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: CalendarEventEntity)

    @Update
    suspend fun update(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun delete(id: String)
}
