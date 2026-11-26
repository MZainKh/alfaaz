package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.lyrics.LrcParser
import io.github.mzainkh.alfaaz.ui.Gap
import io.github.mzainkh.alfaaz.ui.introGap
import io.github.mzainkh.alfaaz.ui.lineAlpha
import io.github.mzainkh.alfaaz.ui.lineGap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InterludeTest {
    private val lines = LrcParser.parse("[00:12.00]One\n[00:15.00]\n[00:25.00]Two\n[00:27.00]\n[00:28.00]Three\n[00:40.00]")

    @Test
    fun findsTheIntroAndLongBreaks() {
        assertEquals(Gap(0, 12_000), introGap(lines))
        assertEquals(Gap(15_000, 25_000), lineGap(lines, 1))
    }

    @Test
    fun ignoresShortBreaksSungLinesAndTheEnd() {
        assertNull(lineGap(lines, 0))
        assertNull(lineGap(lines, 3)) // one second
        assertNull(lineGap(lines, 5)) // nothing follows
        assertNull(introGap(LrcParser.parse("[00:02.00]Soon")))
        assertNull(introGap(emptyList()))
    }

    @Test
    fun fillsOverThePause() {
        val gap = Gap(10_000, 20_000)
        assertEquals(0f, gap.progress(9_000), 0f)
        assertEquals(0.5f, gap.progress(15_000), 0f)
        assertEquals(1f, gap.progress(30_000), 0f)
    }

    @Test
    fun dimsWithDistanceAndSungLinesMost() {
        assertEquals(listOf(0.25f, 1f, 0.5f, 0.4f, 0.3f), listOf(-1, 0, 1, 2, 4).map(::lineAlpha))
    }
}
