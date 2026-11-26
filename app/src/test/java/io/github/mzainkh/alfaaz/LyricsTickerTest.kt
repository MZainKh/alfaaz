@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.lyrics.LyricLine
import io.github.mzainkh.alfaaz.media.lyricsTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsTickerTest {
    private val lines = listOf(11_000L, 13_000L, 20_000L).map { LyricLine(it, "x") }

    @Test
    fun emitsEachLineExactlyWhenItIsDue() = runTest {
        val seen = lyricsTicker(playback(), lines, 0) { currentTime }.map { currentTime to it }.toList()
        assertEquals(listOf(0L to -1, 1_000L to 0, 3_000L to 1, 10_000L to 2), seen)
    }

    @Test
    fun respectsSpeedAndOffset() = runTest {
        val seen = lyricsTicker(playback(speed = 2f), lines, 1_000) { currentTime }.map { currentTime to it }.toList()
        assertEquals(listOf(0L to -1, 1_000L to 0, 2_000L to 1, 5_500L to 2), seen)
    }

    @Test
    fun emitsOnceWhenPaused() = runTest {
        val paused = playback(isPlaying = false, positionMs = 14_000)
        assertEquals(listOf(1), lyricsTicker(paused, lines, 0) { currentTime }.toList())
        assertEquals(0L, currentTime)
    }

    @Test
    fun stopsBeforeLinesPastTheEndOfTheTrack() = runTest {
        val short = playback(durationMs = 15_000)
        assertEquals(listOf(-1, 0, 1), lyricsTicker(short, lines, 0) { currentTime }.toList())
    }

    @Test
    fun throttleKeepsAGapAndAlwaysDeliversTheLatestValue() = runTest {
        val bursts = flow {
            listOf(1, 2, 3, 4).forEach {
                emit(it)
                delay(100)
            }
        }
        val seen = bursts.throttleLatest(1_000).map { currentTime to it }.toList()
        assertEquals(listOf(0L to 1, 1_000L to 4), seen)
    }
}
