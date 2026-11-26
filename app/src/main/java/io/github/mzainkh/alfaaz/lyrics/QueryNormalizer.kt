package io.github.mzainkh.alfaaz.lyrics

/** Removes player-specific noise from titles and artists before asking LRCLIB. */
object QueryNormalizer {
    private val squareTags = Regex("""\s*\[[^\]]*]""")
    private val videoTags = Regex(
        """\s*\((?=[^)]*\b(?:official|(?:lyrics?|music|full) video|lyrics|audio|visuali[sz]er|remaster(?:ed)?)\b)[^)]*\)""",
        RegexOption.IGNORE_CASE,
    )
    private val remasterSuffix =
        Regex("""\s+-\s+(?:\d{4}\s+)?(?:digital(?:ly)?\s+)?remaster(?:ed)?\b.*$""", RegexOption.IGNORE_CASE)
    private val topicSuffix = Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE)
    private val featuring = Regex(
        """\s*(?:[(\[](?:feat|ft|featuring)\b[^)\]]*[)\]]|\s(?:feat\.|ft\.|featuring\s).*$)""",
        RegexOption.IGNORE_CASE,
    )

    fun title(raw: String) = raw.strip(squareTags, videoTags, remasterSuffix)

    fun artist(raw: String) = raw.strip(topicSuffix)

    fun withoutFeaturing(raw: String) = raw.strip(featuring)

    /** Keeps the input when stripping would leave nothing. */
    private fun String.strip(vararg patterns: Regex): String =
        patterns.fold(this) { text, pattern -> text.replace(pattern, "") }.trim().ifEmpty { trim() }
}
