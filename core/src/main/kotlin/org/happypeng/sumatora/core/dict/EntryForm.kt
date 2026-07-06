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

// schema-v2.md EntryForm: the central v2 table, one row per searchable/renderable
// writing or reading. Replaces readingsPrio/readings/writingsPrio/writings string splitting.
data class EntryForm(
    val formId: Long,
    val entryId: Long,
    val ord: Int,
    val formType: String, // writing, reading
    val text: String,
    val reading: String?,
    val isPrimary: Boolean,
    val isCommon: Boolean,
    val isSearchOnly: Boolean,
    val score: Int
)

// FormFuriganaSegment: display-ready ruby segments for a form, replaces bracket furigana parsing.
data class FormFuriganaSegment(
    val formId: Long,
    val ord: Int,
    val base: String,
    val ruby: String?
)

// FormTag: irregular/priority/rare/old-kanji etc. tags attached to a specific form.
data class FormTag(
    val formId: Long,
    val tagId: Long
)

// FormRule: replaces entry-level DictionaryEntry.rules with a per-form deinflection rule set.
data class FormRule(
    val formId: Long,
    val rule: String
)

// DeinflectionRule: rule code -> display label metadata (Deinflector.kt still generates
// candidates; this only supplies the label for a rule code that verified).
data class DeinflectionRule(
    val rule: String,
    val label: String
)

// FormPitch: links a form to a pitch accent row with match confidence.
data class FormPitch(
    val formId: Long,
    val pitchId: Long,
    val confidence: String // exact, reading_fallback
)
