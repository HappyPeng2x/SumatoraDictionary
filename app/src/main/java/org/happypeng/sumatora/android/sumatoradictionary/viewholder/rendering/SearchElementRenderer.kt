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
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder
import org.happypeng.sumatora.android.superrubyspan.tools.JapaneseText
import org.json.JSONArray
import org.json.JSONException
import java.util.HashMap

private fun renderJSONArray(aArray: JSONArray?, aSeparator: String,
                            aResolveEntities: Boolean,
                            entities: HashMap<String, String>,
                            colors: DictionarySearchElementViewHolder.Colors): String {
    if (aArray == null) {
        return ""
    }
    val sb = StringBuilder()
    try {
        for (i in 0 until aArray.length()) {
            val s = aArray.getString(i)
            if (sb.length > 0 && aSeparator != null) {
                sb.append(aSeparator)
            }
            if (aResolveEntities) {
                if (entities != null &&
                        entities.containsKey(s)) {
                    sb.append(entities[s])
                } else {
                    System.err.println("Could not resolve entity: $s")
                }
            } else {
                sb.append(s)
            }
        }
    } catch (e: JSONException) {
        e.printStackTrace()
    }
    return sb.toString()
}

public fun renderEntry(aEntry: DictionarySearchElement,
    entities: HashMap<String, String>, colors: DictionarySearchElementViewHolder.Colors): SpannableStringBuilder {
    val sb = SpannableStringBuilder()
    var writingsCount = 0
    for (w in aEntry.getWritingsPrio().split(" ".toRegex()).toTypedArray()) {
        if (w.length > 0) {
            if (writingsCount > 0) {
                sb.append("・")
                sb.setSpan(ForegroundColorSpan(Color.GRAY), sb.length - 1, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            sb.append(w)
            sb.setSpan(BackgroundColorSpan(colors.highlight),
                    sb.length - w.length, sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            writingsCount = writingsCount + 1
        }
    }
    for (w in aEntry.getWritings().split(" ".toRegex()).toTypedArray()) {
        if (w.length > 0) {
            if (writingsCount > 0) {
                sb.append("・")
                sb.setSpan(ForegroundColorSpan(Color.GRAY), sb.length - 1, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            sb.append(w)
            writingsCount = writingsCount + 1
        }
    }
    if (writingsCount > 0) {
        sb.append(" ")
    }
    sb.setSpan(RelativeSizeSpan(1.4f), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    sb.append("【")
    sb.setSpan(ForegroundColorSpan(Color.GRAY), sb.length - 1, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    var readingsCount = 0
    for (r in aEntry.getReadingsPrio().split(" ".toRegex()).toTypedArray()) {
        if (r.length > 0) {
            if (readingsCount > 0) {
                sb.append("・")
                sb.setSpan(ForegroundColorSpan(Color.GRAY), sb.length - 1, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            sb.append(r)
            sb.setSpan(BackgroundColorSpan(colors.highlight),
                    sb.length - r.length, sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            readingsCount = readingsCount + 1
        }
    }
    for (r in aEntry.getReadings().split(" ".toRegex()).toTypedArray()) {
        if (r.length > 0) {
            if (readingsCount > 0) {
                sb.append("・")
                sb.setSpan(ForegroundColorSpan(Color.GRAY), sb.length - 1, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            sb.append(r)
            readingsCount = readingsCount + 1
        }
    }
    sb.append("】")
    sb.setSpan(ForegroundColorSpan(Color.GRAY), sb.length - 1, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    sb.append("　")
    var glossCount = 0
    try {
        val gloss = JSONArray(aEntry.getGloss())
        var pos: JSONArray? = null
        val posStr = aEntry.getPos()
        if (posStr != null) {
            pos = JSONArray(posStr)
        }
        for (i in 0 until gloss.length()) {
            if (glossCount > 0) {
                sb.append("　")
            }
            val prefix = Integer.toString(glossCount + 1) + ". "
            sb.append(prefix)
            sb.setSpan(StyleSpan(Typeface.BOLD),
                    sb.length - prefix.length, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (pos != null && glossCount < pos.length()) {
                val p = renderJSONArray(pos.getJSONArray(glossCount), ", ", true, entities, colors)
                if (p.length > 0) {
                    sb.append(p)
                    sb.setSpan(ForegroundColorSpan(colors.pos),
                            sb.length - p.length, sb.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.append(" ")
                }
            }
            sb.append(gloss.getString(i))
            glossCount = glossCount + 1
        }
    } catch (e: JSONException) {
        e.printStackTrace()
    }
    try {
        val exampleSentences = aEntry.exampleSentences
        val exampleTranslations = aEntry.exampleTranslations
        if (exampleSentences != null &&
                exampleTranslations != null) {
            val exampleSentencesArray = JSONArray(aEntry.exampleSentences)
            val exampleTranslationsArray = JSONArray(aEntry.exampleTranslations)
            for (i in 0 until exampleSentencesArray.length()) {
                if (i == 0) {
                    sb.append("\n\n")
                    sb.setSpan(RelativeSizeSpan(0.3f), sb.length - 2, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (i < exampleTranslationsArray.length()) {
                    JapaneseText.spannifyWithFurigana(sb, "→ " + exampleSentencesArray.getString(i), 0.9f)
                }
                sb.append(" ")
                sb.append(exampleTranslationsArray.getString(i))
                if (i + 1 < exampleSentencesArray.length()) {
                    sb.append("\n")
                }
            }
        }
    } catch (e: JSONException) {
        e.printStackTrace()
    }
    return sb
}