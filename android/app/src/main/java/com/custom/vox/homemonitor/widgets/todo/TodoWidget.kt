package com.custom.vox.homemonitor.widgets.todo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.custom.vox.homemonitor.R
import com.custom.vox.homemonitor.app
import com.custom.vox.homemonitor.data.todo.TodoDisplayState
import com.custom.vox.homemonitor.data.todo.TodoTask
import com.custom.vox.homemonitor.widgets.DashboardWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TodoWidget(
    private val context: Context,
) : DashboardWidget {

    override val id: String = "todo"

    private var remainingView: TextView? = null
    private var emptyView: TextView? = null
    private var listView: RecyclerView? = null
    private var compactMode = false
    private var lastState: TodoDisplayState? = null
    private var scope: CoroutineScope? = null
    private var collectJob: Job? = null

    private val adapter = TodoTaskAdapter { task -> completeTask(task) }
    private val layoutManager = object : LinearLayoutManager(context) {
        override fun canScrollVertically(): Boolean = !compactMode
    }

    override fun attach(parent: ViewGroup): View {
        val view = LayoutInflater.from(context).inflate(R.layout.widget_todo, parent, false)
        remainingView = view.findViewById(R.id.todoRemaining)
        emptyView = view.findViewById(R.id.todoEmpty)
        listView = view.findViewById(R.id.todoList)
        listView?.apply {
            layoutManager = this@TodoWidget.layoutManager
            adapter = this@TodoWidget.adapter
            itemAnimator = null
            isFocusable = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        parent.addView(view)
        applyListScrollPolicy()
        return view
    }

    fun setCompact(compact: Boolean) {
        val changed = compactMode != compact
        compactMode = compact
        applyListScrollPolicy()
        if (changed || lastState != null) lastState?.let { bind(it) }
    }

    override fun preferredFocusView(): View? = listView

    override fun onStart() {
        if (scope == null) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        collectJob?.cancel()
        collectJob = scope?.launch {
            context.app().todoRepository.observeDisplayState().collectLatest { bind(it) }
        }
    }

    override fun onStop() {
        collectJob?.cancel()
        collectJob = null
        scope?.cancel()
        scope = null
    }

    private fun applyListScrollPolicy() {
        listView?.apply {
            isNestedScrollingEnabled = !compactMode
            overScrollMode = if (compactMode) View.OVER_SCROLL_NEVER else View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
    }

    private fun bind(state: TodoDisplayState) {
        lastState = state
        remainingView?.text = context.getString(R.string.todo_remaining, state.remainingCount)
        val pending = if (compactMode) state.pending.take(COMPACT_MAX_ITEMS) else state.pending
        if (pending.isEmpty()) {
            emptyView?.visibility = View.VISIBLE
            listView?.visibility = View.GONE
            adapter.submitList(emptyList())
        } else {
            emptyView?.visibility = View.GONE
            listView?.visibility = View.VISIBLE
            adapter.submitList(pending)
        }
    }

    private fun completeTask(task: TodoTask) {
        val s = scope ?: return
        s.launch {
            withContext(Dispatchers.IO) {
                runCatching { context.app().todoRepository.complete(task.id) }
            }
        }
    }

    companion object {
        private const val COMPACT_MAX_ITEMS = 2
    }
}
