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

import org.happypeng.sumatora.core.bookmark.Bookmark
import org.happypeng.sumatora.core.bookmark.BookmarkRepository
import org.happypeng.sumatora.desktop.db.DatabaseManager
import java.sql.ResultSet

class BookmarkRepositoryImpl(private val db: DatabaseManager) : BookmarkRepository {

    override fun getAll(): List<Bookmark> {
        val results = mutableListOf<Bookmark>()
        db.connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT seq, bookmark, memo, tags FROM DictionaryBookmark ORDER BY seq").use { rs ->
                while (rs.next()) results.add(rs.toBookmark())
            }
        }
        return results
    }

    override fun getBySeq(seq: Long): Bookmark? =
        db.connection.prepareStatement(
            "SELECT seq, bookmark, memo, tags FROM DictionaryBookmark WHERE seq = ?"
        ).use { ps ->
            ps.setLong(1, seq)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toBookmark() else null }
        }

    override fun insert(bookmark: Bookmark) {
        db.connection.prepareStatement(
            "INSERT OR REPLACE INTO DictionaryBookmark(seq, bookmark, memo, tags) VALUES(?, ?, ?, ?)"
        ).use { ps ->
            ps.setLong(1, bookmark.seq)
            ps.setLong(2, bookmark.bookmark)
            ps.setString(3, bookmark.memo)
            ps.setString(4, bookmark.tags)
            ps.executeUpdate()
        }
    }

    override fun insertMany(bookmarks: List<Bookmark>) {
        val conn = db.connection
        conn.autoCommit = false
        try {
            conn.prepareStatement(
                "INSERT OR REPLACE INTO DictionaryBookmark(seq, bookmark, memo, tags) VALUES(?, ?, ?, ?)"
            ).use { ps ->
                for (b in bookmarks) {
                    ps.setLong(1, b.seq)
                    ps.setLong(2, b.bookmark)
                    ps.setString(3, b.memo)
                    ps.setString(4, b.tags)
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

    override fun delete(seq: Long) {
        db.connection.prepareStatement("DELETE FROM DictionaryBookmark WHERE seq = ?").use { ps ->
            ps.setLong(1, seq)
            ps.executeUpdate()
        }
    }

    override fun deleteAll() {
        db.connection.createStatement().use { it.executeUpdate("DELETE FROM DictionaryBookmark") }
    }

    private fun ResultSet.toBookmark() = Bookmark(
        getLong("seq"), getLong("bookmark"), getString("memo"), getString("tags")
    )
}
