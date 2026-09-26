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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkMergeServiceTest {

    private fun bookmark(seq: Long, bookmark: Long, memo: String?, tags: String?, updatedAt: Long) =
        Bookmark(seq, bookmark, memo, tags, updatedAt)

    private fun tombstone(seq: Long, deletedAt: Long) = BookmarkTombstone(seq, deletedAt)

    @Test
    fun `live both sides no tombstone - field merge picks newer non-empty field`() {
        val local = listOf(bookmark(1, 100, "local memo", null, 100))
        val remote = listOf(bookmark(1, 50, null, "tag-a,tag-b", 200))

        val result = BookmarkMergeService.mergeWithTombstones(local, emptyList(), remote, emptyList())

        assertTrue(result.tombstones.isEmpty())
        assertEquals(1, result.bookmarks.size)
        val merged = result.bookmarks[0]
        assertEquals(100L, merged.bookmark) // max of the two "starred-at" timestamps
        assertEquals("local memo", merged.memo) // only local set a memo
        assertEquals("tag-a,tag-b", merged.tags) // only remote set tags
        assertEquals(200L, merged.updatedAt)
    }

    @Test
    fun `live both sides both set memo - newer updatedAt wins`() {
        val local = listOf(bookmark(1, 0, "old memo", null, 100))
        val remote = listOf(bookmark(1, 0, "new memo", null, 200))

        val result = BookmarkMergeService.mergeWithTombstones(local, emptyList(), remote, emptyList())

        assertEquals("new memo", result.bookmarks[0].memo)
    }

    @Test
    fun `field merge is symmetric regardless of argument order`() {
        val a = listOf(bookmark(1, 0, "memo A", null, 300))
        val b = listOf(bookmark(1, 0, "memo B", null, 300)) // tie on updatedAt

        val ab = BookmarkMergeService.mergeWithTombstones(a, emptyList(), b, emptyList())
        val ba = BookmarkMergeService.mergeWithTombstones(b, emptyList(), a, emptyList())

        assertEquals(ab.bookmarks[0].memo, ba.bookmarks[0].memo)
    }

    @Test
    fun `delete newer than edit stays dead`() {
        val local = listOf(bookmark(1, 0, "memo", null, 100)) // edited at 100
        val remoteTombstones = listOf(tombstone(1, 200)) // deleted remotely at 200

        val result = BookmarkMergeService.mergeWithTombstones(local, emptyList(), emptyList(), remoteTombstones)

        assertTrue(result.bookmarks.isEmpty())
        assertEquals(1, result.tombstones.size)
        assertEquals(200L, result.tombstones[0].deletedAt)
    }

    @Test
    fun `edit newer than delete resurrects the bookmark`() {
        val local = listOf(bookmark(1, 0, "edited after delete", null, 300)) // edited at 300
        val remoteTombstones = listOf(tombstone(1, 200)) // deleted remotely at 200

        val result = BookmarkMergeService.mergeWithTombstones(local, emptyList(), emptyList(), remoteTombstones)

        assertTrue(result.tombstones.isEmpty())
        assertEquals(1, result.bookmarks.size)
        assertEquals("edited after delete", result.bookmarks[0].memo)
    }

    @Test
    fun `tombstone on both sides keeps the newest deletedAt`() {
        val localTombstones = listOf(tombstone(1, 100))
        val remoteTombstones = listOf(tombstone(1, 250))

        val result = BookmarkMergeService.mergeWithTombstones(emptyList(), localTombstones, emptyList(), remoteTombstones)

        assertEquals(1, result.tombstones.size)
        assertEquals(250L, result.tombstones[0].deletedAt)
    }

    @Test
    fun `tombstone only one side stays dead`() {
        val localTombstones = listOf(tombstone(1, 100))

        val result = BookmarkMergeService.mergeWithTombstones(emptyList(), localTombstones, emptyList(), emptyList())

        assertTrue(result.bookmarks.isEmpty())
        assertEquals(1, result.tombstones.size)
    }

    @Test
    fun `new bookmark on only one side is kept as-is`() {
        val remote = listOf(bookmark(1, 0, "new from remote", "tag", 100))

        val result = BookmarkMergeService.mergeWithTombstones(emptyList(), emptyList(), remote, emptyList())

        assertEquals(1, result.bookmarks.size)
        assertEquals("new from remote", result.bookmarks[0].memo)
    }

    @Test
    fun `equal timestamp tie break is deterministic and order independent`() {
        val a = listOf(bookmark(1, 0, "aaa", null, 100))
        val b = listOf(bookmark(1, 0, "zzz", null, 100))

        val ab = BookmarkMergeService.mergeWithTombstones(a, emptyList(), b, emptyList())
        val ba = BookmarkMergeService.mergeWithTombstones(b, emptyList(), a, emptyList())

        assertEquals("aaa", ab.bookmarks[0].memo) // lexicographically smaller wins the tie
        assertEquals(ab.bookmarks[0].memo, ba.bookmarks[0].memo)
    }

    @Test
    fun `both memo and tags empty on both sides yields null fields, not empty strings`() {
        val local = listOf(bookmark(1, 5, null, null, 100))
        val remote = listOf(bookmark(1, 3, "", "", 50))

        val result = BookmarkMergeService.mergeWithTombstones(local, emptyList(), remote, emptyList())

        assertNull(result.bookmarks[0].memo)
        assertNull(result.bookmarks[0].tags)
        assertEquals(5L, result.bookmarks[0].bookmark)
    }
}
