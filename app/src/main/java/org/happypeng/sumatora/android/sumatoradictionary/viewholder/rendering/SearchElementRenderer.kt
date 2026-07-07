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
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.happypeng.sumatora.android.sumatoradictionary.R
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
            renderFuriganaSegments(sb, furiganaSegments, 0, onKanjiClick)
        } else {
            sb.append(primaryText)
        }
    }
    return sb
}

// List-row headword: the primary form, plus any alternate kanji spellings sharing its reading
// trailing in smaller grey text - e.g. "頼む 恃む 憑む" - so an alternate spelling isn't hidden
// until the user taps into the detail sheet's forms table.
fun renderHeadword(summary: EntryListSummary,
                   colors: DictionarySearchElementViewHolder.Colors,
                   onKanjiClick: ((String) -> Unit)? = null): SpannableStringBuilder {
    val sb = renderHeadword(summary.primaryText, summary.furiganaSegments, colors, onKanjiClick)
    for (alt in summary.alternateWritings) {
        sb.append(" ")
        val start = sb.length
        if (alt.furiganaSegments.isNotEmpty()) {
            renderFuriganaSegments(sb, alt.furiganaSegments, 0, onKanjiClick)
        } else {
            sb.append(alt.text)
        }
        sb.setSpan(RelativeSizeSpan(0.85f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(colors.secondary), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return sb
}

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

private fun circledDigit(n: Int): String = when {
    n in 1..20  -> String(charArrayOf(('①'.code + n - 1).toChar()))  // ①–⑳
    n in 21..35 -> String(charArrayOf(('㉑'.code + n - 21).toChar())) // ㉑–㉟
    else        -> "($n)"
}

// One TableRow per sense group: column 1 = tag pills (reusing appendTagPills' existing pill
// spans, just targeting a cell TextView instead of the old monolithic gloss TextView), column 2
// = that group's sense(s), numbered globally across the entry so a gloss/reverse-search hit on
// any sense is still visible - not just a first-sense preview. Name entries synthesize a single
// row from their flat name-type-tags + translations so the same grid path covers both shapes.
fun buildSenseRows(
    container: TableLayout,
    summary: EntryListSummary,
    colors: DictionarySearchElementViewHolder.Colors,
    density: Float,
    deinflectionLabel: String? = null
) {
    val context = container.context

    deinflectionLabel?.let { label ->
        container.addView(TextView(context).apply {
            val sb = SpannableStringBuilder(label)
            sb.setSpan(StyleSpan(Typeface.ITALIC), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(Color.GRAY), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text = sb
            textSize = 13f
        })
    }

    if (summary.isName) {
        if (summary.nameTypeCodes.isEmpty() && summary.translations.isEmpty()) return
        container.addView(TableRow(context).apply {
            addView(tagCell(context, summary.nameTypeCodes, colors, density))
            addView(senseCell(context, summary.translations.joinToString(", ")))
        })
        return
    }

    val totalSenses = summary.senseGroups.sumOf { it.senses.size }
    for (group in summary.senseGroups) {
        val senseText = group.senses.joinToString("\n") { sense ->
            if (totalSenses > 1) "${circledDigit(sense.displayIndex)} ${sense.glossText}" else sense.glossText
        }
        container.addView(TableRow(context).apply {
            addView(tagCell(context, group.tagCodes, colors, density))
            addView(senseCell(context, senseText))
        })
    }
}

private fun tagCell(context: android.content.Context, codes: List<String>,
                     colors: DictionarySearchElementViewHolder.Colors, density: Float): TextView {
    val sb = SpannableStringBuilder()
    appendTagPills(sb, codes, colors, density)
    // appendTagPills ends with a trailing "\n" meant to separate a tag line from the text that
    // used to follow it in the same TextView - here tags and sense text are separate grid cells,
    // so that trailing newline would just leave empty space at the bottom of this cell.
    if (sb.isNotEmpty() && sb.last() == '\n') {
        sb.delete(sb.length - 1, sb.length)
    }
    return TextView(context).apply {
        text = sb
        textSize = 13f
        val lp = TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT
        )
        lp.marginEnd = (8 * density).toInt()
        layoutParams = lp
    }
}

private fun senseCell(context: android.content.Context, text: String): TextView =
    TextView(context).apply {
        this.text = text
        textSize = 14f
        setTextColor(ContextCompat.getColor(context, R.color.text_foreground_primary))
    }
