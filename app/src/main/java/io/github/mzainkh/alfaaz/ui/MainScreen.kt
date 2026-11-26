package io.github.mzainkh.alfaaz.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.mzainkh.alfaaz.AppGraph

/** Now playing, with settings one tap away. Without notification access the app opens on settings. */
@Composable
fun MainScreen(graph: AppGraph) {
    val hasAccess = rememberNotificationAccess()
    var showSettings by rememberSaveable { mutableStateOf(!hasAccess) }
    BackHandler(enabled = showSettings) { showSettings = false }
    if (showSettings) {
        SettingsScreen(graph.settings, hasAccess, onBack = { showSettings = false })
    } else {
        NowPlayingScreen(graph, hasAccess, onSettings = { showSettings = true })
    }
}
