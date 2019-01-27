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

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElementBase;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElementDiffUtil;

import java.util.HashMap;

public class DictionaryPagedListAdapter<T extends DictionarySearchElementBase> extends PagedListAdapter<T, DictionaryPagedListAdapter.DictionaryEntryItemViewHolder> {
    // private HashMap<Long, Long> m_bookmarks;

    public interface ClickListener {
        void onClick(View aView, DictionarySearchElementBase aEntry);
    }

    private ClickListener m_bookmarkClickListener;

    public DictionaryPagedListAdapter() {
        super(DictionarySearchElementDiffUtil.<T>getDiffUtil());

        // m_bookmarks = aBookmarks;

        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getSeq();
    }

    public void setBookmarkClickListener(ClickListener aListener) {
        m_bookmarkClickListener = aListener;
    }

    //public void setBookmarks(HashMap<Long, Long> aBookmarks) {
        // m_bookmarks = aBookmarks;

        // notifyDataSetChanged();
    //}

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
        DictionarySearchElementBase entry = getItem(position);

        if (entry != null) {
            //Long bookmark = null;

            //if (m_bookmarks != null) {
            //    bookmark = m_bookmarks.get(entry.getSeq());
            //}

            holder.bindTo(entry);
        }
    }

    static class DictionaryEntryItemViewHolder extends RecyclerView.ViewHolder {
        private TextView m_textViewView;
        private ImageButton m_bookmarkStar;

        //private Long m_bookmark;

        private ClickListener m_bookmarkClickListener;

        public DictionaryEntryItemViewHolder(View itemView) {
            super(itemView);

            m_textViewView = (TextView) itemView.findViewById(R.id.text);
            m_bookmarkStar = (ImageButton) itemView.findViewById(R.id.bookmark_star);
        }

        public void setBookmarkClickListener(ClickListener aListener) {
            m_bookmarkClickListener = aListener;
        }
/*
        public void updateBookmark(Long bookmark) {
            m_bookmark = bookmark;

            if (m_bookmark != null) {
                m_bookmarkStar.setImageResource(R.drawable.ic_baseline_star_24px);
            } else {
                m_bookmarkStar.setImageResource(R.drawable.ic_outline_star_border_24px);
            }
        }*/

        public void bindTo(final DictionarySearchElementBase entry) {
            SpannableStringBuilder sb = new SpannableStringBuilder();
            int writingsLength = 0;
            int readingsLength = 0;
            int startPos = 0;

            // m_bookmark = bookmark;

            // For debug purposes only
            // sb.append(Integer.toString(entry.entryOrder) + " " + entry.seq + " ");

            for (String w : entry.getWritingsPrio().split(" ")) {
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

            if (entry.getWritings().length() > 0) {
                sb.append(entry.getWritings());
                writingsLength = writingsLength + entry.getWritings().length();
            }

            if (writingsLength > 0) {
                sb.append(" ");

                writingsLength = writingsLength + 1;
            }

            for (String r : entry.getReadingsPrio().split(" ")) {
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

            if (entry.getReadings().length() > 0) {
                sb.append(entry.getReadings());
                readingsLength = readingsLength + entry.getReadings().length();
            }

            sb.append("\n");
            readingsLength = readingsLength + 1;

            if (entry.getGloss().length() > 0) {
                sb.append(entry.getGloss());

                sb.setSpan(new ForegroundColorSpan(Color.GRAY), writingsLength, writingsLength + readingsLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new RelativeSizeSpan(1.5f), 0, writingsLength + readingsLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            m_textViewView.setText(sb);


            if (m_bookmarkClickListener != null) {
                m_bookmarkStar.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        m_bookmarkClickListener.onClick(v, entry);
                    }
                });
            }


            if (entry.getBookmark() != 0) {
                m_bookmarkStar.setImageResource(R.drawable.ic_baseline_star_24px);
            } else {
                m_bookmarkStar.setImageResource(R.drawable.ic_outline_star_border_24px);
            }
        }
    }
}
