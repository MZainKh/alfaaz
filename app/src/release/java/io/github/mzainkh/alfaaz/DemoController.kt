package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.state.LyricsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Release builds ship no demo data. */
@Suppress("UNUSED_PARAMETER")
fun withDemo(real: LyricsController, useDemo: StateFlow<Boolean>, scope: CoroutineScope): LyricsController = real
