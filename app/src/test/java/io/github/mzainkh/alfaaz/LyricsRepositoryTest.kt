package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.lyrics.LrclibClient
import io.github.mzainkh.alfaaz.lyrics.LrclibTrack
import io.github.mzainkh.alfaaz.lyrics.Lyrics
import io.github.mzainkh.alfaaz.lyrics.LyricsRepository
import io.github.mzainkh.alfaaz.lyrics.TrackQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class LyricsRepositoryTest {
    private val api = FakeApi()
    private var time = 0L
    private val repository = LyricsRepository(LrclibClient(api), FakeDao()) { time }
    private val query = TrackQuery("Song", "Someone", null, 200)

    @Test
    fun fetchesOnceThenServesTheCache() = runTest {
        repository.lyrics(query)
        assert(repository.lyrics(query).lyrics is Lyrics.Synced)
        assertEquals(1, api.requests)
    }

    @Test
    fun remembersAMissForADay() = runTest {
        api.answer = { null }
        assertEquals(Lyrics.NotFound, repository.lyrics(query).lyrics)
        time = 23 * 3_600_000L
        repository.lyrics(query)
        assertEquals(1, api.requests)
        time = 25 * 3_600_000L
        repository.lyrics(query)
        assertEquals(2, api.requests)
    }

    @Test
    fun refreshAsksAgainAndKeepsTheOffset() = runTest {
        repository.lyrics(query)
        repository.setOffset(query.cacheKey, 500)
        api.answer = { LrclibTrack(trackName = it, instrumental = true) }
        val refreshed = repository.lyrics(query, refresh = true)
        assertEquals(Lyrics.Instrumental, refreshed.lyrics)
        assertEquals(500, refreshed.offsetMs)
    }

    @Test
    fun failedOrEmptyRefreshKeepsCachedLyrics() = runTest {
        repository.lyrics(query)
        api.offline = true
        assert(repository.lyrics(query, refresh = true).lyrics is Lyrics.Synced)
        api.offline = false
        api.answer = { null }
        assert(repository.lyrics(query, refresh = true).lyrics is Lyrics.Synced)
    }

    @Test(expected = IOException::class)
    fun reportsFailureWhenNothingIsCached() = runTest {
        api.offline = true
        repository.lyrics(query)
    }
}
