package io.github.mzainkh.alfaaz.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.IOException

private const val PHONE_PX = 512
private const val CAR_PX = 256
private const val BACKDROP_PX = 32
private const val BLUR_RADIUS = 2
private const val MAX_BYTES = 8L * 1024 * 1024

/**
 * One track's album art, prepared once. Compared by identity, so screens that see the same object
 * never redraw or resend it.
 */
class Artwork(
    val trackKey: String,
    /** At most [PHONE_PX] on its longest side. */
    val image: Bitmap,
    /** Small and without alpha: the car screen resends it on every update. */
    val car: Bitmap,
    /** A tiny blurred copy; drawn enlarged with smoothing it gives a soft background. */
    val backdrop: Bitmap,
    /** A colour from the art that reads well on a dark background. */
    val accent: Int?,
)

/** Turns the player's art into an [Artwork]. Runs off the main thread. */
class ArtworkProcessor(private val context: Context, private val http: OkHttpClient) {
    suspend fun process(request: ArtRequest<ArtSource<Bitmap>>): Artwork? = withContext(Dispatchers.Default) {
        val source = when (val from = request.source) {
            is ArtSource.Image -> from.image.software()
            is ArtSource.Link -> withContext(Dispatchers.IO) { download(from.uri) }?.let(::decode)
        } ?: return@withContext null
        val image = scaled(source, PHONE_PX, Bitmap.Config.ARGB_8888).apply { prepareToDraw() }
        Artwork(
            trackKey = request.trackKey,
            image = image,
            car = scaled(source, CAR_PX, Bitmap.Config.RGB_565),
            backdrop = blurred(image),
            accent = Palette.from(image).generate().let { it.vibrantSwatch ?: it.dominantSwatch }
                ?.rgb?.let(::readableAccent),
        )
    }

    private fun download(uri: String): ByteArray? = try {
        if (uri.startsWith("https:", ignoreCase = true)) {
            http.newCall(Request.Builder().url(uri).build()).execute().use { response ->
                response.body?.takeIf { response.isSuccessful }?.source()?.capped()
            }
        } else {
            context.contentResolver.openInputStream(uri.toUri())?.use { it.source().buffer().capped() }
        }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        // Another app's content link that this app was not granted.
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun BufferedSource.capped(): ByteArray? = if (request(MAX_BYTES + 1)) null else readByteArray()

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, PHONE_PX) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /** Hardware bitmaps cannot be drawn into a software canvas or read by Palette. */
    private fun Bitmap.software(): Bitmap =
        if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this

    private fun scaled(source: Bitmap, maxSide: Int, config: Bitmap.Config): Bitmap {
        val (width, height) = fitWithin(source.width, source.height, maxSide)
        if (width == source.width && height == source.height && source.config == config) return source
        return createBitmap(width, height, config).also {
            Canvas(it).drawBitmap(source, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }

    private fun blurred(source: Bitmap): Bitmap {
        val small = scaled(source, BACKDROP_PX, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        val blurred = boxBlur(boxBlur(pixels, small.width, small.height, BLUR_RADIUS), small.width, small.height, BLUR_RADIUS)
        return Bitmap.createBitmap(blurred, small.width, small.height, Bitmap.Config.ARGB_8888)
    }
}
