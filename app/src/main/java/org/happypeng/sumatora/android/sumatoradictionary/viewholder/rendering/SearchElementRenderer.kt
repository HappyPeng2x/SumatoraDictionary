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
import org.happypeng.sumatora.android.sumatoradictionary.db.EntryListSummary
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder
import org.happypeng.sumatora.android.superrubyspan.SuperReplacementSpan
import org.happypeng.sumatora.android.superrubyspan.SuperRubySpan

// Ruby scale for headword furigana (base is 18sp → ruby ≈ 14sp)
private const val RUBY_SCALE = 0.75f

private fun isKanji(c: Char): Boolean {
    val cp = c.code
    return cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF || cp in 0xF900..0xFAFF
}

// Renders schema v2's pre-split FormFuriganaSegment rows (base, ruby) directly - no more
// bracket-notation ("食[た]べ物[もの]") parsing needed. If [onKanjiClick] is given, each kanji
// character gets its own tap target (kanji detail lookup).
fun renderFuriganaSegments(sb: SpannableStringBuilder, segments: List<EntryListSummary.FuriganaSegment>,
                           highlight: Int = 0, onKanjiClick: ((String) -> Unit)? = null) {
    for (segment in segments) {
        val start = sb.length
        if (segment.ruby.isNullOrEmpty()) {
            sb.append(segment.base)
        } else {
            val rubySpannable = SpannableString(segment.ruby).also {
                it.setSpan(RelativeSizeSpan(RUBY_SCALE), 0, it.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            sb.append(segment.base)
            sb.setSpan(
                SuperRubySpan(rubySpannable, SuperReplacementSpan.Alignment.JIS, SuperReplacementSpan.Alignment.JIS),
                start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        if (highlight != 0) {
            sb.setSpan(BackgroundColorSpan(highlight), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (onKanjiClick != null) {
            for (i in segment.base.indices) {
                if (isKanji(segment.base[i])) {
                    val charStart = start + i
                    val ch = segment.base[i].toString()
                    sb.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) = onKanjiClick(ch)
                        override fun updateDrawState(ds: TextPaint) { /* keep furigana/plain styling */ }
                    }, charStart, charStart + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
    }
}

fun renderHeadword(primaryText: String?, furiganaSegments: List<EntryListSummary.FuriganaSegment>,
                   colors: DictionarySearchElementViewHolder.Colors,
                   onKanjiClick: ((String) -> Unit)? = null): SpannableStringBuilder {
    val sb = SpannableStringBuilder()
    if (!primaryText.isNullOrBlank()) {
        if (furiganaSegments.isNotEmpty()) {
            renderFuriganaSegments(sb, furiganaSegments, colors.highlight, onKanjiClick)
        } else {
            val start = sb.length
            sb.append(primaryText)
            sb.setSpan(BackgroundColorSpan(colors.highlight), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    return sb
}

fun renderHeadword(summary: EntryListSummary,
                   colors: DictionarySearchElementViewHolder.Colors,
                   onKanjiClick: ((String) -> Unit)? = null): SpannableStringBuilder =
    renderHeadword(summary.primaryText, summary.furiganaSegments, colors, onKanjiClick)

fun renderReading(primaryReading: String?,
                  colors: DictionarySearchElementViewHolder.Colors): SpannableStringBuilder {
    val sb = SpannableStringBuilder()
    primaryReading?.let {
        val start = sb.length
        sb.append(it)
        sb.setSpan(BackgroundColorSpan(colors.highlight), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return sb
}

fun renderReading(summary: EntryListSummary,
                  colors: DictionarySearchElementViewHolder.Colors): SpannableStringBuilder =
    renderReading(summary.primaryReading, colors)

private fun appendTagPills(sb: SpannableStringBuilder, codes: List<String>,
                           colors: DictionarySearchElementViewHolder.Colors, density: Float) {
    if (codes.isEmpty()) return
    val cornerPx   = 3f   * density
    val hPadPx     = 5f   * density
    val vPadPx     = 1.5f * density
    val trailingPx = 4f   * density
    for (code in codes) {
        val bgColor = colors.tagBgColor(code)
        if (bgColor == 0) continue
        val start = sb.length
        sb.append(" ")
        sb.setSpan(
            RoundedTagSpan(TagSystem.label(code), bgColor, 0.85f, cornerPx, hPadPx, vPadPx, trailingPx),
            start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    sb.append("\n")
}

private fun renderNameGloss(summary: EntryListSummary,
                            colors: DictionarySearchElementViewHolder.Colors,
                            density: Float): SpannableStringBuilder {
    val sb = SpannableStringBuilder()
    appendTagPills(sb, summary.nameTypeCodes, colors, density)
    sb.append(summary.translations.joinToString(", "))
    return sb
}

fun renderGloss(summary: EntryListSummary,
                colors: DictionarySearchElementViewHolder.Colors,
                density: Float,
                deinflectionLabel: String? = null): SpannableStringBuilder {
    if (summary.isName) {
        return renderNameGloss(summary, colors, density)
    }

    val sb = SpannableStringBuilder()

    deinflectionLabel?.let { label ->
        val start = sb.length
        sb.append(label)
        sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(Color.GRAY), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append("\n")
    }

    appendTagPills(sb, summary.tagCodes, colors, density)
    summary.glossPreview?.let { sb.append(it) }

    return sb
}
