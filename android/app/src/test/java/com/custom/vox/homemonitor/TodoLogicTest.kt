package com.custom.vox.homemonitor

import com.custom.vox.homemonitor.data.todo.TodoLogic
import com.custom.vox.homemonitor.data.todo.TodoTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoLogicTest {
    private fun task(
        id: String,
        title: String,
        priority: String,
        sortOrder: Int,
        status: String = TodoTask.STATUS_PENDING,
    ) = TodoTask(
        id = id,
        title = title,
        description = null,
        status = status,
        priority = priority,
        dueDate = null,
        sortOrder = sortOrder,
        createdAt = sortOrder.toLong(),
        updatedAt = sortOrder.toLong(),
        completedAt = null,
    )

    @Test
    fun sortsByPriorityThenSortOrder() {
        val all = listOf(
            task("a", "Low", TodoTask.PRIORITY_LOW, 0),
            task("b", "High late", TodoTask.PRIORITY_HIGH, 2),
            task("c", "High early", TodoTask.PRIORITY_HIGH, 1),
            task("d", "Normal", TodoTask.PRIORITY_NORMAL, 0),
            task("e", "Done", TodoTask.PRIORITY_HIGH, 0, TodoTask.STATUS_COMPLETED),
        )
        val pending = TodoLogic.sortPending(all)
        assertEquals(listOf("c", "b", "d", "a"), pending.map { it.id })
    }

    @Test
    fun displayLimitsToFive() {
        val all = (0 until 8).map {
            task(it.toString(), "T$it", TodoTask.PRIORITY_NORMAL, it)
        }
        val state = TodoLogic.buildDisplayState(all, limit = 5)
        assertEquals(5, state.pending.size)
        assertEquals(8, state.remainingCount)
    }

    @Test
    fun normalizePriority() {
        assertEquals(TodoTask.PRIORITY_HIGH, TodoLogic.normalizePriority("HIGH"))
        assertEquals(TodoTask.PRIORITY_NORMAL, TodoLogic.normalizePriority(null))
        assertTrue(TodoLogic.isValidPriority("low"))
    }
}
