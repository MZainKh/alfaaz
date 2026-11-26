package io.github.mzainkh.alfaaz.ui

import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ICON_PX = 48

/** The player's name and icon, for the "playing on" chip. */
class AppLabel(val name: String, val icon: ImageBitmap?)

/**
 * Read from the package manager. Players are visible to this app through the manifest's `<queries>`
 * entry for media browser services; anything else falls back to a known name or the package name.
 */
@Composable
fun rememberAppLabel(packageName: String): State<AppLabel> {
    val context = LocalContext.current
    return produceState(AppLabel(knownName(packageName), null), packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(packageName, 0)
                AppLabel(pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info).toBitmap(ICON_PX, ICON_PX).asImageBitmap())
            } catch (e: PackageManager.NameNotFoundException) {
                AppLabel(knownName(packageName), null)
            }
        }
    }
}

private fun knownName(packageName: String) = when (packageName) {
    "com.spotify.music" -> "Spotify"
    "com.google.android.apps.youtube.music" -> "YouTube Music"
    else -> packageName
}
