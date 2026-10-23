package com.tidalmusic.player.util

import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Duration formatting helpers.
 *
 * Durations are always formatted with Western digits and a colon, independent of the
 * device locale, so that a Persian or Arabic library still reads consistently.
 */
object TimeFormat {

    const val UNKNOWN = "--:--"

    /** Formats [millis] as `m:ss` (or `h:mm:ss` past an hour). */
    fun duration(millis: Long): String {
        if (millis <= 0L) return UNKNOWN
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    /** Formats [millis] as a long, human readable duration such as `1 h 24 min`. */
    fun longDuration(millis: Long): String {
        if (millis <= 0L) return "0 min"
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> String.format(Locale.US, "%d h %d min", hours, minutes)
            hours > 0 -> String.format(Locale.US, "%d h", hours)
            else -> String.format(Locale.US, "%d min", minutes)
        }
    }

    /** Formats a count of bytes, e.g. `4.2 MB`. */
    fun fileSize(bytes: Long): String {
        if (bytes <= 0L) return "—"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        return if (index == 0) {
            String.format(Locale.US, "%d %s", value.toLong(), units[index])
        } else {
            String.format(Locale.US, "%.1f %s", value, units[index])
        }
    }
}
