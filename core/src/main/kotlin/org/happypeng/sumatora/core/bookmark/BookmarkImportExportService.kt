package org.happypeng.sumatora.core.bookmark

import com.fasterxml.jackson.core.type.TypeReference
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
        return mapper.readValue(inputStream, object : TypeReference<List<Bookmark>>() {})
    }
}
