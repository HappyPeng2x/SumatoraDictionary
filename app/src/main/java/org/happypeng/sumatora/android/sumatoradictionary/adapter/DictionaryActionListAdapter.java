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
        along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.happypeng.sumatora.android.sumatoradictionary.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ListAdapter;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.DictionaryAction;
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionaryActionViewHolder;

public class DictionaryActionListAdapter extends ListAdapter<DictionaryAction, DictionaryActionViewHolder> {
    private boolean mInstallButton;
    private boolean mDeleteButton;

    private final DictionaryActionViewHolder.OnClickListener mInstallListener;
    private final DictionaryActionViewHolder.OnClickListener mDeleteListener;

    public DictionaryActionListAdapter(boolean aInstallButton,
                                       boolean aDeleteButton,
                                       final DictionaryActionViewHolder.OnClickListener aInstallListener,
                                       final DictionaryActionViewHolder.OnClickListener aDeleteListener) {
        super(DictionaryAction.getDiffUtil());

        mInstallButton = aInstallButton;
        mDeleteButton = aDeleteButton;

        mInstallListener = aInstallListener;
        mDeleteListener = aDeleteListener;
    }

    @NonNull
    @Override
    public DictionaryActionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View view = layoutInflater.inflate(R.layout.dictionary_card, parent, false);

        return new DictionaryActionViewHolder(view, mInstallButton, mDeleteButton,
                mInstallListener, mDeleteListener);
    }

    @Override
    public void onBindViewHolder(@NonNull DictionaryActionViewHolder holder, int position) {
        holder.bindTo(getItem(position));
    }
}
