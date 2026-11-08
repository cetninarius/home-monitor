package com.custom.vox.homemonitor.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "todo_tasks",
    indices = [
        Index(value = ["status"]),
        Index(value = ["priority", "sortOrder"]),
    ],
)
data class TodoTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    /** pending | completed */
    val status: String,
    /** low | normal | high */
    val priority: String,
    val dueDate: String?,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
)
