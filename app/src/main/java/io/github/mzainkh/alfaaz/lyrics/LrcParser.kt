package io.github.mzainkh.alfaaz.lyrics

object LrcParser {
    private const val GAP = "♪"
    private val timestamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val wordTimestamp = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val spaces = Regex("""\s{2,}""")

    /** Lines sorted by time. Lines without a timestamp, such as metadata tags, are skipped. */
    fun parse(lrc: String): List<LyricLine> {
        // A positive offset tag means the lyrics appear sooner.
        val offset = offsetTag.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = ArrayList<LyricLine>()
        lrc.lineSequence().forEach { raw ->
            val line = raw.trim()
            var end = 0
            // One line may carry several leading timestamps, e.g. a repeated chorus.
            val stamps = generateSequence { timestamp.matchAt(line, end)?.also { end = it.range.last + 1 } }.toList()
            if (stamps.isEmpty()) return@forEach
            val text = line.substring(end).replace(wordTimestamp, "").replace(spaces, " ").trim().ifEmpty { GAP }
            stamps.forEach { lines += LyricLine((it.toMillis() - offset).coerceAtLeast(0), text) }
        }
        lines.sortBy { it.timestampMs }
        return lines
    }

    fun toLyrics(synced: String?, plain: String?, instrumental: Boolean): Lyrics {
        val lines = synced?.let(::parse).orEmpty()
        return when {
            lines.isNotEmpty() -> Lyrics.Synced(lines)
            instrumental -> Lyrics.Instrumental
            !plain.isNullOrBlank() -> Lyrics.Plain(plain.trim().lines().map { it.trim() })
            else -> Lyrics.NotFound
        }
    }

    private fun MatchResult.toMillis(): Long {
        val (minutes, seconds, fraction) = destructured
        return minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fraction.padEnd(3, '0').toLong()
    }
}
