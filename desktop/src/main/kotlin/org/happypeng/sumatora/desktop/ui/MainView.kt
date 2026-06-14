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

import javafx.scene.Node
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.BorderPane
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox

class MainView(ctx: AppContext) : BorderPane() {

    private val searchPane    = SearchPane(ctx)
    private val bookmarksPane = BookmarksPane(ctx)
    private val tagsPane      = TagsPane(ctx)
    private val settingsPane  = SettingsPane(ctx)
    private val contentArea   = StackPane(searchPane, bookmarksPane, tagsPane, settingsPane)

    init {
        val group = ToggleGroup()
        // Prevent deselecting the active tab by clicking it again
        group.selectedToggleProperty().addListener { _, old, new ->
            if (new == null) old?.isSelected = true
        }

        val btnSearch    = navButton("Search",    group)
        val btnBookmarks = navButton("Bookmarks", group)
        val btnTags      = navButton("Tags",      group)
        val btnSettings  = navButton("Settings",  group)

        btnSearch.isSelected = true
        showOnly(searchPane)

        btnSearch.setOnAction    { showOnly(searchPane);    searchPane.refresh() }
        btnBookmarks.setOnAction { showOnly(bookmarksPane); bookmarksPane.refresh() }
        btnTags.setOnAction      { showOnly(tagsPane);      tagsPane.refresh() }
        btnSettings.setOnAction  { showOnly(settingsPane) }

        left = VBox(btnSearch, btnBookmarks, btnTags, btnSettings).apply {
            prefWidth = 160.0
            style = "-fx-background-color: #2b2b2b;"
        }
        center = contentArea
    }

    private fun navButton(label: String, group: ToggleGroup) = ToggleButton(label).apply {
        toggleGroup = group
        maxWidth = Double.MAX_VALUE
        style = NAV_NORMAL
        selectedProperty().addListener { _, _, sel -> style = if (sel) NAV_SELECTED else NAV_NORMAL }
    }

    private fun showOnly(target: Node) =
        contentArea.children.forEach { it.isVisible = it == target }

    companion object {
        private const val NAV_NORMAL = """
            -fx-background-color: transparent;
            -fx-text-fill: #aaaaaa;
            -fx-font-size: 14px;
            -fx-padding: 12 16 12 16;
            -fx-alignment: CENTER_LEFT;
            -fx-cursor: hand;
        """
        private const val NAV_SELECTED = """
            -fx-background-color: #3c5a8a;
            -fx-text-fill: white;
            -fx-font-size: 14px;
            -fx-padding: 12 16 12 16;
            -fx-alignment: CENTER_LEFT;
            -fx-cursor: hand;
        """
    }
}
