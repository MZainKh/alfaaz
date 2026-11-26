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

    val nowPlaying: NowPlaying = object : NowPlaying {
        override val playback: StateFlow<TrackPlaybackState?> = NowPlayingRepository.playback
        override val commands: PlayerCommands = NowPlayingRepository.commands
        override val artwork: StateFlow<Artwork?> =
            ArtworkRepository(NowPlayingRepository.art, scope, ArtworkProcessor(context, http)::process).artwork
    }

    val controller: LyricsController = withDemo(
        RealLyricsController(nowPlaying.playback, repository, scope, SystemClock::elapsedRealtime),
        settings.demoMode.state,
        scope,
    )
}
