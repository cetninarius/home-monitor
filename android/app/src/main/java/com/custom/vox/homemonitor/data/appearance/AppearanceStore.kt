package com.custom.vox.homemonitor.data.appearance

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Appearance prefs. Absent keys intentionally mean the shipping defaults
 * (Baghdad + 40% dim + Glass) so existing installs are not rewritten on upgrade.
 */
class AppearanceStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun snapshot(): AppearanceState = AppearanceState(
        backgroundId = backgroundId(),
        dimPercent = dimPercent(),
        cardStyle = cardStyle(),
    )

    fun backgroundId(): String {
        val raw = prefs.getString(KEY_BACKGROUND_ID, null)?.takeIf { it.isNotBlank() }
            ?: return DEFAULT_BACKGROUND_ID
        return normalizeBackgroundId(raw)
    }

    fun dimPercent(): Int {
        if (!prefs.contains(KEY_DIM_PERCENT)) return DEFAULT_DIM_PERCENT
        return normalizeDim(prefs.getInt(KEY_DIM_PERCENT, DEFAULT_DIM_PERCENT))
    }

    fun cardStyle(): CardStyle =
        CardStyle.fromId(prefs.getString(KEY_CARD_STYLE, null))

    fun setBackgroundId(id: String) {
        prefs.edit().putString(KEY_BACKGROUND_ID, id.trim()).commit()
    }

    fun setDimPercent(percent: Int) {
        prefs.edit().putInt(KEY_DIM_PERCENT, normalizeDim(percent)).commit()
    }

    fun setCardStyle(style: CardStyle) {
        prefs.edit().putString(KEY_CARD_STYLE, style.id).commit()
    }

    fun update(
        backgroundId: String? = null,
        dimPercent: Int? = null,
        cardStyle: CardStyle? = null,
    ) {
        val ed = prefs.edit()
        var changed = false
        if (backgroundId != null) {
            ed.putString(KEY_BACKGROUND_ID, backgroundId.trim())
            changed = true
        }
        if (dimPercent != null) {
            ed.putInt(KEY_DIM_PERCENT, normalizeDim(dimPercent))
            changed = true
        }
        if (cardStyle != null) {
            ed.putString(KEY_CARD_STYLE, cardStyle.id)
            changed = true
        }
        if (changed) ed.commit()
    }

    /** Restore shipping defaults without clearing custom uploaded files. */
    fun restoreDefaults() {
        prefs.edit()
            .putString(KEY_BACKGROUND_ID, DEFAULT_BACKGROUND_ID)
            .putInt(KEY_DIM_PERCENT, DEFAULT_DIM_PERCENT)
            .putString(KEY_CARD_STYLE, CardStyle.GLASS.id)
            .commit()
    }

    fun observe(): Flow<AppearanceState> = callbackFlow {
        trySend(snapshot())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(snapshot())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun publicConfig(custom: List<BackgroundOption>): Map<String, Any?> {
        val state = snapshot()
        return mapOf(
            "backgroundId" to state.backgroundId,
            "dimPercent" to state.dimPercent,
            "cardStyle" to state.cardStyle.id,
            "dimOptions" to DIM_OPTIONS.toList(),
            "cardStyles" to CardStyle.entries.map { it.id },
            "bundled" to BUNDLED.map { mapOf("id" to it.id, "label" to it.label) },
            "custom" to custom.map { mapOf("id" to it.id, "label" to it.label) },
        )
    }

    companion object {
        const val PREFS = "home_monitor_appearance"
        const val KEY_BACKGROUND_ID = "background_id"
        const val KEY_DIM_PERCENT = "dim_percent"
        const val KEY_CARD_STYLE = "card_style"

        const val DEFAULT_BACKGROUND_ID = "bundled:baghdad"
        const val DEFAULT_DIM_PERCENT = 40

        val DIM_OPTIONS = intArrayOf(0, 20, 40, 60)

        val BUNDLED = listOf(
            BackgroundOption("bundled:baghdad", "Baghdad", BackgroundOption.Kind.BUNDLED),
            BackgroundOption("bundled:sunset", "Sunset Valley", BackgroundOption.Kind.BUNDLED),
            BackgroundOption("bundled:jungle", "Jungle Convoy", BackgroundOption.Kind.BUNDLED),
        )

        fun normalizeDim(percent: Int): Int =
            DIM_OPTIONS.minByOrNull { kotlin.math.abs(it - percent) } ?: DEFAULT_DIM_PERCENT

        fun isBundledId(id: String): Boolean = id.startsWith("bundled:")

        fun isCustomId(id: String): Boolean = id.startsWith("custom:")

        /** Remap removed Dusk/Ember gradient ids to Baghdad. */
        fun normalizeBackgroundId(id: String): String = when (id) {
            "bundled:dusk", "bundled:ember" -> DEFAULT_BACKGROUND_ID
            else -> id
        }

        /**
         * Returns the on-disk file id for a custom background, or null if unsafe.
         * Rejects path traversal and non-alphanumeric ids.
         */
        fun customFileId(id: String): String? {
            if (!isCustomId(id)) return null
            val raw = id.removePrefix("custom:")
            if (raw.isBlank()) return null
            if (raw.contains('/') || raw.contains('\\') || raw.contains("..")) return null
            if (!SAFE_FILE_ID.matches(raw)) return null
            return raw
        }

        private val SAFE_FILE_ID = Regex("^[A-Za-z0-9_-]{1,64}$")
    }
}
