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

// NameTranslation: JMnedict proper-name translations. Names use the same Entry/EntryForm/
// FormFuriganaSegment/EntryTag machinery as words (entry_type = 'name'); this flat ordered
// list replaces per-language SenseGloss for names, and EntryTag(category='name_type') carries
// the place/person/surname/etc. codes.
data class NameTranslation(
    val entryId: Long,
    val ord: Int,
    val text: String
)
