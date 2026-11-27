package io.github.mzainkh.alfaaz

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import androidx.core.graphics.createBitmap
import io.github.mzainkh.alfaaz.media.LyricsLayout
import io.github.mzainkh.alfaaz.media.cardBackdrop
import io.github.mzainkh.alfaaz.media.drawCardArt
import io.github.mzainkh.alfaaz.media.layoutLyrics
import io.github.mzainkh.alfaaz.media.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Random
import kotlin.math.roundToInt

private const val SIDE = 512

/** The car crops the picture to the card's shape, so words must stay in its middle half. */
private val SAFE = 128..384

/** Anti-aliasing may touch the pixel just outside. */
private const val SLACK = 1

/**
 * Lowest contrasts allowed, set a little under what the card reaches today: 18.2 for the line being
 * sung, 3.6 for the faded next one and 2.45 for the current line once the car has dimmed it.
 */
private const val CURRENT_FLOOR = 15.0
private const val NEXT_FLOOR = 3.2

/** Beside the map the car dims the picture: this share shows through a grey, as measured on the head unit simulator. */
private const val CAR_SHARE = 0.25
private val CAR_GREY = Color.rgb(70, 65, 75)
private const val DIMMED_CURRENT_FLOOR = 2.3

private class Words(val name: String, val line: String, val next: String, val fits: Boolean = true, val rightToLeft: Boolean = false)

private val samples = listOf(
    Words("short", "Hold on", "Stay a while"),
    Words(
        "long",
        "The lanterns on the harbour keep turning through the night",
        "And nobody is counting how often they turn",
    ),
    Words(
        "very long",
        "Every lantern on the long grey harbour wall keeps turning slowly through the night while the " +
            "tide comes in and the boats lean together and nobody at all is counting how often they turn",
        "Then the morning arrives without asking and the harbour wall is quiet again for one more day",
        fits = false,
    ),
    Words("Arabic", "هذا سطر تجريبي طويل قليلا لاختبار التفاف النص", "السطر الثاني للتجربة", rightToLeft = true),
    Words("Urdu", "یہ ایک آزمائشی سطر ہے جو کچھ لمبی ہے", "آزمائش کی دوسری سطر", rightToLeft = true),
    Words(
        "very long Urdu",
        "اسکرین پر بہت لمبی سطر دیکھنے کے لیے یہ ایک اور آزمائشی جملہ ہے جو ختم ہونے کا نام نہیں لیتا اور چلتا ہی رہتا ہے",
        "اور اس کے بعد ایک اور لمبی سطر آتی ہے تاکہ جگہ کم پڑ جائے اور دیکھا جا سکے کہ کیا ہوتا ہے",
        fits = false,
        rightToLeft = true,
    ),
)

/**
 * How readable the car card is. Sample covers and invented lines are drawn with Android's own
 * graphics, and the words are told apart from the picture by comparing it with and without them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class CardArtTest {
    private val covers: Map<String, Bitmap?> by lazy {
        mapOf(
            "black" to solid(Color.BLACK),
            "white" to solid(Color.WHITE),
            "yellow" to solid(Color.rgb(255, 230, 0)),
            "busy" to busy(),
            "wide" to solid(Color.rgb(40, 130, 210), 640, 360),
            "none" to null,
        )
    }

    @Test
    fun wordsStayInTheMiddleOfTheCard() = forEveryCard { words, _, card, backdrop, _ ->
        val ink = ink(card, backdrop)
        assertTrue("$words draws nothing", ink.isNotEmpty())
        val left = ink.minOf { it.first }
        val right = ink.maxOf { it.first }
        val top = ink.minOf { it.second }
        val bottom = ink.maxOf { it.second }
        assertTrue(
            "$words spills out of the safe area: x $left..$right, y $top..$bottom",
            left >= SAFE.first - SLACK && right <= SAFE.last + SLACK &&
                top >= SAFE.first - SLACK && bottom <= SAFE.last + SLACK,
        )
    }

    @Test
    fun theLineBeingSungStandsOutAndTheNextStaysReadable() = forEveryCard { words, layout, card, backdrop, _ ->
        val ink = ink(card, backdrop)
        layout.texts.forEachIndexed { index, block ->
            val rows = block.top.roundToInt()..(block.top + block.layout.height).roundToInt()
            val glyphs = ink.filter { it.second in rows }
            val text = brightCore(glyphs.map { card.getPixel(it.first, it.second) })
            // The worst place behind this block.
            val behind = brightest(backdrop, SAFE, rows)
            val floor = if (index == 0) CURRENT_FLOOR else NEXT_FLOOR
            val ratio = contrast(text, behind)
            assertTrue("$words, block $index: contrast %.1f is under $floor".format(ratio), ratio >= floor)
            if (index == 0) {
                val dimmed = contrast(dimmedByTheCar(text), dimmedByTheCar(behind))
                assertTrue("$words, dimmed beside the map: %.2f".format(dimmed), dimmed >= DIMMED_CURRENT_FLOOR)
            }
        }
    }

    @Test
    fun theNextLineIsFainterThanTheCurrentOne() {
        val card = drawCardArt(null, "Hold on", "Stay a while")
        val backdrop = cardBackdrop(null, layoutLyrics("Hold on", "Stay a while").height)
        val layout = layoutLyrics("Hold on", "Stay a while")
        val (current, next) = layout.texts.map { block ->
            val rows = block.top.roundToInt()..(block.top + block.layout.height).roundToInt()
            val glyphs = ink(card, backdrop).filter { it.second in rows }
            luminance(brightCore(glyphs.map { card.getPixel(it.first, it.second) }))
        }
        assertTrue("current $current, next $next", current > next * 2)
    }

    @Test
    fun ordinaryLinesAreShownWhole() {
        for (words in samples.filter { it.fits }) {
            for (block in layoutLyrics(words.line, words.next).texts) {
                val cut = (0 until block.layout.lineCount).sumOf { block.layout.getEllipsisCount(it) }
                assertEquals("${words.name} is cut short", 0, cut)
            }
        }
    }

    @Test
    fun rightToLeftLinesReadFromTheRight() {
        for (words in samples) {
            val wanted = if (words.rightToLeft) Layout.DIR_RIGHT_TO_LEFT else Layout.DIR_LEFT_TO_RIGHT
            for (block in layoutLyrics(words.line, words.next).texts) {
                assertEquals(words.name, wanted, block.layout.getParagraphDirection(0))
            }
        }
    }

    @Test
    fun everyLetterHasAGlyph() {
        val paint = Paint()
        val letters = samples.flatMap { (it.line + it.next).toList() }.distinct().filter { it.isLetter() }
        assertEquals(emptyList<Char>(), letters.filterNot { paint.hasGlyph(it.toString()) })
    }

    @Test
    fun withoutALineTheArtStaysClean() {
        val card = drawCardArt(solid(Color.RED), "", "")
        assertEquals(Color.RED, card.getPixel(SIDE / 2, SIDE / 2))
        assertEquals(Color.RED, card.getPixel(2, 2))
    }

    @Test
    fun theArtShowsAboveAndBelowTheWords() {
        val card = drawCardArt(solid(Color.RED), "Hold on", "Stay a while")
        assertEquals(Color.RED, card.getPixel(SIDE / 2, 2))
        assertEquals(Color.RED, card.getPixel(SIDE / 2, SIDE - 3))
    }

    /** Android 8.1 has only the one bold. This covers that branch, not how the older system looks. */
    @Test
    @Config(sdk = [27])
    fun theOlderBoldStaysReadableToo() {
        wordsStayInTheMiddleOfTheCard()
        theLineBeingSungStandsOutAndTheNextStaysReadable()
    }

    @Test
    fun contrastTellsAlikeFromOpposite() {
        assertEquals(1.0, contrast(Color.WHITE, Color.WHITE), 0.01)
        assertEquals(21.0, contrast(Color.WHITE, Color.BLACK), 0.01)
    }

    private fun forEveryCard(check: (String, LyricsLayout, Bitmap, Bitmap, Words) -> Unit) {
        for ((coverName, cover) in covers) {
            for (words in samples) {
                val layout = layoutLyrics(words.line, words.next)
                check(
                    "${words.name} on $coverName",
                    layout,
                    drawCardArt(cover, words.line, words.next),
                    cardBackdrop(cover, layout.height),
                    words,
                )
            }
        }
    }

    private fun solid(color: Int, width: Int = SIDE, height: Int = SIDE) =
        createBitmap(width, height).apply { eraseColor(color) }

    private fun busy(): Bitmap {
        val random = Random(7)
        val pixels = IntArray(SIDE * SIDE)
        val blocks = IntArray(64 * 64) { 0xFF shl 24 or random.nextInt(0xFFFFFF) }
        for (i in pixels.indices) pixels[i] = blocks[(i / SIDE / 8) * 64 + (i % SIDE) / 8]
        return Bitmap.createBitmap(pixels, SIDE, SIDE, Bitmap.Config.ARGB_8888)
    }

    /** Where the words made the picture brighter. Their shadow only darkens it, so it is left out. */
    private fun ink(card: Bitmap, backdrop: Bitmap): List<Pair<Int, Int>> {
        val with = IntArray(SIDE * SIDE).also { card.getPixels(it, 0, SIDE, 0, 0, SIDE, SIDE) }
        val without = IntArray(SIDE * SIDE).also { backdrop.getPixels(it, 0, SIDE, 0, 0, SIDE, SIDE) }
        return with.indices
            .filter { Color.green(with[it]) - Color.green(without[it]) > 24 }
            .map { it % SIDE to it / SIDE }
    }

    /** The solid middle of the strokes, ignoring their soft edges. */
    private fun brightCore(colors: List<Int>): Int = colors.sortedBy { luminance(it) }[colors.size * 9 / 10]

    private fun brightest(bitmap: Bitmap, columns: IntRange, rows: IntRange): Int {
        var best = Color.BLACK
        for (y in rows) for (x in columns) {
            val color = bitmap.getPixel(x, y)
            if (luminance(color) > luminance(best)) best = color
        }
        return best
    }

    private fun contrast(a: Int, b: Int): Double {
        val (low, high) = listOf(luminance(a), luminance(b)).sorted()
        return (high + 0.05) / (low + 0.05)
    }

    private fun dimmedByTheCar(color: Int): Int {
        fun mix(channel: (Int) -> Int) = (CAR_SHARE * channel(color) + (1 - CAR_SHARE) * channel(CAR_GREY)).roundToInt()
        return Color.rgb(mix(Color::red), mix(Color::green), mix(Color::blue))
    }
}
