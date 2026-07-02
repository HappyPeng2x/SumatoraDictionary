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
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder
import org.happypeng.sumatora.android.superrubyspan.SuperReplacementSpan
import org.happypeng.sumatora.android.superrubyspan.SuperRubySpan
import org.json.JSONArray
import org.json.JSONException

// Ruby scale for headword furigana (base is 20sp → ruby ≈ 10sp)
private const val RUBY_SCALE = 0.5f

private fun isKanji(c: Char): Boolean {
    val cp = c.code
    return cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF || cp in 0xF900..0xFAFF
}

/**
 * Append a bracket-notation furigana string (e.g. "食[た]べ物[もの]") to [sb],
 * creating SuperRubySpans for each kanji+ruby pair.
 */
fun appendFurigana(sb: SpannableStringBuilder, furigana: String, scale: Float = RUBY_SCALE) {
    var i = 0
    while (i < furigana.length) {
        val open = furigana.indexOf('[', i)
        if (open < 0) { sb.append(furigana, i, furigana.length); break }
        val close = furigana.indexOf(']', open + 1)
        if (close < 0) { sb.append(furigana, i, furigana.length); break }

        // Walk backward from [open] to find the start of the kanji run that is the ruby base.
        var baseStart = open
        while (baseStart > i && isKanji(furigana[baseStart - 1])) baseStart--

        // Plain text before the kanji base
        if (baseStart > i) sb.append(furigana, i, baseStart)

        // Kanji base + ruby span
        val base = furigana.substring(baseStart, open)
        val ruby = furigana.substring(open + 1, close)
        val spanStart = sb.length
        sb.append(base)
        val rubySpannable = SpannableString(ruby).also {
            it.setSpan(RelativeSizeSpan(scale), 0, it.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        sb.setSpan(
            SuperRubySpan(rubySpannable, SuperReplacementSpan.Alignment.JIS, SuperReplacementSpan.Alignment.JIS),
            spanStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        i = close + 1
    }
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
        val prioParts = entry.writingsPrio.orEmpty().split(" ").filter { it.isNotEmpty() }
        var count = 0
        if (prioParts.isNotEmpty()) {
            val furigana = entry.furigana
            if (!furigana.isNullOrBlank()) {
                val start = sb.length
                appendFurigana(sb, furigana)
                if (colors.highlight != 0) {
                    sb.setSpan(BackgroundColorSpan(colors.highlight), start, sb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else {
                val w = prioParts[0]
                sb.append(w)
                if (colors.highlight != 0) {
                    sb.setSpan(BackgroundColorSpan(colors.highlight), sb.length - w.length, sb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            count = 1
            for (j in 1 until prioParts.size) {
                sb.append("・")
                sb.setSpan(ForegroundColorSpan(Color.GRAY), sb.length - 1, sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val w = prioParts[j]
                sb.append(w)
                if (colors.highlight != 0) {
                    sb.setSpan(BackgroundColorSpan(colors.highlight), sb.length - w.length, sb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                count++
            }
        }
        appendWords(sb, entry.writings, 0, count)
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

        if (gloss.length() == 0) return sb

        // Tags from the first sense group only (matching pos/misc/field/dial of sense 0)
        val posKeys0   = keysAt(pos, 0)
        val miscKeys0  = keysAt(misc, 0)
        val fieldKeys0 = keysAt(field, 0)
        val dialKeys0  = keysAt(dial, 0)
        val seenKeys = linkedSetOf<String>()
        seenKeys += posKeys0 + miscKeys0 + fieldKeys0 + dialKeys0

        val cornerPx   = 3f   * density
        val hPadPx     = 5f   * density
        val vPadPx     = 1.5f * density
        val trailingPx = 4f   * density

        for (key in seenKeys) {
            val bgColor = colors.tagBgColor(key)
            if (bgColor == 0) continue
            val start = sb.length
            sb.append(" ")
            sb.setSpan(
                RoundedTagSpan(TagSystem.label(key), bgColor, 0.85f, cornerPx, hPadPx, vPadPx, trailingPx),
                start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        if (seenKeys.isNotEmpty()) sb.append("\n")

        // Collect and display only first sense group (stop at first tag change)
        var senseIdx = 0
        while (senseIdx < gloss.length()) {
            if (keysAt(pos, senseIdx) != posKeys0 || keysAt(misc, senseIdx) != miscKeys0 ||
                keysAt(field, senseIdx) != fieldKeys0 || keysAt(dial, senseIdx) != dialKeys0) break
            if (senseIdx > 0) sb.append("; ")
            sb.append(gloss.getString(senseIdx))
            senseIdx++
        }
    } catch (e: JSONException) {
        e.printStackTrace()
    }
    return sb
}
