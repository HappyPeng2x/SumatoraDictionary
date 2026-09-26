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

import androidx.annotation.WorkerThread
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmarkTag
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmarkTombstone
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase
import org.happypeng.sumatora.core.bookmark.BookmarkImportExportService
import org.happypeng.sumatora.core.bookmark.BookmarkMergeService
import org.happypeng.sumatora.core.bookmark.SyncPayload
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// Orchestrates one bookmark sync round-trip against Nextcloud. See the nextcloud-sync plan's
// "Sync round-trip" section for the full rationale; in short: GET the remote envelope, merge with
// local state via BookmarkMergeService.mergeWithTombstones (deletion-aware, symmetric), apply the
// merged result locally, then PUT it back with an ETag precondition so a concurrent push from
// another device is detected (412) rather than silently overwritten.
@Singleton
class NextcloudSyncComponent @Inject constructor(
    private val persistentDatabaseComponent: PersistentDatabaseComponent,
    private val credentialStore: NextcloudCredentialStore,
    private val webDavClient: NextcloudWebDavClient
) {
    companion object {
        // Not user-visible, doesn't need MKCOL'd parent directories - lives at the root of the
        // Nextcloud account's files.
        const val SYNC_FILE_PATH = "sumatora-bookmarks-sync.json"
        private const val MAX_CONFLICT_RETRIES = 3
        private val TOMBSTONE_GRACE_PERIOD_MS = TimeUnit.DAYS.toMillis(90)
    }

    sealed class SyncResult {
        data class Success(val bookmarkCount: Int, val tombstoneCount: Int) : SyncResult()
        object NotConfigured : SyncResult()
        object ConflictRetriesExhausted : SyncResult()
        data class Failure(val message: String) : SyncResult()
    }

    @WorkerThread
    fun sync(): SyncResult {
        val credentials = credentialStore.load() ?: return SyncResult.NotConfigured

        try {
            repeat(MAX_CONFLICT_RETRIES) {
                val result = attemptSync(credentials)
                if (result != null) {
                    return result
                }
            }
        } catch (e: IOException) {
            return SyncResult.Failure(e.message ?: e.javaClass.simpleName)
        }

        return SyncResult.ConflictRetriesExhausted
    }

    // Returns null to signal "the remote changed between our GET and PUT, retry the round-trip".
    private fun attemptSync(credentials: NextcloudCredentials): SyncResult? {
        val remoteFile = webDavClient.get(credentials, SYNC_FILE_PATH)
        val remotePayload = remoteFile?.let {
            BookmarkImportExportService.readSyncPayload(it.bytes.inputStream())
        } ?: SyncPayload.empty()

        val db = persistentDatabaseComponent.database
        val localBookmarks = db.dictionaryBookmarkDao().getAll().map { it.toBookmark() }
        val localTombstones = db.dictionaryBookmarkTombstoneDao().getAll().map { it.toBookmarkTombstone() }

        val merged = BookmarkMergeService.mergeWithTombstones(
            localBookmarks, localTombstones, remotePayload.bookmarks, remotePayload.tombstones
        )

        applyMergedResultLocally(db, localBookmarks, merged)

        val outBytes = ByteArrayOutputStream().apply {
            BookmarkImportExportService.writeSyncPayload(
                SyncPayload(1, merged.bookmarks, merged.tombstones), this
            )
        }.toByteArray()

        return when (val putResult = webDavClient.put(credentials, SYNC_FILE_PATH, outBytes, remoteFile?.etag)) {
            is NextcloudWebDavClient.PutResult.Success ->
                SyncResult.Success(merged.bookmarks.size, merged.tombstones.size)
            is NextcloudWebDavClient.PutResult.Conflict -> null
            is NextcloudWebDavClient.PutResult.Error ->
                SyncResult.Failure("HTTP ${putResult.code}: ${putResult.message}")
        }
    }

    private fun applyMergedResultLocally(
        db: PersistentDatabase,
        localBookmarksBeforeMerge: List<org.happypeng.sumatora.core.bookmark.Bookmark>,
        merged: BookmarkMergeService.TombstoneMergeResult
    ) {
        db.runInTransaction {
            val bookmarkDao = db.dictionaryBookmarkDao()
            val tagDao = db.dictionaryBookmarkTagDao()
            val tombstoneDao = db.dictionaryBookmarkTombstoneDao()

            val previousSeqs = localBookmarksBeforeMerge.map { it.seq }.toSet()
            val mergedSeqs = merged.bookmarks.map { it.seq }.toSet()

            // A seq that was alive locally but isn't in the merged result any more was either
            // deleted (won by a tombstone) or never independently resurrected - either way it must
            // be removed here so this device's live table matches the converged state.
            for (seq in previousSeqs - mergedSeqs) {
                bookmarkDao.delete(seq)
                tagDao.deleteTagsForSeq(seq)
            }

            for (bookmark in merged.bookmarks) {
                bookmarkDao.insert(DictionaryBookmark.fromBookmark(bookmark))

                // Rebuild the derived tag index the same way BookmarkImportComponent does for
                // local JSON import.
                tagDao.deleteTagsForSeq(bookmark.seq)
                val tags = BookmarkMergeService.splitTags(bookmark.tags)
                if (tags.isNotEmpty()) {
                    tagDao.insertMany(tags.map { DictionaryBookmarkTag(bookmark.seq, it) })
                }
            }

            // This sync round-trip is itself the "confirmed dead on the remote copy too" signal a
            // tombstone needs before it can be pruned (see the plan's "Tombstone pruning" section)
            // - anything past the grace period at this point can be dropped; it simply won't be
            // re-uploaded on the next sync.
            tombstoneDao.deleteAll()
            val cutoff = System.currentTimeMillis() - TOMBSTONE_GRACE_PERIOD_MS
            val toKeep = merged.tombstones.filter { it.deletedAt >= cutoff }
            if (toKeep.isNotEmpty()) {
                tombstoneDao.insertMany(toKeep.map { DictionaryBookmarkTombstone.fromBookmarkTombstone(it) })
            }
        }
    }
}
