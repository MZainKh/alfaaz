package io.github.mzainkh.alfaaz.ui

import android.os.Build
import android.os.SystemClock
import android.text.TextDirectionHeuristics
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mzainkh.alfaaz.lyrics.BREAK_MARK
import io.github.mzainkh.alfaaz.lyrics.LyricLine
import io.github.mzainkh.alfaaz.media.TrackPlaybackState
import io.github.mzainkh.alfaaz.media.positionAt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

/** After the user scrolls the lyrics, auto-scroll waits this long before following the song again. */
private const val RESUME_AFTER_MS = 3_000L

/** How far down the list the active line sits. */
private const val ANCHOR = 0.3f

/** Lines further than this from the active one all look the same. */
private const val FAR = 4
private const val BLUR_PER_LINE_DP = 1.2f

/** Brightness while the user is reading around. */
private const val BROWSING_ALPHA = 0.6f

/**
 * Synced lyrics in the manner of a music player: the active line is sharp near the top, and the others
 * dim and blur with distance. [activeIndex] is read lazily, so only lines whose look changes redraw.
 * [onSeek] is null when the player can't seek. [height] is the height of the list.
 */
@Composable
fun SyncedLyricsList(
    lines: List<LyricLine>,
    activeIndex: () -> Int,
    onSeek: ((Int) -> Unit)?,
    playback: TrackPlaybackState?,
    offsetMs: Long,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var holding by remember { mutableStateOf(false) }
    val intro = remember(lines) { introGap(lines) }
    // The intro dots take a row of their own above the first line.
    val shift = if (intro != null) 1 else 0
    val rtl = remember(lines) { lines.firstOrNull { it.text != BREAK_MARK }?.text?.isRightToLeft() == true }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collectLatest {
            when (it) {
                is DragInteraction.Start -> holding = true
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    delay(RESUME_AFTER_MS)
                    holding = false
                }
            }
        }
    }
    LaunchedEffect(listState, lines) {
        snapshotFlow { activeIndex() to holding }.collectLatest { (index, held) ->
            // Before the first line is due, the intro dots or that line wait in its place.
            val row = (index + shift).coerceAtLeast(0)
            if (!held && row < lines.size + shift) listState.anchor(row)
        }
    }

    LazyColumn(
        modifier,
        state = listState,
        contentPadding = PaddingValues(top = height * ANCHOR, bottom = height * (1 - ANCHOR)),
    ) {
        if (intro != null) {
            item {
                val distance by rememberDistance(0, activeIndex, shift)
                LyricRow(distance, holding, null) { InterludeDots(intro, distance == 0, rtl, playback, offsetMs) }
            }
        }
        itemsIndexed(lines) { index, line ->
            val distance by rememberDistance(index + shift, activeIndex, shift)
            val gap = remember(lines, index) { lineGap(lines, index) }
            LyricRow(distance, holding, onSeek?.let { seek -> { seek(index) } }) {
                if (gap != null) InterludeDots(gap, distance == 0, rtl, playback, offsetMs) else LyricText(line.text)
            }
        }
    }
}

/** How many rows [row] is from the active one, capped at [FAR] either way. */
@Composable
private fun rememberDistance(row: Int, activeIndex: () -> Int, shift: Int): State<Int> =
    remember(row, activeIndex, shift) { derivedStateOf { (row - activeIndex() - shift).coerceIn(-FAR, FAR) } }

@Composable
private fun LyricRow(distance: Int, browsing: Boolean, onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    val alpha by animateFloatAsState(
        if (browsing && distance != 0) BROWSING_ALPHA else lineAlpha(distance),
        tween(300),
        label = "alpha",
    )
    val blur by animateFloatAsState(if (browsing) 0f else abs(distance) * BLUR_PER_LINE_DP, tween(300), label = "blur")
    Box(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .graphicsLayer {
                this.alpha = alpha
                // Android 12 and up can blur; older versions only dim.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val radius = blur.dp.toPx()
                    renderEffect = if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Decal) else null
                }
            }
            // Inside the layer, so the blur has room around the text.
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) { content() }
}

@Composable
private fun LyricText(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth(),
        color = Color.White,
        // Each line starts from its own reading side.
        style = MaterialTheme.typography.headlineMedium.copy(
            fontSize = 30.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Start,
            textDirection = TextDirection.Content,
        ),
    )
}

/** Three dots that light up one after another over a pause. Only the active pause animates. */
@Composable
private fun InterludeDots(gap: Gap, active: Boolean, rtl: Boolean, playback: TrackPlaybackState?, offsetMs: Long) {
    var progress by remember(gap) { mutableFloatStateOf(0f) }
    if (active && playback != null) {
        LaunchedEffect(gap, playback, offsetMs) {
            do {
                progress = gap.progress(playback.positionAt(SystemClock.elapsedRealtime()) - offsetMs)
                withFrameMillis {}
            } while (playback.isPlaying)
        }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (rtl) Alignment.CenterEnd else Alignment.CenterStart) {
        Canvas(Modifier.size(width = 76.dp, height = 38.dp)) {
            repeat(3) { dot ->
                val lit = if (active) (progress * 3 - dot).coerceIn(0f, 1f) else 0f
                val order = if (rtl) 2 - dot else dot
                drawCircle(
                    Color.White.copy(alpha = 0.3f + 0.7f * lit),
                    radius = 7.dp.toPx(),
                    center = Offset((10 + order * 28).dp.toPx(), size.height / 2),
                )
            }
        }
    }
}

/** Unsynced lyrics: plain, evenly lit text. */
@Composable
fun PlainLyricsList(lines: List<String>, modifier: Modifier = Modifier, bottomPadding: Dp = 24.dp) {
    LazyColumn(modifier, contentPadding = PaddingValues(top = 24.dp, bottom = bottomPadding)) {
        itemsIndexed(lines) { _, line ->
            Text(
                line,
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    textDirection = TextDirection.Content,
                ),
            )
        }
    }
}

private fun String.isRightToLeft() = TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(this, 0, length)

private suspend fun LazyListState.anchor(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) animateScrollToItem(index)
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    animateScrollBy(
        anchorDelta(item.offset, item.size, layoutInfo.viewportStartOffset, layoutInfo.viewportEndOffset, ANCHOR),
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
    )
}
