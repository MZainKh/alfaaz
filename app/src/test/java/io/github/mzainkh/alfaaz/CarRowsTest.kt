package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.car.CarLabels
import io.github.mzainkh.alfaaz.car.CarMode
import io.github.mzainkh.alfaaz.car.carRows
import io.github.mzainkh.alfaaz.lyrics.LyricLine
import io.github.mzainkh.alfaaz.lyrics.Lyrics
import io.github.mzainkh.alfaaz.state.LyricsStatus
import io.github.mzainkh.alfaaz.state.LyricsUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class CarRowsTest {
    private val labels = CarLabels("Playing", "Now", "Next", "Then", "Nothing", "Loading", "Failed", "None", "Instr", "Plain")
    private val synced = LyricsStatus.Ready(Lyrics.Synced(List(10) { LyricLine(it * 1000L, "L$it") }))
    private fun state(status: LyricsStatus = synced, index: Int = -1) =
        LyricsUiState("k", "Song", "Artist", status = status, activeIndex = index)

    private val everyState = listOf(LyricsUiState()) +
        listOf(LyricsStatus.Loading, LyricsStatus.Failed, LyricsStatus.Ready(Lyrics.NotFound)).map { state(it) } +
        (-1..9).map { state(index = it) }

    @Test
    fun fixedModeNeverChangesRowTitlesOrCount() {
        val titles = everyState.map { s -> carRows(s, CarMode.FIXED, 6, labels).map { it.title } }.toSet()
        assertEquals(setOf(listOf("Playing", "Now", "Next", "Then")), titles)
    }

    @Test
    fun fixedModeShowsCurrentAndUpcomingLines() {
        val rows = carRows(state(index = 3), CarMode.FIXED, 6, labels)
        assertEquals(listOf("Song — Artist", "L3", "L4", "L5"), rows.map { it.text })
    }

    @Test
    fun fixedModePutsStatusInTheNowRow() {
        assertEquals("Loading", carRows(state(LyricsStatus.Loading), CarMode.FIXED, 6, labels)[1].text)
        assertEquals("Plain", carRows(state(LyricsStatus.Ready(Lyrics.Plain(listOf("x")))), CarMode.FIXED, 6, labels)[1].text)
    }

    @Test
    fun rollingModeKeepsOneLineAboveTheActiveOne() {
        val titles = carRows(state(index = 4), CarMode.ROLLING, 6, labels).map { it.title }
        assertEquals(listOf("Song", "L3", "► L4", "L5", "L6", "L7"), titles)
    }

    @Test
    fun rollingWindowStaysFullAtBothEnds() {
        assertEquals("L0", carRows(state(index = -1), CarMode.ROLLING, 6, labels)[1].title)
        val end = carRows(state(index = 9), CarMode.ROLLING, 6, labels).map { it.title }
        assertEquals(listOf("Song", "L5", "L6", "L7", "L8", "► L9"), end)
    }

    @Test
    fun neverExceedsTheHostLimit() {
        for (mode in CarMode.entries) for (s in everyState) assert(carRows(s, mode, 3, labels).size <= 3)
    }
}
