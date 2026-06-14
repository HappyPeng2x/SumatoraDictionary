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

import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.ScrollPane
import javafx.scene.control.Separator
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import org.happypeng.sumatora.core.bookmark.BookmarkImportExportService
import org.happypeng.sumatora.core.bookmark.BookmarkMergeService
import org.happypeng.sumatora.desktop.download.DictionaryDownloader
import org.happypeng.sumatora.desktop.model.DictionaryEntry
import org.happypeng.sumatora.desktop.repository.DictionaryRepository
import java.io.File

class SettingsPane(private val ctx: AppContext) : ScrollPane() {

    private val langCombo = ComboBox<String>()
    private val dictDir = File(System.getProperty("user.home"), ".sumatora/dictionaries")
    private val dictEntriesBox = VBox(6.0)

    init {
        isFitToWidth = true
        style = "-fx-background-color: #f5f5f5;"

        val body = VBox(16.0).apply { padding = Insets(24.0) }
        content = body

        // ── Title ─────────────────────────────────────────────────────────────
        body.children += Label("Settings").apply { style = "-fx-font-size: 20px; -fx-font-weight: bold;" }
        body.children += Separator()

        // ── Language ──────────────────────────────────────────────────────────
        val langOptions = ctx.availableDbs.keys.filter { it != "jmdict" }.sorted()
        langCombo.items.setAll(langOptions)
        langCombo.value = ctx.search.lang.takeIf { it in langOptions } ?: langOptions.firstOrNull()
        langCombo.valueProperty().addListener { _, _, newLang ->
            if (newLang != null) ctx.changeLanguage(newLang)
        }

        body.children += Label("Language").apply { style = SECTION_LABEL_STYLE }
        body.children += GridPane().apply {
            hgap = 12.0; vgap = 8.0
            add(Label("Dictionary language:"), 0, 0)
            add(langCombo, 1, 0)
        }
        body.children += Separator()

        // ── Data directory ────────────────────────────────────────────────────
        body.children += Label("Dictionaries").apply { style = SECTION_LABEL_STYLE }
        body.children += Label(dictDir.absolutePath).apply {
            style = "-fx-font-family: monospace; -fx-font-size: 12px; -fx-text-fill: #555;"
        }

        val fetchBtn = Button("Fetch available dictionaries…").apply {
            style = "-fx-cursor: hand;"
            setOnAction { fetchManifest(this) }
        }
        body.children += fetchBtn
        body.children += dictEntriesBox
        body.children += Separator()

        // ── Bookmarks import / export ─────────────────────────────────────────
        body.children += Label("Bookmarks").apply { style = SECTION_LABEL_STYLE }
        body.children += HBox(12.0,
            Button("Export bookmarks…").apply {
                style = "-fx-cursor: hand;"
                setOnAction { exportBookmarks() }
            },
            Button("Import bookmarks…").apply {
                style = "-fx-cursor: hand;"
                setOnAction { importBookmarks() }
            }
        )
    }

    // ── Dictionary management ─────────────────────────────────────────────────

    private fun fetchManifest(fetchBtn: Button) {
        fetchBtn.isDisable = true
        dictEntriesBox.children.setAll(ProgressIndicator().also { it.maxWidth = 32.0 })

        Thread {
            val result = try {
                Result.success(DictionaryRepository.fetchManifest())
            } catch (e: Exception) {
                Result.failure(e)
            }
            Platform.runLater {
                fetchBtn.isDisable = false
                result.fold(
                    onSuccess = { entries -> populateDictEntries(entries) },
                    onFailure = { e ->
                        dictEntriesBox.children.setAll(
                            Label("Failed to fetch: ${e.message}").apply {
                                style = "-fx-text-fill: crimson; -fx-font-size: 12px;"
                            }
                        )
                    }
                )
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun populateDictEntries(entries: List<DictionaryEntry>) {
        val installed = DictionaryRepository.installedLangs(dictDir)
        dictEntriesBox.children.clear()

        entries.filter { it.isSearchable }.forEach { entry ->
            dictEntriesBox.children += buildEntryRow(entry, entry.lang in installed)
        }

        if (dictEntriesBox.children.isEmpty()) {
            dictEntriesBox.children += Label("No searchable dictionaries found in manifest.")
                .apply { style = "-fx-text-fill: #666; -fx-font-size: 12px;" }
        }
    }

    private fun buildEntryRow(entry: DictionaryEntry, alreadyInstalled: Boolean): HBox {
        val nameLabel = Label(entry.displayName).apply {
            style = "-fx-font-size: 13px;"
            HBox.setHgrow(this, Priority.ALWAYS)
        }

        val progressBar = ProgressBar(0.0).apply {
            prefWidth = 160.0
            isVisible = false
        }
        val statusLabel = Label(if (alreadyInstalled) "✓ Installed" else "").apply {
            style = "-fx-text-fill: #2a7a2a; -fx-font-size: 12px;"
            minWidth = 80.0
        }
        val downloadBtn = Button("⬇ Download").apply {
            style = "-fx-cursor: hand; -fx-font-size: 12px;"
            isDisable = alreadyInstalled
            if (alreadyInstalled) isVisible = false
            setOnAction { startDownload(entry, this, progressBar, statusLabel) }
        }

        return HBox(8.0, nameLabel, progressBar, statusLabel, downloadBtn).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(4.0, 0.0, 4.0, 0.0)
        }
    }

    private fun startDownload(
        entry: DictionaryEntry,
        btn: Button,
        bar: ProgressBar,
        status: Label
    ) {
        btn.isVisible = false
        bar.isVisible = true
        bar.progress = 0.0
        status.style = "-fx-text-fill: #555; -fx-font-size: 12px;"
        status.text = "Downloading…"

        Thread {
            try {
                DictionaryDownloader.download(entry, dictDir) { downloaded, total ->
                    Platform.runLater {
                        if (downloaded == -1L) {
                            bar.progress = ProgressBar.INDETERMINATE_PROGRESS
                            status.text = "Decompressing…"
                        } else if (total > 0) {
                            bar.progress = downloaded.toDouble() / total
                            val pct = (downloaded * 100 / total).toInt()
                            status.text = "$pct%"
                        } else {
                            bar.progress = ProgressBar.INDETERMINATE_PROGRESS
                        }
                    }
                }

                // Attach the new database without restarting
                ctx.rescanAndAttach(dictDir)

                // Refresh language combo if a translation db was added
                if (entry.type == "translation") {
                    Platform.runLater {
                        val newOptions = ctx.availableDbs.keys.filter { it != "jmdict" }.sorted()
                        if (entry.lang !in langCombo.items) {
                            langCombo.items.setAll(newOptions)
                        }
                    }
                }

                Platform.runLater {
                    bar.isVisible = false
                    status.style = "-fx-text-fill: #2a7a2a; -fx-font-size: 12px;"
                    status.text = "✓ Installed"
                }
            } catch (e: Exception) {
                Platform.runLater {
                    bar.isVisible = false
                    btn.isVisible = true
                    status.style = "-fx-text-fill: crimson; -fx-font-size: 12px;"
                    status.text = "Failed: ${e.message?.take(40)}"
                }
            }
        }.also { it.isDaemon = true }.start()
    }

    // ── Bookmark import / export ───────────────────────────────────────────────

    private fun exportBookmarks() {
        val chooser = FileChooser().apply {
            title = "Export bookmarks"
            extensionFilters += FileChooser.ExtensionFilter("JSON files", "*.json")
            initialFileName = "sumatora-bookmarks.json"
        }
        val file = chooser.showSaveDialog(scene.window) ?: return
        Thread {
            try {
                val all = ctx.bookmarks.getAll()
                BookmarkImportExportService.writeBookmarks(all, file)
                showInfo("Export complete", "${all.size} bookmarks exported to ${file.name}.")
            } catch (e: Exception) { showError("Export failed", e.message ?: "Unknown error") }
        }.also { it.isDaemon = true }.start()
    }

    private fun importBookmarks() {
        val chooser = FileChooser().apply {
            title = "Import bookmarks"
            extensionFilters += FileChooser.ExtensionFilter("JSON files", "*.json")
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
                            ps.setString(3, b.memo); ps.setString(4, b.tags); ps.addBatch()
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
                } catch (e: Exception) { conn.rollback(); throw e }
                finally { conn.autoCommit = true }

                showInfo("Import complete", "${merged.size} bookmarks imported.")
            } catch (e: Exception) { showError("Import failed", e.message ?: "Unknown error") }
        }.also { it.isDaemon = true }.start()
    }

    private fun showInfo(title: String, msg: String) = Platform.runLater {
        Alert(Alert.AlertType.INFORMATION).apply {
            this.title = title; headerText = null; contentText = msg
        }.showAndWait()
    }

    private fun showError(title: String, msg: String) = Platform.runLater {
        Alert(Alert.AlertType.ERROR).apply {
            this.title = title; headerText = null; contentText = msg
        }.showAndWait()
    }

    companion object {
        private const val SECTION_LABEL_STYLE = "-fx-font-size: 14px; -fx-font-weight: bold;"
    }
}
