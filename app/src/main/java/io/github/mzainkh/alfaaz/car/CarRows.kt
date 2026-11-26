package io.github.mzainkh.alfaaz.car

import io.github.mzainkh.alfaaz.lyrics.LyricLine
import io.github.mzainkh.alfaaz.lyrics.Lyrics
import io.github.mzainkh.alfaaz.state.LyricsStatus
import io.github.mzainkh.alfaaz.state.LyricsUiState

enum class CarMode {
    /** Lyric lines are row titles and the window scrolls. Needs app-driven refresh. */
    ROLLING,

    /** Row titles and count never change, so every update counts as a template refresh. */
    FIXED,
}

data class RowSpec(val title: String, val text: String? = null)

data class CarLabels(
    val nowPlaying: String,
    val now: String,
    val next: String,
    val then: String,
    val nothingPlaying: String,
    val loading: String,
    val failed: String,
    val notFound: String,
    val instrumental: String,
    val unsynced: String,
)

private const val GAP = "♪"
private const val MARKER = "► "

fun carRows(state: LyricsUiState, mode: CarMode, limit: Int, labels: CarLabels): List<RowSpec> {
    val lyrics = (state.status as? LyricsStatus.Ready)?.lyrics
    val lines = (lyrics as? Lyrics.Synced)?.lines.orEmpty()
    val message = when (state.status) {
        LyricsStatus.Idle -> labels.nothingPlaying
        LyricsStatus.Loading -> labels.loading
        LyricsStatus.Failed -> labels.failed
        is LyricsStatus.Ready -> when (lyrics) {
            is Lyrics.Plain -> labels.unsynced
            Lyrics.Instrumental -> labels.instrumental
            Lyrics.NotFound -> labels.notFound
            else -> GAP
        }
    }
    val rows = when (mode) {
        CarMode.FIXED -> fixedRows(state, lines, message, labels)
        CarMode.ROLLING -> rollingRows(state, lines, message, limit, labels)
    }
    return rows.take(limit.coerceAtLeast(1))
}

private fun fixedRows(state: LyricsUiState, lines: List<LyricLine>, message: String, labels: CarLabels): List<RowSpec> {
    val song = listOf(state.title, state.artist).filter { it.isNotBlank() }.joinToString(" — ").ifEmpty { GAP }
    val i = state.activeIndex
    fun line(at: Int) = lines.getOrNull(at)?.text ?: GAP
    return listOf(
        RowSpec(labels.nowPlaying, song),
        RowSpec(labels.now, if (lines.isEmpty()) message else line(i)),
        RowSpec(labels.next, line(i + 1)),
        RowSpec(labels.then, line(i + 2)),
    )
}

private fun rollingRows(
    state: LyricsUiState,
    lines: List<LyricLine>,
    message: String,
    limit: Int,
    labels: CarLabels,
): List<RowSpec> {
    if (state.status == LyricsStatus.Idle) return listOf(RowSpec(labels.nothingPlaying))
    val song = RowSpec(state.title.ifBlank { GAP }, state.artist.ifBlank { null })
    if (lines.isEmpty()) return listOf(song, RowSpec(message))
    val size = (limit - 1).coerceIn(1, lines.size.coerceAtLeast(1))
    // One line of context above the active one; the window stays full at the end of the song.
    val start = (state.activeIndex - 1).coerceIn(0, lines.size - size)
    return listOf(song) + (start until start + size).map {
        RowSpec(if (it == state.activeIndex) MARKER + lines[it].text else lines[it].text)
    }
}
