package io.github.mzainkh.alfaaz.lyrics

import kotlinx.coroutines.delay
import retrofit2.Response
import java.io.IOException
import kotlin.math.abs
import kotlin.random.Random

private const val DURATION_TOLERANCE_SEC = 3
private const val DEFAULT_RETRY_AFTER_SEC = 5L
private const val MAX_RETRY_AFTER_SEC = 60L
private val VALID_DURATION_SEC = 1L..3600L

data class TrackQuery(val title: String, val artist: String, val album: String?, val durationSec: Long?) {
    val cacheKey: String =
        "${QueryNormalizer.artist(artist)}|${QueryNormalizer.title(title)}|${durationSec ?: 0}".lowercase()
}

class LrclibClient(
    private val api: LrclibApi,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val jitter: () -> Double = { Random.nextDouble(0.3) },
) {
    /** The best match, or null when LRCLIB has none. Throws [IOException] if it cannot be asked. */
    suspend fun find(query: TrackQuery): LrclibTrack? {
        val title = QueryNormalizer.title(query.title)
        val artist = QueryNormalizer.artist(query.artist)
        val duration = query.durationSec?.takeIf { it in VALID_DURATION_SEC }
        body { api.get(title, artist, query.album, duration) }?.let { return it }
        search(title, artist, duration)?.let { return it }

        val shortTitle = QueryNormalizer.withoutFeaturing(title)
        val shortArtist = QueryNormalizer.withoutFeaturing(artist)
        if (shortTitle == title && shortArtist == artist) return null
        return search(shortTitle, shortArtist, duration)
    }

    private suspend fun search(title: String, artist: String, duration: Long?): LrclibTrack? =
        best(body { api.search(track = title, artist = artist) }.orEmpty(), title, duration)
            ?: best(body { api.search(q = "$artist $title") }.orEmpty(), title, duration)

    /** Same title and about the same length; synced lyrics win, then the closest duration. */
    private fun best(results: List<LrclibTrack>, title: String, duration: Long?): LrclibTrack? {
        val wanted = title.lowercase()
        fun distance(track: LrclibTrack) =
            if (duration == null || track.duration == null) 0.0 else abs(track.duration - duration)
        return results
            .filter {
                val name = QueryNormalizer.title(it.trackName).lowercase()
                name.isNotEmpty() && (name in wanted || wanted in name) && distance(it) <= DURATION_TOLERANCE_SEC
            }
            .minWithOrNull(compareBy<LrclibTrack> { it.syncedLyrics.isNullOrBlank() }.thenBy(::distance))
    }

    /** The response body, or null for 404. One retry after a 429 that asks for a short wait. */
    private suspend fun <T> body(request: suspend () -> Response<T>): T? {
        var response = request()
        if (response.code() == 429) {
            val seconds = response.headers()["Retry-After"]?.toLongOrNull() ?: DEFAULT_RETRY_AFTER_SEC
            if (seconds <= MAX_RETRY_AFTER_SEC) {
                pause((seconds * 1000 * (1 + jitter())).toLong())
                response = request()
            }
        }
        return when {
            response.isSuccessful -> response.body()
            response.code() == 404 -> null
            else -> throw IOException("LRCLIB HTTP ${response.code()}")
        }
    }
}
