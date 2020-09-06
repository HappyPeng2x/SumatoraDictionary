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
package org.happypeng.sumatora.android.sumatoradictionary.viewholder

import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableEmitter
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.databinding.WordCardBinding
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.operator.ScanConcatMap
import org.happypeng.sumatora.android.superrubyspan.tools.JapaneseText
import org.json.JSONArray
import org.json.JSONException
import java.util.*
import java.util.concurrent.TimeUnit

class DictionarySearchElementViewHolder(private val wordCardBinding: WordCardBinding,
                                        entities: HashMap<String, String>,
                                        disableBookmarkButton: Boolean,
                                        disableMemoEdit: Boolean,
                                        commitConsumer: (Long, Long, String?) -> Unit) : RecyclerView.ViewHolder(wordCardBinding.wordCardView) {
    private val entities: HashMap<String, String>?
    private var textWatcher: TextWatcher? = null

    open class CommitCommand(val seq: Long,
                             val memo: String,
                             val bookmark: Boolean)

    private class ImmediateCommitCommand(seq: Long, memo: String, bookmark: Boolean) : CommitCommand(seq, memo, bookmark)

    val commitSubject: Subject<CommitCommand> = PublishSubject.create()

    private fun openMemo() {
        wordCardBinding.wordCardMemo.visibility = View.VISIBLE
        wordCardBinding.wordCardMemoIcon.visibility = View.GONE
        wordCardBinding.wordCardDeleteMemoIcon.visibility = View.VISIBLE
    }

    private fun closeMemo() {
        wordCardBinding.wordCardMemo.visibility = View.GONE
        wordCardBinding.wordCardMemoIcon.visibility = View.VISIBLE
        wordCardBinding.wordCardDeleteMemoIcon.visibility = View.GONE
        wordCardBinding.wordCardMemo.setText("")
    }

    private fun renderJSONArray(aArray: JSONArray?, aSeparator: String?,
                                aResolveEntities: Boolean): String {
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

    private fun renderEntry(aEntry: DictionarySearchElement): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        var writingsCount = 0
        for (w in aEntry.getWritingsPrio().split(" ".toRegex()).toTypedArray()) {
            if (w.length > 0) {
                if (writingsCount > 0) {
                    sb.append("・")
                    sb.setSpan(ForegroundColorSpan(Color.GRAY), sb.length - 1, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                sb.append(w)
                sb.setSpan(BackgroundColorSpan(Color.parseColor("#ccffcc")),
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
                sb.setSpan(BackgroundColorSpan(Color.parseColor("#ccffcc")),
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
                    val p = renderJSONArray(pos.getJSONArray(glossCount), ", ", true)
                    if (p.length > 0) {
                        sb.append(p)
                        sb.setSpan(ForegroundColorSpan(Color.parseColor("#3333aa")),
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

    fun bindTo(entry: DictionarySearchElement) {
        if (textWatcher != null) {
            wordCardBinding.wordCardMemo.removeTextChangedListener(textWatcher)
            textWatcher = null
        }
        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                commitSubject.onNext(CommitCommand(entry.seq,
                        s.toString(),
                        entry.getBookmark() > 0))
            }
        }
        if (entry.getLang() != entry.langSetting) {
            wordCardBinding.wordCardView.setBackgroundColor(Color.LTGRAY)
        } else {
            wordCardBinding.wordCardView.setBackgroundColor(Color.WHITE)
        }
        wordCardBinding.wordCardText.text = renderEntry(entry)
        if (entry.getBookmark() != 0L) {
            wordCardBinding.wordCardBookmarkIcon.setImageResource(R.drawable.ic_outline_bookmark_24px)
        } else {
            wordCardBinding.wordCardBookmarkIcon.setImageResource(R.drawable.ic_outline_bookmark_border_24px)
        }
        wordCardBinding.wordCardBookmarkIcon.setOnClickListener(View.OnClickListener { v: View? ->
            commitSubject.onNext(ImmediateCommitCommand(entry.seq,
                    wordCardBinding.wordCardMemo.editableText.toString(),
                    entry.getBookmark() <= 0))
        })
        val memo = entry.getMemo()
        if (memo != null && "" != memo) {
            openMemo()
            wordCardBinding.wordCardMemo.setText(memo)
        } else {
            closeMemo()
        }
        wordCardBinding.wordCardDeleteMemoIcon.setOnClickListener { v: View? ->
            wordCardBinding.wordCardMemo.setText("")
            closeMemo()
        }
        wordCardBinding.wordCardMemoIcon.setOnClickListener { v: View? -> openMemo() }
        wordCardBinding.wordCardMemo.addTextChangedListener(textWatcher)
        wordCardBinding.wordCardText.requestFocus()
    }

    init {
        this.entities = entities
        if (disableBookmarkButton) {
            wordCardBinding.wordCardBookmarkIcon.visibility = View.GONE
        }
        if (disableMemoEdit) {
            wordCardBinding.wordCardMemoIcon.visibility = View.GONE
        }
        commitSubject.compose(ScanConcatMap<CommitCommand, CommitCommand> {
            lastStatus: CommitCommand?, newUpstream: CommitCommand ->
            Observable.create { emitter: ObservableEmitter<CommitCommand> ->
                if (lastStatus != null && newUpstream.seq != lastStatus.seq) {
                    emitter.onNext(ImmediateCommitCommand(lastStatus.seq,
                            lastStatus.memo, lastStatus.bookmark))
                }
                emitter.onNext(newUpstream)
                emitter.onComplete()
            }
        }).debounce { command: CommitCommand? ->
            if (command is ImmediateCommitCommand) {
                return@debounce Observable.just(true)
            } else {
                return@debounce Observable.just(true).delay(500, TimeUnit.MILLISECONDS)
            }
        }.subscribe { command: CommitCommand ->
            commitConsumer.invoke(command.seq,
                    if (command.bookmark) 1 else 0.toLong(), command.memo)
        }
    }
}