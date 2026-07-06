/* Sumatora Dictionary
        Copyright (C) 2026 Nicolas Centa

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

import android.os.Bundle
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
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent
import org.happypeng.sumatora.android.sumatoradictionary.databinding.BottomSheetKanjiDetailBinding
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryKanjiInfo
import javax.inject.Inject

@AndroidEntryPoint
class KanjiDetailBottomSheet : BottomSheetDialogFragment() {

    @Inject
    lateinit var persistentDatabaseComponent: PersistentDatabaseComponent

    private val disposables = CompositeDisposable()

    private var _binding: BottomSheetKanjiDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = BottomSheetKanjiDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val character = requireArguments().getString(ARG_CHARACTER).orEmpty()
        binding.kanjiDetailCharacter.text = character

        disposables.add(
            Schedulers.io().scheduleDirect {
                val info = persistentDatabaseComponent.fetchKanjiInfo(character)
                AndroidSchedulers.mainThread().scheduleDirect {
                    if (_binding != null) {
                        if (info != null) buildInfo(info) else showNoData()
                    }
                }
            }
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
        disposables.clear()
        super.onDestroyView()
        _binding = null
    }

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    private fun addInfoRow(label: String, value: String) {
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.text_foreground_primary)
        val secondaryColor = ContextCompat.getColor(requireContext(), R.color.text_foreground_secondary)

        binding.kanjiDetailInfo.addView(TextView(context).apply {
            text = label
            textSize = 11f
            setTextColor(secondaryColor)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 10.dp()
            layoutParams = lp
        })
        binding.kanjiDetailInfo.addView(TextView(context).apply {
            text = value
            textSize = 15f
            setTextColor(primaryColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
    }

    private fun gradeLabel(grade: Int): String = when {
        grade in 1..6 -> "Grade $grade (kyōiku)"
        grade == 8    -> "Jōyō / Jinmeiyō"
        else          -> "Grade $grade"
    }

    private fun jlptLabel(jlpt: Int): String = when (jlpt) {
        4 -> "N5"; 3 -> "N4"; 2 -> "N3"; 1 -> "N1"
        else -> jlpt.toString()
    }

    private fun buildInfo(info: DictionaryKanjiInfo) {
        info.strokes?.let { addInfoRow("Stroke count", it.toString()) }
        info.grade?.let { addInfoRow("Grade", gradeLabel(it)) }
        info.jlpt?.let { addInfoRow("JLPT level", jlptLabel(it)) }
        info.freq?.let { addInfoRow("Frequency rank", it.toString()) }
        info.radical?.let { addInfoRow("Radical", it.toString()) }

        if (info.onReadings.isNotEmpty()) addInfoRow("On readings", info.onReadings.joinToString("、"))
        if (info.kunReadings.isNotEmpty()) addInfoRow("Kun readings", info.kunReadings.joinToString("、"))
        if (info.meanings.isNotEmpty()) addInfoRow("Meanings", info.meanings.joinToString(", "))
    }

    private fun showNoData() {
        addInfoRow("", "No kanji data available.")
    }

    companion object {
        private const val ARG_CHARACTER = "char"

        fun newInstance(character: String) = KanjiDetailBottomSheet().apply {
            arguments = bundleOf(ARG_CHARACTER to character)
        }
    }
}
