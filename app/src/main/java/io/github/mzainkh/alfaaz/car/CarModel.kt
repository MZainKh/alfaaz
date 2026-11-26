package io.github.mzainkh.alfaaz.car

import io.github.mzainkh.alfaaz.lyrics.LyricLine
import io.github.mzainkh.alfaaz.lyrics.Lyrics
import io.github.mzainkh.alfaaz.media.TrackPlaybackState
import io.github.mzainkh.alfaaz.media.Transport
import io.github.mzainkh.alfaaz.state.LyricsStatus
import io.github.mzainkh.alfaaz.state.LyricsUiState

enum class CarMode {
    /** Lyric lines are row titles and the window scrolls. Needs app-driven refresh. */
    ROLLING,

    /**
     * A pane whose title, row count, row titles and action count never change, so every update counts
     * as a refresh of the same screen.
     */
    FIXED,
}

/** [highlight] tints the text; [art] puts the album image on the row (rolling mode). */
data class RowSpec(val title: String, val text: String? = null, val highlight: Boolean = false, val art: Boolean = false)

enum class CarActionKind { PLAY, PAUSE, NEXT }

data class CarAction(val kind: CarActionKind, val enabled: Boolean)

/** Only what the car screen shows, so screens can drop playback updates that change nothing. */
data class PlayUi(val playing: Boolean, val transport: Transport)

fun TrackPlaybackState.playUi() = PlayUi(isPlaying, transport)

/** Everything one car template shows. [art] is null when the placeholder should be shown. */
data class CarModel<A>(
    val mode: CarMode,
    val rows: List<RowSpec>,
    val art: A?,
    val actions: List<CarAction>,
    val loading: Boolean,
)

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
const val FIXED_ROWS = 4

/**
 * [shownContent] says whether the screen already showed lyrics or a status. Until then a loading
 * state shows a spinner, which is free to replace; afterwards loading stays a row message.
 */
fun <A> carModel(
    state: LyricsUiState,
    play: PlayUi?,
    art: A?,
    mode: CarMode,
    limit: Int,
    shownContent: Boolean,
    labels: CarLabels,
): CarModel<A> {
    val loading = !shownContent && state.status == LyricsStatus.Loading
    val rows = if (loading) emptyList() else carRows(state, mode, limit, labels)
    return CarModel(mode, rows, art, actions(play), loading)
}

/** Always two actions, so the screen keeps its shape: the one the player can't do is disabled. */
private fun actions(play: PlayUi?): List<CarAction> {
    val transport = play?.transport ?: Transport()
    val toggle = if (play?.playing == true) {
        CarAction(CarActionKind.PAUSE, transport.canPause)
    } else {
        CarAction(CarActionKind.PLAY, play != null && transport.canPlay)
    }
    return listOf(toggle, CarAction(CarActionKind.NEXT, transport.canNext))
}

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
    val current = if (lines.isEmpty()) RowSpec(labels.now, message) else RowSpec(labels.now, line(i), highlight = i >= 0)
    return listOf(
        RowSpec(labels.nowPlaying, song),
        current,
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
    val song = RowSpec(state.title.ifBlank { GAP }, state.artist.ifBlank { null }, art = true)
    if (lines.isEmpty()) return listOf(song, RowSpec(message))
    val size = (limit - 1).coerceIn(1, lines.size.coerceAtLeast(1))
    // One line of context above the active one; the window stays full at the end of the song.
    val start = (state.activeIndex - 1).coerceIn(0, lines.size - size)
    return listOf(song) + (start until start + size).map {
        RowSpec(if (it == state.activeIndex) MARKER + lines[it].text else lines[it].text)
    }
}
