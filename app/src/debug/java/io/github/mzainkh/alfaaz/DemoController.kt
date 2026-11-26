package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.lyrics.LyricLine
import io.github.mzainkh.alfaaz.lyrics.Lyrics
import io.github.mzainkh.alfaaz.state.LyricsController
import io.github.mzainkh.alfaaz.state.LyricsStatus
import io.github.mzainkh.alfaaz.state.LyricsUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

private const val STEP_MS = 2_000L

/** Debug builds only: cycles through placeholder songs so the car screen can be tested without music. */
fun demoController(scope: CoroutineScope): LyricsController? = object : LyricsController {
    private val songs = listOf(
        "Demo Road" to listOf(
            "First placeholder line", "Second placeholder line", "A much longer placeholder line to check how wrapping looks",
            "Fourth placeholder line", "♪", "Sixth placeholder line", "Seventh placeholder line", "Last placeholder line",
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

    override val state = flow {
        var n = 0
        while (true) {
            val (title, texts) = songs[n % songs.size]
            val base = LyricsUiState("demo-$n", title, "Alfaaz demo ${n + 1}", status = LyricsStatus.Loading)
            emit(base)
            delay(STEP_MS)
            val ready = base.copy(
                status = LyricsStatus.Ready(Lyrics.Synced(texts.mapIndexed { i, t -> LyricLine(i * STEP_MS, t) })),
            )
            texts.indices.forEach {
                emit(ready.copy(activeIndex = it))
                delay(STEP_MS)
            }
            n++
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), LyricsUiState())
}
