package com.custom.vox.homemonitor.data.wf1

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches WF1 queue snapshots. Retains last good data on failure so the UI can mark stale.
 */
class Wf1Repository(
    private val config: Wf1ConfigStore,
    private val api: Wf1ApiClient = Wf1ApiClient(config),
) {
    @Volatile
    var lastSnapshot: Wf1QueueSnapshot? = null
        private set

    fun isConfigured(): Boolean = config.isConfigured()

    suspend fun refresh(): Result<Wf1QueueSnapshot> = withContext(Dispatchers.IO) {
        if (!config.isConfigured()) {
            return@withContext Result.failure(Wf1NotConfiguredException("not_configured"))
        }
        try {
            val snap = api.fetchQueue()
            lastSnapshot = snap
            Result.success(snap)
        } catch (e: Exception) {
            val previous = lastSnapshot
            if (previous != null) {
                val stale = previous.copy(connection = ConnectionState.STALE)
                lastSnapshot = stale
                Result.success(stale)
            } else {
                Result.failure(e)
            }
        }
    }

    fun buildDisplayTasks(snapshot: Wf1QueueSnapshot): List<DisplayTask> {
        val tasks = mutableListOf<DisplayTask>()
        val active = snapshot.active
        if (active != null) {
            val status = mapActiveStatus(active)
            // Never show spinner unless genuinely running + showSpinner from source.
            val spinner = status == DisplayStatus.RUNNING && active.showSpinner &&
                snapshot.connection != ConnectionState.DOWN
            tasks.add(
                DisplayTask(
                    id = "active-${active.issueNumber ?: "unknown"}",
                    issueNumber = active.issueNumber,
                    title = active.title?.ifBlank { null } ?: "Issue #${active.issueNumber ?: "?"}",
                    status = status,
                    detail = buildActiveDetail(active),
                    errorSummary = active.errorSummary,
                    showSpinner = spinner && snapshot.connection == ConnectionState.OK,
                    queuePosition = null,
                ),
            )
        }

        snapshot.queued.forEach { item ->
            tasks.add(item.toDisplay(DisplayStatus.QUEUED, "queued"))
        }
        snapshot.blocked.forEach { item ->
            // Keep blocked visible even if also listed elsewhere.
            if (tasks.none { it.issueNumber != null && it.issueNumber == item.issueNumber && it.status == DisplayStatus.BLOCKED }) {
                tasks.add(item.toDisplay(DisplayStatus.BLOCKED, "blocked"))
            }
        }
        snapshot.readyForReview.forEach { item ->
            if (tasks.none { it.issueNumber != null && it.issueNumber == item.issueNumber }) {
                tasks.add(item.toDisplay(DisplayStatus.READY_FOR_REVIEW, "review"))
            }
        }
        // Recently completed — useful glance only; keep short.
        snapshot.recentDone.take(2).forEach { item ->
            if (tasks.none { it.issueNumber != null && it.issueNumber == item.issueNumber }) {
                tasks.add(item.toDisplay(DisplayStatus.COMPLETED, "done"))
            }
        }
        return tasks
    }

    private fun mapActiveStatus(active: Wf1ActiveTask): DisplayStatus {
        return when (active.status.lowercase()) {
            "running" -> if (active.showSpinner) DisplayStatus.RUNNING else DisplayStatus.UNCONFIRMED
            "blocked" -> DisplayStatus.BLOCKED
            "interrupted" -> DisplayStatus.INTERRUPTED
            "corrections_requested" -> DisplayStatus.READY_FOR_REVIEW
            "unconfirmed" -> DisplayStatus.UNCONFIRMED
            "idle" -> DisplayStatus.IDLE
            else -> DisplayStatus.UNCONFIRMED
        }
    }

    private fun buildActiveDetail(active: Wf1ActiveTask): String? {
        val parts = mutableListOf<String>()
        active.stage?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        active.elapsedMs?.takeIf { it >= 0 }?.let { parts.add(formatElapsed(it)) }
        return parts.joinToString(" · ").ifBlank { null }
    }

    private fun Wf1TaskItem.toDisplay(status: DisplayStatus, prefix: String): DisplayTask {
        return DisplayTask(
            id = "$prefix-${issueNumber ?: title.hashCode()}",
            issueNumber = issueNumber,
            title = title?.ifBlank { null } ?: "Issue #${issueNumber ?: "?"}",
            status = status,
            detail = detail ?: priority,
            errorSummary = errorSummary,
            showSpinner = false,
            queuePosition = queuePosition,
        )
    }

    private fun formatElapsed(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> "%dh %02dm".format(h, m)
            m > 0 -> "%dm %02ds".format(m, s)
            else -> "%ds".format(s)
        }
    }
}
