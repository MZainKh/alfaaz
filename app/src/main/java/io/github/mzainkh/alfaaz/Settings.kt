package io.github.mzainkh.alfaaz

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val forceFixed = MutableStateFlow(prefs.getBoolean(FORCE_FIXED, false))

    /** Forces the fixed-title car layout even on hosts that allow a rolling list. */
    val forceFixedCarRows: StateFlow<Boolean> = forceFixed.asStateFlow()

    fun setForceFixedCarRows(value: Boolean) {
        prefs.edit { putBoolean(FORCE_FIXED, value) }
        forceFixed.value = value
    }

    private companion object {
        const val FORCE_FIXED = "force_fixed_car_rows"
    }
}
