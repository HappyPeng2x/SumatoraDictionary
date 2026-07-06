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

package org.happypeng.sumatora.core.dict

// Replaces the old flat DictionaryResult. Schema v2's SearchTerm-driven query layer no longer
// returns a fully assembled row per hit; it returns entry_id + form_id + match metadata
// (Database.md "Query Result Shape"), and the display layer assembles Entry/EntryForm/Sense/...
// separately by entry_id/form_id. bookmark/memo/tags are joined in alongside the match metadata
// since the search-result list still needs them per row (bookmark star, tag chips).
interface DictionaryQueryResult {
    val entryId: Long
    // JMdict sequence number, stored redundantly from Entry.source_key at query time (0 for
    // non-word entries like proper names) - DictionaryBookmark/DictionaryBookmarkTag still key on
    // this, and re-resolving it per bookmark-star tap would mean a synchronous query on every tap.
    val seq: Long
    val formId: Long?
    val matchKind: String // exact, prefix, substring, gloss, deinflection, name
    val matchedText: String?
    val originalQuery: String?
    val dictionaryForm: String?
    val deinflectionLabel: String?
    val rank: Int
    val bookmark: Long
    val memo: String?
    val tags: String?
}
