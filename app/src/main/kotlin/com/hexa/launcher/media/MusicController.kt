package com.hexa.launcher.media

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import com.hexa.core.music.MusicAccess
import com.hexa.core.music.MusicSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

data class MusicState(
    val snapshot: MusicSnapshot? = null,
    val art: Bitmap? = null,
    val access: MusicAccess = MusicAccess.MEDIA_KEYS_ONLY,
    val sessionPackage: String? = null,
)

/**
 * Follows the active media session through MediaSessionManager/MediaController.
 *
 * Listeners are registered only between [start] and [stop] (the launcher's onStart/onStop), so no
 * work happens while another app is in front. Without notification access the controls still work
 * by sending media key events through AudioManager, which needs no permission.
 */
class MusicController(
    context: Context,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    private val appContext = context.applicationContext
    private val sessionManager = appContext.getSystemService(MediaSessionManager::class.java)!!
    private val audioManager = appContext.getSystemService(AudioManager::class.java)!!
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)!!
    private val listenerComponent = ComponentName(appContext, MediaListenerService::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var started = false
    private var listening = false
    private var artJob: Job? = null
    private var artSource: Bitmap? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> onSessions(list) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // A paused player may lose priority to another one that starts playing.
            if (state?.state != PlaybackState.STATE_PLAYING) reselect() else publish()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()

        override fun onSessionDestroyed() {
            detach()
            reselect()
        }
    }

    fun hasAccess(): Boolean = if (Build.VERSION.SDK_INT >= 27) {
        notificationManager.isNotificationListenerAccessGranted(listenerComponent)
    } else {
        @Suppress("DEPRECATION")
        val flat = Settings.Secure.getString(appContext.contentResolver, "enabled_notification_listeners").orEmpty()
        flat.split(':').any { ComponentName.unflattenFromString(it) == listenerComponent }
    }

    /** Intent that opens the system screen where the user grants notification access. */
    fun accessSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent.flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

    fun start() {
        started = true
        if (!hasAccess()) {
            _state.value = MusicState(access = MusicAccess.MEDIA_KEYS_ONLY)
            return
        }
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
            listening = true
            onSessions(sessionManager.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            _state.value = MusicState(access = MusicAccess.MEDIA_KEYS_ONLY)
        }
    }

    fun stop() {
        started = false
        if (listening) {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
            listening = false
        }
        detach()
    }

    fun playPause() {
        val c = controller
        if (c != null) {
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
        } else {
            mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    fun next() = controller?.transportControls?.skipToNext() ?: mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)

    fun previous() = controller?.transportControls?.skipToPrevious() ?: mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    fun seekToFraction(fraction: Float) {
        val c = controller ?: return
        val duration = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: return
        if (duration > 0) c.transportControls.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
    }

    /** Opens the player's own UI. Returns false when there is no session activity. */
    fun openPlayer(): Boolean {
        val pi: PendingIntent = controller?.sessionActivity ?: return false
        return try {
            pi.send()
            true
        } catch (e: PendingIntent.CanceledException) {
            false
        }
    }

    private fun mediaKey(code: Int) {
        val now = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    private fun reselect() {
        if (!listening) return
        try {
            onSessions(sessionManager.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            detach()
            _state.value = MusicState(access = MusicAccess.MEDIA_KEYS_ONLY)
        }
    }

    private fun onSessions(list: List<MediaController>?) {
        if (!started) return
        val sessions = list.orEmpty()
        val pick = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
        if (pick?.sessionToken != controller?.sessionToken) {
            detach()
            controller = pick
            pick?.registerCallback(callback, handler)
        }
        publish()
    }

    private fun detach() {
        controller?.unregisterCallback(callback)
        controller = null
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            artJob?.cancel()
            artSource = null
            _state.value = MusicState(access = MusicAccess.GRANTED)
            return
        }
        val md = c.metadata
        val ps = c.playbackState
        val actions = ps?.actions ?: 0L
        fun can(flag: Long) = actions == 0L || actions and flag != 0L
        val snapshot = MusicSnapshot(
            packageName = c.packageName,
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            isPlaying = ps?.state == PlaybackState.STATE_PLAYING || ps?.state == PlaybackState.STATE_BUFFERING,
            position = if (ps == null || ps.position == PlaybackState.PLAYBACK_POSITION_UNKNOWN) -1 else ps.position,
            positionUpdatedAt = ps?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            canPlayPause = can(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE),
            canSkipNext = can(PlaybackState.ACTION_SKIP_TO_NEXT),
            canSkipPrevious = can(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
            canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L,
        )
        val rawArt = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val current = _state.value
        val sameSession = current.sessionPackage == c.packageName
        _state.value = MusicState(snapshot, if (sameSession && rawArt === artSource) current.art else null, MusicAccess.GRANTED, c.packageName)
        if (rawArt !== artSource) {
            artSource = rawArt
            artJob?.cancel()
            if (rawArt != null) {
                artJob = scope.launch {
                    val scaled = withContext(io) { downscale(rawArt, 320) }
                    if (artSource === rawArt) _state.value = _state.value.copy(art = scaled)
                }
            }
        }
    }

    private fun downscale(src: Bitmap, maxSide: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxSide) return src
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true)
    }
}
