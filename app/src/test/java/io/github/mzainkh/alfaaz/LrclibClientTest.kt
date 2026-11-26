package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.lyrics.LrclibClient
import io.github.mzainkh.alfaaz.lyrics.TrackQuery
import io.github.mzainkh.alfaaz.lyrics.lrclibApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class LrclibClientTest {
    private val server = MockWebServer()
    private val pauses = mutableListOf<Long>()
    private val client by lazy {
        LrclibClient(lrclibApi("Test/1 (url)", server.url("/").toString()), pause = { pauses += it }, jitter = { 0.1 })
    }
    private val query = TrackQuery("Song (Official Video)", "Someone", "Album", 200)

    @After
    fun tearDown() = server.shutdown()

    private fun reply(body: String, code: Int = 200) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private fun track(name: String = "Song", duration: Int = 200, synced: String? = null) =
        """{"trackName":"$name","artistName":"Someone","duration":$duration,"instrumental":false,""" +
            """"syncedLyrics":${synced?.let { "\"$it\"" }},"plainLyrics":"p","unknownField":1}"""

    @Test
    fun sendsTheCleanedQueryAndIdentifiesItself() = runTest {
        reply(track(synced = "[00:01.00]a"))
        assertEquals("[00:01.00]a", client.find(query)?.syncedLyrics)
        val request = server.takeRequest()
        val url = request.requestUrl!!
        assertEquals("/api/get", url.encodedPath)
        assertEquals("Song", url.queryParameter("track_name"))
        assertEquals("Someone", url.queryParameter("artist_name"))
        assertEquals("Album", url.queryParameter("album_name"))
        assertEquals("200", url.queryParameter("duration"))
        assertEquals("Test/1 (url)", request.getHeader("User-Agent"))
    }

    @Test
    fun omitsUnknownAlbumAndDuration() = runTest {
        reply(track())
        client.find(query.copy(album = null, durationSec = null))
        val url = server.takeRequest().requestUrl!!
        assertEquals(setOf("track_name", "artist_name"), url.queryParameterNames)
    }

    @Test
    fun searchesAfterA404AndPrefersSyncedLyricsOfAboutTheSameLength() = runTest {
        reply("""{"name":"TrackNotFound"}""", 404)
        reply(
            "[" + listOf(
                track(duration = 200),
                track(duration = 202, synced = "[00:01.00]right"),
                track(duration = 260, synced = "[00:01.00]too long"),
                track(name = "Another", synced = "[00:01.00]other song"),
            ).joinToString() + "]",
        )
        assertEquals("[00:01.00]right", client.find(query)?.syncedLyrics)
        server.takeRequest()
        assertEquals("/api/search", server.takeRequest().requestUrl!!.encodedPath)
    }

    @Test
    fun fallsBackToAFreeTextSearchThenGivesUp() = runTest {
        reply("{}", 404)
        reply("[]")
        reply("[]")
        assertNull(client.find(query))
        server.takeRequest()
        server.takeRequest()
        assertEquals("Someone Song", server.takeRequest().requestUrl!!.queryParameter("q"))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun retriesWithoutFeaturedArtists() = runTest {
        reply("{}", 404)
        reply("[]")
        reply("[]")
        reply("[${track()}]")
        assertEquals("Song", client.find(query.copy(title = "Song (feat. Guest)"))?.trackName)
        repeat(3) { server.takeRequest() }
        assertEquals("Song", server.takeRequest().requestUrl!!.queryParameter("track_name"))
    }

    @Test
    fun waitsAsAskedAfterA429() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "2"))
        reply(track())
        assertEquals("Song", client.find(query)?.trackName)
        assertEquals(listOf(2_200L), pauses)
    }

    @Test(expected = IOException::class)
    fun reportsServerErrors() = runTest {
        reply("", 500)
        client.find(query)
    }
}
