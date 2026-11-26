package io.github.mzainkh.alfaaz.media

import android.content.ComponentName
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
import io.github.mzainkh.alfaaz.BuildConfig

private const val TAG = "Alfaaz"

/**
 * Notification access is what allows [MediaSessionManager.getActiveSessions]. Notifications themselves
 * are never read.
 */
class MediaSessionListenerService : NotificationListenerService() {
    private class Watched(val controller: MediaController, val callback: MediaController.Callback)

    private val sessions by lazy { getSystemService(MediaSessionManager::class.java) }
    private val component by lazy { ComponentName(this, javaClass) }
    private val handler = Handler(Looper.getMainLooper())

    /** In the system's order: most relevant first. */
    private val watched = LinkedHashMap<MediaSession.Token, Watched>()
    private var selected: MediaSession.Token? = null
    private val onSessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { watch(it.orEmpty()) }

    override fun onListenerConnected() {
        try {
            sessions.addOnActiveSessionsChangedListener(onSessionsChanged, component, handler)
            watch(sessions.getActiveSessions(component))
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
        super.onDestroy()
    }

    private fun release() {
        sessions.removeOnActiveSessionsChangedListener(onSessionsChanged)
        watch(emptyList())
    }

    private fun watch(controllers: List<MediaController>) {
        val previous = LinkedHashMap(watched)
        watched.clear()
        controllers.forEach { controller ->
            val token = controller.sessionToken
            watched[token] = previous.remove(token) ?: Watched(controller, callbackFor(controller))
                .also { controller.registerCallback(it.callback, handler) }
        }
        previous.values.forEach { it.controller.unregisterCallback(it.callback) }
        publish()
    }

    private fun callbackFor(controller: MediaController) = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (BuildConfig.DEBUG) {
                val id = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                Log.d(TAG, "${controller.packageName} id=$id keys=${metadata?.keySet()}")
            }
            publish()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()

        override fun onSessionDestroyed() {
            watched.remove(controller.sessionToken)?.let { it.controller.unregisterCallback(it.callback) }
            publish()
        }
    }

    private fun publish() {
        val controllers = watched.values.map { it.controller }
        val choice = SessionSelector.select(
            sessions = controllers,
            previous = controllers.firstOrNull { it.sessionToken == selected },
            packageName = { it.packageName },
            isPlaying = { it.playbackState?.state == PlaybackState.STATE_PLAYING },
        )
        selected = choice?.sessionToken
        NowPlayingRepository.publish(choice?.trackState())
    }

    private fun MediaController.trackState(): TrackPlaybackState? {
        val data = metadata ?: return null
        val title = data.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = data.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: data.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        if (!TrackFilter.isSong(data.getString(MediaMetadata.METADATA_KEY_MEDIA_ID), title, artist)) return null
        val playback = playbackState
        return TrackPlaybackState(
            packageName = packageName,
            title = title!!,
            artist = artist!!,
            album = data.getString(MediaMetadata.METADATA_KEY_ALBUM)?.ifBlank { null },
            durationMs = data.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
            isPlaying = playback?.state == PlaybackState.STATE_PLAYING,
            positionMs = playback?.position?.coerceAtLeast(0) ?: 0,
            // Some players never set the update time.
            updateElapsedMs = playback?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = playback?.playbackSpeed ?: 1f,
        )
    }
}
