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

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.Subject
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.databinding.WordCardBinding
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.DictionaryPagedListAdapterCloseIntent
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.DictionaryPagedListAdapterIntent
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering.renderEntry
import java.util.*

class DictionarySearchElementViewHolder(private val wordCardBinding: WordCardBinding,
                                        private val entities: HashMap<String, String>,
                                        disableBookmarkButton: Boolean,
                                        private val disableMemoEdit: Boolean,
                                        private val disableTagEdit: Boolean,
                                        private val commitConsumer: (Long, Long, String?) -> Unit,
                                        private val commitTagsConsumer: (Long, String) -> Unit,
                                        private val tagSuggestionsProvider: () -> List<String>,
                                        private val intentSubject: Subject<DictionaryPagedListAdapterIntent>,
                                        private val colors: Colors) : RecyclerView.ViewHolder(wordCardBinding.wordCardView) {

    class Colors(val activeLang: Int,
                 val backupLang: Int,
                 val highlight: Int,
                 val pos: Int)

    private var subscription: Disposable? = null
    private var tagLoadSubscription: Disposable? = null
    private var isTagEditing = false
    private val currentTags = mutableListOf<String>()

    private fun openMemo() {
        wordCardBinding.wordCardMemo.visibility = View.VISIBLE
        wordCardBinding.wordCardMemoIcon.visibility = View.GONE

        if (!disableMemoEdit) {
            wordCardBinding.wordCardDeleteMemoIcon.visibility = View.VISIBLE
        }
    }

    private fun closeMemo() {
        wordCardBinding.wordCardMemo.visibility = View.GONE

        if (!disableMemoEdit) {
            wordCardBinding.wordCardMemoIcon.visibility = View.VISIBLE
        }

        wordCardBinding.wordCardDeleteMemoIcon.visibility = View.GONE
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
                    }
                }
                wordCardBinding.wordCardTags.addView(chip)
            }
        }
    }

    private fun openTagEditor() {
        isTagEditing = true
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
        isTagEditing = false
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
        subscription?.dispose()
        subscription = null
        tagLoadSubscription?.dispose()
        tagLoadSubscription = null
    }

    fun bindTo(entry: DictionarySearchElement) {
        subscription?.dispose()

        // doFinally captures entry from the previous bindTo call (closed-over val).
        // At dispose time, isTagEditing and currentTags still reflect that previous entry.
        subscription = intentSubject.takeUntil { when (it) {
            DictionaryPagedListAdapterCloseIntent -> true
            else -> false
        } }.doFinally {
            val memo = wordCardBinding.wordCardMemo.editableText.toString()
            if (memo != entry.memo && !(entry.memo == null && memo == "")) {
                commitConsumer.invoke(entry.seq, entry.bookmark,
                    wordCardBinding.wordCardMemo.editableText.toString())
            }
            if (isTagEditing) {
                val tagsStr = currentTags.joinToString(",")
                if (tagsStr != (entry.tags ?: "")) {
                    commitTagsConsumer.invoke(entry.seq, tagsStr)
                }
            }
        }.subscribe()

        // Reset tag state before setting up new entry
        tagLoadSubscription?.dispose()
        tagLoadSubscription = null
        isTagEditing = false

        if (entry.getLang() != entry.langSetting) {
            wordCardBinding.wordCardView.setBackgroundColor(colors.backupLang)
        } else {
            wordCardBinding.wordCardView.setBackgroundColor(colors.activeLang)
        }

        wordCardBinding.wordCardText.text = renderEntry(entry, entities, colors)
        if (entry.getBookmark() != 0L) {
            wordCardBinding.wordCardBookmarkIcon.setImageResource(R.drawable.ic_outline_bookmark_24px)
        } else {
            wordCardBinding.wordCardBookmarkIcon.setImageResource(R.drawable.ic_outline_bookmark_border_24px)
        }

        wordCardBinding.wordCardBookmarkIcon.setOnClickListener {
            commitConsumer.invoke(entry.seq,
                if (entry.getBookmark() > 0) 0 else 1,
                wordCardBinding.wordCardMemo.editableText.toString())
        }

        val memo = entry.getMemo()
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

        wordCardBinding.wordCardMemoIcon.setOnClickListener {
            openMemo()
            wordCardBinding.wordCardMemo.requestFocus()
        }

        wordCardBinding.wordCardMemo.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && wordCardBinding.wordCardMemo.editableText.toString() == "") {
                closeMemo()
                if ("" != entry.memo && entry.memo != null) {
                    commitConsumer.invoke(entry.seq, entry.bookmark, "")
                }
            }
        }

        // Tags: initialise chip list and display
        currentTags.clear()
        val entryTags = entry.getTags()
        if (!entryTags.isNullOrEmpty()) {
            currentTags.addAll(entryTags.split(",").filter { it.isNotBlank() })
        }
        wordCardBinding.wordCardTagInput.setText("")
        wordCardBinding.wordCardTagInput.visibility = View.GONE
        rebuildChips(closeable = false)

        wordCardBinding.wordCardTagIcon.setOnClickListener {
            if (isTagEditing) {
                closeTagEditor(entry)
            } else {
                openTagEditor()
                wordCardBinding.wordCardTagInput.requestFocus()
            }
        }

        // Confirm a typed tag on IME Done
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

    private fun addPendingTag() {
        val tagText = wordCardBinding.wordCardTagInput.text.toString().trim()
        if (tagText.isNotEmpty() && !currentTags.contains(tagText)) {
            currentTags.add(tagText)
            rebuildChips(closeable = true)
        }
        wordCardBinding.wordCardTagInput.setText("")
    }

    init {
        if (disableBookmarkButton) {
            wordCardBinding.wordCardBookmarkIcon.visibility = View.GONE
        }

        if (disableMemoEdit) {
            wordCardBinding.wordCardMemoIcon.visibility = View.GONE
            wordCardBinding.wordCardMemo.inputType = InputType.TYPE_NULL
        }

        if (disableTagEdit) {
            wordCardBinding.wordCardTagIcon.visibility = View.GONE
        }
    }
}
