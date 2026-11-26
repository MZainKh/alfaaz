package io.github.mzainkh.alfaaz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.mzainkh.alfaaz.ui.AlfaazTheme
import io.github.mzainkh.alfaaz.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as AlfaazApp).graph
        setContent { AlfaazTheme { MainScreen(graph) } }
    }
}
