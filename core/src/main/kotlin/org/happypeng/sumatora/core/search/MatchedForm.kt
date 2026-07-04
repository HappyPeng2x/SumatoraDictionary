/* Sumatora Dictionary
        Copyright (C) 2026 Nicolas Centa

        This program is free software: you can redistribute it and/or modify
        it under the terms of the GNU General Public License as published by
        the Free Software Foundation, either version 3 of the License, or
        (at your option) any later version.

        This program is distributed in the hope that it will be useful,
        but WITHOUT ANY WARRANTY; without even the implied warranty of
        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
        GNU General Public License for more details.

        You should have received a copy of the GNU General Public License
        along with this program.  If not, see <http://www.gnu.org/licenses/>.*/

package org.happypeng.sumatora.core.search

import org.happypeng.sumatora.jromkan.Romkan

// Which headword form (kanji writing or kana reading) the user's search term actually
// matched, and the literal token (as it appears in DictionaryEntry.writings*/readings*).
sealed class MatchedForm {
    data class Kanji(val token: String) : MatchedForm()
    data class Kana(val token: String) : MatchedForm()
}

// Reconstructs which writing/reading token a query term matched, mirroring the tier order
// used by DictionarySearchQueryTool (exact writing, exact reading, prefix writing, prefix
// reading). writingsPrio/writings/readingsPrio/readings are the space-separated token strings
// stored on DictionaryEntry (and mirrored on DictionarySearchElement).
object MatchedFormResolver {
    private fun tokens(s: String?): List<String> =
        s.orEmpty().split(" ").filter { it.isNotEmpty() }

    // readingsPrio/readings mix hiragana and katakana (verbatim JMdict readings); normalize
    // both the term and each token through the same kana pipeline used by BasicQueryStatement
    // (kana/romaji -> hepburn romaji -> katakana) before comparing.
    private fun normalizeKana(s: String, romkan: Romkan): String =
        romkan.to_katakana(romkan.to_hepburn(s))

    fun resolve(
        term: String,
        writingsPrio: String?, writings: String?,
        readingsPrio: String?, readings: String?,
        romkan: Romkan
    ): MatchedForm? {
        if (term.isBlank()) return null

        val writingTokens = tokens(writingsPrio) + tokens(writings)
        val readingTokens = tokens(readingsPrio) + tokens(readings)
        val normTerm = normalizeKana(term, romkan)

        writingTokens.firstOrNull { it == term }?.let { return MatchedForm.Kanji(it) }
        readingTokens.firstOrNull { normalizeKana(it, romkan) == normTerm }?.let { return MatchedForm.Kana(it) }
        writingTokens.firstOrNull { it.startsWith(term) }?.let { return MatchedForm.Kanji(it) }
        readingTokens.firstOrNull { normalizeKana(it, romkan).startsWith(normTerm) }?.let { return MatchedForm.Kana(it) }

        return null
    }
}
