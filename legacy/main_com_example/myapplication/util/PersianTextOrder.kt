package com.example.myapplication.util

/**
 * Collation of Persian and Arabic text.
 *
 * The platform collator only applies the Persian tailorings when the device ships the matching
 * locale data, and the result differs between a phone and the unit test runner. The letters are
 * therefore ranked explicitly so that a library of Persian songs is always ordered by the
 * Persian alphabet:
 *
 * `ا ب پ ت ث ج چ ح خ د ذ ر ز ژ س ش ص ض ط ظ ع غ ف ق ک گ ل م ن و ه ی`
 *
 * The Arabic variants of a letter (`ك`/`ک`, `ي`/`ی`, `ة`/`ه`, `أ`/`ا`) share the rank of their
 * Persian counterpart, so a song that is tagged with the Arabic spelling is not sorted away
 * from the Persian one.
 */
object PersianTextOrder {

    /** The Persian alphabet in its usual order. */
    private const val PERSIAN_ALPHABET = "ابپتثجچحخدذرزژسشصضطظعغفقکگلمنوهی"

    private const val LATIN_BASE = 0
    private const val PERSIAN_BASE = 100
    private const val ARABIC_BASE = 200
    private const val DIGIT_BASE = 300
    private const val OTHER_BASE = 1000

    private val persianRanks: Map<Char, Int> =
        PERSIAN_ALPHABET.mapIndexed { index, ch -> ch to index }.toMap()

    /** True when the first letter of [text] belongs to the Arabic script. */
    fun isArabicScript(text: String): Boolean {
        val first = text.trim().firstOrNull { it.isLetter() } ?: return false
        return (first.code in 0x0600..0x06FF)
    }

    /**
     * Compares two strings letter by letter using the Persian order. Only meant for texts that
     * start with an Arabic script letter; other texts are compared by the platform collator.
     */
    fun compare(a: String, b: String): Int {
        val left = a.trim().map { rankOf(it) }
        val right = b.trim().map { rankOf(it) }
        val size = minOf(left.size, right.size)
        for (index in 0 until size) {
            val result = left[index].compareTo(right[index])
            if (result != 0) return result
        }
        return left.size.compareTo(right.size)
    }

    private fun rankOf(ch: Char): Int {
        val folded = foldArabicVariant(ch)
        persianRanks[folded]?.let { return PERSIAN_BASE + it }
        if (folded.code in 0x0600..0x06FF) return ARABIC_BASE + folded.code
        if (folded.isLetter()) return LATIN_BASE + (folded.lowercaseChar() - 'a')
        if (folded.isDigit()) return DIGIT_BASE + (folded - '0')
        return OTHER_BASE + folded.code
    }

    /** Maps the Arabic spelling of a letter onto its Persian counterpart. */
    private fun foldArabicVariant(ch: Char): Char = when (ch) {
        'ك' -> 'ک' // arabic kaf -> keheh
        'ي' -> 'ی' // arabic yeh -> farsi yeh
        'ى' -> 'ی' // alef maksura -> farsi yeh
        'ة' -> 'ه' // teh marbuta -> heh
        'أ', 'إ', 'آ', 'ٱ' -> 'ا' // alef variants -> alef
        'ؤ' -> 'و' // waw with hamza -> waw
        'ئ' -> 'ی' // yeh with hamza -> farsi yeh
        else -> ch
    }
}
