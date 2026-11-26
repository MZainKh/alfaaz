package io.github.mzainkh.alfaaz

import android.media.session.PlaybackState
import io.github.mzainkh.alfaaz.lyrics.LyricLine
import io.github.mzainkh.alfaaz.media.PlayerCommands
import io.github.mzainkh.alfaaz.media.PlayerHub
import io.github.mzainkh.alfaaz.media.Transport
import io.github.mzainkh.alfaaz.media.seekTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingCommands : PlayerCommands {
    val calls = mutableListOf<String>()
    override fun play() { calls += "play" }
    override fun pause() { calls += "pause" }
    override fun skipToNext() { calls += "next" }
    override fun skipToPrevious() { calls += "previous" }
    override fun seekTo(positionMs: Long) { calls += "seek $positionMs" }
}

class PlayerTest {
    @Test
    fun readsWhatThePlayerAccepts() {
        assertEquals(Transport(), Transport.from(0))
        assertEquals(
            Transport(canPlay = true, canPause = true, canNext = true, canPrevious = true, canSeek = true),
            Transport.from(
                PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO,
            ),
        )
        assertEquals(Transport(canPlay = true, canPause = true), Transport.from(PlaybackState.ACTION_PLAY_PAUSE))
    }

    @Test
    fun seeksToWhereTheLineBecomesActive() {
        val line = LyricLine(10_000, "x")
        assertEquals(10_000, seekTarget(line, 0, 60_000))
        assertEquals(10_500, seekTarget(line, 500, 60_000))
        assertEquals(0, seekTarget(LyricLine(200, "x"), -1_000, 60_000))
        assertEquals(60_000, seekTarget(line, 55_000, 60_000))
        assertEquals(65_000, seekTarget(line, 55_000, 0))
    }

    @Test
    fun commandsReachOnlyTheAttachedPlayer() {
        val hub = PlayerHub()
        val first = RecordingCommands()
        val second = RecordingCommands()
        hub.commands.play()
        hub.attach(first)
        hub.commands.skipToNext()
        hub.attach(second)
        hub.detach(first)
        hub.commands.seekTo(1_000)
        hub.detach(second)
        hub.commands.pause()
        assertEquals(listOf("next"), first.calls)
        assertEquals(listOf("seek 1000"), second.calls)
    }
}
