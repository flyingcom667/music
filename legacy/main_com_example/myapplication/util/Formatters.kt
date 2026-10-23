package com.example.myapplication.util

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Pure formatting helpers (unit tested, no Android dependencies). */
object Formatters {

    /** 3:07 / 1:02:33 style duration. */
    fun duration(durationMs: Long): String {
        if (durationMs <= 0L) return "0:00"
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    /** "-1:23" style remaining time. */
    fun remaining(positionMs: Long, durationMs: Long): String {
        val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
        return "-" + duration(remainingMs)
    }

    /** Bytes as KB / MB / GB. */
    fun fileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        val value = bytes.toDouble()
        return when {
            value >= gb -> String.format(Locale.US, "%.2f GB", value / gb)
            value >= mb -> String.format(Locale.US, "%.1f MB", value / mb)
            value >= kb -> String.format(Locale.US, "%.0f KB", value / kb)
            else -> "$bytes B"
        }
    }

    /** Number of tracks, e.g. "12 songs". */
    fun songCount(count: Int): String = if (count == 1) "1 song" else "$count songs"

    fun bitrate(bitsPerSecond: Int): String =
        if (bitsPerSecond <= 0) FileNames.UNKNOWN
        else String.format(Locale.US, "%d kbps", bitsPerSecond / 1000)

    fun sampleRate(hertz: Int): String =
        if (hertz <= 0) FileNames.UNKNOWN
        else String.format(Locale.US, "%.1f kHz", hertz / 1000f)

    fun date(epochMillis: Long): String {
        if (epochMillis <= 0L) return FileNames.UNKNOWN
        return DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
            .format(Date(epochMillis))
    }

    fun dateTime(epochMillis: Long): String {
        if (epochMillis <= 0L) return FileNames.UNKNOWN
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
            .format(Date(epochMillis))
    }

    /** "3 days ago" style relative description, falls back to an absolute date. */
    fun relativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        if (epochMillis <= 0L) return "Never"
        val diff = now - epochMillis
        if (diff < 0L) return date(epochMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            minutes < 1L -> "Just now"
            minutes < 60L -> "$minutes min ago"
            hours < 24L -> "$hours h ago"
            days < 7L -> "$days d ago"
            else -> date(epochMillis)
        }
    }

    fun speed(speed: Float): String =
        if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"
}
