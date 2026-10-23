package com.example.myapplication.domain.model

/** The metadata fields a user can see or edit. */
data class SongMetadata(
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val trackNumber: Int? = null,
    val genre: String? = null,
    val year: Int? = null,
)

/** Fields the user has overridden in the app database. `null` means "not overridden". */
data class MetadataOverride(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val trackNumber: Int? = null,
    val genre: String? = null,
    val year: Int? = null,
) {
    val isEmpty: Boolean
        get() = title == null && artist == null && album == null && albumArtist == null &&
            trackNumber == null && genre == null && year == null
}

/**
 * A playable track.
 *
 * [metadata] is the metadata that is shown in the UI: it is [base] with the user's
 * overrides applied. [base] is what MediaStore reports and is never mutated.
 */
data class Song(
    val mediaId: Long,
    val uri: String,
    val metadata: SongMetadata,
    val base: SongMetadata,
    val albumId: Long,
    val durationMs: Long,
    val displayName: String,
    val relativePath: String?,
    val folder: String?,
    val mimeType: String?,
    val sizeBytes: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val lastPlayedAt: Long,
    val playCount: Int,
    val favoriteAddedAt: Long,
    val titleKey: String,
    val artistKey: String,
    val albumKey: String,
    val albumArtistKey: String,
) {
    val title: String get() = metadata.title
    val artist: String? get() = metadata.artist
    val album: String? get() = metadata.album
    val albumArtist: String? get() = metadata.albumArtist
    val trackNumber: Int? get() = metadata.trackNumber
    val genre: String? get() = metadata.genre
    val year: Int? get() = metadata.year

    val isFavorite: Boolean get() = favoriteAddedAt > 0L

    /** True when the displayed metadata differs from what the file/index reports. */
    val hasOverrides: Boolean get() = metadata != base

    /** Stable key used for Coil artwork requests. */
    val artworkKey: String get() = if (albumId > 0L) "album:$albumId" else "song:$mediaId"

    /** The normalised strings the search looks at; built once per song. */
    val searchKeys: List<String> = listOf(titleKey, artistKey, albumKey, albumArtistKey)
}

/** Queue entry shown in the queue screen. */
data class QueueItem(
    val mediaId: Long,
    val title: String,
    val artist: String?,
    val albumId: Long,
    val durationMs: Long,
) {
    val artworkKey: String get() = if (albumId > 0L) "album:$albumId" else "song:$mediaId"
}

data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val artworkMediaId: Long?,
)
