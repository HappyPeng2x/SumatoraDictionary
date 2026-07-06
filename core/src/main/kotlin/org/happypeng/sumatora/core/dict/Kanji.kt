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

// KanjiEntry: KANJIDIC2 character details, replaces kanjidic2.db's flat char/strokes/grade/
// jlpt/freq/radical row.
data class KanjiEntry(
    val character: String,
    val entryId: Long?,
    val strokes: Int?,
    val grade: Int?,
    val jlpt: Int?,
    val frequency: Int?,
    val radical: Int?
)

// KanjiReading: one row per on/kun/nanori reading, replaces space-separated on/kun columns.
data class KanjiReading(
    val character: String,
    val readingType: String, // on, kun, nanori
    val ord: Int,
    val text: String
)

// KanjiMeaning: one row per language+meaning, replaces the meanings JSON array column.
data class KanjiMeaning(
    val character: String,
    val lang: String,
    val ord: Int,
    val text: String
)
