package io.github.mzainkh.alfaaz.lyrics

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

private const val MISS_TTL_MS = 24 * 60 * 60 * 1000L

data class StoredLyrics(val lyrics: Lyrics, val offsetMs: Long)

class LyricsRepository(
    private val client: LrclibClient,
    private val dao: LyricsDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** LRCLIB asks clients to send requests one at a time. */
    private val network = Mutex()

    /** Cached lyrics when usable, without touching the network. */
    suspend fun cached(query: TrackQuery): StoredLyrics? =
        dao.find(query.cacheKey)?.takeIf { it.isUsable() }?.toStored()

    /**
     * Cached lyrics when usable, otherwise a fetch. A failed or empty fetch never replaces lyrics
     * already cached. Throws [IOException] when the network is needed, fails, and nothing is cached.
     */
    suspend fun lyrics(query: TrackQuery, refresh: Boolean = false): StoredLyrics {
        val cached = dao.find(query.cacheKey)
        if (cached != null && !refresh && cached.isUsable()) return cached.toStored()
        val kept = cached?.takeUnless { it.isMiss() }
        val found = try {
            network.withLock { client.find(query) }
        } catch (e: IOException) {
            return kept?.toStored() ?: throw e
        }
        if (found == null && kept != null) return kept.toStored()

        val entity = LyricsEntity(
            trackKey = query.cacheKey,
            synced = found?.syncedLyrics,
            plain = found?.plainLyrics,
            instrumental = found?.instrumental == true,
            fetchedAt = now(),
            // Read again: the offset may have been changed while the request was in flight.
            offsetMs = dao.find(query.cacheKey)?.offsetMs ?: 0,
        )
        dao.upsert(entity)
        return entity.toStored()
    }

    suspend fun setOffset(trackKey: String, offsetMs: Long) = dao.setOffset(trackKey, offsetMs)

    private fun LyricsEntity.isMiss() = synced == null && plain == null && !instrumental

    /** A miss is retried after a day. */
    private fun LyricsEntity.isUsable() = !isMiss() || now() - fetchedAt < MISS_TTL_MS

    private fun LyricsEntity.toStored() = StoredLyrics(LrcParser.toLyrics(synced, plain, instrumental), offsetMs)
}
