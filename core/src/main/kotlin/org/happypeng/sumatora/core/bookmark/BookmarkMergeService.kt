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

object BookmarkMergeService {

    /**
     * Merges incoming bookmarks into the existing set using the same rules
     * as the SQL UPSERT in BookmarkImportComponent:
     *  - bookmark = MAX(existing, incoming)
     *  - memo updated only if incoming.memo is non-null and non-empty
     *  - tags updated only if incoming.tags is non-null and non-empty
     */
    @JvmStatic
    fun merge(existing: List<Bookmark>, incoming: List<Bookmark>): List<Bookmark> {
        val result = LinkedHashMap<Long, Bookmark>()
        for (b in existing) {
            result[b.seq] = Bookmark(b.seq, b.bookmark, b.memo, b.tags)
        }
        for (inc in incoming) {
            val ex = result[inc.seq]
            result[inc.seq] = if (ex == null) {
                Bookmark(inc.seq, inc.bookmark, inc.memo, inc.tags)
            } else {
                Bookmark(
                    inc.seq,
                    maxOf(ex.bookmark, inc.bookmark),
                    if (!inc.memo.isNullOrEmpty()) inc.memo else ex.memo,
                    if (!inc.tags.isNullOrEmpty()) inc.tags else ex.tags
                )
            }
        }
        return result.values.toList()
    }

    @JvmStatic
    fun splitTags(tagsString: String?): List<String> =
        tagsString?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

    @JvmStatic
    fun joinTags(tags: List<String>): String = tags.joinToString(",")

    data class TombstoneMergeResult(
        val bookmarks: List<Bookmark>,
        val tombstones: List<BookmarkTombstone>
    )

    /**
     * Bidirectional merge of bookmarks + deletion tombstones. Deliberately symmetric: calling
     * this with (deviceA's state, deviceB's state) or (deviceB's state, deviceA's state) produces
     * the identical result, which is what lets two devices converge on the same data after each
     * runs its own sync round independently. See the "Why tombstones are needed" section of the
     * nextcloud-sync plan for the full rationale.
     *
     * Per seq, in the union of every bookmark/tombstone seen on either side:
     *  - only tombstone(s), no live row anywhere -> stays dead, keep the newest deletedAt.
     *  - only live row(s), no tombstone anywhere -> alive, field-merged (see mergeLiveCandidates).
     *  - both a live row and a tombstone somewhere -> compare the tombstone's deletedAt against
     *    the field-merged live row's updatedAt; whichever is newer wins. This is what lets an
     *    edit made after a delete (on another device) resurrect the bookmark, and what stops an
     *    older, already-synced copy from resurrecting something genuinely deleted later.
     */
    @JvmStatic
    fun mergeWithTombstones(
        localBookmarks: List<Bookmark>,
        localTombstones: List<BookmarkTombstone>,
        remoteBookmarks: List<Bookmark>,
        remoteTombstones: List<BookmarkTombstone>
    ): TombstoneMergeResult {
        val localBookmarkMap = localBookmarks.associateBy { it.seq }
        val remoteBookmarkMap = remoteBookmarks.associateBy { it.seq }
        val localTombstoneMap = localTombstones.associateBy { it.seq }
        val remoteTombstoneMap = remoteTombstones.associateBy { it.seq }

        val allSeqs = LinkedHashSet<Long>().apply {
            addAll(localBookmarkMap.keys)
            addAll(remoteBookmarkMap.keys)
            addAll(localTombstoneMap.keys)
            addAll(remoteTombstoneMap.keys)
        }

        val mergedBookmarks = ArrayList<Bookmark>()
        val mergedTombstones = ArrayList<BookmarkTombstone>()

        for (seq in allSeqs) {
            val liveCandidates = listOfNotNull(localBookmarkMap[seq], remoteBookmarkMap[seq])
            val deadCandidates = listOfNotNull(localTombstoneMap[seq], remoteTombstoneMap[seq])

            if (liveCandidates.isEmpty()) {
                mergedTombstones.add(deadCandidates.maxByOrNull { it.deletedAt }!!)
                continue
            }

            val mergedLive = mergeLiveCandidates(liveCandidates)

            if (deadCandidates.isEmpty()) {
                mergedBookmarks.add(mergedLive)
                continue
            }

            val newestTombstone = deadCandidates.maxByOrNull { it.deletedAt }!!

            if (newestTombstone.deletedAt >= mergedLive.updatedAt) {
                mergedTombstones.add(newestTombstone)
            } else {
                mergedBookmarks.add(mergedLive)
            }
        }

        return TombstoneMergeResult(mergedBookmarks, mergedTombstones)
    }

    // Combines 1 or 2 live copies of the same seq into one row. With 2 candidates, per-field
    // selection is symmetric (order of the input list never affects the result): the field with
    // the newer updatedAt wins outright; a tie falls back to a deterministic, value-based
    // (not position-based) comparison so the result never depends on which side is "local".
    private fun mergeLiveCandidates(candidates: List<Bookmark>): Bookmark {
        if (candidates.size == 1) {
            val b = candidates[0]
            return Bookmark(b.seq, b.bookmark, b.memo, b.tags, b.updatedAt)
        }

        val a = candidates[0]
        val b = candidates[1]

        return Bookmark(
            a.seq,
            maxOf(a.bookmark, b.bookmark),
            pickField(a.memo, a.updatedAt, b.memo, b.updatedAt),
            pickField(a.tags, a.updatedAt, b.tags, b.updatedAt),
            maxOf(a.updatedAt, b.updatedAt)
        )
    }

    private fun pickField(v1: String?, t1: Long, v2: String?, t2: Long): String? {
        val e1 = v1.isNullOrEmpty()
        val e2 = v2.isNullOrEmpty()

        return when {
            e1 && e2 -> null
            e1 -> v2
            e2 -> v1
            t1 != t2 -> if (t1 > t2) v1 else v2
            else -> if (v1!! <= v2!!) v1 else v2
        }
    }
}
