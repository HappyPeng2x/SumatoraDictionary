/* Sumatora Dictionary
        Copyright (C) 2020 Nicolas Centa

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
import androidx.recyclerview.widget.ListAdapter
import org.happypeng.sumatora.android.sumatoradictionary.databinding.DictionaryTagsViewBinding
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryTagName
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryTagNameDiffUtil
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionaryTagsViewHolder

class DictionaryTagsAdapter: ListAdapter<DictionaryTagName, DictionaryTagsViewHolder>(DictionaryTagNameDiffUtil.DIFF_UTIL) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DictionaryTagsViewHolder {
        return DictionaryTagsViewHolder(DictionaryTagsViewBinding.inflate(LayoutInflater.from(parent.context)))
    }

    override fun onBindViewHolder(holder: DictionaryTagsViewHolder, position: Int) {
        val entry = getItem(position)

        if (entry != null) {
            holder.bindTo(entry)
        }
    }

    init {
        setHasStableIds(true)
    }
}