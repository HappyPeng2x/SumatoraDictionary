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

package org.happypeng.sumatora.desktop.db

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

class DatabaseManager(dataDir: File) : AutoCloseable {

    private val dbFile = File(dataDir, "sumatora.db")
    val connection: Connection

    init {
        dataDir.mkdirs()
        connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        initSchema()
    }

    private fun initSchema() {
        connection.createStatement().use { stmt ->
            stmt.executeUpdate("PRAGMA journal_mode=WAL")
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS Settings (
                    key   TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS DictionaryBookmark (
                    seq      INTEGER PRIMARY KEY,
                    bookmark INTEGER NOT NULL DEFAULT 0,
                    memo     TEXT,
                    tags     TEXT
                )
            """)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS DictionaryBookmarkTag (
                    seq INTEGER NOT NULL,
                    tag TEXT NOT NULL,
                    PRIMARY KEY (seq, tag)
                )
            """)
        }
    }

    // ── Dictionary attachment ────────────────────────────────────────────────

    fun attachDictionary(file: File, alias: String) {
        connection.createStatement().use { stmt ->
            stmt.execute("ATTACH DATABASE '${file.absolutePath}' AS \"$alias\"")
        }
    }

    fun detachDictionary(alias: String) {
        connection.createStatement().use { stmt ->
            stmt.execute("DETACH DATABASE \"$alias\"")
        }
    }

    fun getAttachedDatabases(): List<String> {
        val names = mutableListOf<String>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("PRAGMA database_list").use { rs ->
                while (rs.next()) {
                    val name = rs.getString("name")
                    if (name != "main" && name != "temp") names.add(name)
                }
            }
        }
        return names
    }

    // ── Settings ─────────────────────────────────────────────────────────────

    fun getSetting(key: String): String? =
        connection.prepareStatement("SELECT value FROM Settings WHERE key = ?").use { ps ->
            ps.setString(1, key)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString("value") else null }
        }

    fun setSetting(key: String, value: String) {
        connection.prepareStatement(
            "INSERT OR REPLACE INTO Settings(key, value) VALUES(?, ?)"
        ).use { ps ->
            ps.setString(1, key)
            ps.setString(2, value)
            ps.executeUpdate()
        }
    }

    // ── Generic query helper (for diagnostics / Phase 1 verification) ────────

    fun rawQuery(sql: String, vararg params: Any?): List<Map<String, Any?>> {
        val rows = mutableListOf<Map<String, Any?>>()
        connection.prepareStatement(sql).use { ps ->
            params.forEachIndexed { i, p -> ps.setObject(i + 1, p) }
            ps.executeQuery().use { rs ->
                val meta = rs.metaData
                val cols = (1..meta.columnCount).map { meta.getColumnName(it) }
                while (rs.next()) {
                    rows.add(cols.associateWith { rs.getObject(it) })
                }
            }
        }
        return rows
    }

    override fun close() {
        if (!connection.isClosed) connection.close()
    }
}
