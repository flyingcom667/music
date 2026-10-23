package com.example.myapplication.util

import java.text.Normalizer
import java.util.Locale

/**
 * Unicode aware text normalisation used for searching.
 *
 * The goals are:
 *  - Persian/Arabic text must be searchable regardless of which variant of a letter the
 *    file name, the MediaStore entry or the user's keyboard uses (ي/ی, ك/ک, ة/ه, أ/ا ...).
 *  - Diacritics (harakat, Latin accents) are ignored.
 *  - Arabic-Indic and Persian digits match their ASCII counterparts.
 *  - Zero width joiners / tatweel do not stand in the way of a match.
 *  - Latin text is matched case insensitively.
 *
 * The object is intentionally pure (no Android dependencies) so it can be unit tested.
 */
object TextNormalizer {

    private const val ZWNJ = '\u200C'
    private const val ZWJ = '\u200D'
    private const val TATWEEL = '\u0640'

    /**
     * Cache of already normalised strings.
     *
     * Normalisation is the most expensive part of the search, and the same titles and artists
     * are normalised again for every song of the library. A small LRU cache keeps the repeated
     * work out of the hot path without growing without bounds.
     */
    private const val CACHE_SIZE = 1024

    private val cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > CACHE_SIZE
    }

    /** Normalised, lower case, whitespace collapsed form used for comparisons. */
    fun normalize(input: String): String {
        if (input.isEmpty()) return ""
        synchronized(cache) {
            cache[input]?.let { return it }
        }
        val normalized = compute(input)
        synchronized(cache) {
            cache[input] = normalized
        }
        return normalized
    }

    private fun compute(input: String): String {
        val folded = StringBuilder(input.length)
        for (ch in stripDiacritics(input)) {
            when (ch) {
                ZWNJ, ZWJ -> folded.append(' ')
                TATWEEL -> Unit
                else -> folded.append(foldChar(ch))
            }
        }
        return collapseWhitespace(folded).lowercase(Locale.ROOT)
    }

    /** Splits a raw query into normalised tokens. Blank tokens are dropped. */
    fun tokenize(query: String): List<String> =
        normalize(query).split(' ').filter { it.isNotEmpty() }

    /**
     * Returns true when every token of [tokens] is contained in at least one of [haystacks].
     * [haystacks] must already be normalised.
     */
    fun matches(tokens: List<String>, haystacks: List<String>): Boolean {
        if (tokens.isEmpty()) return true
        for (token in tokens) {
            var found = false
            for (haystack in haystacks) {
                if (haystack.contains(token)) {
                    found = true
                    break
                }
            }
            if (!found) return false
        }
        return true
    }

    /** Removes combining marks (harakat, accents) by decomposing and dropping them. */
    private fun stripDiacritics(input: String): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        var hasMarks = false
        for (ch in decomposed) {
            if (isMark(ch)) {
                hasMarks = true
                break
            }
        }
        if (!hasMarks) return decomposed
        val out = StringBuilder(decomposed.length)
        for (ch in decomposed) {
            if (!isMark(ch)) out.append(ch)
        }
        return out.toString()
    }

    private fun isMark(ch: Char): Boolean {
        val type = Character.getType(ch)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()
    }

    private fun foldChar(ch: Char): Char = when (ch) {
        // Arabic yeh variants -> Farsi yeh
        '\u064A', '\u0649', '\u0626', '\u06D2' -> '\u06CC'
        // Arabic kaf -> keheh
        '\u0643' -> '\u06A9'
        // teh marbuta / heh with yeh above / ae -> heh
        '\u0629', '\u06C0', '\u06D5' -> '\u0647'
        // alef variants -> bare alef
        '\u0622', '\u0623', '\u0625', '\u0671', '\u0672', '\u0673' -> '\u0627'
        // waw with hamza -> waw
        '\u0624' -> '\u0648'
        else -> when (ch) {
            in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
            in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
            else -> ch
        }
    }

    private fun collapseWhitespace(input: CharSequence): String {
        val out = StringBuilder(input.length)
        var lastWasSpace = true
        for (ch in input) {
            if (ch.isWhitespace()) {
                if (!lastWasSpace) {
                    out.append(' ')
                    lastWasSpace = true
                }
            } else {
                out.append(ch)
                lastWasSpace = false
            }
        }
        // trim trailing space
        if (out.isNotEmpty() && out[out.length - 1] == ' ') out.setLength(out.length - 1)
        return out.toString()
    }
}
