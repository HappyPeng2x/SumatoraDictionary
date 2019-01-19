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

import androidx.annotation.Nullable;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryEntry;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchResult;


public class DictionaryPagedListAdapter extends PagedListAdapter<DictionarySearchResult, DictionaryPagedListAdapter.DictionaryEntryItemViewHolder> {
    public interface ClickListener {
        void onClick(View aView, DictionarySearchResult aEntry);
    }

    private ClickListener m_bookmarkClickListener;

    public DictionaryPagedListAdapter() {
        super(DictionarySearchResult.DIFF_CALLBACK);
    }

    public void setBookmarkClickListener(ClickListener aListener) {
        m_bookmarkClickListener = aListener;
    }

    @Override
    public DictionaryEntryItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View view = layoutInflater.inflate(R.layout.cell_cards, parent, false);
        DictionaryEntryItemViewHolder holder = new DictionaryEntryItemViewHolder(view);
        holder.setBookmarkClickListener(m_bookmarkClickListener);
        return holder;
    }

    @Override
    public void onBindViewHolder(DictionaryEntryItemViewHolder holder, int position) {
        DictionarySearchResult entry = getItem(position);

        if (entry != null) {
            holder.bindTo(entry);
        }
    }

    static class DictionaryEntryItemViewHolder extends RecyclerView.ViewHolder {
        private TextView m_textViewView;
        private ImageButton m_bookmarkStar;

        private ClickListener m_bookmarkClickListener;

        public DictionaryEntryItemViewHolder(View itemView) {
            super(itemView);

            m_textViewView = (TextView) itemView.findViewById(R.id.text);
            m_bookmarkStar = (ImageButton) itemView.findViewById(R.id.bookmark_star);
        }

        public void setBookmarkClickListener(ClickListener aListener) {
            m_bookmarkClickListener = aListener;
        }

        public void bindTo(final DictionarySearchResult entry) {
            SpannableStringBuilder sb = new SpannableStringBuilder();
            int writingsLength = 0;
            int readingsLength = 0;
            int startPos = 0;

            sb.append(entry.entryOrder + " " + entry.seq + " ");
            writingsLength = writingsLength + sb.length();

            for (String w : entry.writingsPrio.split(" ")) {
                startPos = sb.length();

                sb.append(w);

                sb.setSpan(new BackgroundColorSpan(Color.parseColor("#ccffcc")),
                        startPos, sb.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                writingsLength = writingsLength + w.length();
            }

            if (writingsLength > 0) {
                sb.append(" ");

                writingsLength = writingsLength + 1;
            }

            sb.append(entry.writings);
            writingsLength = writingsLength + entry.writings.length();

            if (writingsLength > 0) {
                sb.append(" ");

                writingsLength = writingsLength + 1;
            }

            for (String r : entry.readingsPrio.split(" ")) {
                startPos = sb.length();

                sb.append(r);

                sb.setSpan(new BackgroundColorSpan(Color.parseColor("#ccffcc")),
                        startPos, sb.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                readingsLength = readingsLength + r.length();
            }

            if (readingsLength > 0) {
                sb.append(" ");

                readingsLength = readingsLength + 1;
            }

            sb.append(entry.readings);
            readingsLength = readingsLength + entry.readings.length();

            sb.append("\n");
            readingsLength = readingsLength + 1;

            sb.append(entry.gloss);

            sb.setSpan(new ForegroundColorSpan(Color.GRAY), writingsLength, writingsLength + readingsLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new RelativeSizeSpan(1.5f), 0, writingsLength + readingsLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            m_textViewView.setText(sb);

            if (m_bookmarkClickListener != null) {
                m_bookmarkStar.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        m_bookmarkClickListener.onClick(v, entry);
                    }
                });
            }

            if (entry.bookmarkFolder != null) {
                m_bookmarkStar.setImageResource(R.drawable.ic_baseline_star_24px);
            } else {
                m_bookmarkStar.setImageResource(R.drawable.ic_outline_star_border_24px);
            }
        }
    }
}
