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
package org.happypeng.sumatora.android.sumatoradictionary.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Adapter
import android.widget.ArrayAdapter
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.rxjava3.subjects.PublishSubject
import org.happypeng.sumatora.android.sumatoradictionary.databinding.WordCardBinding
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElementDiffUtil
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.JMDICT_ENTITIES
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.DictionaryPagedListAdapterCloseIntent
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.DictionaryPagedListAdapterIntent
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder
import java.util.*

class DictionaryPagedListAdapter(aDisableBookmarkButton: Boolean,
                                 aDisableMemoEdit: Boolean,
                                 commitConsumer: (Long, Long, String?) -> Unit,
                                 private val completionAdapter: ArrayAdapter<String>,
                                 private val holderColors: DictionarySearchElementViewHolder.Colors) :
        PagedListAdapter<DictionarySearchElement?, DictionarySearchElementViewHolder>(DictionarySearchElementDiffUtil.getDiffUtil()) {
    private val entities = JMDICT_ENTITIES
    private val disableBookmarkButton: Boolean
    private val disableMemoEdit: Boolean
    private val commitConsumer: (Long, Long, String?) -> Unit
    private val intentSubject: PublishSubject<DictionaryPagedListAdapterIntent> = PublishSubject.create()

    fun close() {
        intentSubject.onNext(DictionaryPagedListAdapterCloseIntent)
    }

    // No placeholders = no null values
    override fun getItemId(position: Int): Long {
        return getItem(position)!!.getSeq()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DictionarySearchElementViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val wordCardBinding = WordCardBinding.inflate(layoutInflater)
        return DictionarySearchElementViewHolder(wordCardBinding,
                entities, disableBookmarkButton, disableMemoEdit,
                commitConsumer, intentSubject, completionAdapter, holderColors)
    }

    override fun onBindViewHolder(holder: DictionarySearchElementViewHolder, position: Int) {
        val entry = getItem(position)
        if (entry != null) {
            holder.bindTo(entry)
        }
    }

    init {
        setHasStableIds(true)
        disableBookmarkButton = aDisableBookmarkButton
        disableMemoEdit = aDisableMemoEdit
        this.commitConsumer = commitConsumer
    }

    override fun onViewRecycled(holder: DictionarySearchElementViewHolder) {
        super.onViewRecycled(holder)

        holder.recycle()
    }
}