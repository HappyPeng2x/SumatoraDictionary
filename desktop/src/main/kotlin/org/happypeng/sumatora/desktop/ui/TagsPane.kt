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
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.SplitPane
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.happypeng.sumatora.desktop.model.SearchResult

class TagsPane(private val ctx: AppContext) : BorderPane() {

    private val tagList = ListView<String>()
    private val resultList = ListView<SearchResult>()
    private val detailPane = WordDetailPane(ctx) { _ -> reloadResultsForTag() }
    private var selectedTag: String? = null

    init {
        tagList.setCellFactory { TagCell() }
        tagList.selectionModel.selectedItemProperty().addListener { _, _, tag ->
            selectedTag = tag
            reloadResultsForTag()
        }

        resultList.setCellFactory { TagResultCell() }
        resultList.selectionModel.selectedItemProperty().addListener { _, _, r ->
            if (r != null) detailPane.show(r) else detailPane.clear()
        }

        val header = HBox(
            Label("Tags").apply { style = "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: white;" }
        ).apply {
            padding = Insets(8.0, 12.0, 8.0, 12.0)
            style = "-fx-background-color: #3c3f41;"
        }

        // Left panel: tag list
        val leftPanel = VBox(tagList).apply {
            VBox.setVgrow(tagList, Priority.ALWAYS)
            prefWidth = 180.0
        }

        // Right panel: results + detail
        val rightSplit = SplitPane(resultList, detailPane).apply { setDividerPositions(0.40) }

        val mainSplit = SplitPane(leftPanel, rightSplit).apply { setDividerPositions(0.20) }
        HBox.setHgrow(mainSplit, Priority.ALWAYS)

        top = header
        center = mainSplit
    }

    fun refresh() {
        Thread {
            val allTags = try { ctx.tags.getAllTags() } catch (e: Exception) { emptyList() }
            Platform.runLater {
                val prevTag = tagList.selectionModel.selectedItem
                tagList.items.setAll(allTags)
                prevTag?.let { prev ->
                    allTags.indexOf(prev).takeIf { it >= 0 }?.let { tagList.selectionModel.select(it) }
                }
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun reloadResultsForTag() {
        val tag = selectedTag ?: run { resultList.items.clear(); detailPane.clear(); return }
        Thread {
            val results = try { ctx.search.search("", listOf(tag)) } catch (e: Exception) { emptyList() }
            Platform.runLater { resultList.items.setAll(results) }
        }.also { it.isDaemon = true }.start()
    }
}

private class TagCell : ListCell<String>() {
    override fun updateItem(item: String?, empty: Boolean) {
        super.updateItem(item, empty)
        text = if (empty || item == null) null else item
        graphic = null
    }
}

private class TagResultCell : ListCell<SearchResult>() {
    override fun updateItem(item: SearchResult?, empty: Boolean) {
        super.updateItem(item, empty)
        if (empty || item == null) { graphic = null; text = null; return }
        graphic = VBox(2.0,
            Label(wordHeader(item)).apply { style = "-fx-font-size: 14px; -fx-font-weight: bold;" },
            Label(glossPreview(item.gloss)).apply { style = "-fx-font-size: 12px; -fx-text-fill: #888;" }
        ).apply { padding = Insets(4.0, 8.0, 4.0, 8.0) }
        text = null
    }
}
