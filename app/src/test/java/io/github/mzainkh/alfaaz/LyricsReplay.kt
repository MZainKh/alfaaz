package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.lyrics.LrclibTrack
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** One song as a player might report it, and what should be found. No expected title means nothing should be. */
@Serializable
data class EvalCase(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSec: Long? = null,
    val expectTitle: String? = null,
    val expectArtist: String? = null,
)

/** A saved LRCLIB track. The words are never kept, only whether there were any. */
@Serializable
data class SavedTrack(
    val trackName: String = "",
    val artistName: String = "",
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val synced: Boolean = false,
    val plain: Boolean = false,
) {
    /** What the client is served: the saved facts with stand-in words. */
    fun served() = LrclibTrack(
        trackName,
        artistName,
        duration,
        instrumental,
        plainLyrics = "first stand-in line\nsecond stand-in line".takeIf { plain },
        syncedLyrics = "[00:01.00] first stand-in line\n[00:05.00] second stand-in line".takeIf { synced },
    )

    companion object {
        fun of(track: LrclibTrack) = SavedTrack(
            track.trackName,
            track.artistName,
            track.duration,
            track.instrumental,
            synced = !track.syncedLyrics.isNullOrBlank(),
            plain = !track.plainLyrics.isNullOrBlank(),
        )
    }
}

/** LRCLIB's answer to one request. */
@Serializable
data class SavedReply(val request: String, val status: Int, val tracks: List<SavedTrack> = emptyList())

/** The statuses worth keeping: found, rejected and not found. Anything else is a failed recording. */
private val KEPT_STATUS = setOf(200, 400, 404)
private const val PAUSE_MS = 1_000L
private const val ATTEMPTS = 3

private val lenient = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

private fun HttpUrl.isSearch() = encodedPath.endsWith("/search")

/** The same request always has the same key, whatever order its parameters come in. */
fun requestKey(url: HttpUrl): String =
    url.encodedPath + "?" + url.queryParameterNames.sorted().joinToString("&") { "$it=${url.queryParameter(it)}" }

/** Asks the real service, one request at a time and no faster than once a second. */
class Lrclib(private val userAgent: String) : (HttpUrl) -> SavedReply {
    private val http = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()

    @Synchronized
    override fun invoke(url: HttpUrl): SavedReply {
        val real = url.newBuilder().scheme("https").host("lrclib.net").port(443).build()
        var failure: IOException? = null
        repeat(ATTEMPTS) { attempt ->
            Thread.sleep(PAUSE_MS * (1 + attempt * 5))
            try {
                http.newCall(Request.Builder().url(real).header("User-Agent", userAgent).build()).execute().use { response ->
                    if (response.code !in KEPT_STATUS) throw IOException("LRCLIB HTTP ${response.code}")
                    val body = response.body?.string().orEmpty()
                    val tracks = when {
                        response.code != 200 -> emptyList()
                        url.isSearch() -> lenient.decodeFromString(ListSerializer(LrclibTrack.serializer()), body)
                        else -> listOf(lenient.decodeFromString(LrclibTrack.serializer(), body))
                    }
                    return SavedReply(requestKey(url), response.code, tracks.map(SavedTrack::of))
                }
            } catch (e: IOException) {
                failure = e
            }
        }
        throw failure ?: IOException("LRCLIB did not answer")
    }
}

/**
 * Stands in for LRCLIB. A saved reply is served if there is one; otherwise [upstream] is asked, and
 * without one the request is noted in [missing].
 */
class Replay(
    private val saved: Map<String, SavedReply>,
    private val upstream: ((HttpUrl) -> SavedReply)? = null,
) : Dispatcher() {
    val used = ConcurrentHashMap<String, SavedReply>()
    val missing: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Set when the real service could not be asked, so that a recording is not trusted. */
    @Volatile
    var upstreamFailure: String? = null

    override fun dispatch(request: RecordedRequest): MockResponse {
        val url = request.requestUrl ?: return MockResponse().setResponseCode(501)
        val key = requestKey(url)
        val reply = used[key] ?: saved[key] ?: try {
            upstream?.invoke(url)
        } catch (e: IOException) {
            upstreamFailure = e.javaClass.simpleName
            return MockResponse().setResponseCode(503)
        }
        if (reply == null) {
            missing += key
            // Not 404: that would read as an honest miss.
            return MockResponse().setResponseCode(501)
        }
        used[key] = reply
        val response = MockResponse().setResponseCode(reply.status).setHeader("Content-Type", "application/json")
        if (reply.status != 200) return response.setBody("{}")
        val tracks = reply.tracks.map { it.served() }
        return response.setBody(
            if (url.isSearch()) {
                Json.encodeToString(ListSerializer(LrclibTrack.serializer()), tracks)
            } else {
                Json.encodeToString(LrclibTrack.serializer(), tracks.single())
            },
        )
    }
}
