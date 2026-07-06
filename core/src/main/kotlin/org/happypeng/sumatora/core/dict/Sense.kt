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

// SenseGroup: adjacent-senses-with-identical-tags display grouping (1:1 with Sense today).
data class SenseGroup(
    val senseGroupId: Long,
    val entryId: Long,
    val ord: Int,
    val displayNumber: Int?
)

// SenseGroupTag: pos/field/dialect/misc tags hoisted to the sense-group level.
data class SenseGroupTag(
    val senseGroupId: Long,
    val tagId: Long
)

// Sense: one sense row. sourceOrd preserves the original JMdict sense index.
data class Sense(
    val senseId: Long,
    val entryId: Long,
    val senseGroupId: Long,
    val sourceOrd: Int,
    val ord: Int,
    val displayNumber: Int?
)

// SenseGloss: replaces per-language translation tables; gloss_type distinguishes
// main/literal/figurative/explanation glosses.
data class SenseGloss(
    val senseId: Long,
    val lang: String,
    val ord: Int,
    val text: String,
    val glossType: String
)

// SenseNote: replaces s_inf.
data class SenseNote(
    val senseId: Long,
    val ord: Int,
    val text: String
)

// SenseLanguageSource: replaces lsource.
data class SenseLanguageSource(
    val senseId: Long,
    val ord: Int,
    val lang: String,
    val text: String?,
    val isFull: Boolean,
    val isWasei: Boolean
)

// SenseAppliesToForm: replaces stagk/stagr parsing. A sense with no rows applies to every form;
// a sense with rows applies only to the listed form_ids.
data class SenseAppliesToForm(
    val senseId: Long,
    val formId: Long
)

// SenseReference: replaces xref/ant JSON strings with resolved cross-reference targets.
// previewText is a precomputed target-gloss preview; no live join needed at render time.
data class SenseReference(
    val referenceId: Long,
    val senseId: Long,
    val ord: Int,
    val referenceType: String, // xref, antonym
    val displayText: String,
    val targetEntryId: Long?,
    val targetFormId: Long?,
    val targetSenseId: Long?,
    val targetSenseNumber: Int?,
    val previewText: String?
)
