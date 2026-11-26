package io.github.mzainkh.alfaaz.media

import io.github.mzainkh.alfaaz.lyrics.LyricLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.ceil

private const val MIN_WAIT_MS = 20L

/**
 * Emits the active line index, then sleeps until the next line is due. Completes when nothing more can
 * change; collect with `flatMapLatest` so that a pause, seek or new song restarts it.
 */
fun lyricsTicker(
    playback: TrackPlaybackState,
    lines: List<LyricLine>,
    offsetMs: Long,
    elapsedMs: () -> Long,
): Flow<Int> = flow {
    while (true) {
        val position = playback.positionAt(elapsedMs())
        val index = lines.activeIndex(position, offsetMs)
        emit(index)
        if (!playback.isPlaying || playback.speed <= 0f) return@flow
        val due = (lines.getOrNull(index + 1) ?: return@flow).timestampMs + offsetMs
        // A line past the end of the track is never reached.
        if (playback.durationMs in 1..<due) return@flow
        delay(ceil((due - position) / playback.speed.toDouble()).toLong().coerceAtLeast(MIN_WAIT_MS))
    }
}
