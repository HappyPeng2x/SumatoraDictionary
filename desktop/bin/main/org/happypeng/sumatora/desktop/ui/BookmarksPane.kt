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

import javafx.animation.PauseTransition
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.SplitPane
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.util.Duration
import org.happypeng.sumatora.desktop.model.SearchResult
import java.util.concurrent.atomic.AtomicInteger

class BookmarksPane(private val ctx: AppContext) : VBox() {

    private val resultList = ListView<SearchResult>()
    private val detailPane = WordDetailPane(ctx) { _ -> refresh() }
    private val searchField = TextField().apply {
        promptText = "Filter bookmarks"
        style = "-fx-font-size: 14px;"
    }
    private val delay = PauseTransition(Duration.millis(300.0))
    private val genCounter = AtomicInteger(0)

    init {
        resultList.setCellFactory { BookmarkCell() }
        resultList.selectionModel.selectedItemProperty().addListener { _, _, r ->
            if (r != null) detailPane.show(r) else detailPane.clear()
        }

        delay.setOnFinished { runSearch() }
        searchField.textProperty().addListener { _, _, _ -> delay.playFromStart() }

        val toolbar = HBox(8.0, Label("Bookmarks").apply { style = "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: white;" }, searchField).apply {
            padding = Insets(8.0, 12.0, 8.0, 12.0)
            alignment = Pos.CENTER_LEFT
            style = "-fx-background-color: #3c3f41;"
            HBox.setHgrow(searchField, Priority.ALWAYS)
        }

        val split = SplitPane(resultList, detailPane).apply { setDividerPositions(0.38) }
        VBox.setVgrow(split, Priority.ALWAYS)
        children.addAll(toolbar, split)
    }

    fun refresh() {
        val gen = genCounter.incrementAndGet()
        val (plain, tagList) = parseQuery(searchField.text ?: "")
        Thread {
            val results = try { ctx.search.search(plain, tagList, showBookmarksOnly = true) } catch (e: Exception) { emptyList() }
            if (genCounter.get() == gen) Platform.runLater { resultList.items.setAll(results) }
        }.also { it.isDaemon = true }.start()
    }

    private fun runSearch() = refresh()
}

private class BookmarkCell : ListCell<SearchResult>() {
    override fun updateItem(item: SearchResult?, empty: Boolean) {
        super.updateItem(item, empty)
        if (empty || item == null) { graphic = null; text = null; return }
        val hasMemo = !item.memo.isNullOrEmpty()
        val tagCount = item.tags?.split(",")?.count { it.isNotEmpty() } ?: 0
        val suffix = buildString {
            if (hasMemo) append("  ✏")
            if (tagCount > 0) append("  🏷$tagCount")
        }
        graphic = VBox(2.0,
            Label(wordHeader(item) + suffix).apply { style = "-fx-font-size: 14px; -fx-font-weight: bold;" },
            Label(glossPreview(item.gloss)).apply { style = "-fx-font-size: 12px; -fx-text-fill: #888;" }
        ).apply { padding = Insets(4.0, 8.0, 4.0, 8.0) }
        text = null
    }
}
