package io.github.mzainkh.alfaaz.car

import android.text.SpannableString
import android.text.Spanned
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.mzainkh.alfaaz.AlfaazApp
import io.github.mzainkh.alfaaz.R
import io.github.mzainkh.alfaaz.media.Artwork
import io.github.mzainkh.alfaaz.state.LyricsUiState
import io.github.mzainkh.alfaaz.throttleLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val MIN_REFRESH_MS = 1_000L
private const val MAX_LIST_ROWS = 6

class LyricsScreen(carContext: CarContext) : Screen(carContext) {
    private val graph = (carContext.applicationContext as AlfaazApp).graph
    private val player = graph.nowPlaying
    private val constraints = carContext.getCarService(ConstraintManager::class.java)
    private val appDrivenRefresh = constraints.isAppDrivenRefreshEnabled
    private val labels = carContext.carLabels()

    private val header = Header.Builder()
        .setTitle(carContext.getString(R.string.car_title))
        .setStartHeaderAction(Action.APP_ICON)
        .addEndHeaderAction(
            Action.Builder()
                .setIcon(icon(R.drawable.ic_refresh))
                .setOnClickListener { graph.controller.refresh() }
                .build(),
        )
        .build()

    private val placeholder = icon(R.drawable.art_placeholder)
    private val actionIcons = mapOf(
        CarActionKind.PLAY to icon(R.drawable.ic_play),
        CarActionKind.PAUSE to icon(R.drawable.ic_pause),
        CarActionKind.NEXT to icon(R.drawable.ic_skip_next),
    )

    /** The car resends the image on every update; building the icon once per track is enough. */
    private var artIcon: Pair<Artwork, CarIcon>? = null

    /** Once lyrics or a status were shown, a loading spinner would count as a new screen. */
    private var shownContent = false

    private var model = modelFor(
        graph.controller.state.value,
        player.playback.value?.playUi(),
        player.artwork.value,
        graph.settings.forceFixedCarRows.state.value,
    )

    init {
        Log.i(
            "Alfaaz",
            "car: api=${carContext.carAppApiLevel} appDrivenRefresh=$appDrivenRefresh " +
                "listLimit=${limit(CarMode.ROLLING)} paneLimit=${limit(CarMode.FIXED)}",
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    graph.controller.state,
                    player.playback.map { it?.playUi() }.distinctUntilChanged(),
                    player.artwork,
                    graph.settings.forceFixedCarRows.state,
                    ::modelFor,
                )
                    .distinctUntilChanged()
                    .throttleLatest(MIN_REFRESH_MS)
                    .collect {
                        model = it
                        invalidate()
                    }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val shown = model
        if (!shown.loading) shownContent = true
        return when (shown.mode) {
            CarMode.FIXED -> pane(shown)
            CarMode.ROLLING -> list(shown)
        }
    }

    private fun pane(model: CarModel<Artwork>): Template {
        val pane = Pane.Builder()
        if (model.loading) {
            pane.setLoading(true)
        } else {
            model.rows.forEach { pane.addRow(row(it, model.art)) }
            pane.setImage(art(model.art))
            model.actions.forEach { spec ->
                pane.addAction(
                    action(spec)
                        .setTitle(carContext.getString(spec.kind.label()))
                        .apply { if (spec.kind != CarActionKind.NEXT) setFlags(Action.FLAG_PRIMARY) }
                        .build(),
                )
            }
        }
        return PaneTemplate.Builder(pane.build()).setHeader(header).build()
    }

    private fun list(model: CarModel<Artwork>): Template {
        val template = ListTemplate.Builder().setHeader(header)
        if (model.loading) return template.setLoading(true).build()
        val items = ItemList.Builder()
        model.rows.forEach { items.addItem(row(it, model.art)) }
        template.setSingleList(items.build())
        // Floating buttons take icons only.
        model.actions.forEach { template.addAction(action(it).setBackgroundColor(CarColor.PRIMARY).build()) }
        return template.build()
    }

    private fun row(spec: RowSpec, art: Artwork?): Row {
        val row = Row.Builder().setTitle(spec.title)
        spec.text?.let { text ->
            row.addText(
                if (!spec.highlight) {
                    text
                } else {
                    SpannableString(text).apply {
                        setSpan(ForegroundCarColorSpan.create(CarColor.PRIMARY), 0, length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                    }
                },
            )
        }
        if (spec.art) row.setImage(art(art), Row.IMAGE_TYPE_LARGE)
        return row.build()
    }

    private fun action(action: CarAction): Action.Builder = Action.Builder()
        .setIcon(actionIcons.getValue(action.kind))
        .setEnabled(action.enabled)
        .setOnClickListener {
            val commands = player.commands
            when (action.kind) {
                CarActionKind.PLAY -> commands.play()
                CarActionKind.PAUSE -> commands.pause()
                CarActionKind.NEXT -> commands.skipToNext()
            }
        }

    private fun art(artwork: Artwork?): CarIcon {
        if (artwork == null) return placeholder
        artIcon?.takeIf { it.first === artwork }?.let { return it.second }
        return CarIcon.Builder(IconCompat.createWithBitmap(artwork.car)).build().also { artIcon = artwork to it }
    }

    private fun icon(resource: Int) = CarIcon.Builder(IconCompat.createWithResource(carContext, resource)).build()

    private fun mode(forceFixed: Boolean) = if (appDrivenRefresh && !forceFixed) CarMode.ROLLING else CarMode.FIXED

    private fun limit(mode: CarMode) = when (mode) {
        CarMode.FIXED -> constraints.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PANE).coerceAtMost(FIXED_ROWS)
        CarMode.ROLLING -> constraints.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST).coerceAtMost(MAX_LIST_ROWS)
    }

    private fun modelFor(state: LyricsUiState, play: PlayUi?, artwork: Artwork?, forceFixed: Boolean): CarModel<Artwork> {
        val mode = mode(forceFixed)
        // The art may still belong to the previous song.
        val art = artwork?.takeIf { it.trackKey == state.trackKey }
        return carModel(state, play, art, mode, limit(mode), shownContent, labels)
    }
}

private fun CarActionKind.label() = when (this) {
    CarActionKind.PLAY -> R.string.action_play
    CarActionKind.PAUSE -> R.string.action_pause
    CarActionKind.NEXT -> R.string.action_next
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
