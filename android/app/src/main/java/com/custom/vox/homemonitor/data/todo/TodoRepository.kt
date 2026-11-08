package com.custom.vox.homemonitor.data.todo

import com.custom.vox.homemonitor.data.local.TodoTaskDao
import com.custom.vox.homemonitor.data.local.TodoTaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class TodoRepository(
    private val dao: TodoTaskDao,
) {
    fun observeAll(): Flow<List<TodoTask>> = dao.observeAll().map { list ->
        list.map { it.toModel() }
    }

    fun observeDisplayState(): Flow<TodoDisplayState> = observeAll().map { all ->
        TodoLogic.buildDisplayState(all)
    }

    suspend fun listAll(): List<TodoTask> = dao.listAll().map { it.toModel() }

    suspend fun get(id: String): TodoTask? = dao.getById(id)?.toModel()

    suspend fun create(
        title: String,
        description: String? = null,
        priority: String = TodoTask.PRIORITY_NORMAL,
        dueDate: String? = null,
        sortOrder: Int? = null,
    ): TodoTask {
        if (title.isBlank()) throw IllegalArgumentException("Title is required")
        val p = TodoLogic.normalizePriority(priority)
        if (dueDate != null && dueDate.isNotBlank() && !com.custom.vox.homemonitor.data.calendar.CalendarLogic.isValidDate(dueDate)) {
            throw IllegalArgumentException("Invalid due date")
        }
        val now = System.currentTimeMillis()
        val order = sortOrder ?: (dao.maxSortOrder() + 1)
        val entity = TodoTaskEntity(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            status = TodoTask.STATUS_PENDING,
            priority = p,
            dueDate = dueDate?.takeIf { it.isNotBlank() },
            sortOrder = order,
            createdAt = now,
            updatedAt = now,
            completedAt = null,
        )
        dao.insert(entity)
        return entity.toModel()
    }

    suspend fun update(
        id: String,
        title: String? = null,
        description: String? = null,
        clearDescription: Boolean = false,
        priority: String? = null,
        dueDate: String? = null,
        clearDueDate: Boolean = false,
        sortOrder: Int? = null,
    ): TodoTask {
        val existing = dao.getById(id) ?: throw NoSuchElementException("Task not found")
        val nextTitle = title?.trim() ?: existing.title
        if (nextTitle.isBlank()) throw IllegalArgumentException("Title is required")
        val nextPriority = priority?.let { TodoLogic.normalizePriority(it) } ?: existing.priority
        val nextDue = when {
            clearDueDate -> null
            dueDate != null -> {
                if (dueDate.isNotBlank() && !com.custom.vox.homemonitor.data.calendar.CalendarLogic.isValidDate(dueDate)) {
                    throw IllegalArgumentException("Invalid due date")
                }
                dueDate.takeIf { it.isNotBlank() }
            }
            else -> existing.dueDate
        }
        val updated = existing.copy(
            title = nextTitle,
            description = when {
                clearDescription -> null
                description != null -> description.trim().takeIf { it.isNotEmpty() }
                else -> existing.description
            },
            priority = nextPriority,
            dueDate = nextDue,
            sortOrder = sortOrder ?: existing.sortOrder,
            updatedAt = System.currentTimeMillis(),
        )
        dao.update(updated)
        return updated.toModel()
    }

    suspend fun complete(id: String): TodoTask {
        val existing = dao.getById(id) ?: throw NoSuchElementException("Task not found")
        if (existing.status == TodoTask.STATUS_COMPLETED) return existing.toModel()
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            status = TodoTask.STATUS_COMPLETED,
            completedAt = now,
            updatedAt = now,
        )
        dao.update(updated)
        return updated.toModel()
    }

    suspend fun reopen(id: String): TodoTask {
        val existing = dao.getById(id) ?: throw NoSuchElementException("Task not found")
        if (existing.status == TodoTask.STATUS_PENDING) return existing.toModel()
        val updated = existing.copy(
            status = TodoTask.STATUS_PENDING,
            completedAt = null,
            updatedAt = System.currentTimeMillis(),
        )
        dao.update(updated)
        return updated.toModel()
    }

    suspend fun delete(id: String) {
        if (dao.getById(id) == null) throw NoSuchElementException("Task not found")
        dao.delete(id)
    }

    suspend fun reorder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id ->
            val existing = dao.getById(id) ?: return@forEachIndexed
            if (existing.sortOrder != index) {
                dao.update(existing.copy(sortOrder = index, updatedAt = System.currentTimeMillis()))
            }
        }
    }
}

fun TodoTaskEntity.toModel() = TodoTask(
    id = id,
    title = title,
    description = description,
    status = status,
    priority = priority,
    dueDate = dueDate,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
)
