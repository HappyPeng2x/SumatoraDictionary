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

import android.app.DownloadManager
import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import org.happypeng.sumatora.android.sumatoradictionary.db.CachedManifestEntry
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryChangelog
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Sha256
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Generalizes Phase 0b's manual suffix/names download to every already-installed pack, per
// update-pipeline.md. Deliberately only updates packs the user already has (core/gloss_*/pitch/
// kanji/examples_*/suffix/names all qualify once installed) - it never auto-installs a pack the
// user hasn't opted into, same restraint DictionariesManagementActivity already applies.
object DictionaryUpdateChecker {
    private const val TAG = "DictionaryUpdateChecker"
    private const val TIMEOUT_MILLIS = 15000

    @WorkerThread
    fun checkAndEnqueue(context: Context, db: PersistentDatabase, manifestUrl: String): Int {
        val manifest = RemoteManifestFetcher.fetch(manifestUrl) ?: return 0
        val remoteEntries = manifest.entries
        fetchAndStoreChangelog(context, db, manifest)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadDir = File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }

        // Keep a snapshot of the manifest we just saw so OptionalDictionaryCatalog can offer
        // not-yet-installed optional packs versioned to match whatever core version ends up
        // installed, instead of a hardcoded version that goes stale after an update.
        db.cachedManifestEntryDao().clear()
        db.cachedManifestEntryDao().insertAll(remoteEntries.map {
            CachedManifestEntry(it.type, it.lang, it.description, it.file, it.version, it.date, it.sha256)
        })

        var enqueued = 0

        for (remote in remoteEntries) {
            val installed = db.installedDictionaryDao().getForTypeLang(remote.type, remote.lang) ?: continue

            if (!remote.isSuperiorVersion(installed)) {
                continue
            }

            if (installed.hasPendingUpdate() && !isNewer(remote.version, remote.date,
                    installed.pendingVersion!!, installed.pendingDate!!)) {
                continue
            }

            val alreadyDownloading = db.remoteDictionaryObjectDao()
                .getForTypeLang(remote.type, remote.lang)?.downloadId ?: -1L

            if (alreadyDownloading > -1) {
                continue
            }

            Log.i(TAG, "Enqueuing update for ${remote.type}/${remote.lang}: " +
                    "${installed.version}/${installed.date} -> ${remote.version}/${remote.date}")

            remote.download(downloadManager, downloadDir)
            db.remoteDictionaryObjectDao().insert(remote)
            enqueued++
        }

        return enqueued
    }

    private fun isNewer(version: Int, date: Int, thanVersion: Int, thanDate: Int): Boolean =
        version > thanVersion || (version >= thanVersion && date > thanDate)

    // Runs on every check, independent of whether the user has any packs installed - unlike the
    // per-pack loop above, "recent updates" is meant to tell users about releases even for
    // dictionaries they haven't downloaded yet. hasVersion() short-circuits repeat checks within
    // the same release cycle (this runs every 7 days, but a release is also weekly - see
    // release-dictionaries.yml - so most checks land on an already-seen version).
    @WorkerThread
    private fun fetchAndStoreChangelog(context: Context, db: PersistentDatabase, manifest: RemoteManifest) {
        val url = manifest.changelogUrl ?: return

        if (db.dictionaryChangelogDao().hasVersion(manifest.version)) {
            return
        }

        var connection: HttpURLConnection? = null
        val tempFile = File.createTempFile("changelog", ".json", context.cacheDir)

        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                requestMethod = "GET"
            }

            connection.inputStream.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }

            if (!manifest.changelogSha256.isNullOrEmpty() && !Sha256.matches(tempFile, manifest.changelogSha256)) {
                Log.e(TAG, "Checksum mismatch for changelog.json v${manifest.version}, discarding")
                return
            }

            db.dictionaryChangelogDao().insert(DictionaryChangelog(
                manifest.version, manifest.date, tempFile.readText(), System.currentTimeMillis()
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch changelog.json from $url", e)
        } finally {
            connection?.disconnect()
            tempFile.delete()
        }
    }
}
