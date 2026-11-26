package io.github.mzainkh.alfaaz.media

import io.github.mzainkh.alfaaz.lyrics.LyricLine

/** The position interpolated from the last update the player reported, without polling it. */
fun TrackPlaybackState.positionAt(elapsedMs: Long): Long {
    val position =
        if (isPlaying) positionMs + ((elapsedMs - updateElapsedMs) * speed.toDouble()).toLong() else positionMs
    return if (durationMs > 0) position.coerceIn(0, durationMs) else position.coerceAtLeast(0)
}

/** Index of the last line due at [positionMs], or -1 before the first one. A positive offset delays lines. */
fun List<LyricLine>.activeIndex(positionMs: Long, offsetMs: Long): Int {
    val target = positionMs - offsetMs
    val found = binarySearchBy(target) { it.timestampMs }
    if (found < 0) return -found - 2
    var last = found
    while (last < lastIndex && this[last + 1].timestampMs == target) last++
    return last
}
