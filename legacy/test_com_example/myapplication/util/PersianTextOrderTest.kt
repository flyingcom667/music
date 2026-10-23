package com.example.myapplication.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The library order has to follow the persian alphabet on every device. */
class PersianTextOrderTest {

    @Test
    fun `puts peh right behind beh`() {
        assertTrue(PersianTextOrder.compare("پسر", "تست") < 0)
        assertTrue(PersianTextOrder.compare("تست", "پسر") > 0)
    }

    @Test
    fun `follows the persian alphabet`() {
        val letters = listOf("ی", "ا", "ب", "پ", "ت", "ن")
        val sorted = letters.sortedWith { a, b -> PersianTextOrder.compare(a, b) }

        assertEquals(listOf("ا", "ب", "پ", "ت", "ن", "ی"), sorted)
    }

    @Test
    fun `the arabic spelling of a letter shares the persian rank`() {
        assertEquals(0, PersianTextOrder.compare("كتاب", "کتاب"))
    }

    @Test
    fun `compares the rest of the title when the first letter is equal`() {
        // د comes after ا in the persian alphabet
        assertTrue(PersianTextOrder.compare("آهنگ دوم", "آهنگ اول") > 0)
        assertTrue(PersianTextOrder.compare("آهنگ اول", "آهنگ دوم") < 0)
    }

    @Test
    fun `detects arabic script titles`() {
        assertTrue(PersianTextOrder.isArabicScript("آهنگ"))
        assertTrue(PersianTextOrder.isArabicScript("۲ آهنگ"))
        assertFalse(PersianTextOrder.isArabicScript("2nd song"))
        assertFalse(PersianTextOrder.isArabicScript("Song"))
        assertFalse(PersianTextOrder.isArabicScript(""))
    }
}
