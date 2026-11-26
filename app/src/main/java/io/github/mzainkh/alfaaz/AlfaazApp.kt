package io.github.mzainkh.alfaaz

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.room.Room
import io.github.mzainkh.alfaaz.lyrics.LrclibClient
import io.github.mzainkh.alfaaz.lyrics.LyricsDatabase
import io.github.mzainkh.alfaaz.lyrics.LyricsRepository
import io.github.mzainkh.alfaaz.lyrics.httpClient
import io.github.mzainkh.alfaaz.lyrics.lrclibApi
import io.github.mzainkh.alfaaz.media.Artwork
import io.github.mzainkh.alfaaz.media.ArtworkProcessor
import io.github.mzainkh.alfaaz.media.ArtworkRepository
import io.github.mzainkh.alfaaz.media.LyricsCardService
import io.github.mzainkh.alfaaz.media.NowPlaying
import io.github.mzainkh.alfaaz.media.NowPlayingRepository
import io.github.mzainkh.alfaaz.media.PlayerCommands
import io.github.mzainkh.alfaaz.media.TrackPlaybackState
import io.github.mzainkh.alfaaz.state.LyricsController
import io.github.mzainkh.alfaaz.state.RealLyricsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val USER_AGENT = "Alfaaz/${BuildConfig.VERSION_NAME} (https://github.com/MZainKh/alfaaz)"

class AlfaazApp : Application() {
    val graph by lazy { AppGraph(this) }
}

/** Hand-wired process singletons. */
class AppGraph(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val http = httpClient()

    val settings = Settings(context)

    private val database = Room.databaseBuilder(context, LyricsDatabase::class.java, "lyrics.db")
        // Only a cache lives here.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    private val repository = LyricsRepository(LrclibClient(lrclibApi(USER_AGENT, http = http)), database.lyrics())

    private val processor = ArtworkProcessor(context, http)

    private val player: NowPlaying = object : NowPlaying {
        override val playback: StateFlow<TrackPlaybackState?> = NowPlayingRepository.playback
        override val commands: PlayerCommands = NowPlayingRepository.commands
        override val artwork: StateFlow<Artwork?> =
            ArtworkRepository(NowPlayingRepository.art, scope, processor::process).artwork
    }

    private val live = withDemo(
        Live(RealLyricsController(player.playback, repository, scope, SystemClock::elapsedRealtime), player),
        settings.demoMode.state,
        scope,
        processor::process,
    )

    val controller: LyricsController = live.lyrics
    val nowPlaying: NowPlaying = live.player

    init {
        scope.launch { settings.lyricsCard.state.collect { LyricsCardService.setEnabled(context, it) } }
    }
}

/** What the screens observe: the lyrics and the player they follow. */
class Live(val lyrics: LyricsController, val player: NowPlaying)
