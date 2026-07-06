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

// Example: one translated Tatoeba example sentence.
data class Example(
    val exampleId: Long,
    val sourceId: Long,
    val sourceKey: String,
    val lang: String,
    val translation: String
)

// ExampleSegment: display-ready ruby segments for the Japanese sentence, replaces
// {expression;reading} markup parsing.
data class ExampleSegment(
    val exampleId: Long,
    val ord: Int,
    val base: String,
    val ruby: String?
)

// EntryExample: links an example to an entry (and optionally a specific sense). ord is a
// precomputed "best example first" rank (shorter Japanese sentence first), capped at build
// time - clients just ORDER BY ord and take as many as they have room for.
data class EntryExample(
    val entryId: Long,
    val exampleId: Long,
    val ord: Int,
    val matchedText: String?,
    val senseId: Long?
)
