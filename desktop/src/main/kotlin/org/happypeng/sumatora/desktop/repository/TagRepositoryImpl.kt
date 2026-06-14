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

package org.happypeng.sumatora.desktop.repository

import org.happypeng.sumatora.core.bookmark.BookmarkTag
import org.happypeng.sumatora.core.bookmark.TagRepository
import org.happypeng.sumatora.desktop.db.DatabaseManager

class TagRepositoryImpl(private val db: DatabaseManager) : TagRepository {

    override fun getAllTags(): List<String> {
        val tags = mutableListOf<String>()
        db.connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT DISTINCT tag FROM DictionaryBookmarkTag ORDER BY tag").use { rs ->
                while (rs.next()) tags.add(rs.getString("tag"))
            }
        }
        return tags
    }

    override fun getTagsForSeq(seq: Long): List<String> {
        val tags = mutableListOf<String>()
        db.connection.prepareStatement(
            "SELECT tag FROM DictionaryBookmarkTag WHERE seq = ? ORDER BY tag"
        ).use { ps ->
            ps.setLong(1, seq)
            ps.executeQuery().use { rs ->
                while (rs.next()) tags.add(rs.getString("tag"))
            }
        }
        return tags
    }

    override fun insertTag(tag: BookmarkTag) {
        db.connection.prepareStatement(
            "INSERT OR IGNORE INTO DictionaryBookmarkTag(seq, tag) VALUES(?, ?)"
        ).use { ps ->
            ps.setLong(1, tag.seq)
            ps.setString(2, tag.tag)
            ps.executeUpdate()
        }
    }

    override fun insertManyTags(tags: List<BookmarkTag>) {
        val conn = db.connection
        conn.autoCommit = false
        try {
            conn.prepareStatement(
                "INSERT OR IGNORE INTO DictionaryBookmarkTag(seq, tag) VALUES(?, ?)"
            ).use { ps ->
                for (t in tags) {
                    ps.setLong(1, t.seq)
                    ps.setString(2, t.tag)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }

    override fun deleteTagsForSeq(seq: Long) {
        db.connection.prepareStatement(
            "DELETE FROM DictionaryBookmarkTag WHERE seq = ?"
        ).use { ps ->
            ps.setLong(1, seq)
            ps.executeUpdate()
        }
    }

    override fun deleteTag(seq: Long, tag: String) {
        db.connection.prepareStatement(
            "DELETE FROM DictionaryBookmarkTag WHERE seq = ? AND tag = ?"
        ).use { ps ->
            ps.setLong(1, seq)
            ps.setString(2, tag)
            ps.executeUpdate()
        }
    }
}
