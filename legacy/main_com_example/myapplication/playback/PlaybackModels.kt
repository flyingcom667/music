package com.example.myapplication.playback

import com.example.myapplication.domain.model.RepeatMode

/** Everything the player UI needs, derived from the `MediaController`. */
data class PlaybackUiState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val mediaId: Long = 0L,
    val title: String = "",
    val artist: String? = null,
    val album: String? = null,
    val albumId: Long = 0L,
    val durationMs: Long = 0L,
    val currentIndex: Int = -1,
    val queueSize: Int = 0,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val speed: Float = 1f,
    val errorMessage: String? = null,
) {
    val hasCurrent: Boolean get() = mediaId != 0L

    val artworkKey: String? get() = when {
        mediaId == 0L -> null
        albumId > 0L -> "album:$albumId"
        else -> "song:$mediaId"
    }

    val artworkModel: ArtworkKey? get() =
        if (mediaId == 0L) null else ArtworkKey(mediaId, albumId)
}

data class PlaybackProgress(val positionMs: Long = 0L, val durationMs: Long = 0L)

/** Key identifying the artwork of a song (or of its album when known). */
data class ArtworkKey(val mediaId: Long, val albumId: Long)

object MediaItemExtra {
    const val ALBUM_ID = "albumId"
}
