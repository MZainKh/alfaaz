package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.ui.centerDelta
import io.github.mzainkh.alfaaz.ui.formatTime
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun formatsPlaybackTimes() {
        assertEquals("0:00", formatTime(-5))
        assertEquals("0:09", formatTime(9_999))
        assertEquals("3:05", formatTime(185_000))
        assertEquals("1:02:03", formatTime(3_723_000))
    }

    @Test
    fun centresItemsInTheViewport() {
        // Viewport 0..1000: an item at 0..100 must move up by 450 to be centred.
        assertEquals(-450f, centerDelta(0, 100, 0, 1_000))
        assertEquals(250f, centerDelta(700, 100, 0, 1_000))
        // A top content padding of 200 shifts the viewport start to -200.
        assertEquals(0f, centerDelta(250, 100, -200, 800))
    }
}
