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

private fun keysAt(array: JSONArray?, index: Int): List<String> {
    if (array == null || index >= array.length()) return emptyList()
    val arr = array.optJSONArray(index) ?: return emptyList()
    return (0 until arr.length()).map { arr.getString(it) }
}

private fun SpannableStringBuilder.appendTags(tags: List<String>, posColor: Int) {
    for ((j, tag) in tags.withIndex()) {
        if (j > 0) {
            append("·")
            setSpan(ForegroundColorSpan(Color.GRAY), length - 1, length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        append(tag)
        setSpan(ForegroundColorSpan(posColor), length - tag.length, length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    if (tags.isNotEmpty()) append(" ")
}

fun renderGloss(entry: DictionarySearchElement,
                colors: DictionarySearchElementViewHolder.Colors): SpannableStringBuilder {
    val sb = SpannableStringBuilder()
    var glossCount = 0
    try {
        val gloss = JSONArray(entry.gloss ?: return sb)
        val pos = entry.pos?.let { JSONArray(it) }
        val misc = entry.misc?.let { JSONArray(it) }
        for (i in 0 until gloss.length()) {
            if (glossCount > 0) sb.append("\n")
            val prefix = "${glossCount + 1}. "
            sb.append(prefix)
            sb.setSpan(StyleSpan(Typeface.BOLD), sb.length - prefix.length, sb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.appendTags(keysAt(pos, i) + keysAt(misc, i), colors.pos)
            sb.append(gloss.getString(i))
            glossCount++
        }
    } catch (e: JSONException) {
        e.printStackTrace()
    }
    return sb
}
