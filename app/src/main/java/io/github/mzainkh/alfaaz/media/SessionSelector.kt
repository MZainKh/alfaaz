package io.github.mzainkh.alfaaz.media

object SessionSelector {
    private val preferred = listOf("com.spotify.music", "com.google.android.apps.youtube.music")

    /** Alfaaz's own car media card mirrors the player, so it must never be followed. */
    fun <T> others(sessions: List<T>, ownPackage: String, packageName: (T) -> String): List<T> =
        sessions.filter { packageName(it) != ownPackage }

    /**
     * A playing session wins, Spotify then YouTube Music first. With nothing playing, the previous
     * choice stays; failing that, a paused session of a preferred app.
     */
    fun <T> select(sessions: List<T>, previous: T?, packageName: (T) -> String, isPlaying: (T) -> Boolean): T? {
        fun rank(session: T) = preferred.indexOf(packageName(session)).let { if (it < 0) preferred.size else it }
        return sessions.filter(isPlaying).minByOrNull(::rank)
            ?: previous?.takeIf { it in sessions }
            ?: sessions.filter { packageName(it) in preferred }.minByOrNull(::rank)
    }
}
