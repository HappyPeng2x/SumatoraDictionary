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

// PitchAccent: one (word, reading) pitch accent entry. Looked up via FormPitch by form_id
// when the matched form is known, rather than guessing from whichever form is on hand.
data class PitchAccent(
    val pitchId: Long,
    val word: String?,
    val reading: String,
    val sourceId: Long?
)

// PitchPattern: ordered pitch drop positions for a PitchAccent row.
data class PitchPattern(
    val pitchId: Long,
    val ord: Int,
    val position: Int
)
