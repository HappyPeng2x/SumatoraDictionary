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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElementDiffUtil;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ListAdapter;

public class DictionaryListAdapter extends ListAdapter<DictionarySearchElement, DictionarySearchElementViewHolder> {
    private DictionarySearchElementViewHolder.ClickListener m_bookmarkClickListener;
    private boolean m_disableBookmarkButton;

    public DictionaryListAdapter() {
        super(DictionarySearchElementDiffUtil.getDiffUtil());

        setHasStableIds(true);

        m_disableBookmarkButton = false;
    }

    public DictionaryListAdapter(boolean aDisableBookmarkButton) {
        this();

        m_disableBookmarkButton = aDisableBookmarkButton;
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getSeq();
    }

    public void setBookmarkClickListener(DictionarySearchElementViewHolder.ClickListener aListener) {
        m_bookmarkClickListener = aListener;
    }

    @NonNull
    @Override
    public DictionarySearchElementViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = android.view.LayoutInflater.from(parent.getContext());
        View view = layoutInflater.inflate(R.layout.cell_cards, parent, false);
        DictionarySearchElementViewHolder holder = new DictionarySearchElementViewHolder(view);
        holder.setBookmarkClickListener(m_bookmarkClickListener);

        if (m_disableBookmarkButton) {
            holder.disableBookmarkButton();
        }

        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull DictionarySearchElementViewHolder holder, int position) {
        holder.bindTo(getItem(position));
    }
}
