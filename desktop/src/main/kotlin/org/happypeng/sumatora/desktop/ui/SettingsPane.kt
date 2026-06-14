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

package org.happypeng.sumatora.desktop.ui

import javafx.geometry.Insets
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import org.happypeng.sumatora.core.bookmark.Bookmark
import org.happypeng.sumatora.core.bookmark.BookmarkTag
import org.happypeng.sumatora.core.bookmark.BookmarkImportExportService
import org.happypeng.sumatora.core.bookmark.BookmarkMergeService
import java.io.File

class SettingsPane(private val ctx: AppContext) : VBox(16.0) {

    private val langCombo = ComboBox<String>()

    init {
        padding = Insets(24.0)
        style = "-fx-background-color: #f5f5f5;"

        // ── Section header ────────────────────────────────────────────────────
        children += Label("Settings").apply { style = "-fx-font-size: 20px; -fx-font-weight: bold;" }
        children += Separator()

        // ── Language ─────────────────────────────────────────────────────────
        val langOptions = ctx.availableDbs.keys.filter { it != "jmdict" }.sorted()
        langCombo.items.setAll(langOptions)
        langCombo.value = ctx.search.lang.takeIf { it in langOptions } ?: langOptions.firstOrNull()

        langCombo.valueProperty().addListener { _, _, newLang ->
            if (newLang != null) ctx.changeLanguage(newLang)
        }

        val langRow = GridPane().apply {
            hgap = 12.0; vgap = 8.0
            add(Label("Dictionary language:").apply { style = "-fx-font-size: 14px;" }, 0, 0)
            add(langCombo, 1, 0)
        }
        children += langRow
        children += Separator()

        // ── Data directory info ───────────────────────────────────────────────
        val dataDir = File(System.getProperty("user.home"), ".sumatora")
        val dictDir = File(dataDir, "dictionaries")
        children += Label("Dictionaries directory").apply { style = "-fx-font-size: 14px; -fx-font-weight: bold;" }
        children += Label(dictDir.absolutePath).apply { style = "-fx-font-family: monospace; -fx-font-size: 12px; -fx-text-fill: #555;" }
        val dbList = ctx.availableDbs.keys.sorted().joinToString(", ").ifEmpty { "(none found)" }
        children += Label("Available databases: $dbList").apply { style = "-fx-font-size: 12px; -fx-text-fill: #666;" }
        children += Separator()

        // ── Bookmark import / export ──────────────────────────────────────────
        children += Label("Bookmarks").apply { style = "-fx-font-size: 14px; -fx-font-weight: bold;" }

        val btnExport = Button("Export bookmarks…").apply {
            style = "-fx-font-size: 13px; -fx-cursor: hand;"
            setOnAction { exportBookmarks() }
        }
        val btnImport = Button("Import bookmarks…").apply {
            style = "-fx-font-size: 13px; -fx-cursor: hand;"
            setOnAction { importBookmarks() }
        }
        children += HBox(12.0, btnExport, btnImport)
    }

    private fun exportBookmarks() {
        val chooser = FileChooser().apply {
            title = "Export bookmarks"
            extensionFilters.add(FileChooser.ExtensionFilter("JSON files", "*.json"))
            initialFileName = "sumatora-bookmarks.json"
        }
        val file = chooser.showSaveDialog(scene.window) ?: return
        Thread {
            try {
                val all = ctx.bookmarks.getAll()
                BookmarkImportExportService.writeBookmarks(all, file)
                showInfo("Export complete", "${all.size} bookmarks exported to ${file.name}.")
            } catch (e: Exception) {
                showError("Export failed", e.message ?: "Unknown error")
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun importBookmarks() {
        val chooser = FileChooser().apply {
            title = "Import bookmarks"
            extensionFilters.add(FileChooser.ExtensionFilter("JSON files", "*.json"))
        }
        val file = chooser.showOpenDialog(scene.window) ?: return
        Thread {
            try {
                val incoming = file.inputStream().use { BookmarkImportExportService.readBookmarks(it) }
                val existing = ctx.bookmarks.getAll()
                val merged = BookmarkMergeService.merge(existing, incoming)

                val conn = ctx.db.connection
                conn.autoCommit = false
                try {
                    conn.createStatement().use {
                        it.executeUpdate("DELETE FROM DictionaryBookmark")
                        it.executeUpdate("DELETE FROM DictionaryBookmarkTag")
                    }
                    conn.prepareStatement(
                        "INSERT INTO DictionaryBookmark(seq, bookmark, memo, tags) VALUES(?,?,?,?)"
                    ).use { ps ->
                        for (b in merged) {
                            ps.setLong(1, b.seq); ps.setLong(2, b.bookmark)
                            ps.setString(3, b.memo); ps.setString(4, b.tags)
                            ps.addBatch()
                        }
                        ps.executeBatch()
                    }
                    conn.prepareStatement(
                        "INSERT OR IGNORE INTO DictionaryBookmarkTag(seq, tag) VALUES(?,?)"
                    ).use { ps ->
                        for (b in merged) {
                            b.tags?.split(",")?.filter { it.isNotEmpty() }?.forEach { tag ->
                                ps.setLong(1, b.seq); ps.setString(2, tag); ps.addBatch()
                            }
                        }
                        ps.executeBatch()
                    }
                    conn.commit()
                } catch (e: Exception) {
                    conn.rollback(); throw e
                } finally {
                    conn.autoCommit = true
                }

                showInfo("Import complete", "${merged.size} bookmarks imported.")
            } catch (e: Exception) {
                showError("Import failed", e.message ?: "Unknown error")
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun showInfo(title: String, msg: String) =
        javafx.application.Platform.runLater {
            Alert(Alert.AlertType.INFORMATION).apply {
                this.title = title; headerText = null; contentText = msg
            }.showAndWait()
        }

    private fun showError(title: String, msg: String) =
        javafx.application.Platform.runLater {
            Alert(Alert.AlertType.ERROR).apply {
                this.title = title; headerText = null; contentText = msg
            }.showAndWait()
        }
}
