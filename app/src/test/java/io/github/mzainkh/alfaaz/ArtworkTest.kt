@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.mzainkh.alfaaz

import android.media.MediaMetadata
import io.github.mzainkh.alfaaz.media.ArtRequest
import io.github.mzainkh.alfaaz.media.ArtSource
import io.github.mzainkh.alfaaz.media.ArtworkRepository
import io.github.mzainkh.alfaaz.media.boxBlur
import io.github.mzainkh.alfaaz.media.chooseArt
import io.github.mzainkh.alfaaz.media.fitWithin
import io.github.mzainkh.alfaaz.media.luminance
import io.github.mzainkh.alfaaz.media.readableAccent
import io.github.mzainkh.alfaaz.media.sampleSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkChoiceTest {
    private fun choose(images: Map<String, String> = emptyMap(), texts: Map<String, String> = emptyMap()) =
        chooseArt(images::get, texts::get)

    @Test
    fun prefersTheAlbumImage() {
        val images = mapOf(MediaMetadata.METADATA_KEY_ART to "art", MediaMetadata.METADATA_KEY_ALBUM_ART to "album")
        assertEquals(ArtSource.Image("album"), choose(images))
        assertEquals(ArtSource.Image("icon"), choose(mapOf(MediaMetadata.METADATA_KEY_DISPLAY_ICON to "icon")))
    }

    @Test
    fun fallsBackToLinksThatCanBeOpened() {
        val link = "https://i.example/cover.jpg"
        assertEquals(ArtSource.Link(link), choose(texts = mapOf(MediaMetadata.METADATA_KEY_ART_URI to " $link ")))
        assertEquals(
            ArtSource.Link("content://player/art/1"),
            choose(texts = mapOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI to "content://player/art/1")),
        )
        assertNull(choose(texts = mapOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI to "spotify:image:ab67")))
        assertNull(choose(texts = mapOf(MediaMetadata.METADATA_KEY_ART_URI to "http://plain/cover.jpg")))
        assertNull(choose())
    }
}

class ImageMathTest {
    @Test
    fun decodesLargeImagesInPowerOfTwoSteps() {
        assertEquals(1, sampleSize(600, 600, 512))
        assertEquals(2, sampleSize(1_024, 1_024, 512))
        assertEquals(4, sampleSize(3_000, 2_100, 512))
    }

    @Test
    fun fitsWithoutEnlarging() {
        assertEquals(300 to 200, fitWithin(300, 200, 512))
        assertEquals(256 to 128, fitWithin(1_000, 500, 256))
        assertEquals(1 to 32, fitWithin(10, 1_000, 32))
    }

    @Test
    fun blurSpreadsColourAndKeepsFlatAreas() {
        val flat = IntArray(9) { 0xFF336699.toInt() }
        assertTrue(flat.contentEquals(boxBlur(flat, 3, 3, 1)))
        val dot = IntArray(9).also { it[4] = 0xFFFFFFFF.toInt() }
        val blurred = boxBlur(dot, 3, 3, 1)
        assertTrue(blurred.all { it ushr 24 == 0xFF })
        assertTrue(blurred[0] and 0xFF > 0)
        assertTrue(blurred[4] and 0xFF < 0xFF)
    }

    @Test
    fun accentsAreLightenedOnlyWhenTooDark() {
        val navy = 0xFF101040.toInt()
        assertTrue(luminance(readableAccent(navy)) >= 0.3)
        val yellow = 0xFFFFD54F.toInt()
        assertEquals(yellow, readableAccent(yellow))
    }
}

class ArtworkRepositoryTest {
    private val requests = MutableStateFlow<ArtRequest<String>?>(null)
    private var processed = 0

    private fun TestScope.repository(delayMs: Long = 0, fail: Boolean = false) =
        ArtworkRepository(requests, backgroundScope) { request ->
            processed++
            delay(delayMs)
            if (fail) error("broken image")
            "art of ${request.source}"
        }

    private fun TestScope.observe(repository: ArtworkRepository<String, String>) =
        backgroundScope.launch { repository.artwork.collect {} }.also { runCurrent() }

    @Test
    fun preparesEachImageOnce() = runTest {
        val repository = repository()
        observe(repository)
        requests.value = ArtRequest("one", "one|a", "a")
        runCurrent()
        val first = repository.artwork.value
        assertEquals("art of a", first)
        requests.value = ArtRequest("one", "one|a", "a")
        runCurrent()
        assertSame(first, repository.artwork.value)
        assertEquals(1, processed)
        requests.value = null
        runCurrent()
        assertNull(repository.artwork.value)
    }

    @Test
    fun keepsThePreviousImageWhileTheNextIsPrepared() = runTest {
        val repository = repository(delayMs = 100)
        observe(repository)
        requests.value = ArtRequest("one", "one|a", "a")
        advanceTimeBy(150)
        requests.value = ArtRequest("two", "two|b", "b")
        advanceTimeBy(50)
        assertEquals("art of a", repository.artwork.value)
        advanceTimeBy(100)
        assertEquals("art of b", repository.artwork.value)
    }

    @Test
    fun returningObserversGetTheSameImage() = runTest {
        val repository = repository()
        val first = observe(repository)
        requests.value = ArtRequest("one", "one|a", "a")
        runCurrent()
        val shown = repository.artwork.value
        first.cancel()
        advanceTimeBy(10_000)
        observe(repository)
        assertSame(shown, repository.artwork.value)
        assertEquals(1, processed)
    }

    @Test
    fun brokenImagesShowNothing() = runTest {
        val repository = repository(fail = true)
        observe(repository)
        requests.value = ArtRequest("one", "one|a", "a")
        runCurrent()
        assertNull(repository.artwork.value)
    }
}
