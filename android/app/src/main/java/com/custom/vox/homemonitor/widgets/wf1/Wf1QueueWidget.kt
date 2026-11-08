package com.custom.vox.homemonitor.widgets.wf1

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.custom.vox.homemonitor.R
import com.custom.vox.homemonitor.app
import com.custom.vox.homemonitor.data.wf1.ConnectionState
import com.custom.vox.homemonitor.data.wf1.Wf1NotConfiguredException
import com.custom.vox.homemonitor.data.wf1.Wf1QueueSnapshot
import com.custom.vox.homemonitor.data.wf1.Wf1Repository
import com.custom.vox.homemonitor.widgets.DashboardWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Wf1QueueWidget(
    private val context: Context,
    private val repository: Wf1Repository = context.app().wf1Repository,
) : DashboardWidget {

    override val id: String = "wf1-live-queue"

    private var root: View? = null
    private var countRunning: TextView? = null
    private var countQueued: TextView? = null
    private var countBlocked: TextView? = null
    private var connectionDot: View? = null
    private var connectionText: TextView? = null
    private var emptyView: TextView? = null
    private var emptySecondary: TextView? = null
    private var loadingView: TextView? = null
    private var taskList: RecyclerView? = null
    private val adapter = Wf1TaskAdapter()

    private var scope: CoroutineScope? = null
    private var pollJob: Job? = null
    private var hasLoadedOnce = false
    private var started = false

    override fun attach(parent: ViewGroup): View {
        val view = LayoutInflater.from(context).inflate(R.layout.widget_wf1_queue, parent, false)
        root = view
        countRunning = view.findViewById(R.id.countRunning)
        countQueued = view.findViewById(R.id.countQueued)
        countBlocked = view.findViewById(R.id.countBlocked)
        connectionDot = view.findViewById(R.id.connectionDot)
        connectionText = view.findViewById(R.id.connectionText)
        emptyView = view.findViewById(R.id.wf1Empty)
        emptySecondary = view.findViewById(R.id.wf1EmptySecondary)
        loadingView = view.findViewById(R.id.wf1Loading)
        taskList = view.findViewById(R.id.taskList)

        taskList?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@Wf1QueueWidget.adapter
            itemAnimator = null
            isFocusable = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }

        parent.addView(view)
        showNotConnected()
        return view
    }

    override fun preferredFocusView(): View? = taskList

    override fun onStart() {
        started = true
        if (scope == null) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        }
        syncPolling()
    }

    override fun onStop() {
        started = false
        pollJob?.cancel()
        pollJob = null
        scope?.cancel()
        scope = null
    }

    /** Call after enable/config changes while the dashboard is live. */
    fun syncPolling() {
        if (!started) return
        val enabled = context.app().dashboardPrefs.wf1Enabled()
        if (!enabled) {
            pollJob?.cancel()
            pollJob = null
            return
        }
        if (!repository.isConfigured()) {
            pollJob?.cancel()
            pollJob = null
            hasLoadedOnce = true
            showLoading(false)
            showNotConnected()
            return
        }
        startPolling()
    }

    private fun startPolling() {
        val s = scope ?: return
        pollJob?.cancel()
        pollJob = s.launch {
            while (isActive) {
                refreshOnce()
                delay(POLL_MS)
            }
        }
    }

    private suspend fun refreshOnce() {
        if (!repository.isConfigured()) {
            hasLoadedOnce = true
            showLoading(false)
            showNotConnected()
            return
        }
        val result = withContext(Dispatchers.IO) { repository.refresh() }
        result.onSuccess { snapshot ->
            hasLoadedOnce = true
            bindSnapshot(snapshot)
        }.onFailure { err ->
            hasLoadedOnce = true
            showLoading(false)
            if (err is Wf1NotConfiguredException) {
                showNotConnected()
            } else {
                bindConnection(ConnectionState.DOWN)
                adapter.submitList(emptyList())
                showNotConnected()
            }
        }
    }

    private fun bindSnapshot(snapshot: Wf1QueueSnapshot) {
        showLoading(false)
        countRunning?.text = context.getString(R.string.status_running) + "  ${snapshot.counts.running}"
        countQueued?.text = context.getString(R.string.status_queued) + "  ${snapshot.counts.queued}"
        countBlocked?.text = context.getString(R.string.status_blocked) + "  ${snapshot.counts.blocked}"

        val effective = if (snapshot.connection == ConnectionState.STALE) {
            snapshot.copy(
                active = snapshot.active?.copy(showSpinner = false),
            )
        } else snapshot

        bindConnection(effective.connection)
        val tasks = repository.buildDisplayTasks(effective)
        adapter.submitList(tasks)
        if (tasks.isEmpty()) {
            if (effective.connection == ConnectionState.DOWN) {
                showNotConnected()
            } else {
                emptyView?.visibility = View.VISIBLE
                emptyView?.setText(R.string.empty_queue)
                emptySecondary?.visibility = View.GONE
            }
        } else {
            emptyView?.visibility = View.GONE
            emptySecondary?.visibility = View.GONE
        }
    }

    private fun showNotConnected() {
        countRunning?.text = context.getString(R.string.status_running) + "  —"
        countQueued?.text = context.getString(R.string.status_queued) + "  —"
        countBlocked?.text = context.getString(R.string.status_blocked) + "  —"
        bindConnection(ConnectionState.DOWN)
        adapter.submitList(emptyList())
        emptyView?.visibility = View.VISIBLE
        emptyView?.setText(R.string.wf1_no_engine)
        emptySecondary?.visibility = View.VISIBLE
        emptySecondary?.setText(R.string.wf1_no_engine_hint)
    }

    private fun bindConnection(state: ConnectionState) {
        val (labelRes, colorRes) = when (state) {
            ConnectionState.OK -> R.string.connection_ok to R.color.hm_connection_ok
            ConnectionState.STALE -> R.string.connection_stale to R.color.hm_connection_stale
            ConnectionState.DOWN -> R.string.connection_down to R.color.hm_connection_down
        }
        connectionText?.setText(labelRes)
        connectionText?.setTextColor(ContextCompat.getColor(context, colorRes))
        val dot = connectionDot ?: return
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(ContextCompat.getColor(context, colorRes))
        dot.background = g
    }

    private fun showLoading(loading: Boolean) {
        if (!hasLoadedOnce && loading) {
            loadingView?.visibility = View.VISIBLE
        } else {
            loadingView?.visibility = View.GONE
        }
    }

    companion object {
        private const val POLL_MS = 5_000L
    }
}
