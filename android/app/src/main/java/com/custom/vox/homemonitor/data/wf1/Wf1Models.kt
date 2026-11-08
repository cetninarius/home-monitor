package com.custom.vox.homemonitor.data.wf1

/**
 * Sanitized models matching the Home Monitor proxy `/v1/wf1/queue` contract.
 */
data class Wf1QueueSnapshot(
    val refreshedAt: String?,
    val connection: ConnectionState,
    val counts: Wf1Counts,
    val active: Wf1ActiveTask?,
    val queued: List<Wf1TaskItem>,
    val blocked: List<Wf1TaskItem>,
    val readyForReview: List<Wf1TaskItem>,
    val recentDone: List<Wf1TaskItem>,
)

enum class ConnectionState {
    OK,
    STALE,
    DOWN,
}

data class Wf1Counts(
    val running: Int,
    val queued: Int,
    val blocked: Int,
    val readyForReview: Int,
)

data class Wf1ActiveTask(
    val issueNumber: Int?,
    val title: String?,
    val status: String,
    val showSpinner: Boolean,
    val stage: String?,
    val elapsedMs: Long?,
    val errorSummary: String?,
    val statusConfirmed: Boolean,
)

data class Wf1TaskItem(
    val issueNumber: Int?,
    val title: String?,
    val status: String,
    val priority: String? = null,
    val queuePosition: Int? = null,
    val detail: String? = null,
    val errorSummary: String? = null,
)

enum class DisplayStatus {
    RUNNING,
    QUEUED,
    READY_FOR_REVIEW,
    BLOCKED,
    INTERRUPTED,
    UNCONFIRMED,
    COMPLETED,
    IDLE,
}

data class DisplayTask(
    val id: String,
    val issueNumber: Int?,
    val title: String,
    val status: DisplayStatus,
    val detail: String? = null,
    val errorSummary: String? = null,
    val showSpinner: Boolean = false,
    val queuePosition: Int? = null,
)
