package com.example.myapplication.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** File names are the fallback source of a title, so they must survive untouched. */
class FileNamesTest {

    @Test
    fun `keeps a persian file name readable`() {
        assertEquals("آهنگ من", FileNames.displayTitle("آهنگ من.mp3"))
    }

    @Test
    fun `turns underscores into spaces but keeps the letters`() {
        assertEquals("آهنگ من", FileNames.displayTitle("آهنگ_من.mp3"))
    }

    @Test
    fun `removes only known audio extensions`() {
        assertEquals("Track 1.5 - Intro", FileNames.displayTitle("Track 1.5 - Intro"))
        assertEquals("آهنگ", FileNames.displayTitle("آهنگ.flac"))
    }

    @Test
    fun `treats placeholder metadata as missing`() {
        assertTrue(FileNames.isUnusable("<unknown>"))
        assertTrue(FileNames.isUnusable("  "))
        assertTrue(FileNames.isUnusable(null))
        assertFalse(FileNames.isUnusable("آهنگ"))
    }
}
