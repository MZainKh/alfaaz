@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.mzainkh.alfaaz

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import io.github.mzainkh.alfaaz.lyrics.LyricLine
import io.github.mzainkh.alfaaz.lyrics.Lyrics
import io.github.mzainkh.alfaaz.media.ArtRequest
import io.github.mzainkh.alfaaz.media.ArtSource
import io.github.mzainkh.alfaaz.media.Artwork
import io.github.mzainkh.alfaaz.media.ArtworkRepository
import io.github.mzainkh.alfaaz.media.NowPlaying
import io.github.mzainkh.alfaaz.media.PlayerCommands
import io.github.mzainkh.alfaaz.media.TrackPlaybackState
import io.github.mzainkh.alfaaz.media.Transport
import io.github.mzainkh.alfaaz.media.lyricsTicker
import io.github.mzainkh.alfaaz.media.positionAt
import io.github.mzainkh.alfaaz.state.LyricsController
import io.github.mzainkh.alfaaz.state.LyricsStatus
import io.github.mzainkh.alfaaz.state.LyricsUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn

internal const val DEMO_STEP_MS = 2_000L
private const val RESTART_AFTER_MS = 3_000L
private const val ART_PX = 384

internal class DemoSong(val title: String, texts: List<String>, val colors: Pair<Long, Long>) {
    val lines = texts.mapIndexed { i, text -> LyricLine((i + 1) * DEMO_STEP_MS, text) }
    val durationMs = (texts.size + 2) * DEMO_STEP_MS
}

internal val demoSongs = listOf(
    DemoSong(
        "Demo Road",
        listOf(
            "First placeholder line", "Second placeholder line",
            "A much longer placeholder line to check how wrapping looks", "Fourth placeholder line", "♪",
            "Sixth placeholder line", "Seventh placeholder line", "Last placeholder line",
        ),
        0xFF8E2DE2 to 0xFF4A00E0,
    ),
    DemoSong(
        "تجربة",
        listOf(
            "هذا سطر تجريبي", "السطر الثاني للتجربة", "سطر طويل قليلا لاختبار التفاف النص على الشاشة",
            "السطر الرابع", "السطر الخامس", "السطر الأخير",
        ),
        0xFFF7971E to 0xFFFFD200,
    ),
    DemoSong(
        "آزمائش",
        listOf(
            "یہ ایک آزمائشی سطر ہے", "آزمائش کی دوسری سطر", "اسکرین پر لمبی سطر دیکھنے کے لیے ایک اور جملہ",
            "چوتھی سطر", "پانچویں سطر", "آخری سطر",
        ),
        0xFF11998E to 0xFF38EF7D,
    ),
)

/** A pretend player that steps through [demoSongs], so screens can be tested without a real one. */
internal class DemoDeck(private val elapsedMs: () -> Long) : PlayerCommands {
    class Now(val index: Int, val playback: TrackPlaybackState) {
        val song get() = demoSongs[index]
    }

    private val current = MutableStateFlow(at(0, 0, playing = true))
    val now: StateFlow<Now> = current.asStateFlow()

    private fun at(index: Int, positionMs: Long, playing: Boolean): Now {
        val i = index.mod(demoSongs.size)
        val song = demoSongs[i]
        return Now(
            i,
            TrackPlaybackState(
                packageName = BuildConfig.APPLICATION_ID,
                title = song.title,
                artist = "Alfaaz demo ${i + 1}",
                album = "Demo album",
                durationMs = song.durationMs,
                isPlaying = playing,
                positionMs = positionMs.coerceIn(0, song.durationMs),
                updateElapsedMs = elapsedMs(),
                speed = 1f,
                transport = Transport(canPlay = true, canPause = true, canNext = true, canPrevious = true, canSeek = true),
            ),
        )
    }

    private fun position() = current.value.playback.positionAt(elapsedMs())

    private fun move(index: Int = current.value.index, positionMs: Long = position(), playing: Boolean = isPlaying) {
        current.value = at(index, positionMs, playing)
    }

    private val isPlaying get() = current.value.playback.isPlaying

    override fun play() = move(playing = true)
    override fun pause() = move(playing = false)
    override fun skipToNext() = move(current.value.index + 1, 0)
    override fun skipToPrevious() =
        if (position() > RESTART_AFTER_MS) move(positionMs = 0) else move(current.value.index - 1, 0)

    override fun seekTo(positionMs: Long) = move(positionMs = positionMs)

    /** Milliseconds until the current song ends, or null while paused. */
    fun remainingMs(): Long? = current.value.playback.takeIf { it.isPlaying }?.let { it.durationMs - position() }

    /** Moves to the next song once the current one has played to its end. */
    fun finishIfDone() {
        if ((remainingMs() ?: return) <= 0) skipToNext()
    }
}

private fun DemoDeck.lyrics(elapsedMs: () -> Long): Flow<LyricsUiState> = now.flatMapLatest { now ->
    val playback = now.playback
    val base = LyricsUiState(
        trackKey = playback.query.cacheKey,
        title = playback.title,
        artist = playback.artist,
        sourcePackage = playback.packageName,
        status = LyricsStatus.Ready(Lyrics.Synced(now.song.lines)),
    )
    val ending = flow<Nothing> {
        delay(remainingMs() ?: return@flow)
        finishIfDone()
    }
    merge(lyricsTicker(playback, now.song.lines, 0, elapsedMs).map { base.copy(activeIndex = it) }, ending)
}

private val gradients = mutableMapOf<Int, Bitmap>()

private fun gradient(song: DemoSong, index: Int): Bitmap = synchronized(gradients) {
    gradients.getOrPut(index) {
        val (from, to) = song.colors
        val shader = LinearGradient(0f, 0f, ART_PX.toFloat(), ART_PX.toFloat(), from.toInt(), to.toInt(), Shader.TileMode.CLAMP)
        createBitmap(ART_PX, ART_PX).applyCanvas { drawPaint(Paint().apply { this.shader = shader }) }
    }
}

/** Debug builds: [real], or a demo player with placeholder songs while [useDemo] is on. */
fun withDemo(
    real: Live,
    useDemo: StateFlow<Boolean>,
    scope: CoroutineScope,
    prepareArt: suspend (ArtRequest<ArtSource<Bitmap>>) -> Artwork?,
): Live {
    val elapsed = SystemClock::elapsedRealtime
    val deck = DemoDeck(elapsed)
    val demoArt = ArtworkRepository(
        deck.now.map { ArtRequest(it.playback.query.cacheKey, "demo|${it.index}", ArtSource.Image(gradient(it.song, it.index))) },
        scope,
        prepareArt,
    ).artwork

    // WhileSubscribed() without a delay: the sources below already keep themselves alive for a while.
    fun <T> pick(demo: Flow<T>, live: Flow<T>, initial: T) =
        useDemo.flatMapLatest { if (it) demo else live }.stateIn(scope, SharingStarted.WhileSubscribed(), initial)

    val lyrics = object : LyricsController {
        override val state = pick(deck.lyrics(elapsed), real.lyrics.state, LyricsUiState())

        override fun refresh() {
            if (!useDemo.value) real.lyrics.refresh()
        }

        override fun nudgeOffset(deltaMs: Long) {
            if (!useDemo.value) real.lyrics.nudgeOffset(deltaMs)
        }

        override fun resetOffset() {
            if (!useDemo.value) real.lyrics.resetOffset()
        }
    }
    val player = object : NowPlaying {
        override val playback = pick<TrackPlaybackState?>(deck.now.map { it.playback }, real.player.playback, null)
        override val artwork = pick<Artwork?>(demoArt, real.player.artwork, null)
        private val target get() = if (useDemo.value) deck else real.player.commands
        override val commands = object : PlayerCommands {
            override fun play() = target.play()
            override fun pause() = target.pause()
            override fun skipToNext() = target.skipToNext()
            override fun skipToPrevious() = target.skipToPrevious()
            override fun seekTo(positionMs: Long) = target.seekTo(positionMs)
        }
    }
    return Live(lyrics, player)
}
