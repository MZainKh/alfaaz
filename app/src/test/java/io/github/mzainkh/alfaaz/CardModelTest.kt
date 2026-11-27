package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.lyrics.LyricLine
import io.github.mzainkh.alfaaz.lyrics.Lyrics
import io.github.mzainkh.alfaaz.media.CardLabels
import io.github.mzainkh.alfaaz.media.CardText
import io.github.mzainkh.alfaaz.media.Transport
import io.github.mzainkh.alfaaz.media.cardActions
import io.github.mzainkh.alfaaz.media.cardText
import io.github.mzainkh.alfaaz.state.LyricsStatus
import io.github.mzainkh.alfaaz.state.LyricsUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class CardModelTest {
    private val labels = CardLabels("App", "Nothing", "Loading", "Failed", "None", "Instr", "Plain")
    private val synced = LyricsStatus.Ready(Lyrics.Synced(List(3) { LyricLine(it * 1000L, "L$it") }))
    private fun text(status: LyricsStatus, index: Int = -1) =
        cardText(LyricsUiState("k", "Song", "Artist", status = status, activeIndex = index), labels)

    private fun singing(line: String, next: String = "") = CardText(line, "Song · Artist", line, next)

    @Test
    fun showsTheCurrentLineBrightAndOverTheArt() {
        assertEquals(singing("L1", "L2"), text(synced, 1))
        assertEquals(singing("L2"), text(synced, 2))
    }

    @Test
    fun marksTheWaitBeforeTheFirstLine() {
        assertEquals(singing("♪", "L0"), text(synced, -1))
    }

    @Test
    fun leavesOutAMissingArtist() {
        val state = LyricsUiState("k", "Song", status = synced, activeIndex = 0)
        assertEquals("Song", cardText(state, labels).subtitle)
    }

    @Test
    fun withoutSyncedLyricsShowsTheSongAndWhyAndKeepsTheArtClean() {
        assertEquals(CardText("Song", "Loading"), text(LyricsStatus.Loading))
        assertEquals(CardText("Song", "Failed"), text(LyricsStatus.Failed))
        assertEquals(CardText("Song", "Plain"), text(LyricsStatus.Ready(Lyrics.Plain(listOf("x")))))
        assertEquals(CardText("Song", "Instr"), text(LyricsStatus.Ready(Lyrics.Instrumental)))
        assertEquals(CardText("Song", "None"), text(LyricsStatus.Ready(Lyrics.NotFound)))
        assertEquals(CardText("App", "Nothing"), cardText(LyricsUiState(), labels))
    }

    @Test
    fun offersSkippingAndSeekingOnlyWhenThePlayerCan() {
        val none = Transport.from(cardActions(Transport()))
        assertEquals(Transport(canPlay = true, canPause = true), none)
        val all = Transport(canPlay = true, canPause = true, canNext = true, canPrevious = true, canSeek = true)
        assertEquals(all, Transport.from(cardActions(all)))
    }
}
