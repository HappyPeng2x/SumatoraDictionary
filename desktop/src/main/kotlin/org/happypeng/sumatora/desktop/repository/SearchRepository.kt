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

package org.happypeng.sumatora.desktop.repository

import org.happypeng.sumatora.core.search.QueryUtils
import org.happypeng.sumatora.desktop.db.DatabaseManager
import org.happypeng.sumatora.desktop.model.SearchResult
import org.happypeng.sumatora.jromkan.Romkan
import java.sql.ResultSet

class SearchRepository(
    private val db: DatabaseManager,
    @Volatile var lang: String,
    private val romkan: Romkan = Romkan()
) {
    // (fts column in DictionaryIndex, isKana, isPrefix)
    private data class ForwardStep(val col: String, val isKana: Boolean, val isPrefix: Boolean)

    private val FORWARD_STEPS = listOf(
        ForwardStep("writingsPrio",          false, false),
        ForwardStep("readingsPrioKana",      true,  false),
        ForwardStep("writings",              false, false),
        ForwardStep("readingsKana",          true,  false),
        ForwardStep("writingsPrio",          false, true),
        ForwardStep("readingsPrioKana",      true,  true),
        ForwardStep("writings",              false, true),
        ForwardStep("readingsKana",          true,  true),
        ForwardStep("writingsPrioParts",     false, true),
        ForwardStep("readingsPrioKanaParts", true,  true),
        ForwardStep("writingsParts",         false, true),
        ForwardStep("readingsKanaParts",     true,  true)
    )

    fun search(
        plainTerm: String,
        tags: List<String> = emptyList(),
        showBookmarksOnly: Boolean = false,
        showMemoOnly: Boolean = false
    ): List<SearchResult> {
        val seen = LinkedHashMap<Long, SearchResult>()

        if (plainTerm.isEmpty()) {
            if (showBookmarksOnly || showMemoOnly || tags.isNotEmpty()) {
                runBookmarkQuery(showBookmarksOnly, showMemoOnly).forEach { seen.putIfAbsent(it.seq, it) }
            }
        } else {
            val escaped = QueryUtils.escapeTerm(plainTerm)
            val katakana = romkan.to_katakana(romkan.to_hepburn(escaped))

            for (step in FORWARD_STEPS) {
                val base = if (step.isKana) katakana else escaped
                val term = if (step.isPrefix) "$base*" else base
                runForwardQuery(step.col, term).forEach { seen.putIfAbsent(it.seq, it) }
            }

            // Reverse (English gloss → Japanese entry); no split_offsets ranking on desktop
            runReverseQuery(escaped).forEach { seen.putIfAbsent(it.seq, it) }
            runReverseQuery("$escaped*").forEach { seen.putIfAbsent(it.seq, it) }
        }

        if (tags.isEmpty()) return seen.values.toList()

        return seen.values.filter { result ->
            val resultTags = result.tags?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
            tags.all { it in resultTags }
        }
    }

    private fun selectColumns() = """
        DictionaryEntry.seq,
        DictionaryEntry.readingsPrio, DictionaryEntry.readings,
        DictionaryEntry.writingsPrio, DictionaryEntry.writings,
        DictionaryEntry.pos, DictionaryEntry.xref, DictionaryEntry.ant,
        DictionaryEntry.misc, DictionaryEntry.lsource, DictionaryEntry.dial,
        DictionaryEntry.s_inf, DictionaryEntry.field,
        '$lang' AS lang, '$lang' AS lang_setting,
        json_group_array(DictionaryTranslation.gloss) AS gloss,
        null AS example_sentences, null AS example_translations,
        IFNULL(DictionaryBookmark.bookmark, 0) AS bookmark,
        DictionaryBookmark.memo, DictionaryBookmark.tags
    """.trimIndent()

    private fun fromJoin() = """
        FROM jmdict.DictionaryEntry
        LEFT JOIN DictionaryBookmark ON DictionaryBookmark.seq = DictionaryEntry.seq,
        $lang.DictionaryTranslation
        WHERE DictionaryEntry.seq = DictionaryTranslation.seq
    """.trimIndent()

    private fun runForwardQuery(ftsCol: String, term: String): List<SearchResult> {
        val sql = """
            SELECT ${selectColumns()}
            ${fromJoin()}
            AND DictionaryEntry.seq IN (
                SELECT DictionaryIndex.rowid AS seq
                FROM jmdict.DictionaryIndex
                WHERE $ftsCol MATCH ?
            )
            GROUP BY DictionaryEntry.seq
        """
        return queryResults(sql, term)
    }

    private fun runReverseQuery(term: String): List<SearchResult> {
        val sql = """
            SELECT ${selectColumns()}
            ${fromJoin()}
            AND DictionaryTranslation.rowid IN (
                SELECT DictionaryTranslationIndex.rowid
                FROM $lang.DictionaryTranslationIndex
                WHERE DictionaryTranslationIndex.gloss MATCH ?
            )
            GROUP BY DictionaryEntry.seq
        """
        return queryResults(sql, term)
    }

    private fun runBookmarkQuery(bookmarksOnly: Boolean, memoOnly: Boolean): List<SearchResult> {
        val filter = when {
            bookmarksOnly && memoOnly ->
                "AND (IFNULL(DictionaryBookmark.bookmark, 0) > 0 OR (DictionaryBookmark.memo IS NOT NULL AND DictionaryBookmark.memo != ''))"
            bookmarksOnly ->
                "AND IFNULL(DictionaryBookmark.bookmark, 0) > 0"
            memoOnly ->
                "AND DictionaryBookmark.memo IS NOT NULL AND DictionaryBookmark.memo != ''"
            else ->
                "AND (IFNULL(DictionaryBookmark.bookmark, 0) > 0 OR DictionaryBookmark.memo IS NOT NULL)"
        }
        val sql = """
            SELECT ${selectColumns()}
            ${fromJoin()}
            $filter
            GROUP BY DictionaryEntry.seq
        """
        return queryResults(sql)
    }

    private fun queryResults(sql: String, vararg params: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            db.connection.prepareStatement(sql).use { ps ->
                params.forEachIndexed { i, p -> ps.setString(i + 1, p) }
                ps.executeQuery().use { rs ->
                    while (rs.next()) results.add(rs.toSearchResult())
                }
            }
        } catch (e: Exception) {
            System.err.println("SearchRepository query failed: ${e.message}")
        }
        return results
    }

    private fun ResultSet.toSearchResult() = SearchResult(
        seq = getLong("seq"),
        readingsPrio = getString("readingsPrio"),
        readings = getString("readings"),
        writingsPrio = getString("writingsPrio"),
        writings = getString("writings"),
        pos = getString("pos"),
        xref = getString("xref"),
        ant = getString("ant"),
        misc = getString("misc"),
        lsource = getString("lsource"),
        dial = getString("dial"),
        s_inf = getString("s_inf"),
        field = getString("field"),
        lang = getString("lang"),
        gloss = getString("gloss"),
        exampleSentences = getString("example_sentences"),
        exampleTranslations = getString("example_translations"),
        bookmark = getLong("bookmark"),
        memo = getString("memo"),
        tags = getString("tags")
    )

}
