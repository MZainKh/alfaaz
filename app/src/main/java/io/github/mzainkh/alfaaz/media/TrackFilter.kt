package io.github.mzainkh.alfaaz.media

/** Rejects what cannot have lyrics: ads, podcast episodes and sessions without a title and artist. */
object TrackFilter {
    private val nonSongIds = listOf("spotify:ad:", "spotify:episode:")

    fun isSong(mediaId: String?, title: String?, artist: String?): Boolean =
        !title.isNullOrBlank() &&
            !artist.isNullOrBlank() &&
            !title.equals("Advertisement", ignoreCase = true) &&
            nonSongIds.none { mediaId?.startsWith(it) == true }
}
