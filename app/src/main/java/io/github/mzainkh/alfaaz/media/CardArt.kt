package io.github.mzainkh.alfaaz.media

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation

private const val SIDE_PX = 512

/** The car crops the square art to the card's shape, so the lyrics stay within its middle. */
private const val TEXT_WIDTH_PX = SIDE_PX / 2
private const val TEXT_HEIGHT_PX = SIDE_PX / 2

private const val MAX_TEXT_PX = 42
private const val MIN_TEXT_PX = 22

/** The line being sung is large, heavy and solid; the next one is small and faded. */
private const val CURRENT_WEIGHT = 900
private const val NEXT_WEIGHT = 600
private const val NEXT_SCALE = 0.62f
private const val NEXT_ALPHA = 105
private const val GAP_SCALE = 0.6f
private const val SHADOW_PX = 8f

/** The soft fade behind the words: nearly black around them, easing out into the untouched art. */
private const val FADE_ALPHA = 235
private const val FADE_PADDING_PX = 24
private const val FADE_PX = 120
private val FADE_EASE = floatArrayOf(0f, 0.16f, 0.5f, 0.84f, 1f)
private val PLAIN_BACKGROUND = Color.rgb(20, 24, 32)

/** A block of lyric text and where its top-left corner goes on the card's picture. */
internal class PlacedText(val layout: StaticLayout, val left: Float, val top: Float)

/** The blocks of text for the middle of the card, and the height they take together. */
internal class LyricsLayout(val texts: List<PlacedText>, val height: Int)

/**
 * The card's picture: the album art, with the lyrics in its middle when there are any. Android Auto
 * fixes where the card's own texts go, so the picture is the only way to put words there.
 */
fun drawCardArt(image: Bitmap?, line: String, next: String): Bitmap {
    val lyrics = if (line.isEmpty()) null else layoutLyrics(line, next)
    val art = cardBackdrop(image, lyrics?.height)
    val canvas = Canvas(art)
    lyrics?.texts?.forEach { text -> canvas.withTranslation(text.left, text.top) { text.layout.draw(this) } }
    return art
}

/** The picture without its words: the art, and the fade behind a block of text [textHeight] tall. */
internal fun cardBackdrop(image: Bitmap?, textHeight: Int?): Bitmap {
    val art = createBitmap(SIDE_PX, SIDE_PX, Bitmap.Config.RGB_565)
    val canvas = Canvas(art)
    canvas.drawColor(PLAIN_BACKGROUND)
    if (image != null) canvas.drawFilling(image)
    if (textHeight != null) canvas.drawFade(textHeight)
    return art
}

/** The middle square of [image], filling the card's picture. */
private fun Canvas.drawFilling(image: Bitmap) {
    val side = minOf(image.width, image.height)
    val left = (image.width - side) / 2
    val top = (image.height - side) / 2
    drawBitmap(
        image,
        Rect(left, top, left + side, top + side),
        Rect(0, 0, SIDE_PX, SIDE_PX),
        Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG),
    )
}

/** The largest text at which both lines fit, centred on the card's picture. */
internal fun layoutLyrics(line: String, next: String): LyricsLayout {
    var size = MAX_TEXT_PX
    var texts: List<StaticLayout>
    var height: Int
    while (true) {
        texts = listOfNotNull(
            centred(line, size.toFloat(), CURRENT_WEIGHT, alpha = 255, maxLines = 5),
            next.takeIf { it.isNotEmpty() }?.let { centred(it, size * NEXT_SCALE, NEXT_WEIGHT, NEXT_ALPHA, maxLines = 3) },
        )
        height = texts.sumOf { it.height } + (texts.size - 1) * (size * GAP_SCALE).toInt()
        if (height <= TEXT_HEIGHT_PX || size <= MIN_TEXT_PX) break
        size -= 2
    }

    var y = (SIDE_PX - height) / 2f
    val placed = texts.map { text ->
        PlacedText(text, (SIDE_PX - TEXT_WIDTH_PX) / 2f, y).also { y += text.height + size * GAP_SCALE }
    }
    return LyricsLayout(placed, height)
}

/** A dark band across the art behind a block of text [textHeight] tall, with no visible edge. */
private fun Canvas.drawFade(textHeight: Int) {
    val solid = textHeight / 2f + FADE_PADDING_PX
    val top = SIDE_PX / 2f - solid - FADE_PX
    val bottom = SIDE_PX / 2f + solid + FADE_PX
    // The share of the band that each fading end takes.
    val edge = FADE_PX / (bottom - top)
    val steps = FADE_EASE.lastIndex
    val strengths = FADE_EASE + FADE_EASE.reversedArray()
    val colors = IntArray(strengths.size) { Color.argb((strengths[it] * FADE_ALPHA).toInt(), 0, 0, 0) }
    val stops = FloatArray(strengths.size) {
        if (it <= steps) edge * it / steps else 1 - edge * (strengths.lastIndex - it) / steps
    }
    val paint = Paint(Paint.DITHER_FLAG).apply {
        shader = LinearGradient(0f, top, 0f, bottom, colors, stops, Shader.TileMode.CLAMP)
    }
    drawRect(0f, top, SIDE_PX.toFloat(), bottom, paint)
}

/** Centred, wrapped text that reads from the right in right-to-left languages. */
// Layout's break strategy is the constant that exists below API 29.
@SuppressLint("WrongConstant")
private fun centred(text: String, sizePx: Float, weight: Int, alpha: Int, maxLines: Int): StaticLayout {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        this.alpha = alpha
        textSize = sizePx
        // Before Android 9 there is only the one bold.
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.DEFAULT, weight, false)
        } else {
            Typeface.DEFAULT_BOLD
        }
        setShadowLayer(SHADOW_PX, 0f, 0f, Color.BLACK)
    }
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, TEXT_WIDTH_PX)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
        .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()
}
