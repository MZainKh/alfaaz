package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.car.CarAction
import io.github.mzainkh.alfaaz.car.CarActionKind
import io.github.mzainkh.alfaaz.car.CarLabels
import io.github.mzainkh.alfaaz.car.CarMode
import io.github.mzainkh.alfaaz.car.PlayUi
import io.github.mzainkh.alfaaz.car.carModel
import io.github.mzainkh.alfaaz.car.carRows
import io.github.mzainkh.alfaaz.lyrics.LyricLine
import io.github.mzainkh.alfaaz.lyrics.Lyrics
import io.github.mzainkh.alfaaz.media.Transport
import io.github.mzainkh.alfaaz.state.LyricsStatus
import io.github.mzainkh.alfaaz.state.LyricsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarModelTest {
    private val labels = CarLabels("Playing", "Now", "Next", "Then", "Nothing", "Loading", "Failed", "None", "Instr", "Plain")
    private val synced = LyricsStatus.Ready(Lyrics.Synced(List(10) { LyricLine(it * 1000L, "L$it") }))
    private fun state(status: LyricsStatus = synced, index: Int = -1) =
        LyricsUiState("k", "Song", "Artist", status = status, activeIndex = index)

    private val everyState = listOf(LyricsUiState()) +
        listOf(LyricsStatus.Loading, LyricsStatus.Failed, LyricsStatus.Ready(Lyrics.NotFound)).map { state(it) } +
        (-1..9).map { state(index = it) }

    private val all = Transport(canPlay = true, canPause = true, canNext = true, canPrevious = true, canSeek = true)
    private val everyPlayer = listOf(null, PlayUi(true, all), PlayUi(false, all), PlayUi(true, Transport()))

    private fun model(state: LyricsUiState, play: PlayUi? = PlayUi(true, all), art: String? = null, shown: Boolean = true) =
        carModel(state, play, art, CarMode.FIXED, 4, shown, labels)

    @Test
    fun fixedScreenKeepsItsShapeInEveryState() {
        val shapes = everyState.flatMap { s ->
            everyPlayer.flatMap { play ->
                listOf(null, "art").map { art ->
                    val m = model(s, play, art)
                    m.rows.map { it.title } to m.actions.size
                }
            }
        }.toSet()
        assertEquals(setOf(listOf("Playing", "Now", "Next", "Then") to 2), shapes)
    }

    @Test
    fun fixedModeShowsCurrentAndUpcomingLines() {
        val rows = carRows(state(index = 3), CarMode.FIXED, 6, labels)
        assertEquals(listOf("Song — Artist", "L3", "L4", "L5"), rows.map { it.text })
    }

    @Test
    fun onlyTheCurrentLineIsHighlighted() {
        assertEquals(listOf(false, true, false, false), model(state(index = 3)).rows.map { it.highlight })
        assertTrue(model(state(index = -1)).rows.none { it.highlight })
        assertTrue(model(state(LyricsStatus.Loading)).rows.none { it.highlight })
    }

    @Test
    fun fixedModePutsStatusInTheNowRow() {
        assertEquals("Loading", carRows(state(LyricsStatus.Loading), CarMode.FIXED, 6, labels)[1].text)
        assertEquals("Plain", carRows(state(LyricsStatus.Ready(Lyrics.Plain(listOf("x")))), CarMode.FIXED, 6, labels)[1].text)
    }

    @Test
    fun playAndPauseSwapAndUnsupportedActionsAreDisabled() {
        assertEquals(
            listOf(CarAction(CarActionKind.PAUSE, true), CarAction(CarActionKind.NEXT, true)),
            model(state(), PlayUi(true, all)).actions,
        )
        assertEquals(
            listOf(CarAction(CarActionKind.PLAY, true), CarAction(CarActionKind.NEXT, true)),
            model(state(), PlayUi(false, all)).actions,
        )
        assertEquals(
            listOf(CarAction(CarActionKind.PLAY, false), CarAction(CarActionKind.NEXT, false)),
            model(LyricsUiState(), null).actions,
        )
    }

    @Test
    fun spinnerOnlyBeforeAnythingWasShown() {
        assertTrue(model(state(LyricsStatus.Loading), shown = false).loading)
        assertTrue(model(state(LyricsStatus.Loading), shown = false).rows.isEmpty())
        assertFalse(model(state(LyricsStatus.Loading), shown = true).loading)
        assertFalse(model(LyricsUiState(), shown = false).loading)
    }

    @Test
    fun rollingModePutsTheArtOnTheSongRow() {
        val rows = carModel(state(index = 4), null, "art", CarMode.ROLLING, 6, true, labels).rows
        assertEquals(listOf(true, false, false, false, false, false), rows.map { it.art })
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
