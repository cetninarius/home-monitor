package com.custom.vox.homemonitor.media

data class SmartTubeSessionSnapshot(
    val listenerEnabled: Boolean,
    val sessionFound: Boolean,
    val playbackState: String,
    val playbackStateCode: Int,
    val title: String?,
    val artist: String?,
    val durationMs: Long,
    val reportedPositionMs: Long,
    val estimatedPositionMs: Long?,
    val positionSource: String,
    val albumArtUri: String?,
    val artUriReachable: Boolean?,
    val artUriStatus: String?,
    val error: String? = null,
) {
    fun toDiagText(): String = buildString {
        appendLine("MEDIA SESSION DIAG (org.smarttube.stable)")
        appendLine("listenerEnabled=$listenerEnabled")
        appendLine("sessionFound=$sessionFound")
        if (error != null) appendLine("error=$error")
        appendLine("state=$playbackState ($playbackStateCode)")
        appendLine("title=${title ?: "—"}")
        appendLine("artist=${artist ?: "—"}")
        appendLine("durationMs=$durationMs")
        appendLine("reportedPosMs=$reportedPositionMs")
        appendLine("estimatedPosMs=${estimatedPositionMs ?: "—"} ($positionSource)")
        appendLine("artUri=${albumArtUri ?: "—"}")
        appendLine("artReachable=${artUriReachable ?: "—"} ${artUriStatus ?: ""}")
    }
}
