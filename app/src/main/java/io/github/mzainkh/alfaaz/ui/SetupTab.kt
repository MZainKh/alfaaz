package io.github.mzainkh.alfaaz.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mzainkh.alfaaz.BuildConfig
import io.github.mzainkh.alfaaz.R
import io.github.mzainkh.alfaaz.Settings
import android.provider.Settings as SystemSettings

/** Checked again on every resume, because the user grants it on a system screen. */
@Composable
fun rememberNotificationAccess(): Boolean {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasNotificationAccess()) }
    LifecycleResumeEffect(context) {
        granted = context.hasNotificationAccess()
        onPauseOrDispose {}
    }
    return granted
}

private fun Context.hasNotificationAccess() =
    packageName in NotificationManagerCompat.getEnabledListenerPackages(this)

@Composable
fun PermissionCard() {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.access_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.access_body))
            Button(onClick = { context.startActivity(Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                Text(stringResource(R.string.access_open))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Text(stringResource(R.string.access_restricted), style = MaterialTheme.typography.bodySmall)
                TextButton(
                    onClick = {
                        val uri = "package:${context.packageName}".toUri()
                        context.startActivity(Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                    },
                ) { Text(stringResource(R.string.access_app_info)) }
            }
        }
    }
}

@Composable
fun SetupTab(settings: Settings, hasAccess: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        if (hasAccess) {
            Text(stringResource(R.string.access_granted), Modifier.padding(16.dp))
        } else {
            PermissionCard()
        }
        SwitchRow(R.string.setting_force_fixed, R.string.setting_force_fixed_hint, settings.forceFixedCarRows)
        if (BuildConfig.DEBUG) {
            SwitchRow(R.string.setting_demo, R.string.setting_demo_hint, settings.demoMode)
        }
        SelectionContainer {
            Column {
                Help(R.string.help_dhu_title, R.string.help_dhu_body)
                Help(R.string.help_car_title, R.string.help_car_body)
                Help(R.string.help_safety_title, R.string.help_safety_body)
            }
        }
    }
}

@Composable
private fun SwitchRow(@StringRes title: Int, @StringRes hint: Int, flag: Settings.Flag) {
    val checked by flag.state.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(hint), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = flag::set)
    }
}

@Composable
private fun Help(@StringRes title: Int, @StringRes body: Int) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
    }
}
