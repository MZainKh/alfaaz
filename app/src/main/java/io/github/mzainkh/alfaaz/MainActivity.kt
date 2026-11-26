package io.github.mzainkh.alfaaz

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.mzainkh.alfaaz.ui.AlfaazTheme
import io.github.mzainkh.alfaaz.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Light icons over the dark screens.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val graph = (application as AlfaazApp).graph
        setContent { AlfaazTheme { MainScreen(graph) } }
    }
}
