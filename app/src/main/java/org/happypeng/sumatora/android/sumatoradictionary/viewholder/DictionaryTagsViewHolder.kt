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
package org.happypeng.sumatora.android.sumatoradictionary.viewholder

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.happypeng.sumatora.android.sumatoradictionary.adapter.`object`.DictionaryTagNameAdapterObject
import org.happypeng.sumatora.android.sumatoradictionary.databinding.DictionaryTagsViewBinding
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryTagName

interface DictionaryTagsViewHolderActions {
    fun selectForDeletion(selected: Boolean, tagName: DictionaryTagName)
    fun toggleSelected(tagName: DictionaryTagName)
}

class DictionaryTagsViewHolder(private val dictionaryTagsViewBinding: DictionaryTagsViewBinding,
                               private val actions: DictionaryTagsViewHolderActions): RecyclerView.ViewHolder(dictionaryTagsViewBinding.dictionaryTagsView) {
    fun bindTo(dictionaryTagName: DictionaryTagNameAdapterObject) {
        dictionaryTagsViewBinding.dictionaryTagsViewDeleteButton.setOnCheckedChangeListener(null)

        dictionaryTagsViewBinding.dictionaryTagsViewText.text = dictionaryTagName.tagName.tagName
        dictionaryTagsViewBinding.dictionaryTagsViewDeleteButton.visibility = if (dictionaryTagName.deleteSelectionEnabled) { View.VISIBLE } else { View.GONE }
        dictionaryTagsViewBinding.dictionaryTagsViewDeleteButton.isChecked = dictionaryTagName.selectedForDelete
        dictionaryTagsViewBinding.dictionaryTagsViewSelected.visibility = if (dictionaryTagName.selected) { View.VISIBLE } else { View.GONE }

        dictionaryTagsViewBinding.dictionaryTagsViewDeleteButton.setOnCheckedChangeListener { _, isChecked ->
            actions.selectForDeletion(isChecked, dictionaryTagName.tagName)
        }

        dictionaryTagsViewBinding.dictionaryTagsView.setOnClickListener {
            actions.toggleSelected(dictionaryTagName.tagName)
        }
    }
}