package io.github.mzainkh.alfaaz.media

import android.media.session.PlaybackState
import io.github.mzainkh.alfaaz.lyrics.BREAK_MARK
import io.github.mzainkh.alfaaz.lyrics.Lyrics
import io.github.mzainkh.alfaaz.state.LyricsStatus
import io.github.mzainkh.alfaaz.state.LyricsUiState

/** The two texts on Android Auto's media card. */
data class CardText(val line: String, val next: String)

data class CardLabels(
    val appName: String,
    val nothingPlaying: String,
    val loading: String,
    val failed: String,
    val notFound: String,
    val instrumental: String,
    val unsynced: String,
)

/** The line being sung and the one after it; without synced lyrics, the song and why there are none. */
fun cardText(state: LyricsUiState, labels: CardLabels): CardText {
    fun song(why: String) = CardText(state.title, why)
    return when (val status = state.status) {
        LyricsStatus.Idle -> CardText(labels.appName, labels.nothingPlaying)
        LyricsStatus.Loading -> song(labels.loading)
        LyricsStatus.Failed -> song(labels.failed)
        is LyricsStatus.Ready -> when (val lyrics = status.lyrics) {
            is Lyrics.Synced -> CardText(
                lyrics.lines.getOrNull(state.activeIndex)?.text ?: BREAK_MARK,
                lyrics.lines.getOrNull(state.activeIndex + 1)?.text.orEmpty(),
            )
            is Lyrics.Plain -> song(labels.unsynced)
            Lyrics.Instrumental -> song(labels.instrumental)
            Lyrics.NotFound -> song(labels.notFound)
        }
    }
}

/** Play and pause are always offered, as a media app must; the rest follow the real player. */
fun cardActions(transport: Transport): Long {
    var actions = PlaybackState.ACTION_PLAY or
        PlaybackState.ACTION_PAUSE or
        PlaybackState.ACTION_PLAY_PAUSE or
        PlaybackState.ACTION_STOP or
        PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or
        PlaybackState.ACTION_PLAY_FROM_SEARCH
    if (transport.canNext) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
    if (transport.canPrevious) actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
    if (transport.canSeek) actions = actions or PlaybackState.ACTION_SEEK_TO
    return actions
}
