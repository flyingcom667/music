package com.tidalmusic.player.domain.model

/**
 * A snapshot of the player, as observed by the UI.
 *
 * Kept as a small immutable data class so Compose can skip recomposition when nothing
 * meaningful changed. The playback position lives in a separate [PlaybackProgress]
 * flow so that the ticking position never recomposes the whole now-playing screen.
 */
data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackSpeed: Float = 1.0f,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val queueSize: Int = 0,
    val errorMessage: String? = null,
) {
    val hasSong: Boolean get() = currentSong != null
}

/** The ticking playback position, updated independently of [PlaybackState]. */
data class PlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
) {
    /** 0f..1f playback progress; `0f` when the duration is unknown. */
    val progress: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

/** Repeat modes supported by the player. */
enum class RepeatMode {
    OFF,
    ALL,
    ONE;

    /** Cycles OFF -> ALL -> ONE -> OFF, which is the order every player uses. */
    fun next(): RepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }

    companion object {
        fun fromKey(key: String?): RepeatMode = entries.firstOrNull { it.key == key } ?: OFF
    }

    val key: String
        get() = name.lowercase()
}

/** One entry of the playback queue. */
data class QueueItem(
    val index: Int,
    val song: Song,
    val isCurrent: Boolean = false,
)
