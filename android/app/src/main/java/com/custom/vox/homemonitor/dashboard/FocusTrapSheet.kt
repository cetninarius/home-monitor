package com.custom.vox.homemonitor.dashboard

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/**
 * Honors nextFocus*Id exclusively. If an explicit neighbor is set, geometric
 * focus search is never allowed to skip middle items (e.g. option1 → footer).
 */
class FocusTrapSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    override fun focusSearch(focused: View?, direction: Int): View? {
        if (focused == null) return super.focusSearch(null, direction)
        val explicitId = when (direction) {
            View.FOCUS_UP -> focused.nextFocusUpId
            View.FOCUS_DOWN -> focused.nextFocusDownId
            View.FOCUS_LEFT -> focused.nextFocusLeftId
            View.FOCUS_RIGHT -> focused.nextFocusRightId
            else -> View.NO_ID
        }
        if (explicitId != View.NO_ID) {
            val target = findViewById<View>(explicitId)
            if (target != null && target !== focused && canTakeFocus(target)) {
                return target
            }
            // Explicit self-pin or missing target → stay put (no geometric leap).
            if (explicitId == focused.id || target == null || target === focused) {
                return focused
            }
        }
        // No explicit link — stay inside the sheet; never leak to the dashboard.
        val next = super.focusSearch(focused, direction)
        return if (next != null && isSheetDescendant(next)) next else focused
    }

    private fun canTakeFocus(v: View): Boolean {
        if (!v.isShown || !v.isFocusable || !v.isEnabled) return false
        var cur: View? = v
        while (cur != null) {
            if (cur.visibility != View.VISIBLE) return false
            if (cur === this) return true
            cur = cur.parent as? View
        }
        return false
    }

    private fun isSheetDescendant(v: View): Boolean {
        var cur: View? = v
        while (cur != null) {
            if (cur === this) return true
            cur = cur.parent as? View
        }
        return false
    }
}
