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

package org.happypeng.sumatora.android.sumatoradictionary;

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

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Settings;
import org.happypeng.sumatora.android.sumatoradictionary.model.DictionarySearchFragmentModel;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Set;

public class DictionarySearchElementViewHolder extends RecyclerView.ViewHolder {
    public static class Status {
        public String lang;
    }

    private final Status m_status;

    public interface ClickListener {
        void onClick(View aView, DictionarySearchElement aEntry);
    }

    private final TextView m_textViewView;
    private final ImageButton m_bookmarkStar;
    private final FrameLayout m_cardView;

    private ClickListener m_bookmarkClickListener;

    DictionarySearchElementViewHolder(View itemView, final Status aStatus) {
        super(itemView);

        m_textViewView = (TextView) itemView.findViewById(R.id.word_card_text);
        m_bookmarkStar = (ImageButton) itemView.findViewById(R.id.word_card_bookmark_icon);
        m_cardView = (FrameLayout) itemView.findViewById(R.id.word_card_view);

        m_status = aStatus;
    }

    void disableBookmarkButton() {
        m_bookmarkStar.setVisibility(View.GONE);
    }

    void setBookmarkClickListener(ClickListener aListener) {
        m_bookmarkClickListener = aListener;
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

        for (String g : aEntry.getGloss().split("\n")) {
            if (g.length() > 0) {
                if (glossCount > 0) {
                    sb.append("　");
                }

                int dotIndex = g.indexOf(".");

                if (dotIndex >= 0) {
                    sb.append(g.substring(0, dotIndex + 1));
                    sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                            sb.length() - dotIndex - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    if (g.length() > dotIndex + 1) {
                        sb.append(g.substring(dotIndex + 1));
                    }
                } else {
                    sb.append(g);
                }

                glossCount = glossCount + 1;
            }
        }

        return sb;
    }

    void bindTo(final DictionarySearchElement entry) {
        if (!entry.lang.equals(m_status.lang)) {
            m_cardView.setBackgroundColor(Color.LTGRAY);
        } else {
            m_cardView.setBackgroundColor(Color.WHITE);
        }

        m_textViewView.setText(renderEntry(entry));

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