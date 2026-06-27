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
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.layout.FlowPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.happypeng.sumatora.core.bookmark.Bookmark
import org.happypeng.sumatora.core.bookmark.BookmarkMergeService
import org.happypeng.sumatora.core.bookmark.BookmarkTag
import org.happypeng.sumatora.desktop.model.SearchResult

class WordDetailPane(
    private val ctx: AppContext,
    private val onChanged: (SearchResult) -> Unit = {}
) : ScrollPane() {

    private val body = VBox(10.0).apply { padding = Insets(20.0); minWidth = 300.0 }
    private var current: SearchResult? = null
    private var memoArea: TextArea? = null

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
        memoArea = null
        body.children.clear()
    }

    private fun render(r: SearchResult) {
        body.children.clear()
        memoArea = null

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

        // ── Memo editor ───────────────────────────────────────────────────────
        body.children += Label("Memo").apply {
            style = "-fx-font-size: 12px; -fx-text-fill: #666; -fx-font-weight: bold;"
        }
        val area = TextArea(r.memo ?: "").apply {
            promptText = "Add a personal note…"
            prefRowCount = 3
            isWrapText = true
            style = "-fx-font-size: 13px;"
        }
        // Auto-save memo when the field loses focus, but only if this area is still active.
        area.focusedProperty().addListener { _, _, focused ->
            if (!focused && area === memoArea) persistMemo(r, area.text.trim())
        }
        memoArea = area
        body.children += area

        // ── Tags editor ───────────────────────────────────────────────────────
        body.children += Label("Tags").apply {
            style = "-fx-font-size: 12px; -fx-text-fill: #666; -fx-font-weight: bold;"
        }
        val currentTags = BookmarkMergeService.splitTags(r.tags).toMutableList()
        body.children += buildTagFlow(r, currentTags)

        val tagInput = TextField().apply {
            promptText = "New tag…"
            style = "-fx-font-size: 13px;"
        }
        val addTagBtn = Button("Add").apply {
            style = "-fx-font-size: 13px; -fx-cursor: hand;"
        }
        val doAddTag = {
            val tag = tagInput.text.trim()
            if (tag.isNotEmpty() && !currentTags.contains(tag)) {
                currentTags.add(tag)
                persistTags(r, currentTags)
            } else {
                tagInput.clear()
            }
            Unit
        }
        tagInput.setOnAction { doAddTag() }
        addTagBtn.setOnAction { doAddTag() }
        body.children += HBox(6.0, tagInput, addTagBtn).apply {
            HBox.setHgrow(tagInput, Priority.ALWAYS)
        }

        body.children += Separator()

        // ── Part of speech ────────────────────────────────────────────────────
        val posText = expandPos(r.pos)
        if (posText.isNotEmpty()) {
            body.children += Label(posText).apply {
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

    private fun buildTagFlow(r: SearchResult, currentTags: MutableList<String>): FlowPane =
        FlowPane(4.0, 4.0).apply {
            currentTags.forEach { tag ->
                val removeBtn = Button("×").apply {
                    style = "-fx-background-color: transparent; -fx-font-size: 11px; -fx-padding: 0 2 0 4; -fx-cursor: hand;"
                    setOnAction {
                        currentTags.remove(tag)
                        persistTags(r, currentTags)
                    }
                }
                children += HBox(0.0, Label(tag).apply { style = "-fx-font-size: 12px;" }, removeBtn).apply {
                    style = """
                        -fx-background-color: #dce8f8;
                        -fx-padding: 2 4 2 8;
                        -fx-background-radius: 10;
                        -fx-alignment: center-left;
                    """.trimIndent()
                }
            }
        }

    private fun persistMemo(r: SearchResult, newMemo: String) {
        val existing = ctx.bookmarks.getBySeq(r.seq) ?: Bookmark(r.seq, r.bookmark, null, r.tags)
        ctx.bookmarks.insert(Bookmark(r.seq, existing.bookmark, newMemo.ifEmpty { null }, existing.tags))
        val updated = r.copy(memo = newMemo.ifEmpty { null })
        current = updated
        onChanged(updated)
        // Do not re-render: the TextArea stays focused and the user keeps editing.
    }

    private fun persistTags(r: SearchResult, tags: List<String>) {
        // Capture current memo content before re-rendering destroys the TextArea.
        val liveMemo = memoArea?.text?.trim() ?: r.memo ?: ""
        val tagsStr = tags.joinToString(",").ifEmpty { null }
        ctx.bookmarks.insert(Bookmark(r.seq, r.bookmark, liveMemo.ifEmpty { null }, tagsStr))
        ctx.tags.deleteTagsForSeq(r.seq)
        tags.forEach { ctx.tags.insertTag(BookmarkTag(r.seq, it)) }
        val updated = r.copy(memo = liveMemo.ifEmpty { null }, tags = tagsStr)
        current = updated
        render(updated)
        onChanged(updated)
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
