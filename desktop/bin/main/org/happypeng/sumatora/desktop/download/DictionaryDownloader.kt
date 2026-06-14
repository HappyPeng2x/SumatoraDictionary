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

package org.happypeng.sumatora.desktop.download

import org.happypeng.sumatora.desktop.model.DictionaryEntry
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object DictionaryDownloader {

    /**
     * Downloads and gunzips a dictionary.
     *
     * [onProgress] receives (downloaded bytes, total bytes).
     * total = -1 signals the decompression step.
     * Throws on failure; callers must clean up if needed.
     */
    fun download(
        entry: DictionaryEntry,
        dictDir: File,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ) {
        dictDir.mkdirs()
        val tempGz = File(dictDir, "${entry.lang}.tmp.gz")
        val destDb = File(dictDir, entry.localFileName)

        try {
            // ── Download compressed file ──────────────────────────────────────
            val conn = URL(entry.uri).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 0  // large files — let it run

            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(tempGz).use { out ->
                    val buf = ByteArray(16 * 1024)
                    var downloaded = 0L
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
            conn.disconnect()

            // ── Decompress ───────────────────────────────────────────────────
            onProgress(-1L, -1L)
            GZIPInputStream(FileInputStream(tempGz)).use { gz ->
                FileOutputStream(destDb).use { out -> gz.copyTo(out) }
            }
        } finally {
            tempGz.delete()
        }
    }
}
