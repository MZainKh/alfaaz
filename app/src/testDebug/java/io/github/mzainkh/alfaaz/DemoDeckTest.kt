package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.media.positionAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDeckTest {
    private var clock = 0L
    private val deck = DemoDeck { clock }
    private val position get() = deck.now.value.playback.positionAt(clock)

    @Test
    fun pauseFreezesAndPlayResumes() {
        clock = 5_000
        deck.pause()
        clock = 9_000
        assertFalse(deck.now.value.playback.isPlaying)
        assertEquals(5_000, position)
        assertNull(deck.remainingMs())
        deck.play()
        clock = 10_000
        assertEquals(6_000, position)
    }

    @Test
    fun skipsAndWrapsAround() {
        deck.skipToPrevious()
        assertEquals(demoSongs.lastIndex, deck.now.value.index)
        deck.skipToNext()
        assertEquals(0, deck.now.value.index)
        clock = 4_000
        deck.skipToPrevious()
        assertEquals(0, deck.now.value.index)
        assertEquals(0, position)
    }

    @Test
    fun seeksAndMovesOnAtTheEnd() {
        deck.seekTo(1_000)
        assertEquals(1_000, position)
        clock = deck.remainingMs()!!
        deck.finishIfDone()
        assertEquals(1, deck.now.value.index)
        assertTrue(deck.now.value.playback.transport.canSeek)
    }
}
