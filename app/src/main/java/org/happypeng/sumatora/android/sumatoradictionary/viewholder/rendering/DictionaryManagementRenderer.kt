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

package org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary
import org.happypeng.sumatora.android.sumatoradictionary.db.RemoteDictionaryObject

// Builds the "Installed dictionaries" and "Downloading" rows in DictionariesManagementActivity.
// Built programmatically (same pattern as SearchElementRenderer.buildSenseRows and
// EntryDetailBottomSheet.buildForms) rather than a RecyclerView, since the pack count is small
// and static for the lifetime of one screen visit.
object DictionaryManagementRenderer {

    // Every pack in a release shares the manifest's single <repository version="…" date="…">
    // (see BaseDictionaryObject.fromXML) - there's no such thing as one pack being newer than
    // another, so update status is tracked once for the whole install, not per row.
    private val TYPE_ORDER = listOf("core", "kanji", "pitch", "gloss", "tatoeba", "suffix", "names")

    private val TYPE_LABELS = mapOf(
        "core" to "Core",
        "kanji" to "Kanji data",
        "pitch" to "Pitch accent",
        "gloss" to "Translations",
        "tatoeba" to "Example sentences",
        "suffix" to "Substring search",
        "names" to "Proper names"
    )

    private fun typeLabel(type: String): String =
        TYPE_LABELS[type] ?: type.replaceFirstChar { it.uppercase() }

    private fun typeSortKey(type: String): Int =
        TYPE_ORDER.indexOf(type).let { if (it < 0) TYPE_ORDER.size else it }

    private fun Int.dp(density: Float) = (this * density).toInt()

    private fun formatDate(yyyymmdd: Int): String {
        val s = yyyymmdd.toString()
        return if (s.length == 8) "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)}" else s
    }

    fun buildInstalledRows(container: LinearLayout, installed: List<InstalledDictionary>) {
        container.removeAllViews()
        val context = container.context
        val density = context.resources.displayMetrics.density

        val groups = installed
            .sortedWith(compareBy({ typeSortKey(it.type) }, { it.type }, { it.lang }))
            .groupBy { it.type }
            .entries
            .sortedBy { typeSortKey(it.key) }

        for ((groupIndex, group) in groups.withIndex()) {
            val (type, rows) = group

            if (rows.size == 1) {
                container.addView(buildSingleRow(context, density, typeLabel(type), rows[0]))
            } else {
                container.addView(buildGroupHeader(context, density, typeLabel(type)))
                for (row in rows) {
                    container.addView(buildSingleRow(context, density, row.description, row))
                }
            }

            if (groupIndex != groups.lastIndex) {
                container.addView(divider(context, density))
            }
        }
    }

    fun buildDownloadingRows(container: LinearLayout, downloading: List<RemoteDictionaryObject>) {
        container.removeAllViews()
        val context = container.context
        val density = context.resources.displayMetrics.density

        val sorted = downloading.sortedWith(compareBy({ typeSortKey(it.type) }, { it.type }, { it.lang }))

        for ((index, entry) in sorted.withIndex()) {
            container.addView(buildDownloadingRow(context, density, entry))
            if (index != sorted.lastIndex) {
                container.addView(divider(context, density))
            }
        }
    }

    private fun divider(context: Context, density: Float): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1.dp(density)
        )
        setBackgroundColor(ContextCompat.getColor(context, R.color.dict_card_stroke))
    }

    private fun buildGroupHeader(context: Context, density: Float, label: String): View =
        TextView(context).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.text_foreground_secondary))
            setPadding(0, 10.dp(density), 0, 2.dp(density))
        }

    private fun buildSingleRow(context: Context, density: Float, title: String, row: InstalledDictionary): View {
        val description = TextView(context).apply {
            text = title
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_foreground_primary))
            typeface = Typeface.DEFAULT_BOLD
        }
        val caption = TextView(context).apply {
            text = context.getString(
                R.string.dictionary_version_caption, row.version, formatDate(row.date)
            )
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.text_foreground_secondary))
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 6.dp(density), 0, 6.dp(density))
            addView(description)
            addView(caption)
        }
    }

    private fun buildDownloadingRow(context: Context, density: Float, entry: RemoteDictionaryObject): View {
        val description = TextView(context).apply {
            text = entry.description
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_foreground_primary))
            typeface = Typeface.DEFAULT_BOLD
        }
        val caption = TextView(context).apply {
            text = context.getString(R.string.dictionary_status_downloading)
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.dict_status_pending))
        }
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(description)
            addView(caption)
        }
        val spinner = ProgressBar(context).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(20.dp(density), 20.dp(density)).apply {
                marginStart = 12.dp(density)
            }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 8.dp(density), 0, 8.dp(density))
            addView(textColumn)
            addView(spinner)
        }
    }
}
