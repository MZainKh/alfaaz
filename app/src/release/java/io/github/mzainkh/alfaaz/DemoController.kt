package io.github.mzainkh.alfaaz

import android.graphics.Bitmap
import io.github.mzainkh.alfaaz.media.ArtRequest
import io.github.mzainkh.alfaaz.media.ArtSource
import io.github.mzainkh.alfaaz.media.Artwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Release builds ship no demo data. */
@Suppress("UNUSED_PARAMETER")
fun withDemo(
    real: Live,
    useDemo: StateFlow<Boolean>,
    scope: CoroutineScope,
    prepareArt: suspend (ArtRequest<ArtSource<Bitmap>>) -> Artwork?,
): Live = real
