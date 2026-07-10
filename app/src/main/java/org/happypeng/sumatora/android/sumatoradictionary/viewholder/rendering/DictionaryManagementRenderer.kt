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
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary
import org.happypeng.sumatora.android.sumatoradictionary.db.OptionalDictionaryCatalog
import org.happypeng.sumatora.android.sumatoradictionary.db.RemoteDictionaryObject

// One row per (type, lang) slot DictionariesManagementActivity knows about, whichever state it's
// currently in. Exactly one of [installed]/[remote] is meaningful for that state:
//  - installed only, not downloading: a bundled or optional pack that's in place.
//  - installed set and downloading: an installed pack whose background update is in flight.
//  - remote only, not downloading: an optional pack offered but not yet installed.
//  - remote only, downloading: an optional pack being installed for the first time.
data class DictionaryManagementRow(
    val type: String,
    val lang: String,
    val description: String,
    val version: Int,
    val date: Int,
    val installed: InstalledDictionary?,
    val remote: RemoteDictionaryObject?,
    val downloading: Boolean
)

// Builds a single flat "Dictionaries" list for DictionariesManagementActivity, grouped by type,
// with an install/delete button inline where one applies instead of separate lists per state.
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

    fun buildRows(
        container: LinearLayout,
        rows: List<DictionaryManagementRow>,
        onInstall: (RemoteDictionaryObject) -> Unit,
        onDelete: (InstalledDictionary) -> Unit
    ) {
        container.removeAllViews()
        val context = container.context
        val density = context.resources.displayMetrics.density

        val groups = rows
            .sortedWith(compareBy({ typeSortKey(it.type) }, { it.type }, { it.lang }))
            .groupBy { it.type }
            .entries
            .sortedBy { typeSortKey(it.key) }

        for ((groupIndex, group) in groups.withIndex()) {
            val (type, groupRows) = group

            if (groupRows.size == 1) {
                container.addView(buildRow(context, density, typeLabel(type), groupRows[0], onInstall, onDelete))
            } else {
                container.addView(buildGroupHeader(context, density, typeLabel(type)))
                for (row in groupRows) {
                    container.addView(buildRow(context, density, row.description, row, onInstall, onDelete))
                }
            }

            if (groupIndex != groups.lastIndex) {
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

    private fun buildRow(
        context: Context,
        density: Float,
        title: String,
        row: DictionaryManagementRow,
        onInstall: (RemoteDictionaryObject) -> Unit,
        onDelete: (InstalledDictionary) -> Unit
    ): View {
        val description = TextView(context).apply {
            text = title
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_foreground_primary))
            typeface = Typeface.DEFAULT_BOLD
        }
        val caption = TextView(context).apply {
            if (row.downloading) {
                text = context.getString(R.string.dictionary_status_downloading)
                setTextColor(ContextCompat.getColor(context, R.color.dict_status_pending))
            } else {
                text = context.getString(
                    R.string.dictionary_version_caption, row.version, formatDate(row.date)
                )
                setTextColor(ContextCompat.getColor(context, R.color.text_foreground_secondary))
            }
            textSize = 12f
        }
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(description)
            addView(caption)
        }

        val trailing: View? = when {
            row.downloading -> ProgressBar(context).apply {
                isIndeterminate = true
                layoutParams = LinearLayout.LayoutParams(20.dp(density), 20.dp(density)).apply {
                    marginStart = 12.dp(density)
                }
            }
            row.installed != null && row.type in OptionalDictionaryCatalog.OPTIONAL_TYPES ->
                actionButton(
                    context, density, R.drawable.ic_delete_24px, R.color.tag_register,
                    R.string.delete_icon_description
                ) { onDelete(row.installed) }
            row.installed == null && row.remote != null ->
                actionButton(
                    context, density, R.drawable.ic_add_24px, R.color.dict_status_pending,
                    R.string.install_icon_description
                ) { onInstall(row.remote) }
            else -> null
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 6.dp(density), 0, 6.dp(density))
            addView(textColumn)
            trailing?.let { addView(it) }
        }
    }

    private fun selectableItemBackgroundBorderless(context: Context): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)
        return typedValue.resourceId
    }

    private fun actionButton(
        context: Context,
        density: Float,
        iconRes: Int,
        tintColorRes: Int,
        contentDescriptionRes: Int,
        onClick: () -> Unit
    ): ImageButton = ImageButton(context).apply {
        setImageResource(iconRes)
        setBackgroundResource(selectableItemBackgroundBorderless(context))
        val padding = 8.dp(density)
        setPadding(padding, padding, padding, padding)
        ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(ContextCompat.getColor(context, tintColorRes)))
        contentDescription = context.getString(contentDescriptionRes)
        layoutParams = LinearLayout.LayoutParams(40.dp(density), 40.dp(density)).apply {
            marginStart = 4.dp(density)
        }
        setOnClickListener { onClick() }
    }
}
