package io.github.mzainkh.alfaaz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mzainkh.alfaaz.AppGraph
import io.github.mzainkh.alfaaz.R

@Composable
fun MainScreen(graph: AppGraph) {
    val forceFixed by graph.settings.forceFixedCarRows.collectAsStateWithLifecycle()
    Scaffold { insets ->
        Column(
            Modifier.padding(insets).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            SwitchRow(
                stringResource(R.string.setting_force_fixed),
                stringResource(R.string.setting_force_fixed_hint),
                forceFixed,
                graph.settings::setForceFixedCarRows,
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked, onChange)
    }
}
