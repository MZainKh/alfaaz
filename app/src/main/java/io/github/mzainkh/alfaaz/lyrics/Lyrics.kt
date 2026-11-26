package io.github.mzainkh.alfaaz.lyrics

data class LyricLine(val timestampMs: Long, val text: String)

sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>) : Lyrics
    data class Plain(val lines: List<String>) : Lyrics
    data object Instrumental : Lyrics
    data object NotFound : Lyrics
}
