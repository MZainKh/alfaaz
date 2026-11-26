package io.github.mzainkh.alfaaz.media

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.graphics.get
import io.github.mzainkh.alfaaz.BuildConfig
import java.util.concurrent.Executors

private const val TAG = "Alfaaz"

/**
 * Notification access is what allows [MediaSessionManager.getActiveSessions]. Notifications themselves
 * are never read.
 */
class MediaSessionListenerService : NotificationListenerService() {
    /** Keeps the latest metadata and state from the callbacks, so publishing never asks the player again. */
    private inner class Watched(val controller: MediaController) : MediaController.Callback() {
        var metadata: MediaMetadata? = controller.metadata
        var state: PlaybackState? = controller.playbackState

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (BuildConfig.DEBUG) {
                val id = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                Log.d(TAG, "${controller.packageName} id=$id keys=${metadata?.keySet()}")
            }
            this.metadata = metadata
            publish()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            this.state = state
            publish()
        }

        override fun onSessionDestroyed() {
            watched.remove(controller.sessionToken)?.let { controller.unregisterCallback(it) }
            publish()
        }
    }

    private val sessions by lazy { getSystemService(MediaSessionManager::class.java) }
    private val component by lazy { ComponentName(this, javaClass) }
    private val handler = Handler(Looper.getMainLooper())

    /** In the system's order: most relevant first. */
    private val watched = LinkedHashMap<MediaSession.Token, Watched>()
    private var selected: MediaSession.Token? = null
    private val onSessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { watch(it.orEmpty()) }

    /** Commands are binder calls to the player: run them in order, off the main thread. */
    private val commandThread = Executors.newSingleThreadExecutor()

    @Volatile
    private var controls: MediaController.TransportControls? = null

    private val commands = object : PlayerCommands {
        override fun play() = send { play() }
        override fun pause() = send { pause() }
        override fun skipToNext() = send { skipToNext() }
        override fun skipToPrevious() = send { skipToPrevious() }
        override fun seekTo(positionMs: Long) = send { seekTo(positionMs) }
    }

    override fun onListenerConnected() {
        try {
            sessions.addOnActiveSessionsChangedListener(onSessionsChanged, component, handler)
            watch(sessions.getActiveSessions(component))
            NowPlayingRepository.attach(commands)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification access was withdrawn", e)
        }
    }

    override fun onListenerDisconnected() {
        release()
        requestRebind(component)
    }

    override fun onDestroy() {
        release()
        commandThread.shutdown()
        super.onDestroy()
    }

    private fun release() {
        NowPlayingRepository.detach(commands)
        sessions.removeOnActiveSessionsChangedListener(onSessionsChanged)
        watch(emptyList())
    }

    private fun send(command: MediaController.TransportControls.() -> Unit) {
        val target = controls ?: return
        try {
            commandThread.execute {
                try {
                    target.command()
                } catch (e: RuntimeException) {
                    Log.w(TAG, "The player rejected a command", e)
                }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Command after shutdown", e)
        }
    }

    private fun watch(controllers: List<MediaController>) {
        val previous = LinkedHashMap(watched)
        watched.clear()
        SessionSelector.others(controllers, packageName) { it.packageName }.forEach { controller ->
            val token = controller.sessionToken
            watched[token] = previous.remove(token)
                ?: Watched(controller).also { controller.registerCallback(it, handler) }
        }
        previous.values.forEach { it.controller.unregisterCallback(it) }
        publish()
    }

    private fun publish() {
        val all = watched.values.toList()
        val choice = SessionSelector.select(
            sessions = all,
            previous = all.firstOrNull { it.controller.sessionToken == selected },
            packageName = { it.controller.packageName },
            isPlaying = { it.state?.state == PlaybackState.STATE_PLAYING },
        )
        selected = choice?.controller?.sessionToken
        controls = choice?.controller?.transportControls
        val track = choice?.trackState()
        NowPlayingRepository.publish(track, track?.let { choice.metadata?.artRequest(it.query.cacheKey) })
    }

    private fun MediaMetadata.artRequest(trackKey: String): ArtRequest<ArtSource<Bitmap>>? {
        val source = chooseArt(image = ::getBitmap, text = ::getString) ?: return null
        val identity = when (source) {
            is ArtSource.Image -> source.image.fingerprint()
            is ArtSource.Link -> source.uri
        }
        return ArtRequest(trackKey, "$trackKey|$identity", source)
    }

    /**
     * Players resend metadata, as new bitmap objects, for the same art. Size plus a few pixels tells a
     * new image from a resent one without preparing it again.
     */
    private fun Bitmap.fingerprint(): String = try {
        val samples = listOf(width / 2 to height / 2, width / 4 to height / 4, 3 * width / 4 to 3 * height / 4)
            .joinToString(",") { (x, y) -> this[x, y].toString(16) }
        "${width}x$height:$samples"
    } catch (e: IllegalStateException) {
        // Hardware bitmaps cannot be read here.
        "${width}x$height@${System.identityHashCode(this)}"
    }

    private fun Watched.trackState(): TrackPlaybackState? {
        val data = metadata ?: return null
        val title = data.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = data.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: data.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        if (!TrackFilter.isSong(data.getString(MediaMetadata.METADATA_KEY_MEDIA_ID), title, artist)) return null
        val playback = state
        return TrackPlaybackState(
            packageName = controller.packageName,
            title = title!!,
            artist = artist!!,
            album = data.getString(MediaMetadata.METADATA_KEY_ALBUM)?.ifBlank { null },
            durationMs = data.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
            isPlaying = playback?.state == PlaybackState.STATE_PLAYING,
            positionMs = playback?.position?.coerceAtLeast(0) ?: 0,
            // Some players never set the update time.
            updateElapsedMs = playback?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = playback?.playbackSpeed ?: 1f,
            transport = Transport.from(playback?.actions ?: 0),
        )
    }
}
