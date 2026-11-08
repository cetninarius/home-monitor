package com.custom.vox.homemonitor

import com.custom.vox.homemonitor.data.media.SessionMediaReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YoutubeIdExtractTest {

    @Test
    fun extractsFromCommonCastForms() {
        assertEquals("dQw4w9WgXcQ", SessionMediaReader.extractYoutubeVideoId("dQw4w9WgXcQ"))
        assertEquals(
            "dQw4w9WgXcQ",
            SessionMediaReader.extractYoutubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
        )
        assertEquals(
            "dQw4w9WgXcQ",
            SessionMediaReader.extractYoutubeVideoId("yt:video:dQw4w9WgXcQ"),
        )
        assertEquals(
            "dQw4w9WgXcQ",
            SessionMediaReader.extractYoutubeVideoId("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"),
        )
        assertNull(SessionMediaReader.extractYoutubeVideoId(null))
        assertNull(SessionMediaReader.extractYoutubeVideoId(""))
    }
}
