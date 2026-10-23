package com.example.myapplication.domain

import com.example.myapplication.domain.model.MetadataOverride
import com.example.myapplication.domain.model.SongMetadata
import com.example.myapplication.util.FileNames

/**
 * Merges the metadata reported by MediaStore with the user's local overrides and applies
 * sensible fallbacks. Pure functions only, so Persian/Arabic fallback behaviour is unit
 * tested.
 */
object MetadataResolver {

    private const val FALLBACK_TITLE = "Unknown title"

    fun effective(base: SongMetadata, override: MetadataOverride?, displayName: String): SongMetadata {
        val fileNameTitle = FileNames.displayTitle(displayName)
        val baseTitle = base.title.trim()
        val usableBaseTitle = baseTitle.isNotEmpty() &&
            !FileNames.isUnusable(baseTitle) &&
            baseTitle != displayName

        val title = firstNonBlank(
            override?.title,
            if (usableBaseTitle) baseTitle else null,
            fileNameTitle.takeIf { it.isNotBlank() },
        ) ?: FALLBACK_TITLE

        val artist = firstNonBlank(override?.artist, base.artist)
        val album = firstNonBlank(override?.album, base.album)
        val albumArtist = firstNonBlank(override?.albumArtist, base.albumArtist, artist)

        return SongMetadata(
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            trackNumber = override?.trackNumber ?: base.trackNumber?.takeIf { it > 0 },
            genre = firstNonBlank(override?.genre, base.genre),
            year = override?.year ?: base.year?.takeIf { it > 0 },
        )
    }

    /** The metadata used to pre-fill the editor: overrides on top of the file metadata. */
    fun editorValues(base: SongMetadata, override: MetadataOverride?, displayName: String): SongMetadata =
        effective(base, override, displayName)

    /**
     * True when [value] is worth showing. Collapses "<unknown>" style placeholders and
     * blank strings to `null` so the UI can render a fallback instead.
     */
    fun clean(value: String?): String? = firstNonBlank(value)

    private fun firstNonBlank(vararg values: String?): String? {
        for (value in values) {
            if (!FileNames.isUnusable(value)) return value!!.trim()
        }
        return null
    }
}
