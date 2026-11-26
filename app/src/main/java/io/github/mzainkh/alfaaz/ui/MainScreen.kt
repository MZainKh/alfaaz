package io.github.mzainkh.alfaaz.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mzainkh.alfaaz.AppGraph
import io.github.mzainkh.alfaaz.R

@Composable
fun MainScreen(graph: AppGraph) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val hasAccess = rememberNotificationAccess()
    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Text(
                stringResource(R.string.app_name),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.headlineMedium,
            )
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf(R.string.tab_lyrics, R.string.tab_setup).forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(label)) })
                }
            }
            if (tab == 0) LyricsTab(graph.controller, hasAccess) else SetupTab(graph.settings, hasAccess)
        }
    }
}
