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
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.RemoteDictionaryObjectViewHolder;

public class RemoteDictionaryObjectAdapter extends ListAdapter<org.happypeng.sumatora.android.sumatoradictionary.db.RemoteDictionaryObject, RemoteDictionaryObjectViewHolder> {
    private boolean mInstallButton;
    private boolean mDeleteButton;

    private final RemoteDictionaryObjectViewHolder.OnClickListener mInstallListener;
    private final RemoteDictionaryObjectViewHolder.OnClickListener mDeleteListener;

    public RemoteDictionaryObjectAdapter(boolean aInstallButton,
                                         boolean aDeleteButton,
                                         final RemoteDictionaryObjectViewHolder.OnClickListener aInstallListener,
                                         final RemoteDictionaryObjectViewHolder.OnClickListener aDeleteListener) {
        super(org.happypeng.sumatora.android.sumatoradictionary.db.RemoteDictionaryObject.getDiffUtil());

        mInstallButton = aInstallButton;
        mDeleteButton = aDeleteButton;

        mInstallListener = aInstallListener;
        mDeleteListener = aDeleteListener;
    }

    @NonNull
    @Override
    public RemoteDictionaryObjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View view = layoutInflater.inflate(R.layout.dictionary_card, parent, false);

        return new RemoteDictionaryObjectViewHolder(view, mInstallButton, mDeleteButton,
                mInstallListener, mDeleteListener);
    }

    @Override
    public void onBindViewHolder(@NonNull RemoteDictionaryObjectViewHolder holder, int position) {
        holder.bindTo(getItem(position));
    }
}
