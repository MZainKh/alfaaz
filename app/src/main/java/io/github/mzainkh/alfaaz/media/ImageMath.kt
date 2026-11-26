package io.github.mzainkh.alfaaz.media

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** Largest power-of-two decode step that keeps the shorter side at least [target] pixels. */
fun sampleSize(width: Int, height: Int, target: Int): Int {
    var sample = 1
    while (minOf(width, height) / (sample * 2) >= target) sample *= 2
    return sample
}

/** Size that fits within [maxSide] keeping the aspect ratio. Never enlarges. */
fun fitWithin(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
    val longest = max(width, height)
    if (longest <= maxSide) return width to height
    val scale = maxSide.toFloat() / longest
    return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
}

/** Box blur of ARGB [pixels], horizontal then vertical, with edges clamped. Alpha is kept opaque. */
fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
    require(pixels.size == width * height)
    if (radius <= 0) return pixels.copyOf()
    val horizontal = blurPass(pixels, width, height, radius, dx = 1, dy = 0)
    return blurPass(horizontal, width, height, radius, dx = 0, dy = 1)
}

private fun blurPass(source: IntArray, width: Int, height: Int, radius: Int, dx: Int, dy: Int): IntArray {
    val out = IntArray(source.size)
    val count = 2 * radius + 1
    for (y in 0 until height) {
        for (x in 0 until width) {
            var r = 0
            var g = 0
            var b = 0
            for (k in -radius..radius) {
                val sx = (x + k * dx).coerceIn(0, width - 1)
                val sy = (y + k * dy).coerceIn(0, height - 1)
                val c = source[sy * width + sx]
                r += c shr 16 and 0xFF
                g += c shr 8 and 0xFF
                b += c and 0xFF
            }
            out[y * width + x] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
        }
    }
    return out
}

/** WCAG relative luminance of an ARGB colour, 0 (black) to 1 (white). */
fun luminance(argb: Int): Double {
    fun channel(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(argb shr 16 and 0xFF) + 0.7152 * channel(argb shr 8 and 0xFF) +
        0.0722 * channel(argb and 0xFF)
}

private const val MIN_ACCENT_LUMINANCE = 0.3

/** [argb] lightened toward white until it reads well on the dark backdrop. */
fun readableAccent(argb: Int): Int {
    var color = argb or (0xFF shl 24)
    repeat(10) {
        if (luminance(color) >= MIN_ACCENT_LUMINANCE) return color
        color = blend(color, 0xFFFFFFFF.toInt(), 0.2f)
    }
    return color
}

private fun blend(from: Int, to: Int, amount: Float): Int {
    fun mix(shift: Int): Int {
        val a = from shr shift and 0xFF
        val b = to shr shift and 0xFF
        return (a + (b - a) * amount).roundToInt() shl shift
    }
    return (0xFF shl 24) or mix(16) or mix(8) or mix(0)
}
