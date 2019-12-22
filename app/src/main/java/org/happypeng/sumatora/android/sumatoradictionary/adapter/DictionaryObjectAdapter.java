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
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BaseDictionaryObject;
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionaryObjectViewHolder;

public class DictionaryObjectAdapter<T extends BaseDictionaryObject> extends ListAdapter<T, DictionaryObjectViewHolder<T>> {
    private boolean mInstallButton;
    private boolean mDeleteButton;

    private final DictionaryObjectViewHolder.OnClickListener<T> mInstallListener;
    private final DictionaryObjectViewHolder.OnClickListener<T> mDeleteListener;

    public DictionaryObjectAdapter(boolean aInstallButton,
                                   boolean aDeleteButton,
                                   final DictionaryObjectViewHolder.OnClickListener<T> aInstallListener,
                                   final DictionaryObjectViewHolder.OnClickListener<T> aDeleteListener) {
        super(BaseDictionaryObject.<T>getDiffUtil());

        mInstallButton = aInstallButton;
        mDeleteButton = aDeleteButton;

        mInstallListener = aInstallListener;
        mDeleteListener = aDeleteListener;
    }

    @NonNull
    @Override
    public DictionaryObjectViewHolder<T> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View view = layoutInflater.inflate(R.layout.dictionary_card, parent, false);

        return new DictionaryObjectViewHolder<T>(view, mInstallButton, mDeleteButton,
                mInstallListener, mDeleteListener);
    }

    @Override
    public void onBindViewHolder(@NonNull DictionaryObjectViewHolder<T> holder, int position) {
        holder.bindTo(getItem(position));
    }
}
