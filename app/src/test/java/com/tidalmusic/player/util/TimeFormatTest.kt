package com.tidalmusic.player.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The duration and size formatting helpers.
 */
class TimeFormatTest {

    @Test
    fun `an unknown duration is shown as dashes`() {
        assertEquals("--:--", TimeFormat.duration(0L))
        assertEquals("--:--", TimeFormat.duration(-1L))
    }

    @Test
    fun `short durations use minutes and seconds`() {
        assertEquals("0:05", TimeFormat.duration(5_000L))
        assertEquals("3:07", TimeFormat.duration(187_000L))
    }

    @Test
    fun `durations past an hour add the hour part`() {
        assertEquals("1:00:00", TimeFormat.duration(3_600_000L))
        assertEquals("2:03:04", TimeFormat.duration(7_384_000L))
    }

    @Test
    fun `the format does not depend on the device locale`() {
        assertEquals("3:07", TimeFormat.duration(187_000L))
    }

    @Test
    fun `long durations read as hours and minutes`() {
        assertEquals("0 min", TimeFormat.longDuration(0L))
        assertEquals("45 min", TimeFormat.longDuration(45 * 60_000L))
        assertEquals("1 h", TimeFormat.longDuration(60 * 60_000L))
        assertEquals("1 h 24 min", TimeFormat.longDuration(84 * 60_000L))
    }

    @Test
    fun `file sizes use the largest fitting unit`() {
        assertEquals("—", TimeFormat.fileSize(0L))
        assertEquals("512 B", TimeFormat.fileSize(512L))
        assertEquals("1.0 KB", TimeFormat.fileSize(1024L))
        assertEquals("1.5 MB", TimeFormat.fileSize(1024L * 1024L * 3L / 2L))
    }
}
