package io.github.mzainkh.alfaaz.ui

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Always dark: the now-playing screen sits on darkened album art, and settings match it. */
@Composable
fun AlfaazTheme(content: @Composable () -> Unit) {
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        darkColorScheme(primary = Color(0xFFFFD54F), onPrimary = Color.Black)
    }
    MaterialTheme(colorScheme = colors, content = content)
}
