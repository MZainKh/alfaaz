package io.github.mzainkh.alfaaz.media

import android.graphics.Bitmap
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
    val transport: Transport = Transport(),
) {
    val query: TrackQuery
        get() = TrackQuery(title, artist, album, ((durationMs + 500) / 1000).takeIf { it > 0 })
}

/**
 * Filled by the notification listener. [playback] emits when the song changes, pauses, resumes or
 * seeks; equal states are dropped. [art] says which image the player offers for the track.
 * [commands] reach the player while the listener is connected.
 */
open class PlayerHub {
    private val current = MutableStateFlow<TrackPlaybackState?>(null)
    val playback: StateFlow<TrackPlaybackState?> = current.asStateFlow()

    private val currentArt = MutableStateFlow<ArtRequest<ArtSource<Bitmap>>?>(null)
    val art: StateFlow<ArtRequest<ArtSource<Bitmap>>?> = currentArt.asStateFlow()

    @Volatile
    private var attached: PlayerCommands? = null
    private val target get() = attached ?: PlayerCommands.None

    val commands: PlayerCommands = object : PlayerCommands {
        override fun play() = target.play()
        override fun pause() = target.pause()
        override fun skipToNext() = target.skipToNext()
        override fun skipToPrevious() = target.skipToPrevious()
        override fun seekTo(positionMs: Long) = target.seekTo(positionMs)
    }

    fun publish(state: TrackPlaybackState?, art: ArtRequest<ArtSource<Bitmap>>? = null) {
        current.value = state
        currentArt.value = art
    }

    fun attach(commands: PlayerCommands) {
        attached = commands
    }

    /** Only detaches [commands] if they are still the attached ones. */
    fun detach(commands: PlayerCommands) {
        if (attached === commands) attached = null
    }
}

object NowPlayingRepository : PlayerHub()
