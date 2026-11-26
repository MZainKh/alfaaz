@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.mzainkh.alfaaz.state

import io.github.mzainkh.alfaaz.lyrics.Lyrics
import io.github.mzainkh.alfaaz.lyrics.LyricsRepository
import io.github.mzainkh.alfaaz.lyrics.TrackQuery
import io.github.mzainkh.alfaaz.media.TrackPlaybackState
import io.github.mzainkh.alfaaz.media.lyricsTicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/** Players publish a new song's metadata in stages; waiting briefly avoids asking twice. */
private const val SETTLE_MS = 300L
private const val MAX_OFFSET_MS = 30_000L

/** Does nothing, network included, unless the phone UI or the car screen is observing [state]. */
class RealLyricsController(
    nowPlaying: StateFlow<TrackPlaybackState?>,
    private val repository: LyricsRepository,
    private val scope: CoroutineScope,
    private val elapsedMs: () -> Long,
) : LyricsController {
    private data class Loaded(val trackKey: String?, val status: LyricsStatus, val offsetMs: Long = 0)

    private data class OffsetEdit(val trackKey: String, val offsetMs: Long)

    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val edit = MutableStateFlow<OffsetEdit?>(null)

    private val loaded: Flow<Loaded> = nowPlaying
        .map { it?.query }
        .distinctUntilChanged { old, new -> old?.cacheKey == new?.cacheKey }
        .flatMapLatest { query ->
            if (query == null) {
                flowOf(Loaded(null, LyricsStatus.Idle))
            } else {
                refreshes.map { true }.onStart { emit(false) }.transformLatest { load(query, it) }
            }
        }

    override val state: StateFlow<LyricsUiState> =
        combine(nowPlaying, loaded, edit, ::Triple)
            .flatMapLatest { (playback, loaded, edit) -> uiStates(playback, loaded, edit) }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), LyricsUiState())

    override fun refresh() {
        refreshes.tryEmit(Unit)
    }

    override fun nudgeOffset(deltaMs: Long) = changeOffset { it + deltaMs }

    override fun resetOffset() = changeOffset { 0 }

    private suspend fun FlowCollector<Loaded>.load(query: TrackQuery, refresh: Boolean) {
        emit(Loaded(query.cacheKey, LyricsStatus.Loading))
        if (!refresh) delay(SETTLE_MS)
        val result = try {
            val stored = repository.lyrics(query, refresh)
            Loaded(query.cacheKey, LyricsStatus.Ready(stored.lyrics), stored.offsetMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Network, HTTP and parsing failures all end the same way: offer Refresh.
            Loaded(query.cacheKey, LyricsStatus.Failed)
        }
        emit(result)
    }

    private fun uiStates(playback: TrackPlaybackState?, loaded: Loaded, edit: OffsetEdit?): Flow<LyricsUiState> {
        if (playback == null) return flowOf(LyricsUiState())
        val key = playback.query.cacheKey
        // Just after a song change the loaded lyrics still belong to the previous one.
        val current = loaded.takeIf { it.trackKey == key } ?: Loaded(key, LyricsStatus.Loading)
        val offset = edit?.takeIf { it.trackKey == key }?.offsetMs ?: current.offsetMs
        val base = LyricsUiState(
            trackKey = key,
            title = playback.title,
            artist = playback.artist,
            sourcePackage = playback.packageName,
            status = current.status,
            offsetMs = offset,
        )
        val lines = ((current.status as? LyricsStatus.Ready)?.lyrics as? Lyrics.Synced)?.lines
            ?: return flowOf(base)
        return lyricsTicker(playback, lines, offset, elapsedMs).map { base.copy(activeIndex = it) }
    }

    private fun changeOffset(change: (Long) -> Long) {
        val shown = state.value
        val key = shown.trackKey ?: return
        // The latest edit, not the shown state, so that quick taps add up.
        val from = edit.value?.takeIf { it.trackKey == key }?.offsetMs ?: shown.offsetMs
        val offset = change(from).coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)
        edit.value = OffsetEdit(key, offset)
        scope.launch { repository.setOffset(key, offset) }
    }
}
