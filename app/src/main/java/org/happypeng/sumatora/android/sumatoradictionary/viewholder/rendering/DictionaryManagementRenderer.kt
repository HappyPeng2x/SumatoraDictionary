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
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary

// Builds the "Installed dictionaries" rows in DictionariesManagementActivity - one row per pack,
// each with a description/version caption on the left and an "up to date"/"update ready" status
// pill on the right. Built programmatically (same pattern as SearchElementRenderer.buildSenseRows
// and EntryDetailBottomSheet.buildForms) rather than a RecyclerView, since the pack count is small
// and static for the lifetime of one screen visit.
object DictionaryManagementRenderer {

    private fun Int.dp(density: Float) = (this * density).toInt()

    private fun formatDate(yyyymmdd: Int): String {
        val s = yyyymmdd.toString()
        return if (s.length == 8) "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)}" else s
    }

    fun buildInstalledRows(container: LinearLayout, installed: List<InstalledDictionary>) {
        container.removeAllViews()
        val context = container.context
        val density = context.resources.displayMetrics.density
        val sorted = installed.sortedWith(compareBy({ it.type }, { it.lang }))

        for ((index, row) in sorted.withIndex()) {
            container.addView(buildRow(context, density, row))
            if (index != sorted.lastIndex) {
                container.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1.dp(density)
                    )
                    setBackgroundColor(ContextCompat.getColor(context, R.color.dict_card_stroke))
                })
            }
        }
    }

    private fun buildRow(context: Context, density: Float, row: InstalledDictionary): View {
        val pending = row.hasPendingUpdate()

        val description = TextView(context).apply {
            text = row.description
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
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(description)
            addView(caption)
        }

        val pill = TextView(context).apply {
            text = context.getString(
                if (pending) R.string.dictionary_status_update_ready
                else R.string.dictionary_status_up_to_date
            )
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(
                context, if (pending) R.color.dict_status_pending else R.color.dict_status_ok
            ))
            background = ContextCompat.getDrawable(
                context, if (pending) R.drawable.bg_status_pill_pending else R.drawable.bg_status_pill_ok
            )
            setPadding(10.dp(density), 4.dp(density), 10.dp(density), 4.dp(density))
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 8.dp(density), 0, 8.dp(density))
            addView(textColumn)
            addView(pill.apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = 12.dp(density) }
            })
        }
    }
}
