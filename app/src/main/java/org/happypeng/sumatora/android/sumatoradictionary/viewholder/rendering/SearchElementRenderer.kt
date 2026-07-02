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
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder
import org.json.JSONArray
import org.json.JSONException

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
        val n = appendWords(sb, entry.writingsPrio, colors.highlight, 0)
        appendWords(sb, entry.writings, 0, n)
    } else {
        val n = appendWords(sb, entry.readingsPrio, colors.highlight, 0)
        appendWords(sb, entry.readings, 0, n)
    }
    return sb
}

fun renderReading(entry: DictionarySearchElement,
                  colors: DictionarySearchElementViewHolder.Colors): SpannableStringBuilder {
    val sb = SpannableStringBuilder()
    val n = appendWords(sb, entry.readingsPrio, colors.highlight, 0)
    appendWords(sb, entry.readings, 0, n)
    return sb
}

internal fun keysAt(array: JSONArray?, index: Int): List<String> {
    if (array == null || index >= array.length()) return emptyList()
    val arr = array.optJSONArray(index) ?: return emptyList()
    return (0 until arr.length()).map { arr.getString(it) }
}

fun renderGloss(entry: DictionarySearchElement,
                colors: DictionarySearchElementViewHolder.Colors,
                density: Float): SpannableStringBuilder {
    val sb = SpannableStringBuilder()
    try {
        val gloss = JSONArray(entry.gloss ?: return sb)
        val pos   = entry.pos?.let   { JSONArray(it) }
        val misc  = entry.misc?.let  { JSONArray(it) }
        val field = entry.field?.let { JSONArray(it) }
        val dial  = entry.dial?.let  { JSONArray(it) }

        // Collect all unique tag keys in first-appearance order
        val seenKeys = linkedSetOf<String>()
        for (i in 0 until gloss.length()) {
            seenKeys += keysAt(pos, i)
            seenKeys += keysAt(misc, i)
            seenKeys += keysAt(field, i)
            seenKeys += keysAt(dial, i)
        }

        val cornerPx    = 3f  * density
        val hPadPx      = 5f  * density
        val vPadPx      = 1.5f * density
        val trailingPx  = 4f  * density

        // Tag chips line
        for (key in seenKeys) {
            val label = TagSystem.label(key)
            val bgColor = colors.tagBgColor(key)
            if (bgColor == 0) continue
            val start = sb.length
            sb.append(" ") // placeholder character replaced by the span
            sb.setSpan(
                RoundedTagSpan(label, bgColor, 0.85f, cornerPx, hPadPx, vPadPx, trailingPx),
                start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        if (seenKeys.isNotEmpty()) sb.append("\n")

        // All glosses pipe-separated
        val PIPE = "  |  "
        for (i in 0 until gloss.length()) {
            if (i > 0) {
                val pipeStart = sb.length
                sb.append(PIPE)
                sb.setSpan(ForegroundColorSpan(Color.GRAY), pipeStart, sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            sb.append(gloss.getString(i))
        }
    } catch (e: JSONException) {
        e.printStackTrace()
    }
    return sb
}
