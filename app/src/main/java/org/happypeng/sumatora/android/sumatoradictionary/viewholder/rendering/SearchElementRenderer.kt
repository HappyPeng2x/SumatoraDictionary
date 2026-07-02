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

package org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder
import org.json.JSONArray
import org.json.JSONException
import java.util.HashMap

private fun resolveJSONArray(array: JSONArray?, separator: String,
                             entities: HashMap<String, String>): String {
    if (array == null) return ""
    val sb = StringBuilder()
    try {
        for (i in 0 until array.length()) {
            val s = array.getString(i)
            if (sb.isNotEmpty()) sb.append(separator)
            sb.append(entities[s] ?: run {
                System.err.println("Could not resolve entity: $s")
                s
            })
        }
    } catch (e: JSONException) {
        e.printStackTrace()
    }
    return sb.toString()
}

private fun appendWords(sb: SpannableStringBuilder, spaceSeparated: String?,
                        highlight: Int, count: Int): Int {
    var n = count
    for (w in spaceSeparated.orEmpty().split(" ").filter { it.isNotEmpty() }) {
        if (n > 0) {
            sb.append("・")
            sb.setSpan(ForegroundColorSpan(Color.GRAY), sb.length - 1, sb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        sb.append(w)
        if (highlight != 0) {
            sb.setSpan(BackgroundColorSpan(highlight), sb.length - w.length, sb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        n++
    }
    return n
}

fun renderHeadword(entry: DictionarySearchElement,
                   colors: DictionarySearchElementViewHolder.Colors): SpannableStringBuilder {
    val sb = SpannableStringBuilder()
    val hasWritings = !entry.writingsPrio.isNullOrBlank() || !entry.writings.isNullOrBlank()
    if (hasWritings) {
        var n = appendWords(sb, entry.writingsPrio, colors.highlight, 0)
        appendWords(sb, entry.writings, 0, n)
    } else {
        // Kana-only entry: readings serve as the headword
        var n = appendWords(sb, entry.readingsPrio, colors.highlight, 0)
        appendWords(sb, entry.readings, 0, n)
    }
    return sb
}

fun renderReading(entry: DictionarySearchElement,
                  colors: DictionarySearchElementViewHolder.Colors): SpannableStringBuilder {
    val sb = SpannableStringBuilder()
    var n = appendWords(sb, entry.readingsPrio, colors.highlight, 0)
    appendWords(sb, entry.readings, 0, n)
    return sb
}

fun renderGloss(entry: DictionarySearchElement,
                entities: HashMap<String, String>,
                colors: DictionarySearchElementViewHolder.Colors): SpannableStringBuilder {
    val sb = SpannableStringBuilder()
    var glossCount = 0
    try {
        val gloss = JSONArray(entry.gloss)
        val pos = entry.pos?.let { JSONArray(it) }
        for (i in 0 until gloss.length()) {
            if (glossCount > 0) sb.append("\n")
            val prefix = "${glossCount + 1}. "
            sb.append(prefix)
            sb.setSpan(StyleSpan(Typeface.BOLD), sb.length - prefix.length, sb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (pos != null && glossCount < pos.length()) {
                val p = resolveJSONArray(pos.getJSONArray(glossCount), ", ", entities)
                if (p.isNotEmpty()) {
                    sb.append(p)
                    sb.setSpan(ForegroundColorSpan(colors.pos), sb.length - p.length, sb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.append(" ")
                }
            }
            sb.append(gloss.getString(i))
            glossCount++
        }
    } catch (e: JSONException) {
        e.printStackTrace()
    }
    return sb
}
