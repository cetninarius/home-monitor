package com.custom.vox.homemonitor.widgets

import android.view.View
import android.view.ViewGroup

/**
 * Minimal widget contract for the dashboard shell.
 * Each widget owns its UI, refresh lifecycle, and loading/error presentation.
 */
interface DashboardWidget {
    val id: String

    fun attach(parent: ViewGroup): View

    fun onStart()

    fun onStop()

    /** Preferred focus target for D-pad entry into this widget, if any. */
    fun preferredFocusView(): View? = null
}
