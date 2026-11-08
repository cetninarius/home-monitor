package com.custom.vox.homemonitor.widgets.wf1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.custom.vox.homemonitor.R
import com.custom.vox.homemonitor.data.wf1.DisplayStatus
import com.custom.vox.homemonitor.data.wf1.DisplayTask

class Wf1TaskAdapter : ListAdapter<DisplayTask, Wf1TaskAdapter.Holder>(Diff) {

    object Diff : DiffUtil.ItemCallback<DisplayTask>() {
        override fun areItemsTheSame(oldItem: DisplayTask, newItem: DisplayTask): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: DisplayTask, newItem: DisplayTask): Boolean =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wf1_task, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: View = itemView.findViewById(R.id.taskCard)
        private val statusIcon: ImageView = itemView.findViewById(R.id.statusIcon)
        private val statusSpinner: ProgressBar = itemView.findViewById(R.id.statusSpinner)
        private val issueNumber: TextView = itemView.findViewById(R.id.issueNumber)
        private val statusLabel: TextView = itemView.findViewById(R.id.statusLabel)
        private val taskTitle: TextView = itemView.findViewById(R.id.taskTitle)
        private val taskDetail: TextView = itemView.findViewById(R.id.taskDetail)
        private val taskError: TextView = itemView.findViewById(R.id.taskError)

        fun bind(task: DisplayTask) {
            val ctx = itemView.context
            issueNumber.text = task.issueNumber?.let { "#$it" } ?: "—"
            taskTitle.text = task.title

            val (bg, icon, label, colorRes) = when (task.status) {
                DisplayStatus.RUNNING -> Quad(
                    R.drawable.bg_task_card_running,
                    R.drawable.ic_status_queued,
                    R.string.status_running,
                    R.color.hm_accent_cyan,
                )
                DisplayStatus.QUEUED -> Quad(
                    R.drawable.bg_task_card,
                    R.drawable.ic_status_queued,
                    R.string.status_queued,
                    R.color.hm_text_secondary,
                )
                DisplayStatus.READY_FOR_REVIEW -> Quad(
                    R.drawable.bg_task_card_review,
                    R.drawable.ic_status_review,
                    R.string.status_ready_review,
                    R.color.hm_accent_purple,
                )
                DisplayStatus.BLOCKED, DisplayStatus.INTERRUPTED -> Quad(
                    R.drawable.bg_task_card_blocked,
                    R.drawable.ic_status_blocked,
                    if (task.status == DisplayStatus.INTERRUPTED) {
                        R.string.status_interrupted
                    } else {
                        R.string.status_blocked
                    },
                    R.color.hm_accent_red,
                )
                DisplayStatus.COMPLETED -> Quad(
                    R.drawable.bg_task_card,
                    R.drawable.ic_status_done,
                    R.string.status_completed,
                    R.color.hm_text_secondary,
                )
                DisplayStatus.UNCONFIRMED -> Quad(
                    R.drawable.bg_task_card,
                    R.drawable.ic_status_queued,
                    R.string.status_unconfirmed,
                    R.color.hm_connection_stale,
                )
                DisplayStatus.IDLE -> Quad(
                    R.drawable.bg_task_card,
                    R.drawable.ic_status_queued,
                    R.string.status_idle,
                    R.color.hm_text_muted,
                )
            }

            card.setBackgroundResource(bg)
            if (task.showSpinner) {
                statusIcon.visibility = View.GONE
                statusSpinner.visibility = View.VISIBLE
            } else {
                statusSpinner.visibility = View.GONE
                statusIcon.visibility = View.VISIBLE
                statusIcon.setImageResource(icon)
            }

            statusLabel.setText(label)
            statusLabel.setTextColor(ContextCompat.getColor(ctx, colorRes))

            val detail = when {
                task.queuePosition != null && task.status == DisplayStatus.QUEUED ->
                    listOfNotNull("Queue #${task.queuePosition}", task.detail).joinToString(" · ")
                else -> task.detail
            }
            if (!detail.isNullOrBlank()) {
                taskDetail.visibility = View.VISIBLE
                taskDetail.text = detail
            } else {
                taskDetail.visibility = View.GONE
            }

            if (!task.errorSummary.isNullOrBlank()) {
                taskError.visibility = View.VISIBLE
                taskError.text = task.errorSummary
            } else {
                taskError.visibility = View.GONE
            }
        }
    }

    private data class Quad(
        val bg: Int,
        val icon: Int,
        val label: Int,
        val colorRes: Int,
    )
}
