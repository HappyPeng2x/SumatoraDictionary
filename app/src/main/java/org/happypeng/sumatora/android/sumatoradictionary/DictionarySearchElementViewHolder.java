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

/*
    private SpannableStringBuilder renderEntry(final DictionarySearchElement aEntry) {
        SpannableStringBuilder sb = new SpannableStringBuilder();



        return sb;
    }
*/

     private SpannableStringBuilder renderEntry(final DictionarySearchElement aEntry) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int writingsLength = 0;
        int readingsLength = 0;
        int startPos = 0;

        for (String w : aEntry.getWritingsPrio().split(" ")) {
            startPos = sb.length();

            if (w.length() > 0) {
                sb.append(w);

                sb.setSpan(new BackgroundColorSpan(Color.parseColor("#ccffcc")),
                        startPos, sb.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                writingsLength = writingsLength + w.length();
            }
        }

        if (writingsLength > 0) {
            sb.append(" ");

            writingsLength = writingsLength + 1;
        }

        if (aEntry.getWritings().length() > 0) {
            sb.append(aEntry.getWritings());
            writingsLength = writingsLength + aEntry.getWritings().length();
        }

        if (writingsLength > 0) {
            sb.append(" ");

            writingsLength = writingsLength + 1;
        }

        for (String r : aEntry.getReadingsPrio().split(" ")) {
            startPos = sb.length();

            if (r.length() > 0) {
                sb.append(r);

                sb.setSpan(new BackgroundColorSpan(Color.parseColor("#ccffcc")),
                        startPos, sb.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                readingsLength = readingsLength + r.length();
            }
        }

        if (readingsLength > 0) {
            sb.append(" ");

            readingsLength = readingsLength + 1;
        }

        if (aEntry.getReadings().length() > 0) {
            sb.append(aEntry.getReadings());
            readingsLength = readingsLength + aEntry.getReadings().length();
        }

        sb.append("\n");
        readingsLength = readingsLength + 1;

        if (aEntry.getGloss().length() > 0) {
            sb.append(aEntry.getGloss());

            sb.setSpan(new ForegroundColorSpan(Color.GRAY), writingsLength, writingsLength + readingsLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new RelativeSizeSpan(1.5f), 0, writingsLength + readingsLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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