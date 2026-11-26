package io.github.mzainkh.alfaaz.ui

import java.util.Locale

/** `m:ss`, or `h:mm:ss` from an hour up. Negative times count as zero. */
fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val hours = total / 3600
    val minutes = total / 60 % 60
    val seconds = total % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/**
 * How far to scroll so an item's middle sits [fraction] of the way down the viewport. Offsets are in
 * the list's own coordinates, where the viewport spans [viewportStart] to [viewportEnd].
 */
fun anchorDelta(itemOffset: Int, itemSize: Int, viewportStart: Int, viewportEnd: Int, fraction: Float): Float =
    itemOffset + itemSize / 2f - (viewportStart + (viewportEnd - viewportStart) * fraction)

enum class NowPlayingLayout { SINGLE, TWO_PANE }

/** Side by side on wide windows (tablets, unfolded phones) and on phones turned sideways. */
fun nowPlayingLayout(widthDp: Float, heightDp: Float): NowPlayingLayout =
    if (widthDp >= 600f || (widthDp > heightDp && heightDp < 480f)) NowPlayingLayout.TWO_PANE else NowPlayingLayout.SINGLE
