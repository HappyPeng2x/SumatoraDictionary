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

package org.happypeng.sumatora.desktop.model

data class DictionaryEntry(
    val type: String,
    val lang: String,
    val description: String,
    val uri: String,
    val version: Int,
    val date: Int
) {
    // Installed filename: just lang.db so the search layer finds it by alias
    val localFileName: String get() = "$lang.db"

    // Only main (jmdict) and translation dbs are used in search
    val isSearchable: Boolean get() = type == "main" || type == "translation"

    val displayName: String get() = description.ifEmpty { "$type-$lang" }
}
