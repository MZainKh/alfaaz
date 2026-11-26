package io.github.mzainkh.alfaaz

import android.app.Application
import android.content.Context
import io.github.mzainkh.alfaaz.state.IdleLyricsController
import io.github.mzainkh.alfaaz.state.LyricsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AlfaazApp : Application() {
    val graph by lazy { AppGraph(this) }
}

/** Hand-wired process singletons. */
class AppGraph(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = Settings(context)
    val controller: LyricsController = demoController(scope) ?: IdleLyricsController
}
