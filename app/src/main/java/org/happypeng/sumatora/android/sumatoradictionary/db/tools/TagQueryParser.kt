/* Sumatora Dictionary
        Copyright (C) 2020 Nicolas Centa

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

package org.happypeng.sumatora.android.sumatoradictionary.db.tools

// Parses a search query that may contain #tag tokens.
// Example: "verb #jlpt-n5 #common" → plainTerm="verb", tags=["jlpt-n5", "common"]
object TagQueryParser {
    private val TAG_REGEX = Regex("""#([^\s#,]+)""")

    fun parse(query: String): Pair<String, List<String>> {
        val tags = TAG_REGEX.findAll(query).map { it.groupValues[1] }.toList()
        val plainTerm = TAG_REGEX.replace(query, "").trim()
        return Pair(plainTerm, tags)
    }
}
