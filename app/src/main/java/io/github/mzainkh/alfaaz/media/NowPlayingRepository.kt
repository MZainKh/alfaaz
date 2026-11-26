package io.github.mzainkh.alfaaz.media

import io.github.mzainkh.alfaaz.lyrics.TrackQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TrackPlaybackState(
    val packageName: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val isPlaying: Boolean,
    /** Position at [updateElapsedMs], on the `SystemClock.elapsedRealtime` timeline. */
    val positionMs: Long,
    val updateElapsedMs: Long,
    val speed: Float,
) {
    val query: TrackQuery
        get() = TrackQuery(title, artist, album, ((durationMs + 500) / 1000).takeIf { it > 0 })
}

/** Emits when the song changes, pauses, resumes or seeks; equal states are dropped by the StateFlow. */
object NowPlayingRepository {
    private val current = MutableStateFlow<TrackPlaybackState?>(null)
    val state: StateFlow<TrackPlaybackState?> = current.asStateFlow()

    fun publish(state: TrackPlaybackState?) {
        current.value = state
    }
}
