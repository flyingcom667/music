package com.tidalmusic.player.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.tidalmusic.player.domain.model.Song

/**
 * Translates between the app's [Song] model and Media3's [MediaItem].
 *
 * The song id is stored as the media id, so the controller can always map a playing
 * item back to a track even after the process was restarted.
 */
object MediaItemMapper {

    /** The content URI of the album artwork for a track. */
    fun artworkUri(song: Song): Uri = Uri.parse(
        "content://media/external/audio/albumart/${song.albumId}",
    )

    fun toMediaItem(song: Song): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setDisplayTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setAlbumArtist(song.artist)
            .setTrackNumber(song.trackNumber)
            .setRecordingYear(song.year)
            .setDurationMs(song.durationMs)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setArtworkUri(artworkUri(song))
            .setExtras(
                android.os.Bundle().apply {
                    putLong(EXTRA_SONG_ID, song.id)
                    putLong(EXTRA_DURATION, song.durationMs)
                    putLong(EXTRA_DATE_ADDED, song.dateAdded)
                    putLong(EXTRA_SIZE, song.sizeBytes)
                    putString(EXTRA_DISPLAY_NAME, song.displayName)
                    putString(EXTRA_MIME_TYPE, song.mimeType)
                    putLong(EXTRA_ALBUM_ID, song.albumId)
                },
            )
            .build()

        return MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.uri)
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * Rebuilds a [Song] from a media item.
     *
     * This is the fallback path used when the in-memory catalogue does not know the
     * track — for instance after a process restart. Fields the media item does not
     * carry are left at their empty defaults rather than invented.
     */
    fun toSong(item: MediaItem): Song? {
        val id = item.mediaId.toLongOrNull() ?: return null
        val metadata = item.mediaMetadata
        val extras = metadata.extras
        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() }
            ?: extras?.getString(EXTRA_DISPLAY_NAME)?.substringBeforeLast('.', "")
            ?: return null
        val uri = item.localConfiguration?.uri
            ?: Uri.parse("content://media/external/audio/media/$id")
        return Song(
            id = id,
            contentUri = uri?.toString().orEmpty(),
            title = title,
            artist = metadata.artist?.toString().orEmpty(),
            album = metadata.albumTitle?.toString().orEmpty(),
            durationMs = metadata.durationMs ?: extras?.getLong(EXTRA_DURATION, 0L) ?: 0L,
            trackNumber = metadata.trackNumber ?: 0,
            year = metadata.recordingYear ?: 0,
            genre = null,
            dateAdded = extras?.getLong(EXTRA_DATE_ADDED, 0L) ?: 0L,
            dateModified = 0L,
            sizeBytes = extras?.getLong(EXTRA_SIZE, 0L) ?: 0L,
            displayName = extras?.getString(EXTRA_DISPLAY_NAME).orEmpty(),
            mimeType = extras?.getString(EXTRA_MIME_TYPE),
            albumId = extras?.getLong(EXTRA_ALBUM_ID, id) ?: id,
        )
    }

    const val EXTRA_SONG_ID = "com.tidalmusic.player.SONG_ID"
    const val EXTRA_DURATION = "com.tidalmusic.player.DURATION"
    const val EXTRA_DATE_ADDED = "com.tidalmusic.player.DATE_ADDED"
    const val EXTRA_SIZE = "com.tidalmusic.player.SIZE"
    const val EXTRA_DISPLAY_NAME = "com.tidalmusic.player.DISPLAY_NAME"
    const val EXTRA_MIME_TYPE = "com.tidalmusic.player.MIME_TYPE"
    const val EXTRA_ALBUM_ID = "com.tidalmusic.player.ALBUM_ID"
}
