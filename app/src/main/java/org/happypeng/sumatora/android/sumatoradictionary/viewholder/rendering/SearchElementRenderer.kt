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
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder
import org.happypeng.sumatora.android.superrubyspan.SuperReplacementSpan
import org.happypeng.sumatora.android.superrubyspan.SuperRubySpan
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// Ruby scale for headword furigana (base is 18sp → ruby ≈ 14sp)
private const val RUBY_SCALE = 0.75f

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

// Parse the furigana column (a JSON object keyed by writing form) once per entry.
private fun parseFuriganaMap(furigana: String?): JSONObject? {
    if (furigana.isNullOrBlank()) return null
    return try { JSONObject(furigana) } catch (_: JSONException) { null }
}

// Append a single writing [word], rendered with ruby furigana if [furiganaMap] has an entry
// for it, otherwise as plain text. Optionally highlights the whole token. If [onKanjiClick] is
// given, each kanji character in [word] gets its own tap target (Gap 9: kanji detail lookup).
private fun appendFuriganaWord(sb: SpannableStringBuilder, word: String,
                               furiganaMap: JSONObject?, highlight: Int,
                               onKanjiClick: ((String) -> Unit)? = null) {
    val start = sb.length
    val furi = furiganaMap?.optString(word)
    if (!furi.isNullOrBlank()) {
        appendFurigana(sb, furi)
    } else {
        sb.append(word)
    }
    if (highlight != 0) {
        sb.setSpan(BackgroundColorSpan(highlight), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    if (onKanjiClick != null) {
        for (i in word.indices) {
            if (isKanji(word[i])) {
                val charStart = start + i
                val ch = word[i].toString()
                sb.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) = onKanjiClick(ch)
                    override fun updateDrawState(ds: TextPaint) { /* keep furigana/plain styling */ }
                }, charStart, charStart + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
}

fun renderHeadword(entry: DictionarySearchElement,
                   colors: DictionarySearchElementViewHolder.Colors,
                   onKanjiClick: ((String) -> Unit)? = null): SpannableStringBuilder {
    val sb = SpannableStringBuilder()
    val hasWritings = !entry.writingsPrio.isNullOrBlank() || !entry.writings.isNullOrBlank()
    if (hasWritings) {
        val furiganaMap = parseFuriganaMap(entry.furigana)
        val prioParts = entry.writingsPrio.orEmpty().split(" ").filter { it.isNotEmpty() }
        val nonPrioParts = entry.writings.orEmpty().split(" ").filter { it.isNotEmpty() }
        var first = true
        for (w in prioParts) {
            if (!first) {
                sb.append("・")
                sb.setSpan(ForegroundColorSpan(Color.GRAY), sb.length - 1, sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            appendFuriganaWord(sb, w, furiganaMap, colors.highlight, onKanjiClick)
            first = false
        }
        for (w in nonPrioParts) {
            if (!first) {
                sb.append("・")
                sb.setSpan(ForegroundColorSpan(Color.GRAY), sb.length - 1, sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            appendFuriganaWord(sb, w, furiganaMap, 0, onKanjiClick)
            first = false
        }
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

// A resolved xref/ant reference: display text, plus the target entry's seq/sense if resolution
// succeeded at pipeline build time (absent when resolution failed upstream).
data class XrefRef(val text: String, val seq: Long?, val sense: Int?)

// Like keysAt, but for the resolved xref/ant JSON shape: an array (per sense) of arrays of
// {"text": ..., "seq": ..., "sense": ...} objects (seq/sense may be absent).
internal fun refsAt(array: JSONArray?, index: Int): List<XrefRef> {
    if (array == null || index >= array.length()) return emptyList()
    val arr = array.optJSONArray(index) ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        val text = obj.optString("text")
        if (text.isNullOrEmpty()) return@mapNotNull null
        val seq = if (obj.has("seq")) obj.optLong("seq") else null
        val sense = if (obj.has("sense")) obj.optInt("sense") else null
        XrefRef(text, seq, sense)
    }
}

// Gap 8 — proper names (JMnedict) carry a flat translations array in `gloss` and their
// type list (place/person/station/…) in `properNounTypes`, discriminated by `isProperNoun`.
private fun renderProperNounGloss(entry: DictionarySearchElement,
                                  colors: DictionarySearchElementViewHolder.Colors,
                                  density: Float): SpannableStringBuilder {
    val sb = SpannableStringBuilder()

    val types = entry.properNounTypes?.let { try { JSONArray(it) } catch (_: JSONException) { null } }
    if (types != null && types.length() > 0) {
        val cornerPx   = 3f   * density
        val hPadPx     = 5f   * density
        val vPadPx     = 1.5f * density
        val trailingPx = 4f   * density
        for (i in 0 until types.length()) {
            val start = sb.length
            sb.append(" ")
            sb.setSpan(
                RoundedTagSpan(types.optString(i), colors.pos, 0.85f,
                    cornerPx, hPadPx, vPadPx, trailingPx),
                start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        sb.append("\n")
    }

    val translations = entry.gloss?.let { try { JSONArray(it) } catch (_: JSONException) { null } }
    if (translations != null) {
        for (i in 0 until translations.length()) {
            if (i > 0) sb.append(", ")
            sb.append(translations.getString(i))
        }
    }

    return sb
}

fun renderGloss(entry: DictionarySearchElement,
                colors: DictionarySearchElementViewHolder.Colors,
                density: Float): SpannableStringBuilder {
    if (entry.isProperNoun) {
        return renderProperNounGloss(entry, colors, density)
    }

    val sb = SpannableStringBuilder()

    // Gap 4 — label conjugated (deinflected) hits with the conjugation applied, e.g. "past tense".
    entry.deinflectionLabel?.let { label ->
        val start = sb.length
        sb.append(label)
        sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(Color.GRAY), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append("\n")
    }

    val glossJson = entry.gloss
    if (glossJson != null) {
        try {
            val gloss = JSONArray(glossJson)
            val pos   = entry.pos?.let   { JSONArray(it) }
            val misc  = entry.misc?.let  { JSONArray(it) }
            val field = entry.field?.let { JSONArray(it) }
            val dial  = entry.dial?.let  { JSONArray(it) }

            if (gloss.length() > 0) {
                val cornerPx   = 3f   * density
                val hPadPx     = 5f   * density
                val vPadPx     = 1.5f * density
                val trailingPx = 4f   * density

                var prevPosKeys   = listOf<String>()
                var prevMiscKeys  = listOf<String>()
                var prevFieldKeys = listOf<String>()
                var prevDialKeys  = listOf<String>()
                var groupStarted  = false
                var firstInGroup  = true

                for (i in 0 until gloss.length()) {
                    val posKeys   = keysAt(pos, i)
                    val miscKeys  = keysAt(misc, i)
                    val fieldKeys = keysAt(field, i)
                    val dialKeys  = keysAt(dial, i)

                    val newGroup = !groupStarted ||
                        posKeys != prevPosKeys || miscKeys != prevMiscKeys ||
                        fieldKeys != prevFieldKeys || dialKeys != prevDialKeys

                    if (newGroup) {
                        if (groupStarted) sb.append("\n")
                        val seenKeys = linkedSetOf<String>()
                        seenKeys += posKeys + miscKeys + fieldKeys + dialKeys
                        for (key in seenKeys) {
                            val bgColor = colors.tagBgColor(key)
                            if (bgColor == 0) continue
                            val start = sb.length
                            sb.append(" ")
                            sb.setSpan(
                                RoundedTagSpan(TagSystem.label(key), bgColor, 0.85f,
                                    cornerPx, hPadPx, vPadPx, trailingPx),
                                start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                        if (seenKeys.isNotEmpty()) sb.append("\n")
                        prevPosKeys   = posKeys
                        prevMiscKeys  = miscKeys
                        prevFieldKeys = fieldKeys
                        prevDialKeys  = dialKeys
                        groupStarted  = true
                        firstInGroup  = true
                    }

                    if (!firstInGroup) sb.append("; ")
                    sb.append(gloss.getString(i))
                    firstInGroup = false
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    // Append first example sentence in grey (strip {kanji;reading} furigana markup)
    val sentJson = entry.example_sentences
    if (sentJson != null) {
        try {
            val sentences = JSONArray(sentJson)
            if (sentences.length() > 0) {
                val plain = stripCurlyFurigana(sentences.getString(0))
                if (plain.isNotEmpty()) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    val exStart = sb.length
                    sb.append(plain)
                    sb.setSpan(ForegroundColorSpan(Color.GRAY), exStart, sb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(RelativeSizeSpan(0.85f), exStart, sb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        } catch (_: JSONException) { }
    }

    return sb
}

// Strip {kanji;reading} → kanji (Tatoeba example furigana format)
private fun stripCurlyFurigana(s: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < s.length) {
        if (s[i] == '{') {
            val close = s.indexOf('}', i + 1)
            if (close < 0) { out.append(s[i++]); continue }
            val semi = s.indexOf(';', i + 1)
            if (semi in (i + 1) until close) out.append(s, i + 1, semi)
            else out.append(s, i + 1, close)
            i = close + 1
        } else {
            out.append(s[i++])
        }
    }
    return out.toString()
}
