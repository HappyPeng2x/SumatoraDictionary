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
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Separator
import javafx.scene.layout.FlowPane
import javafx.scene.layout.VBox
import org.happypeng.sumatora.core.bookmark.Bookmark
import org.happypeng.sumatora.desktop.model.SearchResult

class WordDetailPane(
    private val ctx: AppContext,
    private val onChanged: (SearchResult) -> Unit = {}
) : ScrollPane() {

    private val body = VBox(10.0).apply { padding = Insets(20.0); minWidth = 300.0 }
    private var current: SearchResult? = null

    init {
        content = body
        isFitToWidth = true
        style = "-fx-background-color: white; -fx-border-color: #ddd; -fx-border-width: 0 0 0 1;"
    }

    fun show(result: SearchResult) {
        current = result
        render(result)
    }

    fun clear() {
        current = null
        body.children.clear()
    }

    private fun render(r: SearchResult) {
        body.children.clear()

        // ── Writing / reading header ───────────────────────────────────────────
        val writing = r.writingsPrio ?: r.writings
        val reading = r.readingsPrio ?: r.readings
        if (writing != null) {
            body.children += Label(writing).apply { style = "-fx-font-size: 30px;" }
            if (!reading.isNullOrEmpty())
                body.children += Label(reading).apply { style = "-fx-font-size: 16px; -fx-text-fill: #555;" }
        } else {
            body.children += Label(reading ?: "").apply { style = "-fx-font-size: 24px;" }
        }

        // ── Bookmark button ────────────────────────────────────────────────────
        val starred = r.bookmark > 0
        val bookmarkBtn = Button(if (starred) "★  Bookmarked" else "☆  Add bookmark").apply {
            style = if (starred)
                "-fx-background-color: #f5c518; -fx-font-size: 13px; -fx-cursor: hand;"
            else
                "-fx-font-size: 13px; -fx-cursor: hand;"
            setOnAction { toggleBookmark(r) }
        }
        body.children += bookmarkBtn

        // ── Memo ──────────────────────────────────────────────────────────────
        if (!r.memo.isNullOrEmpty()) {
            body.children += Label("Memo: ${r.memo}").apply {
                style = "-fx-font-style: italic; -fx-text-fill: #444; -fx-font-size: 13px;"
                isWrapText = true
            }
        }

        // ── Tags ──────────────────────────────────────────────────────────────
        val tagList = r.tags?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        if (tagList.isNotEmpty()) {
            val flow = FlowPane(4.0, 4.0).apply {
                children.addAll(tagList.map { tag ->
                    Label(tag).apply {
                        style = """
                            -fx-background-color: #dce8f8;
                            -fx-padding: 2 8 2 8;
                            -fx-background-radius: 10;
                            -fx-font-size: 12px;
                        """.trimIndent()
                    }
                })
            }
            body.children += flow
        }

        body.children += Separator()

        // ── Part of speech ────────────────────────────────────────────────────
        if (!r.pos.isNullOrEmpty()) {
            body.children += Label(r.pos).apply {
                style = "-fx-font-style: italic; -fx-text-fill: #666; -fx-font-size: 12px;"
                isWrapText = true
            }
        }

        // ── Glosses ───────────────────────────────────────────────────────────
        val glosses = parseGloss(r.gloss)
        if (glosses.isNotEmpty()) {
            val glossBox = VBox(4.0)
            glosses.forEachIndexed { i, g ->
                glossBox.children += Label("${i + 1}. $g").apply {
                    style = "-fx-font-size: 14px;"
                    isWrapText = true
                    maxWidth = Double.MAX_VALUE
                }
            }
            body.children += glossBox
        }
    }

    private fun toggleBookmark(r: SearchResult) {
        val newBookmark = if (r.bookmark > 0) 0L else 1L
        val existing = ctx.bookmarks.getBySeq(r.seq)
        if (newBookmark == 0L && existing?.memo.isNullOrEmpty() && existing?.tags.isNullOrEmpty()) {
            ctx.bookmarks.delete(r.seq)
        } else {
            ctx.bookmarks.insert(Bookmark(r.seq, newBookmark, existing?.memo ?: r.memo, existing?.tags ?: r.tags))
        }
        val updated = r.copy(bookmark = newBookmark)
        current = updated
        render(updated)
        onChanged(updated)
    }
}
