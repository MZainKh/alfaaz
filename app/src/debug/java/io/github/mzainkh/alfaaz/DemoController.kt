@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.lyrics.LyricLine
import io.github.mzainkh.alfaaz.lyrics.Lyrics
import io.github.mzainkh.alfaaz.state.LyricsController
import io.github.mzainkh.alfaaz.state.LyricsStatus
import io.github.mzainkh.alfaaz.state.LyricsUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

private const val STEP_MS = 2_000L

private val songs = listOf(
    "Demo Road" to listOf(
        "First placeholder line", "Second placeholder line",
        "A much longer placeholder line to check how wrapping looks", "Fourth placeholder line", "♪",
        "Sixth placeholder line", "Seventh placeholder line", "Last placeholder line",
    ),
    "تجربة" to listOf(
        "هذا سطر تجريبي", "السطر الثاني للتجربة", "سطر طويل قليلا لاختبار التفاف النص على الشاشة",
        "السطر الرابع", "السطر الخامس", "السطر الأخير",
    ),
    "آزمائش" to listOf(
        "یہ ایک آزمائشی سطر ہے", "آزمائش کی دوسری سطر", "اسکرین پر لمبی سطر دیکھنے کے لیے ایک اور جملہ",
        "چوتھی سطر", "پانچویں سطر", "آخری سطر",
    ),
)

/** Cycles through placeholder songs so the car screen can be tested without a player. */
private val demoStates = flow {
    var n = 0
    while (true) {
        val (title, texts) = songs[n % songs.size]
        val loading = LyricsUiState("demo-$n", title, "Alfaaz demo ${n + 1}", status = LyricsStatus.Loading)
        emit(loading)
        delay(STEP_MS)
        val lines = texts.mapIndexed { i, text -> LyricLine(i * STEP_MS, text) }
        val ready = loading.copy(status = LyricsStatus.Ready(Lyrics.Synced(lines)))
        lines.indices.forEach {
            emit(ready.copy(activeIndex = it))
            delay(STEP_MS)
        }
        n++
    }
}

/** Debug builds: [real], or the demo songs while [useDemo] is on. */
fun withDemo(real: LyricsController, useDemo: StateFlow<Boolean>, scope: CoroutineScope): LyricsController =
    object : LyricsController {
        override val state = useDemo
            .flatMapLatest { if (it) demoStates else real.state }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), LyricsUiState())

        override fun refresh() = real.refresh()

        override fun nudgeOffset(deltaMs: Long) {
            if (!useDemo.value) real.nudgeOffset(deltaMs)
        }

        override fun resetOffset() {
            if (!useDemo.value) real.resetOffset()
        }
    }
