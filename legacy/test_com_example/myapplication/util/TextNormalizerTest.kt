package com.example.myapplication.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The normaliser decides whether a Persian or Arabic song can be found by search, so the
 * folding rules are pinned down here.
 */
class TextNormalizerTest {

    @Test
    fun `folds the arabic yeh onto the persian yeh`() {
        assertEquals(TextNormalizer.normalize("کتاب"), TextNormalizer.normalize("كتاب"))
    }

    @Test
    fun `folds the arabic kaf onto the persian keheh`() {
        assertEquals("کتاب", TextNormalizer.normalize("كتاب"))
    }

    @Test
    fun `folds the alef variants onto the bare alef`() {
        assertEquals("اهنگ", TextNormalizer.normalize("آهنگ"))
        assertEquals("ارام", TextNormalizer.normalize("أرام"))
    }

    @Test
    fun `drops arabic harakat`() {
        assertEquals("محمد", TextNormalizer.normalize("مُحَمَّد"))
    }

    @Test
    fun `drops latin diacritics`() {
        assertEquals("cafe", TextNormalizer.normalize("Café"))
    }

    @Test
    fun `folds persian and arabic digits onto ascii digits`() {
        assertEquals("1398", TextNormalizer.normalize("۱۳۹۸"))
        assertEquals("1398", TextNormalizer.normalize("١٣٩٨"))
    }

    @Test
    fun `turns the zero width joiner into a space`() {
        // می + ZWNJ + رود
        assertEquals("می رود", TextNormalizer.normalize("می\u200Cرود"))
    }

    @Test
    fun `ignores the tatweel elongation`() {
        // ک + tatweel + تاب
        assertEquals("کتاب", TextNormalizer.normalize("ک\u0640تاب"))
    }

    @Test
    fun `collapses repeated whitespace and trims`() {
        assertEquals("اهنگ ارام", TextNormalizer.normalize("  آهنگ    آرام  "))
    }

    @Test
    fun `lower cases latin text without touching the locale`() {
        assertEquals("ab cd", TextNormalizer.normalize("AB CD"))
    }

    @Test
    fun `tokenizes a persian query`() {
        assertEquals(listOf("اهنگ", "پارس"), TextNormalizer.tokenize("  آهنگ   پارس "))
    }

    @Test
    fun `matches every token in one of the haystacks`() {
        val haystacks = listOf("اهنگ", "علی رضایی", "شب")
        assertTrue(TextNormalizer.matches(listOf("رضایی"), haystacks))
        assertTrue(TextNormalizer.matches(listOf("اهنگ", "شب"), haystacks))
        assertFalse(TextNormalizer.matches(listOf("اهنگ", "ناموجود"), haystacks))
    }

    @Test
    fun `an empty query matches everything`() {
        assertTrue(TextNormalizer.matches(emptyList(), listOf("anything")))
    }
}
