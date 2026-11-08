package com.custom.vox.homemonitor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoTaskDao {
    @Query("SELECT * FROM todo_tasks ORDER BY status ASC, priority DESC, sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<TodoTaskEntity>>

    @Query("SELECT * FROM todo_tasks ORDER BY status ASC, priority DESC, sortOrder ASC, createdAt ASC")
    suspend fun listAll(): List<TodoTaskEntity>

    @Query(
        """
        SELECT * FROM todo_tasks
        WHERE status = 'pending'
        ORDER BY
          CASE priority WHEN 'high' THEN 0 WHEN 'normal' THEN 1 ELSE 2 END ASC,
          sortOrder ASC,
          createdAt ASC
        """,
    )
    fun observePending(): Flow<List<TodoTaskEntity>>

    @Query("SELECT * FROM todo_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TodoTaskEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM todo_tasks")
    suspend fun maxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TodoTaskEntity)

    @Update
    suspend fun update(task: TodoTaskEntity)

    @Query("DELETE FROM todo_tasks WHERE id = :id")
    suspend fun delete(id: String)
}
