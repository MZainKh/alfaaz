package io.github.mzainkh.alfaaz.car

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.mzainkh.alfaaz.AlfaazApp
import io.github.mzainkh.alfaaz.R
import io.github.mzainkh.alfaaz.state.LyricsUiState
import io.github.mzainkh.alfaaz.throttleLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val MIN_REFRESH_MS = 1_000L
private const val MAX_ROWS = 6

class LyricsScreen(carContext: CarContext) : Screen(carContext) {
    private val graph = (carContext.applicationContext as AlfaazApp).graph
    private val constraints = carContext.getCarService(ConstraintManager::class.java)
    private val appDrivenRefresh = constraints.isAppDrivenRefreshEnabled
    private val labels = carContext.carLabels()

    private val header = Header.Builder()
        .setTitle(carContext.getString(R.string.car_title))
        .setStartHeaderAction(Action.APP_ICON)
        .addEndHeaderAction(
            Action.Builder()
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_refresh)).build())
                .setOnClickListener { graph.controller.refresh() }
                .build(),
        )
        .build()

    private var rows = rowsFor(graph.controller.state.value, graph.settings.forceFixedCarRows.state.value)

    init {
        Log.i("Alfaaz", "car: api=${carContext.carAppApiLevel} appDrivenRefresh=$appDrivenRefresh listLimit=${listLimit()}")
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(graph.controller.state, graph.settings.forceFixedCarRows.state, ::rowsFor)
                    .distinctUntilChanged()
                    .throttleLatest(MIN_REFRESH_MS)
                    .collect {
                        rows = it
                        invalidate()
                    }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
        rows.forEach { spec ->
            val row = Row.Builder().setTitle(spec.title)
            spec.text?.let { row.addText(it) }
            items.addItem(row.build())
        }
        return ListTemplate.Builder().setHeader(header).setSingleList(items.build()).build()
    }

    private fun listLimit() =
        constraints.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST).coerceAtMost(MAX_ROWS)

    private fun rowsFor(state: LyricsUiState, forceFixed: Boolean): List<RowSpec> {
        val mode = if (appDrivenRefresh && !forceFixed) CarMode.ROLLING else CarMode.FIXED
        return carRows(state, mode, listLimit(), labels)
    }
}

private fun CarContext.carLabels() = CarLabels(
    nowPlaying = getString(R.string.car_now_playing),
    now = getString(R.string.car_now),
    next = getString(R.string.car_next),
    then = getString(R.string.car_then),
    nothingPlaying = getString(R.string.status_nothing_playing),
    loading = getString(R.string.status_loading),
    failed = getString(R.string.status_failed),
    notFound = getString(R.string.status_not_found),
    instrumental = getString(R.string.status_instrumental),
    unsynced = getString(R.string.status_unsynced),
)
