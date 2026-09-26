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

package org.happypeng.sumatora.android.sumatoradictionary.sync

import android.util.Base64
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

// Hand-rolled WebDAV PUT/GET against a Nextcloud server's file endpoint - not a general WebDAV
// client (no PROPFIND/directory listing), since sync only ever touches one fixed file. Kept on
// HttpURLConnection rather than a library, consistent with this project's existing
// DownloadManager-over-Retrofit preference (see the nextcloud-sync plan).
@Singleton
class NextcloudWebDavClient @Inject constructor() {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
    }

    data class RemoteFile(val bytes: ByteArray, val etag: String?)

    sealed class PutResult {
        data class Success(val etag: String?) : PutResult()
        // HTTP 412 Precondition Failed - the remote file changed since our last GET.
        object Conflict : PutResult()
        data class Error(val code: Int, val message: String) : PutResult()
    }

    // Returns null if the file doesn't exist yet on the server (first sync).
    fun get(credentials: NextcloudCredentials, path: String): RemoteFile? {
        val connection = openConnection(credentials, path, "GET")

        return try {
            when (val code = connection.responseCode) {
                404 -> null
                in 200..299 -> RemoteFile(
                    connection.inputStream.use { it.readBytes() },
                    connection.getHeaderField("ETag")
                )
                else -> throw IOException("WebDAV GET failed: HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    // ifMatchEtag should be the ETag from the GET this PUT is based on, or null for a first
    // upload (in which case If-None-Match: * stops this from clobbering a file that appeared
    // concurrently between our GET-404 and this PUT).
    fun put(credentials: NextcloudCredentials, path: String, bytes: ByteArray, ifMatchEtag: String?): PutResult {
        val connection = openConnection(credentials, path, "PUT")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        if (ifMatchEtag != null) {
            connection.setRequestProperty("If-Match", ifMatchEtag)
        } else {
            connection.setRequestProperty("If-None-Match", "*")
        }

        return try {
            connection.outputStream.use { it.write(bytes) }
            when (val code = connection.responseCode) {
                in 200..299 -> PutResult.Success(connection.getHeaderField("ETag"))
                412 -> PutResult.Conflict
                else -> PutResult.Error(code, connection.responseMessage ?: "")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(credentials: NextcloudCredentials, path: String, method: String): HttpURLConnection {
        val url = buildDavUrl(credentials.server, credentials.loginName, path)
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS

        val basicAuth = Base64.encodeToString(
            "${credentials.loginName}:${credentials.appPassword}".toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )
        connection.setRequestProperty("Authorization", "Basic $basicAuth")

        return connection
    }

    private fun buildDavUrl(server: String, loginName: String, path: String): String {
        val encodedUser = URLEncoder.encode(loginName, "UTF-8")
        val encodedPath = path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }
        return "$server/remote.php/dav/files/$encodedUser/$encodedPath"
    }
}
