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
import android.view.ViewGroup;

import org.happypeng.sumatora.android.sumatoradictionary.databinding.WordCardBinding;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElementDiffUtil;
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder;

import java.util.HashMap;

public class DictionaryPagedListAdapter extends PagedListAdapter<DictionarySearchElement, DictionarySearchElementViewHolder> {
    private final HashMap<String, String> entities;
    private final boolean disableBookmarkButton;
    private final boolean disableMemoEdit;

    private final DictionarySearchElementViewHolder.CommitConsumer commitConsumer;

    public DictionaryPagedListAdapter(@NonNull final HashMap<String, String> entities,
                                      final boolean aDisableBookmarkButton,
                                      final boolean aDisableMemoEdit,
                                      final DictionarySearchElementViewHolder.CommitConsumer commitConsumer) {
        super(DictionarySearchElementDiffUtil.getDiffUtil());

        setHasStableIds(true);

        this.entities = entities;
        this.disableBookmarkButton = aDisableBookmarkButton;
        this.disableMemoEdit = aDisableMemoEdit;
        this.commitConsumer = commitConsumer;
    }

    // No placeholders = no null values
    @Override
    public long getItemId(int position) {
        return getItem(position).getSeq();
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull DictionarySearchElementViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
    }

    @NonNull
    @Override
    public DictionarySearchElementViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        WordCardBinding wordCardBinding = WordCardBinding.inflate(layoutInflater);
        DictionarySearchElementViewHolder holder = new DictionarySearchElementViewHolder(wordCardBinding,
                entities, disableBookmarkButton, disableMemoEdit,
                commitConsumer);

        return holder;
    }

    @Override
    public void onBindViewHolder(DictionarySearchElementViewHolder holder, int position) {
        DictionarySearchElement entry = getItem(position);

        if (entry != null) {
            holder.bindTo(entry);
        }
    }
}