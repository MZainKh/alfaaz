package io.github.mzainkh.alfaaz.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mzainkh.alfaaz.lyrics.LyricLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** After the user scrolls the lyrics, auto-scroll waits this long before following the song again. */
private const val RESUME_AFTER_MS = 3_000L
private const val ACTIVE_ALPHA = 1f
private const val PAST_ALPHA = 0.35f
private const val UPCOMING_ALPHA = 0.6f
private const val ACTIVE_SCALE = 1.04f

/**
 * Synced lyrics that keep the active line in the middle. Only the lines whose state changes redraw;
 * [activeIndex] is read lazily for that reason. [onSeek] is null when the player can't seek.
 */
@Composable
fun SyncedLyricsList(
    lines: List<LyricLine>,
    activeIndex: () -> Int,
    onSeek: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    edgePadding: Dp = 160.dp,
) {
    val listState = rememberLazyListState()
    var holding by remember { mutableStateOf(false) }

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
            // Before the first line is due, that line waits in the middle.
            val target = index.coerceAtLeast(0)
            if (!held && target in lines.indices) listState.centerOn(target)
        }
    }

    LazyColumn(modifier, state = listState, contentPadding = PaddingValues(vertical = edgePadding)) {
        itemsIndexed(lines) { index, line ->
            // -1 for lines already sung, 0 for the active one, 1 for those to come.
            val place by remember(index, activeIndex) { derivedStateOf { index.compareTo(activeIndex()).coerceIn(-1, 1) } }
            LyricLineText(line.text, place, onSeek?.let { seek -> { seek(index) } })
        }
    }
}

@Composable
private fun LyricLineText(text: String, place: Int, onClick: (() -> Unit)?) {
    val alpha by animateFloatAsState(
        when (place) {
            0 -> ACTIVE_ALPHA
            -1 -> PAST_ALPHA
            else -> UPCOMING_ALPHA
        },
        tween(300),
        label = "alpha",
    )
    val scale by animateFloatAsState(if (place == 0) ACTIVE_SCALE else 1f, tween(300), label = "scale")
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin.Center
            },
        color = Color.White,
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, lineHeight = 32.sp),
        textAlign = TextAlign.Center,
    )
}

/** Unsynced lyrics: plain, evenly lit text. */
@Composable
fun PlainLyricsList(lines: List<String>, modifier: Modifier = Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(vertical = 24.dp)) {
        itemsIndexed(lines) { _, line ->
            Text(
                line,
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private suspend fun LazyListState.centerOn(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) animateScrollToItem(index)
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    animateScrollBy(centerDelta(item.offset, item.size, layoutInfo.viewportStartOffset, layoutInfo.viewportEndOffset))
}
