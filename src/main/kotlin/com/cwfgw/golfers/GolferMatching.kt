package com.cwfgw.golfers

import java.text.Normalizer

private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")

// Unicode-aware, so a non-breaking space separates words as an ASCII one does.
private val WHITESPACE = Regex("(?U)\\s+")

// Letters NFD leaves whole; lowercase only, because normalizeName lowercases before it folds.
private val NON_DECOMPOSABLE_LETTERS: Map<String, String> =
    mapOf(
        "ø" to "o",
        "å" to "a",
        "æ" to "ae",
        "œ" to "oe",
        "ß" to "ss",
        "ł" to "l",
        "đ" to "d",
        "ð" to "d",
        "þ" to "th",
    )

/**
 * Folds case, accents and hyphens, so "Byeong-Hun An" and "Thorbjørn Olesen" meet a roster's "Byeong Hun An"
 * and "Thorbjorn Olesen". Substitution only, never edit distance: "Tom Kim" must not match "Tim Kim".
 * Lowercasing first is what makes a second pass change nothing.
 */
internal fun normalizeName(name: String): String = collapseWhitespace(foldLetters(name).replace('-', ' '))

/**
 * Keeps a hyphenated surname whole and looks past a generational suffix, so a lone "Petersen" or "III" can't
 * become the unique surname of "Nørgaard-Petersen" or "Harold Varner III".
 */
internal fun surnameKey(name: String): String {
    val words = collapseWhitespace(foldLetters(name)).split(' ')
    return words.dropLastWhile { it in GENERATIONAL_SUFFIXES }.lastOrNull() ?: words.last()
}

private val GENERATIONAL_SUFFIXES: Set<String> = setOf("jr", "jr.", "sr", "sr.", "ii", "iii", "iv")

private fun foldLetters(name: String): String {
    val decomposed = Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")
    return NON_DECOMPOSABLE_LETTERS.entries.fold(decomposed) { acc, (from, to) -> acc.replace(from, to) }
}

private fun collapseWhitespace(name: String): String = name.replace(WHITESPACE, " ").trim()

/**
 * Cut at the last space: "Byeong Hun An" is ("Byeong Hun", "An"), "Erik van Rooyen" knowingly ("Erik van", "Rooyen").
 * A lone word is a surname (a team partner, roster "Scheffler"), as reports read `last_name`. Matching never reads
 * the split; it only decides the stored columns.
 */
@ConsistentCopyVisibility
internal data class GolferName private constructor(val firstName: String, val lastName: String) {
    companion object {
        /** Null for a blank name, the one input with no surname to keep. */
        fun splitName(fullName: String): GolferName? {
            val collapsed = collapseWhitespace(fullName)
            if (collapsed.isEmpty()) return null
            return GolferName(
                firstName = collapsed.substringBeforeLast(' ', missingDelimiterValue = ""),
                lastName = collapsed.substringAfterLast(' '),
            )
        }
    }
}
