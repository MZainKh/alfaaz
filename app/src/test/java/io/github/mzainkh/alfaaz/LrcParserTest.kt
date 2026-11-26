package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.lyrics.LrcParser
import io.github.mzainkh.alfaaz.lyrics.LyricLine
import io.github.mzainkh.alfaaz.lyrics.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesEveryTimestampPrecision() {
        val lines = LrcParser.parse("[00:01]a\n[00:02.5]b\n[00:03.45]c\n[01:04.456]d\n[100:00:07]e")
        assertEquals(listOf(1_000L, 2_500L, 3_450L, 64_456L, 6_000_070L), lines.map { it.timestampMs })
    }

    @Test
    fun repeatsALineForEachTimestampAndSortsByTime() {
        val lines = LrcParser.parse("[00:20.00][00:05.00]chorus\n[00:10.00]verse")
        assertEquals(
            listOf(LyricLine(5_000, "chorus"), LyricLine(10_000, "verse"), LyricLine(20_000, "chorus")),
            lines,
        )
    }

    @Test
    fun appliesTheOffsetTagWithoutGoingNegative() {
        assertEquals(listOf(0L, 9_000L), LrcParser.parse("[offset:+1000]\n[00:00.50]a\n[00:10.00]b").map { it.timestampMs })
        assertEquals(listOf(10_250L), LrcParser.parse("[offset:-250]\n[00:10.00]a").map { it.timestampMs })
    }

    @Test
    fun skipsMetadataHandlesCrlfAndMarksBlankLines() {
        val lines = LrcParser.parse("[ar:Someone]\r\n[ti:Song]\r\nno timestamp\r\n[00:01.00]  \r\n[00:02.00] hi \r\n")
        assertEquals(listOf(LyricLine(1_000, "♪"), LyricLine(2_000, "hi")), lines)
    }

    @Test
    fun removesWordTimestamps() {
        assertEquals("one two", LrcParser.parse("[00:01.00]<00:01.00> one <00:01.40> two").single().text)
    }

    @Test
    fun keepsRightToLeftText() {
        assertEquals("یہ ایک سطر ہے", LrcParser.parse("[00:01.00]یہ ایک سطر ہے").single().text)
    }

    @Test
    fun choosesTheBestAvailableKind() {
        assert(LrcParser.toLyrics("[00:01.00]a", "a", false) is Lyrics.Synced)
        assertEquals(Lyrics.Plain(listOf("a", "b")), LrcParser.toLyrics(null, "a\r\nb\n", false))
        assertEquals(Lyrics.Plain(listOf("a")), LrcParser.toLyrics("no stamps", "a", false))
        assertEquals(Lyrics.Instrumental, LrcParser.toLyrics(null, null, true))
        assertEquals(Lyrics.NotFound, LrcParser.toLyrics(null, " ", false))
    }
}
