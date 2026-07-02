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

package org.happypeng.sumatora.android.sumatoradictionary.fragment

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.databinding.BottomSheetEntryDetailBinding
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering.TagSystem
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering.keysAt
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering.renderHeadword
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering.renderReading
import org.happypeng.sumatora.android.superrubyspan.tools.JapaneseText
import org.happypeng.sumatora.core.dict.JMDICT_ENTITIES
import org.json.JSONArray
import org.json.JSONException

class EntryDetailBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEntryDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = BottomSheetEntryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()
        val args = requireArguments()
        val density = resources.displayMetrics.density

        val tagColors = DictionarySearchElementViewHolder.Colors.TagColors(
            ContextCompat.getColor(ctx, R.color.tag_pos),
            ContextCompat.getColor(ctx, R.color.tag_register),
            ContextCompat.getColor(ctx, R.color.tag_kana),
            ContextCompat.getColor(ctx, R.color.tag_kanji),
            ContextCompat.getColor(ctx, R.color.tag_usage),
            ContextCompat.getColor(ctx, R.color.tag_domain),
            ContextCompat.getColor(ctx, R.color.tag_dialect)
        )
        val colors = DictionarySearchElementViewHolder.Colors(
            ContextCompat.getColor(ctx, R.color.text_background_primary),
            ContextCompat.getColor(ctx, R.color.text_background_primary_backup),
            ContextCompat.getColor(ctx, R.color.render_highlight),
            ContextCompat.getColor(ctx, R.color.render_pos),
            tagColors
        )

        val primaryColor   = ContextCompat.getColor(ctx, R.color.text_foreground_primary)
        val secondaryColor = ContextCompat.getColor(ctx, R.color.text_foreground_secondary)
        val exBoxBgColor   = ContextCompat.getColor(ctx, R.color.example_box_bg)
        val exBoxBorderColor = ContextCompat.getColor(ctx, R.color.example_box_border)

        val writingsPrio = args.getString(ARG_WRITINGS_PRIO)
        val writings     = args.getString(ARG_WRITINGS)
        val readingsPrio = args.getString(ARG_READINGS_PRIO)
        val readings     = args.getString(ARG_READINGS)

        val stub = DictionarySearchElement().apply {
            this.writingsPrio = writingsPrio
            this.writings     = writings
            this.readingsPrio = readingsPrio
            this.readings     = readings
        }

        binding.entryDetailHeadword.text = renderHeadword(stub, colors)

        val hasWritings = !writingsPrio.isNullOrBlank() || !writings.isNullOrBlank()
        binding.entryDetailReading.visibility = if (hasWritings) View.VISIBLE else View.GONE
        if (hasWritings) binding.entryDetailReading.text = renderReading(stub, colors)

        buildSenses(
            container      = binding.entryDetailSenses,
            glossJson      = args.getString(ARG_GLOSS),
            posJson        = args.getString(ARG_POS),
            miscJson       = args.getString(ARG_MISC),
            fieldJson      = args.getString(ARG_FIELD),
            dialJson       = args.getString(ARG_DIAL),
            sInfJson       = args.getString(ARG_SINF),
            primaryColor   = primaryColor,
            secondaryColor = secondaryColor,
            density        = density
        )

        buildExamples(
            container         = binding.entryDetailExamples,
            header            = binding.entryDetailExamplesHeader,
            divider           = binding.entryDetailExamplesDivider,
            sentencesJson     = args.getString(ARG_EXAMPLE_SENTENCES),
            translationsJson  = args.getString(ARG_EXAMPLE_TRANSLATIONS),
            primaryColor      = primaryColor,
            secondaryColor    = secondaryColor,
            exBoxBgColor      = exBoxBgColor,
            exBoxBorderColor  = exBoxBorderColor,
            density           = density
        )
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun tagBgColor(key: String): Int {
        val ctx = requireContext()
        val resId = when (TagSystem.category(key)) {
            TagSystem.Category.POS      -> R.color.tag_pos
            TagSystem.Category.REGISTER -> R.color.tag_register
            TagSystem.Category.KANA     -> R.color.tag_kana
            TagSystem.Category.KANJI    -> R.color.tag_kanji
            TagSystem.Category.USAGE    -> R.color.tag_usage
            TagSystem.Category.DOMAIN   -> R.color.tag_domain
            TagSystem.Category.DIALECT  -> R.color.tag_dialect
        }
        return ContextCompat.getColor(ctx, resId)
    }

    private fun buildSenses(container: LinearLayout,
                             glossJson: String?, posJson: String?, miscJson: String?,
                             fieldJson: String?, dialJson: String?, sInfJson: String?,
                             primaryColor: Int, secondaryColor: Int,
                             density: Float) {
        if (glossJson.isNullOrBlank()) return

        val glossArray = try { JSONArray(glossJson) } catch (e: JSONException) { return }
        val posArray   = posJson?.let   { try { JSONArray(it) } catch (e: JSONException) { null } }
        val miscArray  = miscJson?.let  { try { JSONArray(it) } catch (e: JSONException) { null } }
        val fieldArray = fieldJson?.let { try { JSONArray(it) } catch (e: JSONException) { null } }
        val dialArray  = dialJson?.let  { try { JSONArray(it) } catch (e: JSONException) { null } }
        val sInfArray  = sInfJson?.let  { try { JSONArray(it) } catch (e: JSONException) { null } }

        // Each group key is the full ordered list of tag keys (pos+misc+field+dial)
        data class SenseGroup(
            val tagKeys: List<String>,
            val glosses: MutableList<Pair<String, List<String>>> = mutableListOf()
        )

        val groups = mutableListOf<SenseGroup>()
        for (i in 0 until glossArray.length()) {
            val tagKeys = keysAt(posArray, i) + keysAt(miscArray, i) +
                          keysAt(fieldArray, i) + keysAt(dialArray, i)
            val sInfNotes = keysAt(sInfArray, i)
            val last = groups.lastOrNull()
            if (last != null && last.tagKeys == tagKeys) {
                last.glosses.add(glossArray.getString(i) to sInfNotes)
            } else {
                groups.add(SenseGroup(tagKeys,
                    mutableListOf(glossArray.getString(i) to sInfNotes)))
            }
        }

        fun Int.dp() = (this * density).toInt()
        fun Float.dp() = (this * density).toInt()

        var absoluteIndex = 1
        for (group in groups) {
            // Header row: * bullet + tag chips
            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.topMargin = 14.dp()
                lp.bottomMargin = 4.dp()
                layoutParams = lp
            }

            // * bullet
            val bullet = TextView(context).apply {
                text = "*"
                textSize = 13f
                setTextColor(secondaryColor)
                typeface = Typeface.DEFAULT_BOLD
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = 6.dp()
                layoutParams = lp
            }
            headerRow.addView(bullet)

            // Chips (deduplicated)
            val seenChipKeys = linkedSetOf<String>()
            seenChipKeys += group.tagKeys
            for (key in seenChipKeys) {
                headerRow.addView(makeChip(key, density))
            }
            container.addView(headerRow)

            // Glosses
            for ((gloss, sInfNotes) in group.glosses) {
                val glossView = TextView(context).apply {
                    val sb = SpannableStringBuilder()
                    val prefix = "$absoluteIndex. "
                    sb.append(prefix)
                    sb.setSpan(StyleSpan(Typeface.BOLD), 0, prefix.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.append(gloss)
                    text = sb
                    textSize = 15f
                    setTextColor(primaryColor)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.topMargin = 3.dp()
                    layoutParams = lp
                }
                container.addView(glossView)

                // s_inf notes in italic secondary
                if (sInfNotes.isNotEmpty()) {
                    val noteView = TextView(context).apply {
                        val sb = SpannableStringBuilder()
                        sb.append(sInfNotes.joinToString("; "))
                        sb.setSpan(StyleSpan(Typeface.ITALIC), 0, sb.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        text = sb
                        textSize = 12f
                        setTextColor(secondaryColor)
                        val lp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT)
                        lp.topMargin = 1.dp()
                        lp.bottomMargin = 1.dp()
                        layoutParams = lp
                    }
                    container.addView(noteView)
                }

                absoluteIndex++
            }
        }
    }

    private fun makeChip(key: String, density: Float): TextView {
        val bgColor = tagBgColor(key)
        val label = TagSystem.label(key)
        val bg = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = 4f * density
        }
        return TextView(context).apply {
            text = label
            contentDescription = JMDICT_ENTITIES[key] ?: label
            textSize = 11f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = bg
            val hPad = (5 * density).toInt()
            val vPad = (2 * density).toInt()
            setPadding(hPad, vPad, hPad, vPad)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = (4 * density).toInt()
            layoutParams = lp
        }
    }

    private fun buildExamples(container: LinearLayout, header: TextView, divider: View,
                               sentencesJson: String?, translationsJson: String?,
                               primaryColor: Int, secondaryColor: Int,
                               exBoxBgColor: Int, exBoxBorderColor: Int,
                               density: Float) {
        if (sentencesJson.isNullOrBlank() || translationsJson.isNullOrBlank()) return

        val sentences    = try { JSONArray(sentencesJson)    } catch (e: JSONException) { return }
        val translations = try { JSONArray(translationsJson) } catch (e: JSONException) { return }
        if (sentences.length() == 0) return

        divider.visibility = View.VISIBLE
        header.visibility  = View.VISIBLE
        container.visibility = View.VISIBLE

        fun Int.dp() = (this * density).toInt()

        for (i in 0 until sentences.length()) {
            if (i >= translations.length()) break

            // Bordered rounded box
            val boxBg = GradientDrawable().apply {
                setColor(exBoxBgColor)
                setStroke((1 * density).toInt(), exBoxBorderColor)
                cornerRadius = 6f * density
            }
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = boxBg
                val pad = 10.dp()
                setPadding(pad, pad, pad, pad)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.topMargin = if (i == 0) 0 else 10.dp()
                layoutParams = lp
            }

            // 用例 label
            val labelView = TextView(context).apply {
                text = "用例"
                textSize = 10f
                setTextColor(secondaryColor)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = 4.dp()
                layoutParams = lp
            }
            box.addView(labelView)

            // Japanese sentence with furigana
            val sentenceView = TextView(context).apply {
                val sb = SpannableStringBuilder()
                JapaneseText.spannifyWithFurigana(sb, "🇯🇵 " + sentences.getString(i), 0.85f)
                text = sb
                textSize = 14f
                setTextColor(primaryColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            box.addView(sentenceView)

            // English translation
            val translationView = TextView(context).apply {
                text = "🇬🇧 " + translations.getString(i)
                textSize = 13f
                setTextColor(secondaryColor)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.topMargin = 3.dp()
                layoutParams = lp
            }
            box.addView(translationView)

            container.addView(box)
        }
    }

    companion object {
        private const val ARG_WRITINGS_PRIO       = "wp"
        private const val ARG_WRITINGS             = "w"
        private const val ARG_READINGS_PRIO        = "rp"
        private const val ARG_READINGS             = "r"
        private const val ARG_GLOSS                = "g"
        private const val ARG_POS                  = "p"
        private const val ARG_MISC                 = "m"
        private const val ARG_FIELD                = "f"
        private const val ARG_DIAL                 = "d"
        private const val ARG_SINF                 = "si"
        private const val ARG_EXAMPLE_SENTENCES    = "es"
        private const val ARG_EXAMPLE_TRANSLATIONS = "et"

        fun newInstance(entry: DictionarySearchElement) = EntryDetailBottomSheet().apply {
            arguments = bundleOf(
                ARG_WRITINGS_PRIO       to entry.writingsPrio,
                ARG_WRITINGS            to entry.writings,
                ARG_READINGS_PRIO       to entry.readingsPrio,
                ARG_READINGS            to entry.readings,
                ARG_GLOSS               to entry.gloss,
                ARG_POS                 to entry.pos,
                ARG_MISC                to entry.misc,
                ARG_FIELD               to entry.field,
                ARG_DIAL                to entry.dial,
                ARG_SINF                to entry.s_inf,
                ARG_EXAMPLE_SENTENCES   to entry.example_sentences,
                ARG_EXAMPLE_TRANSLATIONS to entry.example_translations
            )
        }
    }
}
