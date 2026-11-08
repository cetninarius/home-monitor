package com.custom.vox.homemonitor.data.appearance

enum class CardStyle(val id: String) {
    GLASS("glass"),
    SOLID("solid"),
    MINIMAL("minimal");

    companion object {
        fun fromId(raw: String?): CardStyle =
            entries.firstOrNull { it.id == raw } ?: GLASS
    }
}

data class AppearanceState(
    val backgroundId: String = AppearanceStore.DEFAULT_BACKGROUND_ID,
    val dimPercent: Int = AppearanceStore.DEFAULT_DIM_PERCENT,
    val cardStyle: CardStyle = CardStyle.GLASS,
)

data class BackgroundOption(
    val id: String,
    val label: String,
    val kind: Kind,
) {
    enum class Kind { BUNDLED, CUSTOM }
}
