package io.github.mzainkh.alfaaz.state

import io.github.mzainkh.alfaaz.lyrics.Lyrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface LyricsStatus {
    data object Idle : LyricsStatus
    data object Loading : LyricsStatus
    data object Failed : LyricsStatus
    data class Ready(val lyrics: Lyrics) : LyricsStatus
}

data class LyricsUiState(
    val trackKey: String? = null,
    val title: String = "",
    val artist: String = "",
    val sourcePackage: String = "",
    val status: LyricsStatus = LyricsStatus.Idle,
    val activeIndex: Int = -1,
    val offsetMs: Long = 0,
)

/** The single source of truth shared by the car screen and the phone UI. */
interface LyricsController {
    val state: StateFlow<LyricsUiState>
    fun refresh() {}
    fun nudgeOffset(deltaMs: Long) {}
    fun resetOffset() {}
}

object IdleLyricsController : LyricsController {
    override val state: StateFlow<LyricsUiState> = MutableStateFlow(LyricsUiState())
}
