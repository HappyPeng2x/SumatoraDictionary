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
import android.widget.TextView;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryEntry;


public class DictionaryPagedListAdapter extends PagedListAdapter<DictionaryEntry, DictionaryPagedListAdapter.DictionaryEntryItemViewHolder> {
    protected DictionaryPagedListAdapter() {
        super(DictionaryEntry.DIFF_CALLBACK);
    }

    @Override
    public DictionaryEntryItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View view = layoutInflater.inflate(R.layout.cell_cards, parent, false);
        return new DictionaryEntryItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(DictionaryEntryItemViewHolder holder, int position) {
        DictionaryEntry entry = getItem(position);

        if (entry != null) {
            holder.bindTo(entry);
        }
    }

    static class DictionaryEntryItemViewHolder extends RecyclerView.ViewHolder {
        private TextView m_textViewView;

        public DictionaryEntryItemViewHolder(View itemView) {
            super(itemView);

            m_textViewView = (TextView) itemView.findViewById(R.id.text);
        }

        public void bindTo(DictionaryEntry entry) {
            SpannableStringBuilder sb = new SpannableStringBuilder();
            int writingsLength = 0;
            int readingsLength = 0;

            for (String w : entry.writings.split(" ")) {
                if (w.contains("/")) {
                    String nw = w.replace("/", "");

                    sb.append(nw,
                            new BackgroundColorSpan(Color.parseColor("#ccffcc")), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    writingsLength = writingsLength + nw.length();
                } else {
                    String nw = w.replace("-", "");

                    sb.append(nw);

                    writingsLength = writingsLength + nw.length();
                }

                if (!w.isEmpty()) {
                    sb.append(" ");

                    writingsLength = writingsLength + 1;
                }
            }

            for (String r : entry.readings.split(" ")) {
                if (r.contains("/")) {
                    String nr = r.replace("/", "");

                    sb.append(nr,
                            new BackgroundColorSpan(Color.parseColor("#ccffcc")), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    readingsLength = readingsLength + nr.length();
                } else {
                    String nr = r.replace("-", "");

                    sb.append(nr);

                    readingsLength = readingsLength + nr.length();
                }

                if (!r.isEmpty()) {
                    sb.append(" ");

                    readingsLength = readingsLength + 1;
                }
            }

            sb.append("\n");
            readingsLength = readingsLength + 1;

            sb.append(entry.gloss);

            sb.setSpan(new ForegroundColorSpan(Color.GRAY), writingsLength, writingsLength + readingsLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new RelativeSizeSpan(1.5f), 0, writingsLength + readingsLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            m_textViewView.setText(sb);
        }
    }
}
