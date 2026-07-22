/* Sumatora Dictionary
        Copyright (C) 2019 Nicolas Centa

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

package org.happypeng.sumatora.android.sumatoradictionary.viewholder

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.Subject
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent
import org.happypeng.sumatora.android.sumatoradictionary.databinding.WordCardBinding
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.db.EntryListSummary
import org.happypeng.sumatora.android.sumatoradictionary.adapter.OnEntryClickListener
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.DictionaryPagedListAdapterCloseIntent
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.DictionaryPagedListAdapterIntent
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering.TagSystem
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering.buildSenseRows
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering.renderHeadword
class DictionarySearchElementViewHolder(private val wordCardBinding: WordCardBinding,
                                        private val disableBookmarkButton: Boolean,
                                        private val disableMemoEdit: Boolean,
                                        private val disableTagEdit: Boolean,
                                        private val commitConsumer: (Long, Long, String?) -> Unit,
                                        private val commitTagsConsumer: (Long, String) -> Unit,
                                        private val tagSuggestionsProvider: () -> List<String>,
                                        private val intentSubject: Subject<DictionaryPagedListAdapterIntent>,
                                        private val colors: Colors,
                                        private val onEntryClick: OnEntryClickListener = OnEntryClickListener {}) : RecyclerView.ViewHolder(wordCardBinding.wordCardView) {

    class Colors(val activeLang: Int,
                 val backupLang: Int,
                 val pos: Int,
                 val tags: TagColors,
                 val secondary: Int) {

        class TagColors(
            val pos: Int,
            val register: Int,
            val kana: Int,
            val kanji: Int,
            val usage: Int,
            val domain: Int,
            val dialect: Int
        )

        fun tagBgColor(key: String): Int = when (TagSystem.category(key)) {
            TagSystem.Category.POS      -> tags.pos
            TagSystem.Category.REGISTER -> tags.register
            TagSystem.Category.KANA     -> tags.kana
            TagSystem.Category.KANJI    -> tags.kanji
            TagSystem.Category.USAGE    -> tags.usage
            TagSystem.Category.DOMAIN   -> tags.domain
            TagSystem.Category.DIALECT  -> tags.dialect
        }
    }

    private var subscription: Disposable? = null
    private var tagLoadSubscription: Disposable? = null
    // editingSeq is the single source of truth for whether the tag editor is open and for which
    // entry. Unlike isTagEditing (a boolean), it cannot be inadvertently reset by bindTo() because
    // it is only written in openTagEditor(), closeTagEditor(), and the else-branch of bindTo()
    // (which handles a genuine entry change). wasEditingSameEntry is derived purely from this field.
    private var editingSeq: Long? = null
    // True only during the subscription?.dispose() call inside bindTo() when rebinding the same
    // entry. Lets doFinally distinguish a rebind (skip commit) from a recycle or entry-change
    // (commit in-progress tags).
    private var isRebindingSameEntry = false
    private val currentTags = mutableListOf<String>()
    private var currentEntry: DictionarySearchElement? = null
    // Plain-text rendering of the currently bound row, refreshed alongside the headword/senses in
    // bindSummary() - text selection inside a RecyclerView row fights row recycling (a rebind can
    // rewrite the headword/senses out from under an in-progress selection) and the row's own click
    // listener below, so rows offer a long-press-to-copy affordance instead of native selection.
    private var currentCopyText: String? = null

    private fun openMemo() {
        wordCardBinding.wordCardMemoRow.visibility = View.VISIBLE
    }

    private fun closeMemo() {
        wordCardBinding.wordCardMemoRow.visibility = View.GONE
        wordCardBinding.wordCardMemo.setText("")
    }

    private fun rebuildChips(closeable: Boolean) {
        wordCardBinding.wordCardTags.removeAllViews()
        if (currentTags.isEmpty()) {
            wordCardBinding.wordCardTags.visibility = if (closeable) View.VISIBLE else View.GONE
        } else {
            wordCardBinding.wordCardTags.visibility = View.VISIBLE
            for (tag in currentTags.toList()) {
                val chip = LayoutInflater.from(wordCardBinding.wordCardTags.context)
                    .inflate(R.layout.chip_item, wordCardBinding.wordCardTags, false) as Chip
                chip.text = tag
                chip.isCloseIconVisible = closeable
                if (closeable) {
                    chip.setOnCloseIconClickListener {
                        currentTags.remove(tag)
                        rebuildChips(closeable = true)
                        commitCurrentTags()
                    }
                }
                wordCardBinding.wordCardTags.addView(chip)
            }
        }
    }

    private fun commitCurrentTags() {
        currentEntry?.let { entry ->
            val tagsStr = currentTags.joinToString(",")
            commitTagsConsumer.invoke(entry.seq, tagsStr)
        }
    }

    private fun openTagEditor() {
        editingSeq = currentEntry?.seq
        wordCardBinding.wordCardTagInput.visibility = View.VISIBLE
        rebuildChips(closeable = true)
        tagLoadSubscription?.dispose()
        tagLoadSubscription = Single.fromCallable { tagSuggestionsProvider() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { suggestions ->
                wordCardBinding.wordCardTagInput.setAdapter(
                    ArrayAdapter(wordCardBinding.wordCardTagInput.context,
                        android.R.layout.simple_dropdown_item_1line, suggestions)
                )
            }
    }

    private fun closeTagEditor(entry: DictionarySearchElement) {
        addPendingTag()
        editingSeq = null
        wordCardBinding.wordCardTagInput.setText("")
        wordCardBinding.wordCardTagInput.dismissDropDown()
        wordCardBinding.wordCardTagInput.visibility = View.GONE
        tagLoadSubscription?.dispose()
        tagLoadSubscription = null
        rebuildChips(closeable = false)
        val tagsStr = currentTags.joinToString(",")
        if (tagsStr != (entry.tags ?: "")) {
            commitTagsConsumer.invoke(entry.seq, tagsStr)
        }
    }

    fun recycle() {
        subscription?.dispose()   // doFinally runs here; commits tags if editor was open
        editingSeq = null
        subscription = null
        tagLoadSubscription?.dispose()
        tagLoadSubscription = null
    }

    fun bindTo(entry: DictionarySearchElement) {
        val wasEditingSameEntry = editingSeq == entry.seq
        // Same seq means this is a rebind of the entry already showing here (e.g. a bookmark/memo/
        // tag change triggered a DB refresh), not a recycled holder receiving a different row.
        // The headword/senses only depend on static dictionary content keyed by seq, so they don't
        // need to be cleared and refetched in that case - avoids a visible blank-then-repaint blink.
        val isSameEntryRebind = currentEntry?.seq == entry.seq
        currentEntry = entry
        isRebindingSameEntry = wasEditingSameEntry
        subscription?.dispose()
        isRebindingSameEntry = false

        // doFinally captures entry from the previous bindTo call (closed-over val).
        // At dispose time, editingSeq and currentTags still reflect that previous entry.
        subscription = intentSubject.takeUntil {
            it == DictionaryPagedListAdapterCloseIntent
        }.doFinally {
            val memo = wordCardBinding.wordCardMemo.editableText.toString()
            if (memo != entry.memo && !(entry.memo == null && memo == "")) {
                commitConsumer.invoke(entry.seq, entry.bookmark,
                    wordCardBinding.wordCardMemo.editableText.toString())
            }
            // Commit in-progress tags only when truly leaving this entry (recycle or entry-change),
            // not on a same-entry DB-triggered rebind.
            if (editingSeq != null && !isRebindingSameEntry) {
                val tagsStr = currentTags.joinToString(",")
                if (tagsStr != (entry.tags ?: "")) {
                    commitTagsConsumer.invoke(entry.seq, tagsStr)
                }
            }
        }.subscribe()

        // editingSeq is NOT reset here; it survives same-entry rebinds.
        tagLoadSubscription?.dispose()
        tagLoadSubscription = null

        // Every tier's search query precomputes the row's render payload at insert time
        // (render_json - see DictionarySearchQueryTool.buildRenderJsonExpr) via the same
        // json_group_array()-packed correlated subqueries PersistentDatabaseComponent's detail
        // queries use, so this is pure JSON parsing with no DB access - safe to do synchronously
        // right here on the main thread. Skipped entirely on a same-entry rebind, since the
        // summary can't have changed and re-parsing would just blink the content that's already
        // correctly on screen.
        if (!isSameEntryRebind) {
            val precomputed = entry.render_json?.let { PersistentDatabaseComponent.parsePrecomputedSummary(it) }
            if (precomputed != null) {
                bindSummary(precomputed, entry)
            } else {
                wordCardBinding.wordCardHeadword.text = ""
                wordCardBinding.wordCardSenses.removeAllViews()
                currentCopyText = null
            }
        }
        wordCardBinding.wordCardContent.setOnClickListener { onEntryClick.onClick(entry) }
        wordCardBinding.wordCardContent.setOnLongClickListener { view -> copyCurrentEntryToClipboard(view) }
        wordCardBinding.wordCardBookmarkBadge.visibility =
            if (entry.bookmark != 0L) View.VISIBLE else View.GONE

        val memo = entry.memo
        if (memo != null && "" != memo) {
            openMemo()
            wordCardBinding.wordCardMemo.setText(memo)
        } else {
            closeMemo()
        }

        wordCardBinding.wordCardDeleteMemoIcon.setOnClickListener {
            wordCardBinding.wordCardMemo.setText("")
            closeMemo()
            if ("" != entry.memo && entry.memo != null) {
                commitConsumer.invoke(entry.seq, entry.bookmark, "")
            }
        }

        wordCardBinding.wordCardMemo.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && wordCardBinding.wordCardMemo.editableText.toString() == "") {
                closeMemo()
                if ("" != entry.memo && entry.memo != null) {
                    commitConsumer.invoke(entry.seq, entry.bookmark, "")
                }
            }
        }

        // If the same entry is being rebound (e.g. DB-triggered RecyclerView refresh) while
        // the editor is open, preserve the in-progress chip state and keep the editor open.
        // editingSeq is the ground truth — it survives intermediate bindTo() calls and is never
        // reset by them, only by closeTagEditor() or a genuine entry change.
        if (wasEditingSameEntry) {
            wordCardBinding.wordCardTagInput.visibility = View.VISIBLE
            rebuildChips(closeable = true)
        } else {
            editingSeq = null
            currentTags.clear()
            val entryTags = entry.tags
            if (!entryTags.isNullOrEmpty()) {
                currentTags.addAll(entryTags.split(",").filter { it.isNotBlank() })
            }
            wordCardBinding.wordCardTagInput.setText("")
            wordCardBinding.wordCardTagInput.visibility = View.GONE
            rebuildChips(closeable = false)
        }

        wordCardBinding.wordCardOverflowIcon.setOnClickListener { anchor ->
            val popup = PopupMenu(anchor.context, anchor)
            val menu = popup.menu

            if (!disableBookmarkButton) {
                val bookmarkLabel = if (entry.bookmark != 0L)
                    R.string.action_remove_bookmark else R.string.action_add_bookmark
                menu.add(bookmarkLabel).setOnMenuItemClickListener {
                    commitConsumer.invoke(entry.seq,
                        if (entry.bookmark > 0) 0 else 1,
                        wordCardBinding.wordCardMemo.editableText.toString())
                    true
                }
            }

            if (!disableMemoEdit) {
                menu.add(R.string.action_memo).setOnMenuItemClickListener {
                    openMemo()
                    wordCardBinding.wordCardMemo.requestFocus()
                    true
                }
            }

            if (!disableTagEdit) {
                val tagLabel = if (editingSeq != null)
                    R.string.action_done_editing_tags else R.string.action_edit_tags
                menu.add(tagLabel).setOnMenuItemClickListener {
                    if (editingSeq != null) {
                        closeTagEditor(entry)
                    } else {
                        openTagEditor()
                        wordCardBinding.wordCardTagInput.requestFocus()
                    }
                    true
                }
            }

            popup.show()
        }

        wordCardBinding.wordCardTagInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addPendingTag()
                true
            } else false
        }

        // Confirm a selected suggestion (tapping a dropdown item clears the field,
        // so we read the text before AutoCompleteTextView clears it via post)
        wordCardBinding.wordCardTagInput.setOnItemClickListener { _, _, _, _ ->
            wordCardBinding.wordCardTagInput.post { addPendingTag() }
        }
    }

    private fun bindSummary(summary: EntryListSummary, entry: DictionarySearchElement) {
        // Graying is per-sense now (see buildSenseRows), not per-entry - the whole card always
        // uses the active-language background; a recycled row must still have this set every
        // bind or it'd keep whatever the previously bound row left behind.
        wordCardBinding.wordCardView.setBackgroundColor(colors.activeLang)

        wordCardBinding.wordCardHeadword.text = renderHeadword(summary, colors)
        val density = wordCardBinding.wordCardSenses.context.resources.displayMetrics.density
        wordCardBinding.wordCardSenses.removeAllViews()
        buildSenseRows(wordCardBinding.wordCardSenses, summary, colors, density, entry.deinflectionLabel)
        currentCopyText = buildCopyText(summary)
    }

    // Plain-text equivalent of what bindSummary just rendered as spans/table rows - headword,
    // matched reading, and every visible gloss - for the long-press-to-copy affordance below.
    private fun buildCopyText(summary: EntryListSummary): String {
        val parts = mutableListOf<String>()
        summary.primaryText?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        summary.primaryReading?.let { parts.add(it) }
        val glosses = if (summary.isName) {
            summary.translations
        } else {
            summary.senseGroups.flatMap { it.senses }.mapNotNull { it.glossText }
        }
        if (glosses.isNotEmpty()) parts.add(glosses.joinToString("; "))
        return parts.joinToString(" - ")
    }

    private fun copyCurrentEntryToClipboard(view: View): Boolean {
        val text = currentCopyText?.takeIf { it.isNotEmpty() } ?: return false
        val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText(text, text))
        // Android 13+ (API 33) already shows its own system confirmation when the clipboard
        // changes - a second Toast here would just be a redundant, stale-looking duplicate.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(view.context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun addPendingTag() {
        val tagText = wordCardBinding.wordCardTagInput.text.toString().trim()
        if (tagText.isNotEmpty() && !currentTags.contains(tagText)) {
            currentTags.add(tagText)
            rebuildChips(closeable = true)
            commitCurrentTags()
        }
        wordCardBinding.wordCardTagInput.setText("")
    }

    init {
        if (disableMemoEdit) {
            wordCardBinding.wordCardDeleteMemoIcon.visibility = View.GONE
            wordCardBinding.wordCardMemo.inputType = InputType.TYPE_NULL
        }

        if (disableBookmarkButton && disableMemoEdit && disableTagEdit) {
            wordCardBinding.wordCardOverflowIcon.visibility = View.GONE
        }
    }
}
