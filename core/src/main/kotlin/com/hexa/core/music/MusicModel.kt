package com.hexa.core.music

/** Playback state as reported by the active media session, independent of Android classes. */
data class MusicSnapshot(
    val packageName: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val isPlaying: Boolean,
    /** Position in ms at [positionUpdatedAt], or -1 when unknown. */
    val position: Long,
    /** Monotonic clock in ms (Android: SystemClock.elapsedRealtime, as reported by PlaybackState). */
    val positionUpdatedAt: Long,
    val speed: Float,
    /** Duration in ms, or <= 0 when unknown (live streams, some players). */
    val duration: Long,
    val canPlayPause: Boolean,
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
    val canSeek: Boolean,
) {
    val hasProgress: Boolean get() = duration > 0 && position >= 0

    /** Extrapolated position at [now] (same clock as [positionUpdatedAt]), clamped to the duration. */
    fun positionAt(now: Long): Long {
        if (position < 0) return -1
        val elapsed = if (isPlaying) ((now - positionUpdatedAt) * speed).toLong().coerceAtLeast(0) else 0
        val p = position + elapsed
        return if (duration > 0) p.coerceIn(0, duration) else p
    }

    fun progressAt(now: Long): Float = if (!hasProgress) 0f else positionAt(now).toFloat() / duration.toFloat()
}

enum class MusicAccess {
    /** Notification access granted: metadata and session controls available. */
    GRANTED,

    /** No notification access: only media-key controls (play/pause/next/previous) are possible. */
    MEDIA_KEYS_ONLY,
}

/** What the expanded widget shows; computed from the snapshot so the UI stays declarative. */
data class MusicDisplay(
    val title: String,
    val subtitle: String,
    val statusLabel: String,
    val showProgress: Boolean,
    val showAccessHint: Boolean,
    val controlsEnabled: Boolean,
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
    val isPlaying: Boolean,
) {
    companion object {
        fun from(snapshot: MusicSnapshot?, access: MusicAccess, appLabel: String?): MusicDisplay = when {
            snapshot != null -> MusicDisplay(
                title = snapshot.title?.takeIf { it.isNotBlank() } ?: (appLabel ?: "Sem título"),
                subtitle = listOfNotNull(snapshot.artist?.takeIf { it.isNotBlank() }, snapshot.album?.takeIf { it.isNotBlank() })
                    .joinToString(" • ").ifEmpty { appLabel ?: "" },
                statusLabel = if (snapshot.isPlaying) "A reproduzir" + (appLabel?.let { " · $it" } ?: "") else "Em pausa" + (appLabel?.let { " · $it" } ?: ""),
                showProgress = snapshot.hasProgress,
                showAccessHint = false,
                controlsEnabled = true,
                canSkipNext = snapshot.canSkipNext,
                canSkipPrevious = snapshot.canSkipPrevious,
                isPlaying = snapshot.isPlaying,
            )
            access == MusicAccess.MEDIA_KEYS_ONLY -> MusicDisplay(
                title = "Música",
                subtitle = "Permite o acesso a notificações para ver a faixa atual",
                statusLabel = "Controlos básicos",
                showProgress = false,
                showAccessHint = true,
                controlsEnabled = true,
                canSkipNext = true,
                canSkipPrevious = true,
                isPlaying = false,
            )
            else -> MusicDisplay(
                title = "Nada a reproduzir",
                subtitle = "Inicia a música numa aplicação compatível",
                statusLabel = "Parado",
                showProgress = false,
                showAccessHint = false,
                controlsEnabled = true,
                canSkipNext = false,
                canSkipPrevious = false,
                isPlaying = false,
            )
        }
    }
}
