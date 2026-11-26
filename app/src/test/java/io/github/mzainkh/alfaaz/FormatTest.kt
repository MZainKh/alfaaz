package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.ui.NowPlayingLayout.SINGLE
import io.github.mzainkh.alfaaz.ui.NowPlayingLayout.TWO_PANE
import io.github.mzainkh.alfaaz.ui.anchorDelta
import io.github.mzainkh.alfaaz.ui.controlsPinned
import io.github.mzainkh.alfaaz.ui.formatTime
import io.github.mzainkh.alfaaz.ui.nowPlayingLayout
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
    fun bringsItemsToTheAnchor() {
        // Viewport 0..1000: an item at 0..100 must move up by 450 to be centred.
        assertEquals(-450f, anchorDelta(0, 100, 0, 1_000, 0.5f))
        assertEquals(250f, anchorDelta(700, 100, 0, 1_000, 0.5f))
        // A top content padding of 300 puts the anchor, 30% down, at offset 0.
        assertEquals(0f, anchorDelta(-50, 100, -300, 700, 0.3f), 0.001f)
    }

    @Test
    fun picksTwoPanesForWideOrSidewaysWindows() {
        assertEquals(SINGLE, nowPlayingLayout(412f, 915f)) // phone, upright
        assertEquals(TWO_PANE, nowPlayingLayout(915f, 412f)) // phone, sideways
        assertEquals(TWO_PANE, nowPlayingLayout(600f, 960f)) // small tablet or unfolded phone
        assertEquals(SINGLE, nowPlayingLayout(412f, 440f)) // half of a split screen
        assertEquals(SINGLE, nowPlayingLayout(599f, 900f))
    }

    @Test
    fun hidesControlsOnlyWhileSyncedLyricsPlay() {
        assertEquals(false, controlsPinned(playing = true, synced = true, touchExploration = false))
        assertEquals(true, controlsPinned(playing = false, synced = true, touchExploration = false))
        assertEquals(true, controlsPinned(playing = true, synced = false, touchExploration = false))
        assertEquals(true, controlsPinned(playing = true, synced = true, touchExploration = true))
    }
}
