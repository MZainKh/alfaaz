package io.github.mzainkh.alfaaz.ui

import io.github.mzainkh.alfaaz.lyrics.BREAK_MARK
import io.github.mzainkh.alfaaz.lyrics.LyricLine

/** A pause at least this long is shown as dots that fill up. */
private const val MIN_GAP_MS = 4_000L

/** A stretch with nothing sung, in the lyrics' own time. */
data class Gap(val startMs: Long, val endMs: Long) {
    fun progress(positionMs: Long): Float = ((positionMs - startMs).toFloat() / (endMs - startMs)).coerceIn(0f, 1f)
}

private fun gap(startMs: Long, endMs: Long) = if (endMs - startMs >= MIN_GAP_MS) Gap(startMs, endMs) else null

/** The wait before the first line, if it is long enough to show. */
fun introGap(lines: List<LyricLine>): Gap? = lines.firstOrNull()?.let { gap(0, it.timestampMs) }

/** The pause marked by line [index], if it is a long enough break with an end. */
fun lineGap(lines: List<LyricLine>, index: Int): Gap? {
    val line = lines[index]
    if (line.text != BREAK_MARK) return null
    val end = lines.getOrNull(index + 1)?.timestampMs ?: return null
    return gap(line.timestampMs, end)
}

/** How bright a line is, [distance] lines after (or, when negative, before) the active one. */
fun lineAlpha(distance: Int): Float = when {
    distance == 0 -> 1f
    distance < 0 -> 0.25f
    distance == 1 -> 0.5f
    distance == 2 -> 0.4f
    else -> 0.3f
}
