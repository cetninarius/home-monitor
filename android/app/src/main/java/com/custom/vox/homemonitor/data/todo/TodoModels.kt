package com.custom.vox.homemonitor.data.todo

data class TodoTask(
    val id: String,
    val title: String,
    val description: String?,
    val status: String,
    val priority: String,
    val dueDate: String?,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
) {
    val isPending: Boolean get() = status == STATUS_PENDING
    val isCompleted: Boolean get() = status == STATUS_COMPLETED

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_COMPLETED = "completed"
        const val PRIORITY_LOW = "low"
        const val PRIORITY_NORMAL = "normal"
        const val PRIORITY_HIGH = "high"
    }
}

data class TodoDisplayState(
    val pending: List<TodoTask>,
    val remainingCount: Int,
)
