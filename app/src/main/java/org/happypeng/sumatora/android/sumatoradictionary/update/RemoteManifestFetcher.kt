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

// version/date on the result mirror the <repository> element (every <dictionary> child shares
// them - see BaseDictionaryObject.fromXML), so callers needing "what version is this manifest"
// don't need to dig into entries. changelogUrl/changelogSha256 are null when the manifest predates
// changelog-pipeline.md's changelog attributes (older releases, or a manifest with no changes to
// report yet).
data class RemoteManifest(
    val entries: List<RemoteDictionaryObject>,
    val version: Int,
    val date: Int,
    val changelogUrl: String?,
    val changelogSha256: String?
)

// Fetches and parses the dictionaries.xml manifest describing what's currently published -
// same XML shape as the bundled asset manifest (see PersistentDatabaseInitialization), served
// from a stable raw.githubusercontent.com URL rather than a release-tagged one, per
// update-pipeline.md. Returns null on any network/parse failure so callers can just skip this
// check cycle rather than crash.
object RemoteManifestFetcher {
    private const val TAG = "RemoteManifestFetcher"
    private const val TIMEOUT_MILLIS = 15000

    @WorkerThread
    fun fetch(manifestUrl: String): RemoteManifest? {
        var connection: HttpURLConnection? = null

        return try {
            connection = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                requestMethod = "GET"
            }

            var repoVersion = 0
            var repoDate = 0
            var changelogUrl: String? = null
            var changelogSha256: String? = null

            val entries = connection.inputStream.use { stream ->
                BaseDictionaryObject.fromXML(stream, { uri, description, type, lang, version, date, sha256 ->
                    RemoteDictionaryObject(uri, description, type, lang, version, date, sha256)
                }, { version, date, changelog, changelogSha ->
                    repoVersion = version
                    repoDate = date
                    changelogUrl = changelog
                    changelogSha256 = changelogSha
                })
            } ?: return null

            RemoteManifest(entries, repoVersion, repoDate, changelogUrl, changelogSha256)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch dictionary manifest from $manifestUrl", e)
            null
        } finally {
            connection?.disconnect()
        }
    }
}
