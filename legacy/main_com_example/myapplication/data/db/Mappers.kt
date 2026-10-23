package com.example.myapplication.data.db

import com.example.myapplication.domain.MetadataResolver
import com.example.myapplication.domain.model.MetadataOverride
import com.example.myapplication.domain.model.Playlist
import com.example.myapplication.domain.model.Song
import com.example.myapplication.domain.model.SongMetadata
import com.example.myapplication.util.TextNormalizer

fun SongEntity.baseMetadata(): SongMetadata = SongMetadata(
    title = title,
    artist = MetadataResolver.clean(artist),
    album = MetadataResolver.clean(album),
    albumArtist = MetadataResolver.clean(albumArtist),
    trackNumber = trackNumber?.takeIf { it > 0 },
    genre = MetadataResolver.clean(genre),
    year = year?.takeIf { it > 0 },
)

fun SongEntity.toDomain(
    override: SongOverrideEntity? = null,
    favoriteAddedAt: Long = 0L,
    playCount: Int = 0,
    lastPlayedAt: Long = 0L,
): Song {
    val base = baseMetadata()
    val effective = MetadataResolver.effective(
        base = base,
        override = override?.toDomain(),
        displayName = displayName,
    )
    return Song(
        mediaId = mediaId,
        uri = uri,
        metadata = effective,
        base = base,
        albumId = albumId,
        durationMs = durationMs,
        displayName = displayName,
        relativePath = relativePath,
        folder = folder,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        dateAdded = dateAdded,
        dateModified = dateModified,
        lastPlayedAt = lastPlayedAt,
        playCount = playCount,
        favoriteAddedAt = favoriteAddedAt,
        titleKey = TextNormalizer.normalize(effective.title),
        artistKey = TextNormalizer.normalize(effective.artist.orEmpty()),
        albumKey = TextNormalizer.normalize(effective.album.orEmpty()),
        albumArtistKey = TextNormalizer.normalize(effective.albumArtist.orEmpty()),
    )
}

fun SongOverrideEntity.toDomain(): MetadataOverride = MetadataOverride(
    title = title?.takeIf { it.isNotBlank() },
    artist = artist?.takeIf { it.isNotBlank() },
    album = album?.takeIf { it.isNotBlank() },
    albumArtist = albumArtist?.takeIf { it.isNotBlank() },
    trackNumber = trackNumber,
    genre = genre?.takeIf { it.isNotBlank() },
    year = year,
)

fun MetadataOverride.toEntity(mediaId: Long): SongOverrideEntity = SongOverrideEntity(
    mediaId = mediaId,
    title = title?.trim()?.takeIf { it.isNotEmpty() },
    artist = artist?.trim()?.takeIf { it.isNotEmpty() },
    album = album?.trim()?.takeIf { it.isNotEmpty() },
    albumArtist = albumArtist?.trim()?.takeIf { it.isNotEmpty() },
    trackNumber = trackNumber,
    genre = genre?.trim()?.takeIf { it.isNotEmpty() },
    year = year,
)

fun PlaylistSummary.toDomain(): Playlist = Playlist(
    id = id,
    name = name,
    songCount = songCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    artworkMediaId = artworkMediaId,
)
