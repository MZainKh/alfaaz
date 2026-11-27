package io.github.mzainkh.alfaaz.media

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.SystemClock
import android.service.media.MediaBrowserService
import android.util.Log
import io.github.mzainkh.alfaaz.AlfaazApp
import io.github.mzainkh.alfaaz.R
import io.github.mzainkh.alfaaz.state.LyricsUiState
import io.github.mzainkh.alfaaz.throttleLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "Alfaaz"
private const val ANDROID_AUTO = "com.google.android.projection.gearhead"
private const val ROOT = "root"
private const val LYRICS_ITEM = "lyrics"
private const val MIN_UPDATE_MS = 500L

/**
 * A silent media session that mirrors the player, so that Android Auto's media card shows the lyrics
 * beside the map. It plays no audio, takes no audio focus and passes every control to the real player.
 */
class LyricsCardService : MediaBrowserService() {
    /** What the card shows apart from the playback state. */
    private data class Card(val text: CardText, val art: Artwork?, val durationMs: Long)

    companion object {
        /** Android Auto lists Alfaaz as a music app only while this service is enabled. */
        fun setEnabled(context: Context, enabled: Boolean) {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, LyricsCardService::class.java),
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    private val graph by lazy { (application as AlfaazApp).graph }
    private val labels by lazy { cardLabels() }
    private val scope = MainScope()
    private lateinit var session: MediaSession

    override fun onCreate() {
        super.onCreate()
        session = MediaSession(this, TAG)
        session.setCallback(Forwarder())
        sessionToken = session.sessionToken
        val player = graph.nowPlaying
        // Android Auto reads the session as soon as it connects, so fill it before that.
        session.setMetadata(metadata(card(graph.controller.state.value, player.playback.value, player.artwork.value)))
        showPlayback(player.playback.value)
        scope.launch {
            combine(graph.controller.state, player.playback, player.artwork, ::card)
                .distinctUntilChanged()
                .throttleLatest(MIN_UPDATE_MS)
                .map(::metadata)
                // Drawing the artwork stays off the main thread.
                .flowOn(Dispatchers.Default)
                .collect { session.setMetadata(it) }
        }
        scope.launch { player.playback.collect { showPlayback(it) } }
    }

    /** Anyone else is refused, so the session only becomes active for the car. */
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        if (clientPackageName != ANDROID_AUTO) return null
        Log.i(TAG, "card: Android Auto connected")
        session.isActive = true
        return BrowserRoot(ROOT, null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowser.MediaItem>>) {
        val description = MediaDescription.Builder().setMediaId(LYRICS_ITEM).setTitle(getString(R.string.card_item)).build()
        val items = mutableListOf<MediaBrowser.MediaItem>()
        if (parentId == ROOT) items += MediaBrowser.MediaItem(description, MediaBrowser.MediaItem.FLAG_PLAYABLE)
        result.sendResult(items)
    }

    override fun onDestroy() {
        scope.cancel()
        session.release()
        super.onDestroy()
    }

    private fun card(state: LyricsUiState, playback: TrackPlaybackState?, art: Artwork?) = Card(
        cardText(state, labels),
        // The art may still belong to the previous song.
        art?.takeIf { it.trackKey == state.trackKey },
        playback?.durationMs ?: 0,
    )

    private fun metadata(card: Card): MediaMetadata {
        val text = card.text
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, text.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, text.subtitle)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, text.title)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, text.subtitle)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, card.durationMs)
        val image = card.art?.image
        if (image != null || text.line.isNotEmpty()) {
            metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, drawCardArt(image, text.line, text.next))
        }
        return metadata.build()
    }

    private fun showPlayback(playback: TrackPlaybackState?) {
        val playing = playback?.isPlaying == true
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(cardActions(playback?.transport ?: Transport()))
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    playback?.positionMs ?: 0,
                    playback?.takeIf { it.isPlaying }?.speed ?: 0f,
                    playback?.updateElapsedMs ?: SystemClock.elapsedRealtime(),
                )
                .build(),
        )
    }

    private fun cardLabels() = CardLabels(
        appName = getString(R.string.app_name),
        nothingPlaying = getString(R.string.status_nothing_playing),
        loading = getString(R.string.status_loading),
        failed = getString(R.string.status_failed),
        notFound = getString(R.string.status_not_found),
        instrumental = getString(R.string.status_instrumental),
        unsynced = getString(R.string.status_unsynced),
    )

    /** The card's buttons and voice requests all go to the player being mirrored. */
    private inner class Forwarder : MediaSession.Callback() {
        private val commands get() = graph.nowPlaying.commands

        override fun onPlay() = commands.play()
        override fun onPause() = commands.pause()
        override fun onStop() = commands.pause()
        override fun onSkipToNext() = commands.skipToNext()
        override fun onSkipToPrevious() = commands.skipToPrevious()
        override fun onSeekTo(pos: Long) = commands.seekTo(pos)
        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) = commands.play()
        override fun onPlayFromSearch(query: String?, extras: Bundle?) = commands.play()
    }
}
