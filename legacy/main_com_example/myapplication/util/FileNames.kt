package com.example.myapplication.util

import java.util.Locale

/**
 * Helpers for turning file names into human readable titles without destroying
 * non-Latin characters or meaningful punctuation.
 */
object FileNames {

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "m4b", "aac", "flac", "ogg", "oga", "opus", "wav", "wave",
        "wma", "amr", "aiff", "aif", "alac", "mka", "mp4", "3gp", "mid", "midi",
    )

    const val UNKNOWN_ARTIST = "Unknown artist"
    const val UNKNOWN_ALBUM = "Unknown album"
    const val UNKNOWN = "Unknown"

    /** Lower case extension without the dot, or an empty string. */
    fun extension(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        if (dot <= 0 || dot == fileName.length - 1) return ""
        return fileName.substring(dot + 1).lowercase(Locale.ROOT)
    }

    /**
     * Removes a trailing audio extension. Only recognised audio extensions are removed so
     * that names such as "Track 1.5 - Intro" survive untouched.
     */
    fun removeAudioExtension(fileName: String): String {
        val ext = extension(fileName)
        if (ext.isNotEmpty() && ext in AUDIO_EXTENSIONS) {
            return fileName.substring(0, fileName.length - ext.length - 1)
        }
        return fileName
    }

    /**
     * Best effort display title from a file name: the extension is dropped, underscores are
     * turned into spaces but unicode letters and punctuation are preserved.
     */
    fun displayTitle(fileName: String): String {
        if (fileName.isBlank()) return ""
        val withoutExtension = removeAudioExtension(fileName)
        val cleaned = withoutExtension
            .replace('_', ' ')
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.ifBlank { withoutExtension.trim() }
    }

    /** True when a metadata value is missing, blank or a known placeholder. */
    fun isUnusable(value: String?): Boolean {
        if (value == null) return true
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return true
        return when (trimmed.lowercase(Locale.ROOT)) {
            "<unknown>", "unknown", "unkn", "null", "-", "--", "n/a", "(null)" -> true
            else -> false
        }
    }
}
