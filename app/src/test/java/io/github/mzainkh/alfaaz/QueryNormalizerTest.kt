package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.lyrics.QueryNormalizer
import io.github.mzainkh.alfaaz.lyrics.TrackQuery
import org.junit.Assert.assertEquals
import org.junit.Test

class QueryNormalizerTest {
    @Test
    fun stripsPlayerNoiseFromTitles() {
        listOf(
            "Song (Official Video)", "Song (Official Music Video)", "Song (Lyric Video)", "Song (Audio)",
            "Song [4K]", "Song - Remastered 2011", "Song - 2011 Remaster", "Song (Remastered)",
        ).forEach { assertEquals(it, "Song", QueryNormalizer.title(it)) }
    }

    @Test
    fun keepsMeaningfulText() {
        listOf("Song (Live)", "Video Games", "Song (Part 2)", "One - Two").forEach {
            assertEquals(it, QueryNormalizer.title(it))
        }
    }

    @Test
    fun neverReturnsAnEmptyQuery() {
        assertEquals("[Intro]", QueryNormalizer.title("[Intro]"))
    }

    @Test
    fun cleansArtistsAndFeaturedCredits() {
        assertEquals("Someone", QueryNormalizer.artist("Someone - Topic"))
        assertEquals("Song", QueryNormalizer.withoutFeaturing("Song (feat. Guest)"))
        assertEquals("Song", QueryNormalizer.withoutFeaturing("Song ft. Guest"))
        assertEquals("Defeat", QueryNormalizer.withoutFeaturing("Defeat"))
    }

    @Test
    fun cacheKeyIgnoresNoiseAndCaseButNotDuration() {
        val a = TrackQuery("Song (Official Video)", "Someone - Topic", null, 200)
        assertEquals(a.cacheKey, TrackQuery("song", "SOMEONE", "Album", 200).cacheKey)
        assert(a.cacheKey != a.copy(durationSec = 260).cacheKey)
    }
}
