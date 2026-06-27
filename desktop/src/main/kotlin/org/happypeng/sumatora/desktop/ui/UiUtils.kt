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

package org.happypeng.sumatora.desktop.ui

import com.fasterxml.jackson.databind.ObjectMapper
import org.happypeng.sumatora.core.dict.JMDICT_ENTITIES
import org.happypeng.sumatora.desktop.model.SearchResult

private val jsonMapper = ObjectMapper()

internal fun parseGloss(json: String?): List<String> {
    if (json.isNullOrEmpty()) return emptyList()
    return try {
        jsonMapper.readValue(json, Array<String>::class.java).toList()
    } catch (e: Exception) { emptyList() }
}

internal fun glossPreview(json: String?, maxItems: Int = 2): String =
    parseGloss(json).take(maxItems).joinToString("; ")

internal fun wordHeader(r: SearchResult): String = buildString {
    if (r.bookmark > 0) append("★ ")
    val w = r.writingsPrio ?: r.writings
    val rd = r.readingsPrio ?: r.readings
    if (w != null) { append(w); if (!rd.isNullOrEmpty()) append("  $rd") }
    else append(rd ?: "")
}

internal fun expandPos(posJson: String?): String {
    if (posJson.isNullOrEmpty()) return ""
    return try {
        val groups = jsonMapper.readValue(posJson, Array<Array<String>>::class.java)
        groups.flatMap { it.toList() }.distinct().mapNotNull { JMDICT_ENTITIES[it] }.joinToString(", ")
    } catch (e: Exception) { "" }
}
