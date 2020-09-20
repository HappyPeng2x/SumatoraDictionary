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

import android.graphics.Color
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.rxjava3.disposables.Disposable
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
                                        disableMemoEdit: Boolean,
                                        private val commitConsumer: (Long, Long, String?) -> Unit,
                                        private val intentSubject: Subject<DictionaryPagedListAdapterIntent>) : RecyclerView.ViewHolder(wordCardBinding.wordCardView) {
    private var subscription: Disposable? = null

    private fun openMemo() {
        wordCardBinding.wordCardMemo.visibility = View.VISIBLE
        wordCardBinding.wordCardMemoIcon.visibility = View.GONE
        wordCardBinding.wordCardDeleteMemoIcon.visibility = View.VISIBLE
    }

    private fun closeMemo() {
        wordCardBinding.wordCardMemo.visibility = View.GONE
        wordCardBinding.wordCardMemoIcon.visibility = View.VISIBLE
        wordCardBinding.wordCardDeleteMemoIcon.visibility = View.GONE
        wordCardBinding.wordCardMemo.setText("")
    }

    fun recycle() {
        subscription?.dispose()
        subscription = null
    }

    fun bindTo(entry: DictionarySearchElement) {
        subscription?.dispose()

        subscription = intentSubject.takeUntil { when (it) {
            DictionaryPagedListAdapterCloseIntent -> true
            else -> false
        } }.doFinally {
            val memo = wordCardBinding.wordCardMemo.editableText.toString()

            if (memo != entry.memo && !(entry.memo == null && memo == "")) {
                commitConsumer.invoke(entry.seq,
                        entry.bookmark,
                        wordCardBinding.wordCardMemo.editableText.toString())
            }
        }.subscribe()

        if (entry.getLang() != entry.langSetting) {
            wordCardBinding.wordCardView.setBackgroundColor(Color.LTGRAY)
        } else {
            wordCardBinding.wordCardView.setBackgroundColor(Color.WHITE)
        }

        wordCardBinding.wordCardText.text = renderEntry(entry, entities)
        if (entry.getBookmark() != 0L) {
            wordCardBinding.wordCardBookmarkIcon.setImageResource(R.drawable.ic_outline_bookmark_24px)
        } else {
            wordCardBinding.wordCardBookmarkIcon.setImageResource(R.drawable.ic_outline_bookmark_border_24px)
        }

        wordCardBinding.wordCardBookmarkIcon.setOnClickListener { _ ->
            commitConsumer.invoke(entry.seq,
                    if (entry.getBookmark() > 0) { 0 } else { 1 },
                    wordCardBinding.wordCardMemo.editableText.toString())
        }

        val memo = entry.getMemo()
        if (memo != null && "" != memo) {
            openMemo()
            wordCardBinding.wordCardMemo.setText(memo)
        } else {
            closeMemo()
        }
        wordCardBinding.wordCardDeleteMemoIcon.setOnClickListener { _ ->
            wordCardBinding.wordCardMemo.setText("")
            closeMemo()
        }
        wordCardBinding.wordCardMemoIcon.setOnClickListener { _ -> openMemo() }
        wordCardBinding.wordCardText.requestFocus()
    }

    init {
        if (disableBookmarkButton) {
            wordCardBinding.wordCardBookmarkIcon.visibility = View.GONE
        }

        if (disableMemoEdit) {
            wordCardBinding.wordCardMemoIcon.visibility = View.GONE
        }
    }
}