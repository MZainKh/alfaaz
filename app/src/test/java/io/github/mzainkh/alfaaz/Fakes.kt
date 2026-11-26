package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.lyrics.LrclibApi
import io.github.mzainkh.alfaaz.lyrics.LrclibTrack
import io.github.mzainkh.alfaaz.lyrics.LyricsDao
import io.github.mzainkh.alfaaz.lyrics.LyricsEntity
import kotlinx.coroutines.delay
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.io.IOException

class FakeApi : LrclibApi {
    var requests = 0
    var offline = false
    var delayMs = 0L

    /** Null answers 404. */
    var answer: (String) -> LrclibTrack? = { LrclibTrack(trackName = it, syncedLyrics = "[00:01.00]$it") }

    override suspend fun get(track: String, artist: String, album: String?, durationSec: Long?): Response<LrclibTrack> {
        requests++
        delay(delayMs)
        if (offline) throw IOException("offline")
        return answer(track)?.let { Response.success(it) } ?: Response.error(404, "".toResponseBody())
    }

    override suspend fun search(track: String?, artist: String?, q: String?) =
        Response.success(emptyList<LrclibTrack>())
}

class FakeDao : LyricsDao {
    val rows = mutableMapOf<String, LyricsEntity>()

    override suspend fun find(trackKey: String) = rows[trackKey]

    override suspend fun upsert(entity: LyricsEntity) {
        rows[entity.trackKey] = entity
    }

    override suspend fun setOffset(trackKey: String, offsetMs: Long) {
        rows[trackKey]?.let { rows[trackKey] = it.copy(offsetMs = offsetMs) }
    }
}
