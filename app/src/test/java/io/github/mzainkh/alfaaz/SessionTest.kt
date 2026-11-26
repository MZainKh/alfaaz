package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.media.SessionSelector
import io.github.mzainkh.alfaaz.media.TrackFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionTest {
    private data class Session(val pkg: String, val playing: Boolean = false)

    private val spotify = "com.spotify.music"
    private val youTubeMusic = "com.google.android.apps.youtube.music"
    private val other = "some.player"

    private fun select(vararg sessions: Session, previous: Session? = null) =
        SessionSelector.select(sessions.toList(), previous, { it.pkg }, { it.playing })

    @Test
    fun aPlayingSessionBeatsAPausedPreferredOne() {
        assertEquals(other, select(Session(spotify), Session(other, true))?.pkg)
    }

    @Test
    fun spotifyThenYouTubeMusicWinTies() {
        val all = arrayOf(Session(other, true), Session(youTubeMusic, true), Session(spotify, true))
        assertEquals(spotify, select(*all)?.pkg)
        assertEquals(youTubeMusic, select(all[0], all[1])?.pkg)
    }

    @Test
    fun keepsThePreviousChoiceWhileEverythingIsPaused() {
        val paused = Session(other)
        assertEquals(paused, select(Session(spotify), paused, previous = paused))
        assertEquals(spotify, select(Session(spotify), previous = paused)?.pkg)
    }

    @Test
    fun ignoresPausedSessionsOfOtherApps() {
        assertNull(select(Session(other)))
        assertNull(select())
    }

    @Test
    fun filtersAdsPodcastsAndIncompleteTracks() {
        assert(TrackFilter.isSong("spotify:track:1", "Song", "Artist"))
        assert(TrackFilter.isSong(null, "Song", "Artist"))
        assert(!TrackFilter.isSong("spotify:ad:1", "Song", "Artist"))
        assert(!TrackFilter.isSong("spotify:episode:1", "Show", "Host"))
        assert(!TrackFilter.isSong(null, "Advertisement", "Brand"))
        assert(!TrackFilter.isSong(null, "Song", " "))
        assert(!TrackFilter.isSong(null, null, "Artist"))
    }
}
