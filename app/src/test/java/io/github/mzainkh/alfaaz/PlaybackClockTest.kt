package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.lyrics.LyricLine
import io.github.mzainkh.alfaaz.media.TrackPlaybackState
import io.github.mzainkh.alfaaz.media.activeIndex
import io.github.mzainkh.alfaaz.media.positionAt
import org.junit.Assert.assertEquals
import org.junit.Test

fun playback(
    isPlaying: Boolean = true,
    positionMs: Long = 10_000,
    updateElapsedMs: Long = 0,
    speed: Float = 1f,
    durationMs: Long = 60_000,
) = TrackPlaybackState("pkg", "Song", "Artist", null, durationMs, isPlaying, positionMs, updateElapsedMs, speed)

class PlaybackClockTest {
    private val lines = listOf(1_000L, 2_000L, 2_000L, 5_000L).map { LyricLine(it, "x") }

    @Test
    fun advancesWithSpeedOnlyWhilePlaying() {
        assertEquals(12_000, playback().positionAt(2_000))
        assertEquals(13_000, playback(speed = 1.5f).positionAt(2_000))
        assertEquals(10_000, playback(isPlaying = false).positionAt(2_000))
    }

    @Test
    fun staysInsideTheTrack() {
        assertEquals(60_000, playback().positionAt(900_000))
        assertEquals(0, playback(positionMs = 0, updateElapsedMs = 500).positionAt(0))
        assertEquals(910_000, playback(durationMs = 0).positionAt(900_000))
    }

    @Test
    fun findsTheActiveLine() {
        assertEquals(-1, lines.activeIndex(999, 0))
        assertEquals(0, lines.activeIndex(1_000, 0))
        assertEquals(2, lines.activeIndex(2_000, 0))
        assertEquals(2, lines.activeIndex(4_999, 0))
        assertEquals(3, lines.activeIndex(60_000, 0))
        assertEquals(-1, emptyList<LyricLine>().activeIndex(0, 0))
    }

    @Test
    fun aPositiveOffsetDelaysLines() {
        assertEquals(-1, lines.activeIndex(1_000, 500))
        assertEquals(0, lines.activeIndex(1_500, 500))
        assertEquals(0, lines.activeIndex(500, -500))
    }
}
