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

import android.graphics.Typeface
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
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.databinding.BottomSheetEntryDetailBinding
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering.renderHeadword
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering.renderReading
import org.happypeng.sumatora.core.dict.JMDICT_ENTITIES
import org.happypeng.sumatora.android.superrubyspan.tools.JapaneseText
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

        val colors = DictionarySearchElementViewHolder.Colors(
            ContextCompat.getColor(ctx, R.color.text_background_primary),
            ContextCompat.getColor(ctx, R.color.text_background_primary_backup),
            ContextCompat.getColor(ctx, R.color.render_highlight),
            ContextCompat.getColor(ctx, R.color.render_pos)
        )
        val primaryColor = ContextCompat.getColor(ctx, R.color.text_foreground_primary)
        val secondaryColor = ContextCompat.getColor(ctx, R.color.text_foreground_secondary)
        val posColor = ContextCompat.getColor(ctx, R.color.render_pos)
        val posChipBgColor = ContextCompat.getColor(ctx, R.color.render_pos_chip_bg)

        val writingsPrio = args.getString(ARG_WRITINGS_PRIO)
        val writings = args.getString(ARG_WRITINGS)
        val readingsPrio = args.getString(ARG_READINGS_PRIO)
        val readings = args.getString(ARG_READINGS)

        // Reuse the existing render functions via a lightweight entry stub
        val stub = DictionarySearchElement().apply {
            this.writingsPrio = writingsPrio
            this.writings = writings
            this.readingsPrio = readingsPrio
            this.readings = readings
        }

        binding.entryDetailHeadword.text = renderHeadword(stub, colors)

        val hasWritings = !writingsPrio.isNullOrBlank() || !writings.isNullOrBlank()
        if (hasWritings) {
            binding.entryDetailReading.visibility = View.VISIBLE
            binding.entryDetailReading.text = renderReading(stub, colors)
        } else {
            binding.entryDetailReading.visibility = View.GONE
        }

        buildSenses(binding.entryDetailSenses, args.getString(ARG_GLOSS),
            args.getString(ARG_POS), args.getString(ARG_MISC),
            primaryColor, posColor, posChipBgColor, secondaryColor)

        buildExamples(binding.entryDetailExamples, binding.entryDetailExamplesHeader,
            binding.entryDetailExamplesDivider,
            args.getString(ARG_EXAMPLE_SENTENCES), args.getString(ARG_EXAMPLE_TRANSLATIONS),
            primaryColor, secondaryColor)
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

    private fun buildSenses(container: LinearLayout, glossJson: String?, posJson: String?,
                             miscJson: String?,
                             primaryColor: Int, posColor: Int, posChipBgColor: Int,
                             secondaryColor: Int) {
        if (glossJson.isNullOrBlank()) return
        val glossArray = try { JSONArray(glossJson) } catch (e: JSONException) { return }
        val posArray = try { posJson?.let { JSONArray(it) } } catch (e: JSONException) { null }
        val miscArray = try { miscJson?.let { JSONArray(it) } } catch (e: JSONException) { null }

        fun keysAt(array: JSONArray?, i: Int): List<String> {
            if (array == null || i >= array.length()) return emptyList()
            val arr = array.optJSONArray(i) ?: return emptyList()
            return (0 until arr.length()).map { arr.getString(it) }
        }

        // Group consecutive senses that share the same POS
        data class SenseGroup(val posKeys: List<String>,
                               val glosses: MutableList<Pair<String, List<String>>> = mutableListOf())
        val groups = mutableListOf<SenseGroup>()

        for (i in 0 until glossArray.length()) {
            val posKeys = keysAt(posArray, i)
            val miscKeys = keysAt(miscArray, i)
            val last = groups.lastOrNull()
            if (last != null && last.posKeys == posKeys) {
                last.glosses.add(glossArray.getString(i) to miscKeys)
            } else {
                groups.add(SenseGroup(posKeys, mutableListOf(glossArray.getString(i) to miscKeys)))
            }
        }

        val density = resources.displayMetrics.density
        fun Int.dp() = (this * density).toInt()

        var absoluteIndex = 1
        for (group in groups) {
            if (group.posKeys.isNotEmpty()) {
                val posRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.topMargin = 12.dp()
                    lp.bottomMargin = 4.dp()
                    layoutParams = lp
                }
                for (key in group.posKeys) {
                    posRow.addView(makeChip(key, posColor, posChipBgColor))
                }
                container.addView(posRow)
            }

            for ((gloss, miscKeys) in group.glosses) {
                val glossView = TextView(context).apply {
                    val sb = SpannableStringBuilder()
                    val prefix = "$absoluteIndex. "
                    sb.append(prefix)
                    sb.setSpan(StyleSpan(Typeface.BOLD), 0, prefix.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    // Inline misc tags (uk, col, id, arch, …) before the gloss text
                    for ((j, key) in miscKeys.withIndex()) {
                        if (j > 0) {
                            sb.append("·")
                            sb.setSpan(android.text.style.ForegroundColorSpan(android.graphics.Color.GRAY),
                                sb.length - 1, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        sb.append(key)
                        sb.setSpan(android.text.style.ForegroundColorSpan(posColor),
                            sb.length - key.length, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (miscKeys.isNotEmpty()) sb.append("  ")
                    sb.append(gloss)
                    text = sb
                    textSize = 15f
                    setTextColor(primaryColor)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.topMargin = 2.dp()
                    lp.bottomMargin = 2.dp()
                    layoutParams = lp
                }
                container.addView(glossView)
                absoluteIndex++
            }
        }
    }

    private fun makeChip(key: String, posColor: Int, posChipBgColor: Int): TextView {
        val density = resources.displayMetrics.density
        fun Int.dp() = (this * density).toInt()
        return TextView(context).apply {
            text = key
            contentDescription = JMDICT_ENTITIES[key] ?: key
            textSize = 11f
            setTextColor(posColor)
            typeface = Typeface.DEFAULT_BOLD
            background = resources.getDrawable(R.drawable.bg_pos_chip, context.theme)
            setPadding(6.dp(), 2.dp(), 6.dp(), 2.dp())
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = 4.dp()
            layoutParams = lp
        }
    }

    private fun buildExamples(container: LinearLayout, header: TextView, divider: View,
                               sentencesJson: String?, translationsJson: String?,
                               primaryColor: Int, secondaryColor: Int) {
        if (sentencesJson.isNullOrBlank() || translationsJson.isNullOrBlank()) return

        val sentences = try { JSONArray(sentencesJson) } catch (e: JSONException) { return }
        val translations = try { JSONArray(translationsJson) } catch (e: JSONException) { return }
        if (sentences.length() == 0) return

        divider.visibility = View.VISIBLE
        header.visibility = View.VISIBLE
        container.visibility = View.VISIBLE

        val density = resources.displayMetrics.density
        fun Int.dp() = (this * density).toInt()

        for (i in 0 until sentences.length()) {
            if (i >= translations.length()) break

            val sentenceView = TextView(context).apply {
                val sb = SpannableStringBuilder()
                JapaneseText.spannifyWithFurigana(sb, "→ " + sentences.getString(i), 0.85f)
                text = sb
                textSize = 14f
                setTextColor(primaryColor)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.topMargin = if (i == 0) 0 else 10.dp()
                layoutParams = lp
            }
            container.addView(sentenceView)

            val translationView = TextView(context).apply {
                text = translations.getString(i)
                textSize = 13f
                setTextColor(secondaryColor)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.topMargin = 2.dp()
                layoutParams = lp
            }
            container.addView(translationView)
        }
    }

    companion object {
        private const val ARG_WRITINGS_PRIO = "wp"
        private const val ARG_WRITINGS = "w"
        private const val ARG_READINGS_PRIO = "rp"
        private const val ARG_READINGS = "r"
        private const val ARG_GLOSS = "g"
        private const val ARG_POS = "p"
        private const val ARG_MISC = "m"
        private const val ARG_EXAMPLE_SENTENCES = "es"
        private const val ARG_EXAMPLE_TRANSLATIONS = "et"

        fun newInstance(entry: DictionarySearchElement) = EntryDetailBottomSheet().apply {
            arguments = bundleOf(
                ARG_WRITINGS_PRIO to entry.writingsPrio,
                ARG_WRITINGS to entry.writings,
                ARG_READINGS_PRIO to entry.readingsPrio,
                ARG_READINGS to entry.readings,
                ARG_GLOSS to entry.gloss,
                ARG_POS to entry.pos,
                ARG_MISC to entry.misc,
                ARG_EXAMPLE_SENTENCES to entry.example_sentences,
                ARG_EXAMPLE_TRANSLATIONS to entry.example_translations
            )
        }
    }
}
