package io.github.mzainkh.alfaaz.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mzainkh.alfaaz.R
import io.github.mzainkh.alfaaz.lyrics.LyricLine
import io.github.mzainkh.alfaaz.lyrics.Lyrics
import io.github.mzainkh.alfaaz.state.LyricsController
import io.github.mzainkh.alfaaz.state.LyricsStatus
import io.github.mzainkh.alfaaz.state.LyricsUiState

private const val OFFSET_STEP_MS = 500L

/** Lines kept above the active one while auto-scrolling. */
private const val LINES_ABOVE = 2

@Composable
fun LyricsTab(controller: LyricsController, hasAccess: Boolean) {
    val state by controller.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        if (!hasAccess) PermissionCard()
        if (state.trackKey != null) NowPlayingCard(state, controller)
        when (val lyrics = (state.status as? LyricsStatus.Ready)?.lyrics) {
            is Lyrics.Synced -> SyncedLyrics(lyrics.lines, state.activeIndex)
            is Lyrics.Plain -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                items(lyrics.lines) { LyricText(it, active = false) }
            }
            else -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(state.status.label()), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun NowPlayingCard(state: LyricsUiState, controller: LyricsController) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp)) {
            Text(
                state.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(state.artist, appName(state.sourcePackage)).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(stringResource(state.status.label()), style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = controller::refresh) { Text(stringResource(R.string.action_refresh)) }
                if ((state.status as? LyricsStatus.Ready)?.lyrics is Lyrics.Synced) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { controller.nudgeOffset(-OFFSET_STEP_MS) }) {
                        Text(stringResource(R.string.offset_earlier))
                    }
                    // Shows the current offset; tapping it resets.
                    TextButton(onClick = controller::resetOffset) {
                        Text(stringResource(R.string.offset_value, state.offsetMs / 1000f))
                    }
                    TextButton(onClick = { controller.nudgeOffset(OFFSET_STEP_MS) }) {
                        Text(stringResource(R.string.offset_later))
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, activeIndex: Int) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines, activeIndex) {
        // Leave the list alone while the user is scrolling it.
        if (!listState.isScrollInProgress) {
            listState.animateScrollToItem((activeIndex - LINES_ABOVE).coerceAtLeast(0))
        }
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp)) {
        itemsIndexed(lines) { index, line -> LyricText(line.text, index == activeIndex) }
    }
}

@Composable
private fun LyricText(text: String, active: Boolean) {
    val type = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    Text(
        text,
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        color = if (active) colors.primary else colors.onSurfaceVariant,
        fontWeight = if (active) FontWeight.Bold else null,
        textAlign = TextAlign.Center,
        style = if (active) type.titleLarge else type.bodyLarge,
    )
}

@StringRes
private fun LyricsStatus.label(): Int = when (this) {
    LyricsStatus.Idle -> R.string.status_nothing_playing
    LyricsStatus.Loading -> R.string.status_loading
    LyricsStatus.Failed -> R.string.status_failed
    is LyricsStatus.Ready -> when (lyrics) {
        is Lyrics.Synced -> R.string.status_synced
        is Lyrics.Plain -> R.string.status_plain
        Lyrics.Instrumental -> R.string.status_instrumental
        Lyrics.NotFound -> R.string.status_not_found
    }
}

private fun appName(packageName: String) = when (packageName) {
    "com.spotify.music" -> "Spotify"
    "com.google.android.apps.youtube.music" -> "YouTube Music"
    else -> packageName
}
