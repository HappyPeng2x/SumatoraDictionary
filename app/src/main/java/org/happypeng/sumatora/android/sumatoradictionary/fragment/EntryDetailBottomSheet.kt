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

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
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
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

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

        val writingsPrio = args.getString(ARG_WRITINGS_PRIO)
        val writings     = args.getString(ARG_WRITINGS)
        val readingsPrio = args.getString(ARG_READINGS_PRIO)
        val readings     = args.getString(ARG_READINGS)
        val furigana     = args.getString(ARG_FURIGANA)

        val stub = DictionarySearchElement().apply {
            this.writingsPrio = writingsPrio
            this.writings     = writings
            this.readingsPrio = readingsPrio
            this.readings     = readings
            this.furigana     = furigana
        }

        // Headword with optional priority star
        val headwordSb = SpannableStringBuilder(renderHeadword(stub, colors))
        val isPriority = !readingsPrio.isNullOrBlank() || !writingsPrio.isNullOrBlank()
        if (isPriority) {
            val starStart = headwordSb.length
            headwordSb.append(" ★")
            headwordSb.setSpan(
                ForegroundColorSpan(Color.parseColor("#DAA520")),
                starStart, headwordSb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            headwordSb.setSpan(
                RelativeSizeSpan(0.55f),
                starStart, headwordSb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        binding.entryDetailHeadword.text = headwordSb

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
            xrefJson       = args.getString(ARG_XREF),
            antJson        = args.getString(ARG_ANT),
            lsourceJson    = args.getString(ARG_LSOURCE),
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

    // Returns a Drawable that paints a solid strip of [widthPx] on the left edge
    private fun leftBorderDrawable(color: Int, widthPx: Int): Drawable = object : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        override fun draw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, widthPx.toFloat(), bounds.height().toFloat(), paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    // Rounded pill chip matching Jitendex tag style
    private fun makeTagChip(key: String, color: Int, density: Float): TextView {
        val bg = GradientDrawable().apply {
            setColor(color)
            cornerRadius = 4f * density
        }
        return TextView(context).apply {
            text = TagSystem.label(key)
            textSize = 11f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = bg
            val h = (5 * density).toInt()
            val v = (2 * density).toInt()
            setPadding(h, v, h, v)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = (4 * density).toInt()
            layoutParams = lp
        }
    }

    // Left-border info box matching Jitendex .extra-box style
    private fun makeExtraBox(
        content: String, label: String,
        borderColor: Int, textColor: Int, density: Float
    ): View {
        val dp3 = (3 * density).toInt()
        val dp8 = (8 * density).toInt()
        val dp4 = (4 * density).toInt()

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = leftBorderDrawable(borderColor, dp3)
            setPadding(dp3 + dp8, dp4, dp8, dp4)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp8
            layoutParams = lp

            // Italic label in border color
            addView(TextView(context).apply {
                text = label
                textSize = 10f
                setTextColor(borderColor)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            addView(TextView(context).apply {
                text = content
                textSize = 13f
                setTextColor(textColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }
    }

    private fun circledDigit(n: Int): String = when {
        n in 1..20  -> String(charArrayOf(('①'.code + n - 1).toChar()))  // ①–⑳
        n in 21..35 -> String(charArrayOf(('㉑'.code + n - 21).toChar())) // ㉑–㉟
        else        -> "($n)"
    }

    private fun langName(code: String): String = when (code) {
        "eng" -> "English";  "ger" -> "German";   "fre" -> "French"
        "por" -> "Portuguese"; "kor" -> "Korean"; "chi" -> "Chinese"
        "dut" -> "Dutch";    "ita" -> "Italian";  "spa" -> "Spanish"
        "nor" -> "Norwegian"; "rus" -> "Russian"; "tur" -> "Turkish"
        "grc" -> "Ancient Greek"; "lat" -> "Latin"; "per" -> "Persian"
        "ara" -> "Arabic";   "san" -> "Sanskrit"; "pol" -> "Polish"
        "swe" -> "Swedish";  "dan" -> "Danish";   "fin" -> "Finnish"
        "hun" -> "Hungarian"; "ind" -> "Indonesian"; "may" -> "Malay"
        "ain" -> "Ainu";     "vie" -> "Vietnamese"; "yid" -> "Yiddish"
        else  -> code
    }

    private fun buildSenses(
        container: LinearLayout,
        glossJson: String?, posJson: String?, miscJson: String?,
        fieldJson: String?, dialJson: String?, sInfJson: String?,
        xrefJson: String?, antJson: String?, lsourceJson: String?,
        primaryColor: Int, secondaryColor: Int, density: Float
    ) {
        if (glossJson.isNullOrBlank()) return

        val gloss   = try { JSONArray(glossJson) }   catch (e: JSONException) { return }
        val pos     = posJson?.let     { try { JSONArray(it) } catch (_: JSONException) { null } }
        val misc    = miscJson?.let    { try { JSONArray(it) } catch (_: JSONException) { null } }
        val field   = fieldJson?.let   { try { JSONArray(it) } catch (_: JSONException) { null } }
        val dial    = dialJson?.let    { try { JSONArray(it) } catch (_: JSONException) { null } }
        val sInf    = sInfJson?.let    { try { JSONArray(it) } catch (_: JSONException) { null } }
        val xref    = xrefJson?.let    { try { JSONArray(it) } catch (_: JSONException) { null } }
        val ant     = antJson?.let     { try { JSONArray(it) } catch (_: JSONException) { null } }
        val lsource = lsourceJson?.let { try { JSONArray(it) } catch (_: JSONException) { null } }

        val n = gloss.length()
        if (n == 0) return

        data class Sense(
            val text: String,
            val notes: List<String>,
            val xrefs: List<String>,
            val ants: List<String>,
            val lsources: List<JSONObject>
        )
        data class Group(
            val posKeys: List<String>, val miscKeys: List<String>,
            val fieldKeys: List<String>, val dialKeys: List<String>,
            val senses: MutableList<Sense> = mutableListOf()
        )

        val groups = mutableListOf<Group>()
        for (i in 0 until n) {
            val pk = keysAt(pos, i); val mk = keysAt(misc, i)
            val fk = keysAt(field, i); val dk = keysAt(dial, i)
            val lsArr = lsource?.optJSONArray(i)
            val ls: List<JSONObject> = if (lsArr != null) {
                (0 until lsArr.length()).mapNotNull { lsArr.optJSONObject(it) }
            } else emptyList()

            val sense = Sense(
                text     = gloss.getString(i),
                notes    = keysAt(sInf, i),
                xrefs    = keysAt(xref, i),
                ants     = keysAt(ant, i),
                lsources = ls
            )
            val last = groups.lastOrNull()
            if (last != null && last.posKeys == pk && last.miscKeys == mk &&
                last.fieldKeys == fk && last.dialKeys == dk) {
                last.senses.add(sense)
            } else {
                groups.add(Group(pk, mk, fk, dk, mutableListOf(sense)))
            }
        }

        val totalSenses = n
        val showBullet  = groups.size > 1

        fun Int.dp() = (this * density).toInt()

        val posColor    = ContextCompat.getColor(requireContext(), R.color.tag_pos)
        val miscColor   = ContextCompat.getColor(requireContext(), R.color.tag_register)
        val fieldColor  = ContextCompat.getColor(requireContext(), R.color.tag_domain)
        val dialColor   = ContextCompat.getColor(requireContext(), R.color.tag_dialect)
        val noteColor   = ContextCompat.getColor(requireContext(), R.color.border_note)
        val xrefColor   = ContextCompat.getColor(requireContext(), R.color.border_xref)
        val antColor    = ContextCompat.getColor(requireContext(), R.color.border_ant)
        val lsColor     = ContextCompat.getColor(requireContext(), R.color.border_lsource)

        var senseIndex = 1
        for ((groupIdx, group) in groups.withIndex()) {
            val hasAnyTags = group.posKeys.isNotEmpty() || group.miscKeys.isNotEmpty() ||
                             group.fieldKeys.isNotEmpty() || group.dialKeys.isNotEmpty()

            // Group header: ＊ bullet + tag chips
            if (showBullet || hasAnyTags) {
                val headerRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = if (groupIdx == 0) 8.dp() else 18.dp()
                    lp.bottomMargin = 2.dp()
                    layoutParams = lp
                }

                if (showBullet) {
                    headerRow.addView(TextView(context).apply {
                        text = "＊"
                        textSize = 13f
                        setTextColor(secondaryColor)
                        val lp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        lp.marginEnd = 6.dp()
                        layoutParams = lp
                    })
                }

                for (key in group.posKeys)   headerRow.addView(makeTagChip(key, posColor,   density))
                for (key in group.miscKeys)  headerRow.addView(makeTagChip(key, miscColor,  density))
                for (key in group.fieldKeys) headerRow.addView(makeTagChip(key, fieldColor, density))
                for (key in group.dialKeys)  headerRow.addView(makeTagChip(key, dialColor,  density))

                container.addView(headerRow)
            }

            // Senses
            for (sense in group.senses) {
                // Gloss line: [① ]definition text
                val glossView = TextView(context).apply {
                    val sb = SpannableStringBuilder()
                    if (totalSenses > 1) {
                        val prefix = "${circledDigit(senseIndex)} "
                        sb.append(prefix)
                        sb.setSpan(
                            StyleSpan(Typeface.BOLD), 0, prefix.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    sb.append(sense.text)
                    text = sb
                    textSize = 15f
                    setTextColor(primaryColor)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = 4.dp()
                    if (totalSenses > 1) lp.marginStart = 4.dp()
                    layoutParams = lp
                }
                container.addView(glossView)

                // Goldenrod box: sense notes (s_inf)
                for (note in sense.notes) {
                    container.addView(makeExtraBox(note, "Note", noteColor, primaryColor, density))
                }

                // Blue box: cross-references
                for (xr in sense.xrefs) {
                    container.addView(makeExtraBox(xr, "See also", xrefColor, primaryColor, density))
                }

                // Brown box: antonyms
                for (a in sense.ants) {
                    container.addView(makeExtraBox(a, "Antonym", antColor, primaryColor, density))
                }

                // Purple box: language of origin (lsource)
                for (ls in sense.lsources) {
                    val lang   = ls.optString("lang", "")
                    val word   = ls.optString("text", "")
                    val wasei  = ls.optBoolean("wasei", false)
                    val display = buildString {
                        append(langName(lang))
                        if (word.isNotEmpty()) append(": $word")
                        if (wasei) append(" (wasei-eigo)")
                    }
                    container.addView(makeExtraBox(display, "Language of Origin", lsColor, primaryColor, density))
                }

                senseIndex++
            }
        }
    }

    private fun buildExamples(
        container: LinearLayout, header: TextView, divider: View,
        sentencesJson: String?, translationsJson: String?,
        primaryColor: Int, secondaryColor: Int, density: Float
    ) {
        if (sentencesJson.isNullOrBlank() || translationsJson.isNullOrBlank()) return
        val sentences    = try { JSONArray(sentencesJson)    } catch (e: JSONException) { return }
        val translations = try { JSONArray(translationsJson) } catch (e: JSONException) { return }
        if (sentences.length() == 0) return

        divider.visibility   = View.VISIBLE
        header.visibility    = View.VISIBLE
        container.visibility = View.VISIBLE

        val exColor = ContextCompat.getColor(requireContext(), R.color.border_example)
        fun Int.dp() = (this * density).toInt()

        for (i in 0 until sentences.length()) {
            if (i >= translations.length()) break

            val dp3 = (3 * density).toInt()
            val dp8 = (8 * density).toInt()
            val dp4 = (4 * density).toInt()

            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = leftBorderDrawable(exColor, dp3)
                setPadding(dp3 + dp8, dp4, dp8, dp4)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = if (i == 0) 0 else dp8
                layoutParams = lp
            }

            // Japanese sentence with Tatoeba furigana
            box.addView(TextView(context).apply {
                val sb = SpannableStringBuilder()
                JapaneseText.spannifyWithFurigana(sb, sentences.getString(i), 0.85f)
                text = sb
                textSize = 14f
                setTextColor(primaryColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            // English translation
            box.addView(TextView(context).apply {
                text = translations.getString(i)
                textSize = 12f
                setTextColor(secondaryColor)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = 2.dp()
                layoutParams = lp
            })

            container.addView(box)
        }
    }

    companion object {
        private const val ARG_WRITINGS_PRIO       = "wp"
        private const val ARG_WRITINGS             = "w"
        private const val ARG_READINGS_PRIO        = "rp"
        private const val ARG_READINGS             = "r"
        private const val ARG_FURIGANA             = "furi"
        private const val ARG_GLOSS                = "g"
        private const val ARG_POS                  = "p"
        private const val ARG_MISC                 = "m"
        private const val ARG_FIELD                = "f"
        private const val ARG_DIAL                 = "d"
        private const val ARG_SINF                 = "si"
        private const val ARG_XREF                 = "xr"
        private const val ARG_ANT                  = "an"
        private const val ARG_LSOURCE              = "ls"
        private const val ARG_EXAMPLE_SENTENCES    = "es"
        private const val ARG_EXAMPLE_TRANSLATIONS = "et"

        fun newInstance(entry: DictionarySearchElement) = EntryDetailBottomSheet().apply {
            arguments = bundleOf(
                ARG_WRITINGS_PRIO        to entry.writingsPrio,
                ARG_WRITINGS             to entry.writings,
                ARG_READINGS_PRIO        to entry.readingsPrio,
                ARG_READINGS             to entry.readings,
                ARG_FURIGANA             to entry.furigana,
                ARG_GLOSS                to entry.gloss,
                ARG_POS                  to entry.pos,
                ARG_MISC                 to entry.misc,
                ARG_FIELD                to entry.field,
                ARG_DIAL                 to entry.dial,
                ARG_SINF                 to entry.s_inf,
                ARG_XREF                 to entry.xref,
                ARG_ANT                  to entry.ant,
                ARG_LSOURCE              to entry.lsource,
                ARG_EXAMPLE_SENTENCES    to entry.example_sentences,
                ARG_EXAMPLE_TRANSLATIONS to entry.example_translations
            )
        }
    }
}
