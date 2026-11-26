package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.lyrics.LrclibApi
import io.github.mzainkh.alfaaz.lyrics.LrclibClient
import io.github.mzainkh.alfaaz.lyrics.LrclibTrack
import io.github.mzainkh.alfaaz.lyrics.Lyrics
import io.github.mzainkh.alfaaz.lyrics.LyricsDao
import io.github.mzainkh.alfaaz.lyrics.LyricsEntity
import io.github.mzainkh.alfaaz.lyrics.LyricsRepository
import io.github.mzainkh.alfaaz.lyrics.TrackQuery
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class LyricsRepositoryTest {
    private var requests = 0
    private var offline = false
    private var answer: LrclibTrack? = LrclibTrack(trackName = "Song", syncedLyrics = "[00:01.00]a")
    private var time = 0L

    private val api = object : LrclibApi {
        override suspend fun get(track: String, artist: String, album: String?, durationSec: Long?): Response<LrclibTrack> {
            requests++
            if (offline) throw IOException("offline")
            return answer?.let { Response.success(it) } ?: Response.error(404, "".toResponseBody())
        }

        override suspend fun search(track: String?, artist: String?, q: String?) =
            Response.success(emptyList<LrclibTrack>())
    }
    private val dao = object : LyricsDao {
        val rows = mutableMapOf<String, LyricsEntity>()
        override suspend fun find(trackKey: String) = rows[trackKey]
        override suspend fun upsert(entity: LyricsEntity) { rows[entity.trackKey] = entity }
        override suspend fun setOffset(trackKey: String, offsetMs: Long) {
            rows[trackKey]?.let { rows[trackKey] = it.copy(offsetMs = offsetMs) }
        }
    }
    private val repository = LyricsRepository(LrclibClient(api), dao) { time }
    private val query = TrackQuery("Song", "Someone", null, 200)

    @Test
    fun fetchesOnceThenServesTheCache() = runTest {
        repository.lyrics(query)
        assert(repository.lyrics(query).lyrics is Lyrics.Synced)
        assertEquals(1, requests)
    }

    @Test
    fun remembersAMissForADay() = runTest {
        answer = null
        assertEquals(Lyrics.NotFound, repository.lyrics(query).lyrics)
        time = 23 * 3_600_000L
        repository.lyrics(query)
        assertEquals(1, requests)
        time = 25 * 3_600_000L
        repository.lyrics(query)
        assertEquals(2, requests)
    }

    @Test
    fun refreshAsksAgainAndKeepsTheOffset() = runTest {
        repository.lyrics(query)
        repository.setOffset(query.cacheKey, 500)
        answer = LrclibTrack(trackName = "Song", instrumental = true)
        val refreshed = repository.lyrics(query, refresh = true)
        assertEquals(Lyrics.Instrumental, refreshed.lyrics)
        assertEquals(500, refreshed.offsetMs)
    }

    @Test
    fun failedOrEmptyRefreshKeepsCachedLyrics() = runTest {
        repository.lyrics(query)
        offline = true
        assert(repository.lyrics(query, refresh = true).lyrics is Lyrics.Synced)
        offline = false
        answer = null
        assert(repository.lyrics(query, refresh = true).lyrics is Lyrics.Synced)
    }

    @Test(expected = IOException::class)
    fun reportsFailureWhenNothingIsCached() = runTest {
        offline = true
        repository.lyrics(query)
    }
}
