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

    /**
     * Splits [millis] into whole hours and minutes for a long, human readable duration.
     *
     * The worded form ("1 h 24 min") is built by the UI from string resources so the
     * abbreviations follow the device language; only the split lives here.
     */
    fun longDurationParts(millis: Long): Pair<Long, Long> {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis.coerceAtLeast(0L))
        return totalMinutes / 60 to totalMinutes % 60
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
