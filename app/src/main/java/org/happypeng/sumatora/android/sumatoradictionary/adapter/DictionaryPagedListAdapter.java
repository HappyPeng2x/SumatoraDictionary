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

package org.happypeng.sumatora.android.sumatoradictionary.adapter;

import androidx.annotation.NonNull;
import androidx.paging.PagedListAdapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElementDiffUtil;
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder;

import java.util.HashMap;

public class DictionaryPagedListAdapter extends PagedListAdapter<DictionarySearchElement, DictionarySearchElementViewHolder> {
    private HashMap<Long, Long> m_bookmarks;
    private final DictionarySearchElementViewHolder.Status m_status;
    private boolean m_disableBookmarkButton;

    private DictionarySearchElementViewHolder.EventListener m_bookmarkEventListener;

    public DictionaryPagedListAdapter(@NonNull final DictionarySearchElementViewHolder.Status aStatus,
                                      boolean aDisableBookmarkButton) {
        super(DictionarySearchElementDiffUtil.getDiffUtil());

        setHasStableIds(true);

        m_status = aStatus;
        m_disableBookmarkButton = aDisableBookmarkButton;
    }

    // No placeholders = no null values
    @Override
    public long getItemId(int position) {
        return getItem(position).getSeq();
    }

    public void setBookmarkClickListener(DictionarySearchElementViewHolder.EventListener aListener) {
        m_bookmarkEventListener = aListener;
    }

    public void setBookmarks(HashMap<Long, Long> aBookmarks) {
        m_bookmarks = aBookmarks;

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DictionarySearchElementViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View view = layoutInflater.inflate(R.layout.word_card, parent, false);
        DictionarySearchElementViewHolder holder = new DictionarySearchElementViewHolder(view, m_status);

        if (m_disableBookmarkButton) {
            holder.disableBookmarkButton();
        }

        holder.setBookmarkClickListener(m_bookmarkEventListener);
        return holder;
    }

    @Override
    public void onBindViewHolder(DictionarySearchElementViewHolder holder, int position) {
        DictionarySearchElement entry = getItem(position);

        if (entry != null) {
            Long bookmark = null;

            if (m_bookmarks != null) {
                bookmark = m_bookmarks.get(entry.getSeq());

                if (bookmark != null) {
                    entry.bookmark = bookmark;
                } else {
                    entry.bookmark = 0;
                }
            }

            holder.bindTo(entry);
        }
    }
}
