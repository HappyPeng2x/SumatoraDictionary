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
import javafx.scene.control.CheckBox
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.SplitPane
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.control.Label
import javafx.util.Duration
import org.happypeng.sumatora.core.bookmark.BookmarkMergeService
import org.happypeng.sumatora.core.search.TagQueryParser
import org.happypeng.sumatora.desktop.model.SearchResult
import java.util.concurrent.atomic.AtomicInteger

class SearchPane(private val ctx: AppContext) : VBox() {

    private val resultList = ListView<SearchResult>()
    private val detailPane = WordDetailPane(ctx) { updated ->
        val idx = resultList.items.indexOfFirst { it.seq == updated.seq }
        if (idx >= 0) resultList.items[idx] = updated
    }
    private val searchField = TextField().apply {
        promptText = "Search (e.g. 水, mizu, water, #tag)"
        style = "-fx-font-size: 14px;"
    }
    private val bookmarksCheck = CheckBox("Bookmarks only")
    private val delay = PauseTransition(Duration.millis(300.0))
    private val genCounter = AtomicInteger(0)

    init {
        resultList.setCellFactory { ResultCell() }
        resultList.selectionModel.selectedItemProperty().addListener { _, _, r ->
            if (r != null) detailPane.show(r) else detailPane.clear()
        }

        delay.setOnFinished { runSearch() }
        searchField.textProperty().addListener { _, _, _ -> delay.playFromStart() }
        bookmarksCheck.selectedProperty().addListener { _, _, _ -> runSearch() }

        val toolbar = HBox(8.0, searchField, bookmarksCheck).apply {
            padding = Insets(8.0, 12.0, 8.0, 12.0)
            alignment = Pos.CENTER_LEFT
            style = "-fx-background-color: #3c3f41;"
            HBox.setHgrow(searchField, Priority.ALWAYS)
        }

        val split = SplitPane(resultList, detailPane).apply { setDividerPositions(0.38) }
        VBox.setVgrow(split, Priority.ALWAYS)
        children.addAll(toolbar, split)
    }

    fun refresh() = runSearch()

    private fun runSearch() {
        val raw = searchField.text ?: ""
        val bookmarksOnly = bookmarksCheck.isSelected
        val (plain, tagList) = TagQueryParser.parse(raw)
        val gen = genCounter.incrementAndGet()

        Thread {
            val results = try { ctx.search.search(plain, tagList, bookmarksOnly) } catch (e: Exception) { emptyList() }
            if (genCounter.get() == gen) Platform.runLater {
                val prevSeq = resultList.selectionModel.selectedItem?.seq
                resultList.items.setAll(results)
                prevSeq?.let { seq ->
                    results.indexOfFirst { it.seq == seq }.takeIf { it >= 0 }
                        ?.let { resultList.selectionModel.select(it) }
                }
            }
        }.also { it.isDaemon = true }.start()
    }
}

private class ResultCell : ListCell<SearchResult>() {
    override fun updateItem(item: SearchResult?, empty: Boolean) {
        super.updateItem(item, empty)
        if (empty || item == null) { graphic = null; text = null; return }
        val rows = mutableListOf<javafx.scene.Node>(
            Label(wordHeader(item)).apply { style = "-fx-font-size: 14px; -fx-font-weight: bold;" },
            Label(glossPreview(item.gloss)).apply { style = "-fx-font-size: 12px; -fx-text-fill: #888;" }
        )
        if (!item.memo.isNullOrEmpty()) {
            rows += Label(item.memo).apply { style = "-fx-font-size: 11px; -fx-text-fill: #aaa; -fx-font-style: italic;" }
        }
        val tags = BookmarkMergeService.splitTags(item.tags)
        if (tags.isNotEmpty()) {
            rows += HBox(4.0, *tags.map { tag ->
                Label(tag).apply { style = "-fx-background-color: #4a7c9e; -fx-background-radius: 10; -fx-padding: 1 6 1 6; -fx-text-fill: white; -fx-font-size: 11px;" }
            }.toTypedArray()).apply { padding = Insets(2.0, 0.0, 0.0, 0.0) }
        }
        graphic = VBox(2.0, *rows.toTypedArray()).apply { padding = Insets(4.0, 8.0, 4.0, 8.0) }
        text = null
    }
}
