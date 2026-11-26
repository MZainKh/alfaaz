package io.github.mzainkh.alfaaz.media

import android.media.session.PlaybackState
import io.github.mzainkh.alfaaz.lyrics.LyricLine
import kotlinx.coroutines.flow.StateFlow

/** Commands for the followed player. Calls return at once; the player acts on them shortly after. */
interface PlayerCommands {
    fun play()
    fun pause()
    fun skipToNext()
    fun skipToPrevious()
    fun seekTo(positionMs: Long)

    /** Used while no player can be reached. */
    object None : PlayerCommands {
        override fun play() = Unit
        override fun pause() = Unit
        override fun skipToNext() = Unit
        override fun skipToPrevious() = Unit
        override fun seekTo(positionMs: Long) = Unit
    }
}

/** What the followed player accepts, read from its [PlaybackState] actions. */
data class Transport(
    val canPlay: Boolean = false,
    val canPause: Boolean = false,
    val canNext: Boolean = false,
    val canPrevious: Boolean = false,
    val canSeek: Boolean = false,
) {
    companion object {
        fun from(actions: Long): Transport {
            fun has(action: Long) = actions and action != 0L
            val toggles = has(PlaybackState.ACTION_PLAY_PAUSE)
            return Transport(
                canPlay = toggles || has(PlaybackState.ACTION_PLAY),
                canPause = toggles || has(PlaybackState.ACTION_PAUSE),
                canNext = has(PlaybackState.ACTION_SKIP_TO_NEXT),
                canPrevious = has(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
                canSeek = has(PlaybackState.ACTION_SEEK_TO),
            )
        }
    }
}

/** The followed track and a way to control its player. */
interface NowPlaying {
    val playback: StateFlow<TrackPlaybackState?>
    val commands: PlayerCommands
}

/** Where a tap on [line] sends the player: the moment that line becomes the active one. */
fun seekTarget(line: LyricLine, offsetMs: Long, durationMs: Long): Long {
    val target = (line.timestampMs + offsetMs).coerceAtLeast(0)
    return if (durationMs > 0) target.coerceAtMost(durationMs) else target
}
