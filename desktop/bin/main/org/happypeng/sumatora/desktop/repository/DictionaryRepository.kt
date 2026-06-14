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

package org.happypeng.sumatora.desktop.repository

import org.happypeng.sumatora.desktop.model.DictionaryEntry
import org.w3c.dom.Element
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

object DictionaryRepository {

    const val DEFAULT_MANIFEST_URL = "https://sumatora.happypeng.org/dictionaries/v4/dictionaries.xml"

    fun fetchManifest(url: String = DEFAULT_MANIFEST_URL): List<DictionaryEntry> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout   = 30_000

        return conn.inputStream.use { stream ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
            val root = doc.documentElement  // <repository version="N" date="N">
            val version = root.getAttribute("version").toIntOrNull() ?: 0
            val date    = root.getAttribute("date").toIntOrNull() ?: 0
            val nodes   = root.getElementsByTagName("dictionary")
            (0 until nodes.length).map { i ->
                val el = nodes.item(i) as Element
                DictionaryEntry(
                    type        = el.getAttribute("type"),
                    lang        = el.getAttribute("lang"),
                    description = el.getAttribute("description") ?: "",
                    uri         = el.getAttribute("uri"),
                    version     = version,
                    date        = date
                )
            }
        }
    }

    fun installedLangs(dictDir: File): Set<String> =
        dictDir.listFiles { f -> f.extension == "db" }
            ?.map { it.nameWithoutExtension }?.toSet() ?: emptySet()
}
