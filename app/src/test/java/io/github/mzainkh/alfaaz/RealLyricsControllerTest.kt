@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.mzainkh.alfaaz

import io.github.mzainkh.alfaaz.lyrics.LrclibClient
import io.github.mzainkh.alfaaz.lyrics.LyricsRepository
import io.github.mzainkh.alfaaz.media.TrackPlaybackState
import io.github.mzainkh.alfaaz.state.LyricsStatus
import io.github.mzainkh.alfaaz.state.RealLyricsController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RealLyricsControllerTest {
    private val player = MutableStateFlow<TrackPlaybackState?>(null)
    private val api = FakeApi()
    private val dao = FakeDao()

    private fun TestScope.controller(observed: Boolean = true) =
        RealLyricsController(player, LyricsRepository(LrclibClient(api), dao), backgroundScope) { currentTime }
            .also {
                if (observed) backgroundScope.launch { it.state.collect {} }
                runCurrent()
            }

    private fun TestScope.play(title: String = "Song") {
        player.value = playback(positionMs = 0, updateElapsedMs = currentTime).copy(title = title)
        runCurrent()
    }

    @Test
    fun loadsLyricsAndFollowsTheSong() = runTest {
        val controller = controller()
        assertEquals(LyricsStatus.Idle, controller.state.value.status)
        play()
        assertEquals(LyricsStatus.Loading, controller.state.value.status)
        advanceTimeBy(500)
        assert(controller.state.value.status is LyricsStatus.Ready)
        assertEquals(-1, controller.state.value.activeIndex)
        advanceTimeBy(600)
        assertEquals(0, controller.state.value.activeIndex)
    }

    @Test
    fun neverShowsThePreviousSongsLyricsUnderANewTitle() = runTest {
        val controller = controller()
        play("One")
        advanceTimeBy(500)
        play("Two")
        assertEquals("Two", controller.state.value.title)
        assertEquals(LyricsStatus.Loading, controller.state.value.status)
        advanceTimeBy(500)
        assert(controller.state.value.status is LyricsStatus.Ready)
    }

    @Test
    fun skippingQuicklyOnlyAsksForTheSongThatStays() = runTest {
        controller()
        play("One")
        advanceTimeBy(100)
        play("Two")
        advanceTimeBy(500)
        assertEquals(1, api.requests)
    }

    @Test
    fun offsetTapsAddUpAndAreSaved() = runTest {
        val controller = controller()
        play()
        advanceTimeBy(1_200)
        controller.nudgeOffset(500)
        controller.nudgeOffset(500)
        runCurrent()
        assertEquals(1_000, controller.state.value.offsetMs)
        assertEquals(-1, controller.state.value.activeIndex)
        assertEquals(1_000, dao.rows.values.single().offsetMs)
        controller.resetOffset()
        runCurrent()
        assertEquals(0, controller.state.value.offsetMs)
    }

    @Test
    fun refreshRecoversFromAFailure() = runTest {
        val controller = controller()
        api.offline = true
        play()
        advanceTimeBy(500)
        assertEquals(LyricsStatus.Failed, controller.state.value.status)
        api.offline = false
        controller.refresh()
        runCurrent()
        assert(controller.state.value.status is LyricsStatus.Ready)
    }

    @Test
    fun aReturningScreenSkipsTheLoadingState() = runTest {
        val controller = controller(observed = false)
        val first = backgroundScope.launch { controller.state.collect {} }
        play()
        advanceTimeBy(500)
        first.cancel()
        advanceTimeBy(10_000)
        val seen = mutableListOf<LyricsStatus>()
        backgroundScope.launch { controller.state.collect { seen += it.status } }
        runCurrent()
        assert(seen.isNotEmpty() && seen.all { it is LyricsStatus.Ready }) { seen.toString() }
        assertEquals(1, api.requests)
    }

    @Test
    fun cachedLyricsSkipTheSettleWait() = runTest {
        controller()
        play()
        advanceTimeBy(500)
        val fresh = controller()
        assert(fresh.state.value.status is LyricsStatus.Ready)
        assertEquals(1, api.requests)
    }

    @Test
    fun refreshKeepsTheSavedOffset() = runTest {
        controller().also { play(); advanceTimeBy(500); it.nudgeOffset(700); runCurrent() }
        val fresh = controller()
        api.delayMs = 1_000
        fresh.refresh()
        runCurrent()
        assertEquals(LyricsStatus.Loading, fresh.state.value.status)
        assertEquals(700, fresh.state.value.offsetMs)
        advanceTimeBy(1_500)
        assert(fresh.state.value.status is LyricsStatus.Ready)
        assertEquals(700, fresh.state.value.offsetMs)
    }

    @Test
    fun offsetsStayWithTheirSongs() = runTest {
        val controller = controller()
        play("One")
        advanceTimeBy(500)
        controller.nudgeOffset(500)
        play("Two")
        advanceTimeBy(500)
        controller.nudgeOffset(-1_000)
        play("One")
        assertEquals(500, controller.state.value.offsetMs)
        assert(controller.state.value.status is LyricsStatus.Ready)
        assertEquals(2, api.requests)
    }

    @Test
    fun staysIdleWithoutAnObserver() = runTest {
        controller(observed = false)
        play()
        advanceTimeBy(5_000)
        assertEquals(0, api.requests)
    }
}
