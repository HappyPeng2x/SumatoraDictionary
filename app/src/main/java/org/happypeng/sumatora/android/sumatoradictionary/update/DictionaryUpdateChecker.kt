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
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase
import java.io.File

// Generalizes Phase 0b's manual suffix/names download to every already-installed pack, per
// update-pipeline.md. Deliberately only updates packs the user already has (core/gloss_*/pitch/
// kanji/examples_*/suffix/names all qualify once installed) - it never auto-installs a pack the
// user hasn't opted into, same restraint DictionariesManagementActivity already applies.
object DictionaryUpdateChecker {
    private const val TAG = "DictionaryUpdateChecker"

    @WorkerThread
    fun checkAndEnqueue(context: Context, db: PersistentDatabase, manifestUrl: String): Int {
        val remoteEntries = RemoteManifestFetcher.fetch(manifestUrl) ?: return 0
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
}
