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
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.tools.ExampleWord;
import org.json.JSONArray;
import org.json.JSONException;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.happypeng.sumatora.android.sumatoradictionary.viewholder.tools.ExamplesRenderingKt;

import java.util.HashMap;
import java.util.Iterator;

import kotlin.sequences.Sequence;
import se.fekete.furiganatextview.furiganaview.FuriganaTextView;

public class DictionarySearchElementViewHolder extends RecyclerView.ViewHolder {
    public static class Status {
        public HashMap<String, String> entities;
    }

    private final Status m_status;

    public interface ClickListener {
        void onClick(View aView, DictionarySearchElement aEntry);
    }

    private final TextView m_textViewView;
    private final ImageButton m_bookmarkStar;
    private final FrameLayout m_cardView;

    private final FuriganaTextView m_furiganaTextView;

    private ClickListener m_bookmarkClickListener;

    public DictionarySearchElementViewHolder(View itemView, final Status aStatus) {
        super(itemView);

        m_textViewView = itemView.findViewById(R.id.word_card_text);
        m_bookmarkStar = itemView.findViewById(R.id.word_card_bookmark_icon);
        m_cardView = itemView.findViewById(R.id.word_card_view);
        m_furiganaTextView = itemView.findViewById(R.id.word_card_examples);

        m_status = aStatus;
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
                    if (m_status.entities != null &&
                        m_status.entities.containsKey(s)) {
                        sb.append(m_status.entities.get(s));
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

    public void disableBookmarkButton() {
        m_bookmarkStar.setVisibility(View.GONE);
    }

    public void setBookmarkClickListener(ClickListener aListener) {
        m_bookmarkClickListener = aListener;
    }

    private String renderEntryExamples(final DictionarySearchElement aEntry) {
        StringBuilder exampleSb = new StringBuilder();

        if (aEntry.getExampleSentences() != null) {
            try {
                JSONArray sentences = new JSONArray(aEntry.getExampleSentences());
                JSONArray translations = new JSONArray(aEntry.getExampleTranslations());

                for (int i = 0; i < sentences.length(); i++) {
                    exampleSb.append(ExamplesRenderingKt.renderSentence(sentences.getString(i)));
                    exampleSb.append("\n");
                    exampleSb.append(translations.getString(i));
                    exampleSb.append(" ");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return exampleSb.toString();
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

         return sb;
    }

    public void bindTo(final DictionarySearchElement entry) {
        if (!entry.getLang().equals(entry.getLangSetting())) {
            m_cardView.setBackgroundColor(Color.LTGRAY);
        } else {
            m_cardView.setBackgroundColor(Color.WHITE);
        }

        m_textViewView.setText(renderEntry(entry));

        String examples = renderEntryExamples(entry);

        if (examples.length() > 0) {
            m_furiganaTextView.setFuriganaText(examples);
            m_furiganaTextView.setVisibility(View.VISIBLE);
        } else {
            m_furiganaTextView.setVisibility(View.GONE);
        }

        if (m_bookmarkClickListener != null) {
            m_bookmarkStar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    m_bookmarkClickListener.onClick(v, entry);
                }
            });
        }

        if (entry.getBookmark() != 0) {
            m_bookmarkStar.setImageResource(R.drawable.ic_outline_bookmark_24px);
        } else {
            m_bookmarkStar.setImageResource(R.drawable.ic_outline_bookmark_border_24px);
        }
    }
}