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
        along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.happypeng.sumatora.android.sumatoradictionary.update

import android.util.Log
import androidx.annotation.WorkerThread
import org.happypeng.sumatora.android.sumatoradictionary.db.RemoteDictionaryObject
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BaseDictionaryObject
import java.net.HttpURLConnection
import java.net.URL

// Fetches and parses the dictionaries.xml manifest describing what's currently published -
// same XML shape as the bundled asset manifest (see PersistentDatabaseInitialization), served
// from a stable raw.githubusercontent.com URL rather than a release-tagged one, per
// update-pipeline.md. Returns null on any network/parse failure so callers can just skip this
// check cycle rather than crash.
object RemoteManifestFetcher {
    private const val TAG = "RemoteManifestFetcher"
    private const val TIMEOUT_MILLIS = 15000

    @WorkerThread
    fun fetch(manifestUrl: String): List<RemoteDictionaryObject>? {
        var connection: HttpURLConnection? = null

        return try {
            connection = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                requestMethod = "GET"
            }

            connection.inputStream.use { stream ->
                BaseDictionaryObject.fromXML(stream) { uri, description, type, lang, version, date, sha256 ->
                    RemoteDictionaryObject(uri, description, type, lang, version, date, sha256)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch dictionary manifest from $manifestUrl", e)
            null
        } finally {
            connection?.disconnect()
        }
    }
}
