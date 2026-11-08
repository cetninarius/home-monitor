package com.custom.vox.homemonitor.data.todo

object TodoLogic {
    private val priorityRank = mapOf(
        TodoTask.PRIORITY_HIGH to 0,
        TodoTask.PRIORITY_NORMAL to 1,
        TodoTask.PRIORITY_LOW to 2,
    )

    fun sortPending(tasks: List<TodoTask>): List<TodoTask> {
        return tasks
            .filter { it.isPending }
            .sortedWith(
                compareBy<TodoTask> { priorityRank[it.priority] ?: 1 }
                    .thenBy { it.sortOrder }
                    .thenBy { it.createdAt },
            )
    }

    fun buildDisplayState(all: List<TodoTask>, limit: Int = 5): TodoDisplayState {
        val pending = sortPending(all)
        return TodoDisplayState(
            pending = pending.take(limit),
            remainingCount = pending.size,
        )
    }

    fun normalizePriority(value: String?): String {
        return when (value?.lowercase()) {
            TodoTask.PRIORITY_LOW -> TodoTask.PRIORITY_LOW
            TodoTask.PRIORITY_HIGH -> TodoTask.PRIORITY_HIGH
            else -> TodoTask.PRIORITY_NORMAL
        }
    }

    fun isValidPriority(value: String): Boolean =
        value == TodoTask.PRIORITY_LOW ||
            value == TodoTask.PRIORITY_NORMAL ||
            value == TodoTask.PRIORITY_HIGH

    fun isValidStatus(value: String): Boolean =
        value == TodoTask.STATUS_PENDING || value == TodoTask.STATUS_COMPLETED
}
