package io.github.mzainkh.alfaaz.ui

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mzainkh.alfaaz.AppGraph
import io.github.mzainkh.alfaaz.R
import io.github.mzainkh.alfaaz.lyrics.Lyrics
import io.github.mzainkh.alfaaz.media.Artwork
import io.github.mzainkh.alfaaz.media.PlayerCommands
import io.github.mzainkh.alfaaz.media.TrackPlaybackState
import io.github.mzainkh.alfaaz.media.Transport
import io.github.mzainkh.alfaaz.media.positionAt
import io.github.mzainkh.alfaaz.media.seekTarget
import io.github.mzainkh.alfaaz.state.LyricsController
import io.github.mzainkh.alfaaz.state.LyricsStatus
import io.github.mzainkh.alfaaz.state.LyricsUiState

private const val OFFSET_STEP_MS = 500L
private val DefaultAccent = Color(0xFFFFD54F)
private val BaseColor = Color(0xFF10343C)
private val Dim = Color.White.copy(alpha = 0.7f)

/** Everything the now-playing screen shows, read once so the pieces below stay simple. */
class NowPlayingUi(
    val state: LyricsUiState,
    val activeIndex: () -> Int,
    val playback: TrackPlaybackState?,
    val art: Artwork?,
    val controller: LyricsController,
    val commands: PlayerCommands,
)

@Composable
fun NowPlayingScreen(graph: AppGraph, hasAccess: Boolean, onSettings: () -> Unit) {
    val lyrics = graph.controller.state.collectAsStateWithLifecycle()
    val playback by graph.nowPlaying.playback.collectAsStateWithLifecycle()
    val artwork by graph.nowPlaying.artwork.collectAsStateWithLifecycle()
    val state = lyrics.value
    // The art may still belong to the previous song.
    val art = artwork?.takeIf { it.trackKey == state.trackKey }
    val ui = NowPlayingUi(state, { lyrics.value.activeIndex }, playback, art, graph.controller, graph.nowPlaying.commands)
    val accent by animateColorAsState(art?.accent?.let(::Color) ?: DefaultAccent, tween(600), label = "accent")

    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = accent, onPrimary = Color.Black)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val layout = nowPlayingLayout(maxWidth.value, maxHeight.value)
            Backdrop(art)
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                TopBar(ui, onSettings)
                if (!hasAccess) PermissionCard()
                when {
                    state.trackKey == null -> LyricsPane(ui, Modifier.weight(1f).fillMaxWidth())
                    layout == NowPlayingLayout.TWO_PANE -> Row(Modifier.weight(1f)) {
                        Column(Modifier.weight(0.45f).fillMaxHeight()) {
                            TrackHeader(ui, Modifier.weight(1f), large = true)
                            PlayerPanel(ui)
                        }
                        LyricsPane(ui, Modifier.weight(0.55f).fillMaxHeight())
                    }
                    else -> {
                        TrackHeader(ui)
                        LyricsPane(ui, Modifier.weight(1f).fillMaxWidth())
                        PlayerPanel(ui)
                    }
                }
            }
        }
    }
}

/** Progress, transport buttons and the timing offset. */
@Composable
fun PlayerPanel(ui: NowPlayingUi, modifier: Modifier = Modifier) {
    Column(modifier.padding(bottom = 8.dp)) {
        PlaybackBar(ui.playback, ui.commands)
        Controls(ui.playback, ui.commands)
        OffsetRow(ui.state, ui.controller)
    }
}

/** The album art blurred and darkened, so white text stays readable on any cover. */
@Composable
fun Backdrop(art: Artwork?) {
    Box(Modifier.fillMaxSize().background(BaseColor)) {
        Crossfade(art, animationSpec = tween(600), label = "backdrop") { current ->
            if (current != null) {
                Image(
                    remember(current) { current.backdrop.asImageBitmap() },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.45f), 1f to Color.Black.copy(alpha = 0.8f)),
            ),
        )
    }
}

@Composable
private fun TopBar(ui: NowPlayingUi, onSettings: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.app_name),
            Modifier.weight(1f),
            color = Dim,
            style = MaterialTheme.typography.titleMedium,
        )
        if (ui.state.trackKey != null) {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.action_more), tint = Color.White)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_refresh)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_refresh), null) },
                        onClick = {
                            menu = false
                            ui.controller.refresh()
                        },
                    )
                }
            }
        }
        IconButton(onClick = onSettings) {
            Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.action_settings), tint = Color.White)
        }
    }
}

/** Art, title, artist and album. [large] stacks a big cover above the text (two-pane layout). */
@Composable
fun TrackHeader(ui: NowPlayingUi, modifier: Modifier = Modifier, large: Boolean = false) {
    if (large) {
        Column(
            modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // As large as the space allows, never taller than it.
            ArtImage(
                ui.art,
                Modifier.weight(1f, fill = false).aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(20.dp)),
            )
            Spacer(Modifier.height(16.dp))
            TrackText(ui, center = true)
        }
    } else {
        Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtImage(ui.art, Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) { TrackText(ui, center = false) }
        }
    }
}

@Composable
private fun TrackText(ui: NowPlayingUi, center: Boolean) {
    val align = if (center) TextAlign.Center else TextAlign.Start
    val playback = ui.playback
    Text(
        ui.state.title,
        Modifier.fillMaxWidth(),
        color = Color.White,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
    )
    Text(
        listOfNotNull(ui.state.artist.ifBlank { null }, playback?.album).joinToString(" · "),
        Modifier.fillMaxWidth(),
        color = Dim,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
    )
    val app by rememberAppLabel(ui.state.sourcePackage)
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        app.icon?.let {
            Image(it, contentDescription = null, modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            listOf(app.name, stringResource(ui.state.status.label())).joinToString(" · "),
            color = Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtImage(art: Artwork?, modifier: Modifier) {
    if (art != null) {
        Image(
            remember(art) { art.image.asImageBitmap() },
            contentDescription = stringResource(R.string.album_art),
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Image(
            painterResource(R.drawable.art_placeholder),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

/** Synced lyrics, plain lyrics or a status message. */
@Composable
fun LyricsPane(ui: NowPlayingUi, modifier: Modifier = Modifier) {
    when (val lyrics = (ui.state.status as? LyricsStatus.Ready)?.lyrics) {
        is Lyrics.Synced -> {
            val playback = ui.playback
            val onSeek = if (playback?.transport?.canSeek == true) {
                { index: Int -> ui.commands.seekTo(seekTarget(lyrics.lines[index], ui.state.offsetMs, playback.durationMs)) }
            } else {
                null
            }
            BoxWithConstraints(modifier) {
                SyncedLyricsList(lyrics.lines, ui.activeIndex, onSeek, Modifier.fillMaxSize(), edgePadding = maxHeight * 0.4f)
            }
        }
        is Lyrics.Plain -> PlainLyricsList(lyrics.lines, modifier)
        else -> Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(ui.state.status.label()),
                color = Dim,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A thin bar that moves every frame while only the time labels recompose, once a second. */
@Composable
fun PlaybackBar(playback: TrackPlaybackState?, commands: PlayerCommands, modifier: Modifier = Modifier) {
    val track = playback ?: return
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(track) {
        now = SystemClock.elapsedRealtime()
        if (track.isPlaying) while (true) withFrameMillis { now = SystemClock.elapsedRealtime() }
    }
    val second by remember(track) { derivedStateOf { track.positionAt(now) / 1000 } }
    val accent = MaterialTheme.colorScheme.primary
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val duration = track.durationMs
    val seek = if (track.transport.canSeek && duration > 0) {
        Modifier.pointerInput(track) {
            detectTapGestures { tap ->
                val fraction = (tap.x / size.width).coerceIn(0f, 1f).let { if (rtl) 1f - it else it }
                commands.seekTo((fraction * duration).toLong())
            }
        }
    } else {
        Modifier
    }
    Column(modifier.padding(horizontal = 24.dp)) {
        Box(
            Modifier.fillMaxWidth().height(24.dp).then(seek).drawBehind {
                val fraction = if (duration > 0) track.positionAt(now).toFloat() / duration else 0f
                val y = size.height / 2
                val start = if (rtl) size.width else 0f
                val end = if (rtl) size.width * (1 - fraction) else size.width * fraction
                val stroke = 4.dp.toPx()
                drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
                drawLine(accent, Offset(start, y), Offset(end, y), stroke, StrokeCap.Round)
                drawCircle(accent, radius = 6.dp.toPx(), center = Offset(end, y))
            },
        )
        Row(Modifier.fillMaxWidth()) {
            val elapsed = second * 1000
            Text(formatTime(elapsed), color = Dim, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            if (duration > 0) {
                Text("-" + formatTime(duration - elapsed), color = Dim, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Previous, play or pause, next. Kept left to right in every language, like other players. */
@Composable
fun Controls(playback: TrackPlaybackState?, commands: PlayerCommands, modifier: Modifier = Modifier) {
    val transport = playback?.transport ?: Transport()
    val playing = playback?.isPlaying == true
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = commands::skipToPrevious, enabled = transport.canPrevious, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.ic_skip_previous), stringResource(R.string.action_previous), Modifier.size(32.dp), tint = Color.White)
            }
            FilledIconButton(
                onClick = { if (playing) commands.pause() else commands.play() },
                enabled = if (playing) transport.canPause else transport.canPlay,
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                    stringResource(if (playing) R.string.action_pause else R.string.action_play),
                    Modifier.size(36.dp),
                )
            }
            IconButton(onClick = commands::skipToNext, enabled = transport.canNext, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.ic_skip_next), stringResource(R.string.action_next), Modifier.size(32.dp), tint = Color.White)
            }
        }
    }
}

/** Earlier, the current offset (tap to reset) and later. Only synced lyrics have timing to adjust. */
@Composable
fun OffsetRow(state: LyricsUiState, controller: LyricsController, modifier: Modifier = Modifier) {
    if ((state.status as? LyricsStatus.Ready)?.lyrics !is Lyrics.Synced) return
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { controller.nudgeOffset(-OFFSET_STEP_MS) }) {
            Text(stringResource(R.string.offset_earlier), color = Dim)
        }
        TextButton(onClick = controller::resetOffset) {
            Text(stringResource(R.string.offset_value, state.offsetMs / 1000f), color = Color.White)
        }
        TextButton(onClick = { controller.nudgeOffset(OFFSET_STEP_MS) }) {
            Text(stringResource(R.string.offset_later), color = Dim)
        }
    }
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
