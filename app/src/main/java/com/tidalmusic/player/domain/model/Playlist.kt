package com.tidalmusic.player.domain.model

/**
 * A user-created playlist. Playlists never own the audio files they reference —
 * deleting a playlist only removes the playlist and its ordering.
 */
data class Playlist(
    val id: Long,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A playlist together with its resolved tracks (in playlist order). */
data class PlaylistWithSongs(
    val playlist: Playlist,
    val songs: List<Song>,
) {
    val trackCount: Int get() = songs.size

    val totalDurationMs: Long get() = songs.sumOf { it.durationMs }
}
