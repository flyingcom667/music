package com.tidalmusic.player.domain.model

import android.net.Uri

/**
 * A single audio track as the app understands it.
 *
 * The media provider is the source of truth for audio files; this model is a thin,
 * immutable projection of one `MediaStore.Audio` row. Only stable identifiers and
 * metadata are stored — never the file bytes.
 *
 * @param id the stable `MediaStore` row id (`_ID`).
 * @param contentUri the `content://media/external/audio/media/<id>` URI used for playback.
 *   It is kept as text so that the model stays a plain data class that can be built in a
 *   unit test without the Android framework; [uri] parses it on demand.
 * @param title track title; falls back to the display name without extension.
 * @param artist track artist; may be the literal `<unknown>` value from the provider.
 * @param album album name; may be the literal `<unknown>` value from the provider.
 * @param durationMs duration in milliseconds, or `0` when unknown.
 * @param trackNumber track number within the album, or `0` when unknown.
 * @param year release year, or `0` when unknown.
 * @param genre genre, or `null` when unknown.
 * @param dateAdded seconds since the epoch when the file was added to the media store.
 * @param dateModified seconds since the epoch when the file was last modified.
 * @param sizeBytes file size in bytes.
 * @param displayName original file name, used as a last-resort title.
 * @param mimeType MIME type of the file, when the provider exposes it.
 */
data class Song(
    val id: Long,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val genre: String?,
    val dateAdded: Long,
    val dateModified: Long,
    val sizeBytes: Long,
    val displayName: String,
    val mimeType: String?,
    val albumId: Long = id,
) {
    /** The parsed [contentUri]; the only place where the Android URI type is needed. */
    val uri: Uri get() = Uri.parse(contentUri)

    /** True when the provider did not expose a usable artist name. */
    val hasUnknownArtist: Boolean
        get() = artist.isBlank() || artist == UNKNOWN_ARTIST || artist == UNKNOWN_ALBUM

    /** True when the provider did not expose a usable album name. */
    val hasUnknownAlbum: Boolean
        get() = album.isBlank() || album == UNKNOWN_ARTIST || album == UNKNOWN_ALBUM

    /** Stable key for lazy lists. */
    val key: Long get() = id

    companion object {
        const val UNKNOWN_ARTIST = "<unknown>"
        const val UNKNOWN_ALBUM = "<unknown>"
    }
}
