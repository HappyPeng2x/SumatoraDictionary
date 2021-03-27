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
package org.happypeng.sumatora.android.sumatoradictionary.adapter.`object`

import androidx.recyclerview.widget.DiffUtil
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryTagName

data class DictionaryTagNameAdapterObject(val tagName: DictionaryTagName,
                                     val tags: List<Long>,
                                     val deleteSelectionEnabled: Boolean,
                                     val selectedForDelete: Boolean,
                                     val selected: Boolean)

class DictionaryTagNameAdapterObjectDiffUtil : DiffUtil.ItemCallback<DictionaryTagNameAdapterObject>() {
    override fun areItemsTheSame(oldItem: DictionaryTagNameAdapterObject, newItem: DictionaryTagNameAdapterObject): Boolean {
        return oldItem.tagName.tagId == newItem.tagName.tagId
    }

    override fun areContentsTheSame(oldItem: DictionaryTagNameAdapterObject, newItem: DictionaryTagNameAdapterObject): Boolean {
        return (oldItem.tagName.tagId == newItem.tagName.tagId) &&
                (oldItem.tagName.tagName == newItem.tagName.tagName) &&
                (oldItem.deleteSelectionEnabled == newItem.deleteSelectionEnabled) &&
                (oldItem.selectedForDelete == newItem.selectedForDelete) &&
                (oldItem.selected == newItem.selected) &&
                (oldItem.tags == newItem.tags)
    }

    companion object {
        val DIFF_UTIL = DictionaryTagNameAdapterObjectDiffUtil()
    }
}