package com.custom.vox.homemonitor.dashboard

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.custom.vox.homemonitor.R
import com.custom.vox.homemonitor.data.appearance.AppearanceState
import com.custom.vox.homemonitor.data.appearance.AppearanceStore
import com.custom.vox.homemonitor.data.appearance.BackgroundImageStore
import com.custom.vox.homemonitor.data.appearance.CardStyle

/**
 * Applies background image, dim scrim, and card-style panel drawables.
 * Does not touch layout weights/animations.
 */
class AppearanceApplier(
    private val root: View,
    private val backgrounds: BackgroundImageStore,
) {
    private var customBitmap: Bitmap? = null
    private var lastBackgroundId: String? = null
    private var lastDim: Int? = null
    private var lastStyle: CardStyle? = null

    fun apply(state: AppearanceState) {
        applyBackground(state.backgroundId)
        applyDim(state.dimPercent)
        applyCardStyle(state.cardStyle)
    }

    fun release() {
        val iv = root.findViewById<ImageView>(R.id.backgroundImage)
        iv?.setImageDrawable(null)
        customBitmap?.let { if (!it.isRecycled) it.recycle() }
        customBitmap = null
        lastBackgroundId = null
        lastDim = null
        lastStyle = null
    }

    private fun applyBackground(id: String) {
        if (id == lastBackgroundId) return
        val iv = root.findViewById<ImageView>(R.id.backgroundImage) ?: return
        val previous = customBitmap
        customBitmap = null

        val bundledRes = backgrounds.bundledRes(id)
        if (bundledRes != 0) {
            iv.setImageResource(bundledRes)
        } else {
            val bmp = backgrounds.loadDisplayBitmap(id)
            if (bmp != null) {
                customBitmap = bmp
                iv.setImageDrawable(BitmapDrawable(root.resources, bmp))
            } else {
                iv.setImageResource(R.drawable.bg_baghdad)
            }
        }
        lastBackgroundId = id
        if (previous != null && previous !== customBitmap && !previous.isRecycled) {
            previous.recycle()
        }
    }

    private fun applyDim(percent: Int) {
        val dim = AppearanceStore.normalizeDim(percent)
        val scrim = root.findViewById<View>(R.id.backgroundScrim) ?: return
        // Always paint — even when value unchanged after restore — so preview is honest.
        scrim.setBackgroundColor(Color.argb((dim * 255) / 100, 0, 0, 0))
        scrim.alpha = 1f
        scrim.visibility = View.VISIBLE
        lastDim = dim
    }

    private fun applyCardStyle(style: CardStyle) {
        val drawable = when (style) {
            CardStyle.GLASS -> R.drawable.bg_panel_glass
            CardStyle.SOLID -> R.drawable.bg_panel_solid
            CardStyle.MINIMAL -> R.drawable.bg_panel_minimal
        }
        // Prefer stable widget root ids (not slot children index — NP/compact can reshuffle).
        PANEL_ROOT_IDS.forEach { id ->
            root.findViewById<View>(id)?.setBackgroundResource(drawable)
        }
        // Fallback: first child of each slot in case a root id is missing.
        SLOT_IDS.forEach { slotId ->
            val slot = root.findViewById<ViewGroup>(slotId) ?: return@forEach
            if (slot.childCount == 0) return@forEach
            val child = slot.getChildAt(0)
            if (PANEL_ROOT_IDS.none { child.id == it }) {
                child.setBackgroundResource(drawable)
            }
        }
        lastStyle = style
    }

    companion object {
        private val PANEL_ROOT_IDS = intArrayOf(
            R.id.calendarRoot,
            R.id.todoRoot,
            R.id.servicesRoot,
            R.id.weatherRoot,
            R.id.nowPlayingRoot,
            R.id.wf1Root,
            R.id.adminHintBanner,
        )
        private val SLOT_IDS = intArrayOf(
            R.id.calendarSlot,
            R.id.todoSlot,
            R.id.servicesSlot,
            R.id.weatherSlot,
            R.id.rightSlot,
            R.id.nowPlayingSlot,
        )
    }
}
