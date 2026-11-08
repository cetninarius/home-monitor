package com.custom.vox.homemonitor.widgets.todo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.custom.vox.homemonitor.R
import com.custom.vox.homemonitor.data.todo.TodoTask

class TodoTaskAdapter(
    private val onActivate: (TodoTask) -> Unit,
) : ListAdapter<TodoTask, TodoTaskAdapter.Holder>(Diff) {

    object Diff : DiffUtil.ItemCallback<TodoTask>() {
        override fun areItemsTheSame(oldItem: TodoTask, newItem: TodoTask): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TodoTask, newItem: TodoTask): Boolean =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_todo_task, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val check: ImageView = itemView.findViewById(R.id.todoCheck)
        private val title: TextView = itemView.findViewById(R.id.todoTitle)
        private val priority: TextView = itemView.findViewById(R.id.todoPriority)

        fun bind(task: TodoTask) {
            title.text = task.title
            check.setImageResource(R.drawable.ic_status_queued)
            when (task.priority) {
                TodoTask.PRIORITY_HIGH -> {
                    priority.visibility = View.VISIBLE
                    priority.setText(R.string.todo_priority_high)
                    priority.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.hm_accent_red),
                    )
                }
                else -> priority.visibility = View.GONE
            }
            itemView.setOnClickListener { onActivate(task) }
            itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_UP &&
                    (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == android.view.KeyEvent.KEYCODE_ENTER)
                ) {
                    onActivate(task)
                    true
                } else {
                    false
                }
            }
        }
    }
}
