package io.github.mzainkh.alfaaz

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Forces the fixed-title car layout even on hosts that allow a rolling list. */
    val forceFixedCarRows = Flag("force_fixed_car_rows")

    /** Lists Alfaaz as a music app in the car, so its media card can show the lyrics. */
    val lyricsCard = Flag("lyrics_card")

    /** Debug builds only: placeholder songs instead of the real player. */
    val demoMode = Flag("demo_mode")

    inner class Flag(private val key: String) {
        private val current = MutableStateFlow(prefs.getBoolean(key, false))
        val state: StateFlow<Boolean> = current.asStateFlow()

        fun set(value: Boolean) {
            prefs.edit { putBoolean(key, value) }
            current.value = value
        }
    }
}
