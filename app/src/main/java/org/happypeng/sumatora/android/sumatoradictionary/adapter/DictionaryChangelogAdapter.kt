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
package org.happypeng.sumatora.android.sumatoradictionary.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.databinding.ItemDictionaryChangelogBinding
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryChangelog
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.ChangelogParser

// Row count grows by one every release (weekly, see release-dictionaries.yml) with no upper bound,
// unlike DictionariesManagementActivity's small fixed pack list - a real RecyclerView instead of
// that screen's programmatic LinearLayout-of-rows pattern.
class DictionaryChangelogAdapter :
    RecyclerView.Adapter<DictionaryChangelogAdapter.ChangelogViewHolder>() {

    private var rows: List<DictionaryChangelog> = emptyList()

    fun submitList(newRows: List<DictionaryChangelog>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChangelogViewHolder {
        val binding = ItemDictionaryChangelogBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChangelogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChangelogViewHolder, position: Int) {
        holder.bind(rows[position])
    }

    override fun getItemCount() = rows.size

    class ChangelogViewHolder(private val binding: ItemDictionaryChangelogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: DictionaryChangelog) {
            val context = binding.root.context

            binding.itemDictionaryChangelogHeader.text = context.getString(
                R.string.dictionary_version_caption, row.version, formatDate(row.date)
            )

            val deltas = ChangelogParser.parse(row.json)
            binding.itemDictionaryChangelogDetails.text = if (deltas.isEmpty()) {
                context.getString(R.string.recent_updates_empty)
            } else {
                deltas.joinToString("\n") { delta -> "${delta.label}: ${formatDelta(context, delta)}" }
            }
        }

        private fun formatDelta(context: android.content.Context, delta: ChangelogParser.Delta): String {
            val parts = mutableListOf<String>()
            if (delta.added > 0) parts += context.getString(R.string.changelog_delta_added, delta.added)
            if (delta.modified > 0) parts += context.getString(R.string.changelog_delta_modified, delta.modified)
            if (delta.removed > 0) parts += context.getString(R.string.changelog_delta_removed, delta.removed)
            return parts.joinToString(", ")
        }

        private fun formatDate(yyyymmdd: Int): String {
            val s = yyyymmdd.toString()
            return if (s.length == 8) "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)}" else s
        }
    }
}
