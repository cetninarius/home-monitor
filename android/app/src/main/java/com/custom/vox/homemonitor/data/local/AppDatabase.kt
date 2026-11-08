package com.custom.vox.homemonitor.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CalendarEventEntity::class, TodoTaskEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun calendarEvents(): CalendarEventDao
    abstract fun todoTasks(): TodoTaskDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "home_monitor.db",
                )
                    // No destructive fallback — schema changes require explicit migrations.
                    .build()
                    .also { instance = it }
            }
        }
    }
}
