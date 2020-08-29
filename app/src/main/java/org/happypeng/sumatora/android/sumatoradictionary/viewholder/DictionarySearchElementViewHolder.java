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

package org.happypeng.sumatora.android.sumatoradictionary.viewholder;

import android.graphics.Color;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.databinding.WordCardBinding;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.operator.ScanConcatMap;
import org.happypeng.sumatora.android.superrubyspan.tools.JapaneseText;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

public class DictionarySearchElementViewHolder extends RecyclerView.ViewHolder  {
    private final HashMap<String, String> entities;

    private TextWatcher textWatcher;

    private static class CommitCommand {
        final private long seq;
        final private String memo;
        final private boolean bookmark;

        public CommitCommand(final long seq,
                             final String memo,
                             final boolean bookmark) {
            this.seq = seq;
            this.memo = memo;
            this.bookmark = bookmark;
        }

        public long getSeq() { return seq; }
        public String getMemo() { return memo; }
        public boolean getBookmark() { return bookmark; }
    }

    private static class ImmediateCommitCommand  extends CommitCommand {
        public ImmediateCommitCommand(long seq, String memo, boolean bookmark) {
            super(seq, memo, bookmark);
        }
    }

    final Subject<CommitCommand> commitSubject = PublishSubject.create();

    public interface CommitConsumer {
        void commit(long seq, long bookmark, String memo);
    }

    private final WordCardBinding wordCardBinding;

    private void openMemo() {
        wordCardBinding.wordCardMemo.setVisibility(View.VISIBLE);

        wordCardBinding.wordCardMemoIcon.setVisibility(View.GONE);
        wordCardBinding.wordCardDeleteMemoIcon.setVisibility(View.VISIBLE);
    }

    private void closeMemo() {
        wordCardBinding.wordCardMemo.setVisibility(View.GONE);

        wordCardBinding.wordCardMemoIcon.setVisibility(View.VISIBLE);
        wordCardBinding.wordCardDeleteMemoIcon.setVisibility(View.GONE);

        wordCardBinding.wordCardMemo.setText("");
    }

    public DictionarySearchElementViewHolder(final @NonNull WordCardBinding wordCardBinding,
                                             final @NonNull HashMap<String, String> entities,
                                             final boolean disableBookmarkButton,
                                             final boolean disableMemoEdit,
                                             final @NonNull CommitConsumer commitConsumer) {
        super(wordCardBinding.wordCardView);

        this.wordCardBinding = wordCardBinding;
        this.entities = entities;

        if (disableBookmarkButton) {
            wordCardBinding.wordCardBookmarkIcon.setVisibility(View.GONE);
        }

        if (disableMemoEdit) {
            wordCardBinding.wordCardMemoIcon.setVisibility(View.GONE);
        }

        commitSubject.compose(new ScanConcatMap<>(new ScanConcatMap.ScanOperator<CommitCommand, CommitCommand>() {
            @Override
            public Observable<CommitCommand> apply(CommitCommand lastStatus, CommitCommand newUpstream) {
                return Observable.create(emitter -> {
                    if (lastStatus != null && newUpstream.seq != lastStatus.seq) {
                        emitter.onNext(new ImmediateCommitCommand(lastStatus.seq,
                                lastStatus.memo, lastStatus.bookmark));
                    }

                    emitter.onNext(newUpstream);
                    emitter.onComplete();
                });
            }
        })).debounce(command -> {
            if (command instanceof ImmediateCommitCommand) {
                return Observable.just(true);
            } else {
                return Observable.just(true).delay(500, TimeUnit.MILLISECONDS);
            }
        }).subscribe(command -> commitConsumer.commit(command.seq,
                command.bookmark ? 1 : 0, command.memo));
    }

    @NonNull
    private String renderJSONArray(final JSONArray aArray, String aSeparator,
                                   boolean aResolveEntities) {
        if (aArray == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        try {
            for (int i = 0; i < aArray.length(); i++) {
                String s = aArray.getString(i);

                if (sb.length() > 0 && aSeparator != null) {
                    sb.append(aSeparator);
                }

                if (aResolveEntities) {
                    if (entities != null &&
                            entities.containsKey(s)) {
                        sb.append(entities.get(s));
                    } else {
                        System.err.println("Could not resolve entity: " + s);
                    }
                } else {
                    sb.append(s);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return sb.toString();
    }

    private SpannableStringBuilder renderEntry(final DictionarySearchElement aEntry) {
        SpannableStringBuilder sb = new SpannableStringBuilder();

        int writingsCount = 0;

        for (String w : aEntry.getWritingsPrio().split(" ")) {
            if (w.length() > 0) {
                if (writingsCount > 0) {
                    sb.append("・");
                    sb.setSpan(new ForegroundColorSpan(Color.GRAY), sb.length() - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                sb.append(w);

                sb.setSpan(new BackgroundColorSpan(Color.parseColor("#ccffcc")),
                        sb.length() - w.length(), sb.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                writingsCount = writingsCount + 1;
            }
        }

        for (String w : aEntry.getWritings().split(" ")) {
            if (w.length() > 0) {
                if (writingsCount > 0) {
                    sb.append("・");
                    sb.setSpan(new ForegroundColorSpan(Color.GRAY), sb.length() - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                sb.append(w);

                writingsCount = writingsCount + 1;
            }
        }

        if (writingsCount > 0) {
            sb.append(" ");
        }

        sb.setSpan(new RelativeSizeSpan(1.4f), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        sb.append("【");
        sb.setSpan(new ForegroundColorSpan(Color.GRAY), sb.length()-1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int readingsCount = 0;

        for (String r : aEntry.getReadingsPrio().split(" ")) {
            if (r.length() > 0) {
                if (readingsCount > 0) {
                    sb.append("・");
                    sb.setSpan(new ForegroundColorSpan(Color.GRAY), sb.length() - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                sb.append(r);

                sb.setSpan(new BackgroundColorSpan(Color.parseColor("#ccffcc")),
                        sb.length() - r.length(), sb.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                readingsCount = readingsCount + 1;
            }
        }

        for (String r : aEntry.getReadings().split(" ")) {
            if (r.length() > 0) {
                if (readingsCount > 0) {
                    sb.append("・");
                    sb.setSpan(new ForegroundColorSpan(Color.GRAY), sb.length() - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                sb.append(r);

                readingsCount = readingsCount + 1;
            }
        }

        sb.append("】");
        sb.setSpan(new ForegroundColorSpan(Color.GRAY), sb.length()-1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("　");

        int glossCount = 0;

        try {
            JSONArray gloss = new JSONArray(aEntry.getGloss());
            JSONArray pos = null;

            String posStr = aEntry.getPos();

            if (posStr != null) {
                pos = new JSONArray(posStr);
            }

            for (int i = 0; i < gloss.length(); i++) {
                if (glossCount > 0) {
                    sb.append("　");
                }

                String prefix = Integer.toString(glossCount + 1) + ". ";

                sb.append(prefix);

                sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                        sb.length() - prefix.length(), sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                if (pos != null && glossCount < pos.length()) {
                    String p = renderJSONArray(pos.getJSONArray(glossCount), ", ", true);

                    if (p.length() > 0) {
                        sb.append(p);

                        sb.setSpan(new ForegroundColorSpan(Color.parseColor("#3333aa")),
                                sb.length() - p.length(), sb.length(),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                        sb.append(" ");
                    }
                }

                sb.append(gloss.getString(i));

                glossCount = glossCount + 1;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            final String exampleSentences = aEntry.getExampleSentences();
            final String exampleTranslations = aEntry.getExampleTranslations();

            if (exampleSentences != null &&
                    exampleTranslations != null) {
                final JSONArray exampleSentencesArray = new JSONArray(aEntry.getExampleSentences());
                final JSONArray exampleTranslationsArray = new JSONArray(aEntry.getExampleTranslations());

                for (int i = 0; i < exampleSentencesArray.length(); i++) {
                    if (i == 0) {
                        sb.append("\n\n");
                        sb.setSpan(new RelativeSizeSpan(0.3f), sb.length() - 2, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    if (i < exampleTranslationsArray.length()) {
                        JapaneseText.spannifyWithFurigana(sb, "→ " + exampleSentencesArray.getString(i), 0.9f);
                    }

                    sb.append(" ");
                    sb.append(exampleTranslationsArray.getString(i));

                    if (i + 1 < exampleSentencesArray.length()) {
                        sb.append("\n");
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return sb;
    }

    public void bindTo(final DictionarySearchElement entry) {
        if (textWatcher != null) {
            wordCardBinding.wordCardMemo.removeTextChangedListener(textWatcher);
            textWatcher = null;
        }

        textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                commitSubject.onNext(new CommitCommand(entry.seq,
                        s.toString(),
                        entry.getBookmark() > 0));
            }
        };

        if (!entry.getLang().equals(entry.getLangSetting())) {
            wordCardBinding.wordCardView.setBackgroundColor(Color.LTGRAY);
        } else {
            wordCardBinding.wordCardView.setBackgroundColor(Color.WHITE);
        }

        wordCardBinding.wordCardText.setText(renderEntry(entry));

        if (entry.getBookmark() != 0) {
            wordCardBinding.wordCardBookmarkIcon.setImageResource(R.drawable.ic_outline_bookmark_24px);
        } else {
            wordCardBinding.wordCardBookmarkIcon.setImageResource(R.drawable.ic_outline_bookmark_border_24px);
        }

        wordCardBinding.wordCardBookmarkIcon.setOnClickListener((View.OnClickListener) v -> {
            commitSubject.onNext(new ImmediateCommitCommand(entry.seq,
                    wordCardBinding.wordCardMemo.getEditableText().toString(),
                    !(entry.getBookmark() > 0)));
        });

        final String memo = entry.getMemo();

        if (memo != null && !"".equals(memo)) {
            openMemo();

            wordCardBinding.wordCardMemo.setText(memo);
        } else {
            closeMemo();
        }

        wordCardBinding.wordCardDeleteMemoIcon.setOnClickListener(v -> {
            wordCardBinding.wordCardMemo.setText("");

            closeMemo();
        });

        wordCardBinding.wordCardMemoIcon.setOnClickListener(v -> {
            openMemo();
        });

        wordCardBinding.wordCardMemo.addTextChangedListener(textWatcher);

        wordCardBinding.wordCardText.requestFocus();
    }
}