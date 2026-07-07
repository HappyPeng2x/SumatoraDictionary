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
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent
import org.happypeng.sumatora.android.sumatoradictionary.databinding.BottomSheetEntryDetailBinding
import org.happypeng.sumatora.android.sumatoradictionary.db.EntryDetail
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings
import org.happypeng.sumatora.core.dict.DictionaryQueryResult
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering.TagSystem
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering.renderFuriganaSegments
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering.renderHeadword
import org.happypeng.sumatora.android.superrubyspan.tools.JapaneseText
import javax.inject.Inject

@AndroidEntryPoint
class EntryDetailBottomSheet : BottomSheetDialogFragment() {

    @Inject
    lateinit var persistentDatabaseComponent: PersistentDatabaseComponent

    private val disposables = CompositeDisposable()

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
        val primaryColor   = ContextCompat.getColor(ctx, R.color.text_foreground_primary)
        val secondaryColor = ContextCompat.getColor(ctx, R.color.text_foreground_secondary)

        val colors = DictionarySearchElementViewHolder.Colors(
            ContextCompat.getColor(ctx, R.color.text_background_primary),
            ContextCompat.getColor(ctx, R.color.text_background_primary_backup),
            ContextCompat.getColor(ctx, R.color.render_pos),
            tagColors,
            secondaryColor
        )

        val entryId = args.getLong(ARG_ENTRY_ID)
        val formId = args.getLong(ARG_FORM_ID, -1).let { if (it < 0) null else it }
        val isName = args.getBoolean(ARG_IS_NAME)
        val deinflectionLabel = args.getString(ARG_DEINFLECTION_LABEL)
        val scrollToSense = args.getInt(ARG_SCROLL_TO_SENSE, -1)

        disposables.add(
            Schedulers.io().scheduleDirect {
                val settings = persistentDatabaseComponent.database.persistentLanguageSettingsDao()
                    .getLanguageSettingsDirect(0)
                    ?: PersistentLanguageSettings().also { it.lang = PersistentLanguageSettings.LANG_DEFAULT }
                val detail = persistentDatabaseComponent.fetchEntryDetail(entryId, formId, isName, settings)
                AndroidSchedulers.mainThread().scheduleDirect {
                    if (_binding != null) {
                        render(detail, colors, primaryColor, secondaryColor, density,
                            deinflectionLabel, scrollToSense)
                    }
                }
            }
        )
    }

    private fun render(
        detail: EntryDetail,
        colors: DictionarySearchElementViewHolder.Colors,
        primaryColor: Int, secondaryColor: Int, density: Float,
        deinflectionLabel: String?, scrollToSense: Int
    ) {
        // Headword, matched/promoted reading (bold) plus any other readings the same kanji
        // spelling can take (smaller) - same treatment as the search-result list row, since
        // furigana alone only shows the matched reading and the forms table further down
        // requires scrolling to discover the rest. Optional priority star at the end.
        val headwordSb = SpannableStringBuilder(
            renderHeadword(detail.primaryText, detail.furiganaSegments, colors) { character ->
                KanjiDetailBottomSheet.newInstance(character).show(parentFragmentManager, "kanji_detail")
            }
        )
        detail.primaryReading?.let { reading ->
            headwordSb.append("   ")
            val start = headwordSb.length
            headwordSb.append(reading)
            headwordSb.setSpan(StyleSpan(Typeface.BOLD), start, headwordSb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            headwordSb.setSpan(ForegroundColorSpan(colors.pos), start, headwordSb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (altReading in detail.alternateReadings) {
            headwordSb.append(" ")
            val start = headwordSb.length
            headwordSb.append(altReading)
            headwordSb.setSpan(RelativeSizeSpan(0.85f), start, headwordSb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            headwordSb.setSpan(ForegroundColorSpan(colors.pos), start, headwordSb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.entryDetailHeadword.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        if (detail.isPriority) {
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

        if (!deinflectionLabel.isNullOrEmpty()) {
            val parent = binding.entryDetailHeadword.parent as? LinearLayout
            if (parent != null) {
                val index = parent.indexOfChild(binding.entryDetailHeadword)
                parent.addView(TextView(context).apply {
                    text = deinflectionLabel
                    textSize = 12f
                    setTextColor(secondaryColor)
                    setTypeface(typeface, Typeface.ITALIC)
                }, index)
            }
        }

        if (detail.pitchPatterns.isNotEmpty()) {
            renderPitchBadges(detail.pitchPatterns)
        }

        if (detail.isName) {
            buildNameTranslations(binding.entryDetailSenses, detail, primaryColor, density)
        } else {
            buildSenses(binding.entryDetailSenses, detail.senseGroups, primaryColor, secondaryColor, density)
            buildExamples(
                binding.entryDetailExamples, binding.entryDetailExamplesHeader, binding.entryDetailExamplesDivider,
                detail.examples, primaryColor, secondaryColor, density
            )
            buildForms(
                binding.entryDetailForms, binding.entryDetailFormsHeader, binding.entryDetailFormsDivider,
                detail.forms, primaryColor, secondaryColor, density
            )
        }

        if (scrollToSense > 0) {
            binding.root.post {
                val target = binding.entryDetailSenses.findViewWithTag<View>("sense_$scrollToSense")
                if (target != null) {
                    binding.root.scrollTo(0, target.top)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onDestroyView() {
        disposables.clear()
        super.onDestroyView()
        _binding = null
    }

    // Renders pitch-accent pattern badges (e.g. "[0]", "[2]") after the headword line.
    private fun renderPitchBadges(pitches: List<Int>) {
        val density = resources.displayMetrics.density
        val pitchColor = ContextCompat.getColor(requireContext(), R.color.tag_pos)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (4 * density).toInt()
            layoutParams = lp
        }
        for (p in pitches) {
            container.addView(TextView(context).apply {
                text = "[$p]"
                textSize = 11f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    setColor(pitchColor)
                    cornerRadius = 4f * density
                }
                val h = (5 * density).toInt()
                val v = (2 * density).toInt()
                setPadding(h, v, h, v)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = (4 * density).toInt()
                layoutParams = lp
            })
        }

        val parent = binding.entryDetailHeadword.parent as? LinearLayout ?: return
        val index = parent.indexOfChild(binding.entryDetailHeadword)
        parent.addView(container, index + 1)
    }

    // Opens the cross-referenced entry in a new bottom sheet, scrolling to its target sense if
    // given. target_entry_id/preview_text are already resolved at pipeline build time (per
    // Database.md), so this is a plain navigation - no live lookup needed.
    private fun openXrefTarget(targetEntryId: Long, targetSenseNumber: Int?) {
        if (isAdded) {
            val sheet = newInstance(targetEntryId, null, false, null, targetSenseNumber)
            sheet.show(parentFragmentManager, "entry_detail")
        }
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
    private fun makeTagChip(code: String, color: Int, density: Float): TextView {
        val bg = GradientDrawable().apply {
            setColor(color)
            cornerRadius = 4f * density
        }
        return TextView(context).apply {
            text = TagSystem.label(code)
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

    // Like makeExtraBox, but for a single cross-reference/antonym: tappable (opens the target
    // entry) when target_entry_id resolved at build time, plain text otherwise.
    private fun makeRefBox(
        ref: EntryDetail.Xref, label: String,
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
                val targetEntryId = ref.targetEntryId
                val previewSuffix = ref.previewText?.let { " ($it)" }.orEmpty()
                if (targetEntryId != null) {
                    val sb = SpannableStringBuilder(ref.displayText)
                    sb.setSpan(ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.border_xref)),
                        0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(UnderlineSpan(), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            openXrefTarget(targetEntryId, ref.targetSenseNumber)
                        }
                    }, 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.append(previewSuffix)
                    text = sb
                    movementMethod = android.text.method.LinkMovementMethod.getInstance()
                } else {
                    text = ref.displayText + previewSuffix
                }
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

    private fun buildNameTranslations(
        container: LinearLayout, detail: EntryDetail, primaryColor: Int, density: Float
    ) {
        fun Int.dp() = (this * density).toInt()
        val posColor = ContextCompat.getColor(requireContext(), R.color.tag_pos)

        if (detail.nameTypeCodes.isNotEmpty()) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = 8.dp()
                layoutParams = lp
            }
            for (code in detail.nameTypeCodes) row.addView(makeTagChip(code, posColor, density))
            container.addView(row)
        }

        if (detail.translations.isNotEmpty()) {
            container.addView(TextView(context).apply {
                text = detail.translations.joinToString(", ")
                textSize = 15f
                setTextColor(primaryColor)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = 4.dp()
                layoutParams = lp
            })
        }
    }

    private fun buildSenses(
        container: LinearLayout, senseGroups: List<EntryDetail.SenseGroup>,
        primaryColor: Int, secondaryColor: Int, density: Float
    ) {
        if (senseGroups.isEmpty()) return

        val totalSenses = senseGroups.sumOf { it.senses.size }
        val showBullet = senseGroups.size > 1

        fun Int.dp() = (this * density).toInt()

        val posColor    = ContextCompat.getColor(requireContext(), R.color.tag_pos)
        val miscColor   = ContextCompat.getColor(requireContext(), R.color.tag_register)
        val fieldColor  = ContextCompat.getColor(requireContext(), R.color.tag_domain)
        val dialColor   = ContextCompat.getColor(requireContext(), R.color.tag_dialect)
        val noteColor   = ContextCompat.getColor(requireContext(), R.color.border_note)
        val xrefColor   = ContextCompat.getColor(requireContext(), R.color.border_xref)
        val antColor    = ContextCompat.getColor(requireContext(), R.color.border_ant)
        val lsColor     = ContextCompat.getColor(requireContext(), R.color.border_lsource)

        for ((groupIdx, group) in senseGroups.withIndex()) {
            val hasAnyTags = group.posTagCodes.isNotEmpty() || group.miscTagCodes.isNotEmpty() ||
                             group.fieldTagCodes.isNotEmpty() || group.dialectTagCodes.isNotEmpty()

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

                for (code in group.posTagCodes)     headerRow.addView(makeTagChip(code, posColor,   density))
                for (code in group.miscTagCodes)    headerRow.addView(makeTagChip(code, miscColor,  density))
                for (code in group.fieldTagCodes)   headerRow.addView(makeTagChip(code, fieldColor, density))
                for (code in group.dialectTagCodes) headerRow.addView(makeTagChip(code, dialColor,  density))

                container.addView(headerRow)
            }

            group.restrictionLabel?.let { label ->
                container.addView(TextView(context).apply {
                    text = getString(R.string.restricted_to_format, label)
                    textSize = 12f
                    setTextColor(secondaryColor)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.bottomMargin = 2.dp()
                    layoutParams = lp
                })
            }

            for (sense in group.senses) {
                val glossView = TextView(context).apply {
                    tag = "sense_${sense.displayIndex}"
                    val sb = SpannableStringBuilder()
                    if (totalSenses > 1) {
                        val prefix = "${circledDigit(sense.displayIndex)} "
                        sb.append(prefix)
                        sb.setSpan(
                            StyleSpan(Typeface.BOLD), 0, prefix.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    sb.append(sense.glossText.orEmpty())
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

                for (note in sense.notes) {
                    container.addView(makeExtraBox(note, "Note", noteColor, primaryColor, density))
                }

                for (xr in sense.xrefs) {
                    container.addView(makeRefBox(xr, "See also", xrefColor, primaryColor, density))
                }

                for (a in sense.antonyms) {
                    container.addView(makeRefBox(a, "Antonym", antColor, primaryColor, density))
                }

                for (ls in sense.languageSources) {
                    val display = buildString {
                        append(langName(ls.lang))
                        if (!ls.text.isNullOrEmpty()) append(": ${ls.text}")
                        if (ls.wasei) append(" (wasei-eigo)")
                    }
                    container.addView(makeExtraBox(display, "Language of Origin", lsColor, primaryColor, density))
                }

                for (example in sense.examples) {
                    container.addView(buildExampleBox(example, primaryColor, secondaryColor, density, 8.dp()))
                }
            }
        }
    }

    // Single bordered example box (Japanese sentence + translation), reused both for the
    // entry-level fallback section and for examples nested directly under a sense.
    private fun buildExampleBox(
        example: EntryDetail.Example, primaryColor: Int, secondaryColor: Int, density: Float,
        topMarginPx: Int
    ): View {
        val exColor = ContextCompat.getColor(requireContext(), R.color.border_example)
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
            lp.topMargin = topMarginPx
            layoutParams = lp
        }

        // Japanese sentence with pre-split furigana segments
        box.addView(TextView(context).apply {
            val sb = SpannableStringBuilder()
            renderFuriganaSegments(sb, example.segments, 0, null)
            val token = example.matchedText
            if (!token.isNullOrEmpty()) {
                val start = sb.toString().indexOf(token)
                if (start >= 0) {
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, start + token.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            text = sb
            textSize = 14f
            setTextColor(primaryColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })

        // Translation
        box.addView(TextView(context).apply {
            text = example.translation.orEmpty()
            textSize = 12f
            setTextColor(secondaryColor)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (2 * density).toInt()
            layoutParams = lp
        })

        return box
    }

    private fun buildExamples(
        container: LinearLayout, header: TextView, divider: View,
        examples: List<EntryDetail.Example>,
        primaryColor: Int, secondaryColor: Int, density: Float
    ) {
        if (examples.isEmpty()) return

        divider.visibility   = View.VISIBLE
        header.visibility    = View.VISIBLE
        container.visibility = View.VISIBLE

        val dp8 = (8 * density).toInt()
        for ((i, example) in examples.withIndex()) {
            container.addView(buildExampleBox(example, primaryColor, secondaryColor, density, if (i == 0) 0 else dp8))
        }
    }

    // Small colored circular badge for one forms-table cell: 優(green)/可(grey)/稀(purple) for
    // primary/common/rare tiers - our score/is_common data can only bucket this coarsely, so
    // e.g. multiple equally-irregular kanji spellings all render the same "rare" badge.
    //
    // Wrapped in a FrameLayout because TableRow ignores a cell's own requested width during
    // layout and stretches every cell in a column to match that column's widest cell (here, the
    // kanji header text) - without the wrapper the badge itself gets stretched into an oval whose
    // width tracks the column's kanji spelling length instead of staying a fixed circle.
    private fun badgeCell(tier: String, density: Float): View {
        val (symbol, colorRes) = when (tier) {
            "primary" -> "優" to R.color.tag_dialect
            "rare"    -> "稀" to R.color.tag_domain
            else      -> "可" to R.color.tag_pos
        }
        val size = (22 * density).toInt()
        val badge = TextView(context).apply {
            text = symbol
            textSize = 11f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(requireContext(), colorRes))
                shape = GradientDrawable.OVAL
            }
            layoutParams = FrameLayout.LayoutParams(size, size, android.view.Gravity.CENTER)
        }
        return FrameLayout(requireContext()).apply {
            addView(badge)
            layoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT
            ).apply {
                val m = (3 * density).toInt()
                setMargins(m, m, m, m)
            }
        }
    }

    // "Forms" table: every kanji+reading combination for the entry, pivoted into a grid (columns
    // = distinct written forms, rows = distinct readings, plus a "∅" column for kana-only
    // readings with no kanji pairing at all).
    private fun buildForms(
        container: LinearLayout, header: TextView, divider: View,
        forms: List<EntryDetail.FormRow>, primaryColor: Int, secondaryColor: Int, density: Float
    ) {
        if (forms.isEmpty()) return

        divider.visibility   = View.VISIBLE
        header.visibility    = View.VISIBLE
        container.visibility = View.VISIBLE

        fun Int.dp() = (this * density).toInt()
        val kanjilessColumn = "∅"

        val columns = LinkedHashSet<String>()
        val rows = LinkedHashMap<String, MutableMap<String, String>>()
        for (f in forms) {
            if (f.isKanjiless) {
                rows.getOrPut(f.text) { LinkedHashMap() }[kanjilessColumn] = f.tier
            } else {
                columns.add(f.text)
                rows.getOrPut(f.reading ?: f.text) { LinkedHashMap() }[f.text] = f.tier
            }
        }
        val columnList = columns.toMutableList()
        if (rows.values.any { it.containsKey(kanjilessColumn) }) columnList.add(kanjilessColumn)

        fun headerCell(text: String) = TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(secondaryColor)
            gravity = android.view.Gravity.CENTER
            setPadding(8.dp(), 4.dp(), 8.dp(), 4.dp())
        }

        val table = TableLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        table.addView(TableRow(context).apply {
            addView(headerCell(""))
            for (col in columnList) addView(headerCell(col))
        })

        for ((rowKey, cells) in rows) {
            table.addView(TableRow(context).apply {
                addView(TextView(context).apply {
                    text = rowKey
                    textSize = 14f
                    setTextColor(primaryColor)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, 4.dp(), 8.dp(), 4.dp())
                })
                for (col in columnList) {
                    val tier = cells[col]
                    addView(
                        if (tier != null) badgeCell(tier, density)
                        else TextView(context).apply { setPadding(8.dp(), 4.dp(), 8.dp(), 4.dp()) }
                    )
                }
            })
        }

        container.addView(table)
    }

    companion object {
        private const val ARG_ENTRY_ID             = "entry_id"
        private const val ARG_FORM_ID              = "form_id"
        private const val ARG_IS_NAME              = "is_name"
        private const val ARG_SCROLL_TO_SENSE      = "scroll_sense"
        private const val ARG_DEINFLECTION_LABEL   = "dl"

        fun newInstance(entryId: Long, formId: Long?, isName: Boolean,
                        deinflectionLabel: String?, scrollToSense: Int? = null) =
            EntryDetailBottomSheet().apply {
            arguments = bundleOf(
                ARG_ENTRY_ID           to entryId,
                ARG_FORM_ID            to (formId ?: -1L),
                ARG_IS_NAME            to isName,
                ARG_DEINFLECTION_LABEL to deinflectionLabel,
                ARG_SCROLL_TO_SENSE    to (scrollToSense ?: -1)
            )
        }

        fun newInstance(entry: DictionaryQueryResult, scrollToSense: Int? = null) =
            newInstance(entry.entryId, entry.formId, "name" == entry.matchKind,
                entry.deinflectionLabel, scrollToSense)
    }
}
