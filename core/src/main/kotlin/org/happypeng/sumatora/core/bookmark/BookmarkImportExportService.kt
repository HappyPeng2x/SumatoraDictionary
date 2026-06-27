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

package org.happypeng.sumatora.core.bookmark

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object BookmarkImportExportService {

    private val mapper = ObjectMapper()

    @JvmStatic
    fun writeBookmarks(bookmarks: List<Bookmark>, outputFile: File) {
        mapper.writeValue(outputFile, bookmarks)
    }

    @JvmStatic
    fun writeBookmarks(bookmarks: List<Bookmark>, outputStream: OutputStream) {
        mapper.writeValue(outputStream, bookmarks)
    }

    @JvmStatic
    fun readBookmarks(inputStream: InputStream): List<Bookmark> {
        val listType = mapper.typeFactory.constructCollectionType(List::class.java, Bookmark::class.java)
        return mapper.readValue(inputStream, listType)
    }
}
