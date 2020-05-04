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

package org.happypeng.sumatora.android.sumatoradictionary.viewholder;

import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.RemoteDictionaryObject;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BaseDictionaryObject;

public class DictionaryObjectViewHolder<T extends BaseDictionaryObject> extends RecyclerView.ViewHolder {
    public interface OnClickListener<U extends BaseDictionaryObject> {
        void onClick(U aEntry);
    }

    private final TextView mDescription;

    private final ImageButton mInstallButton;
    private final ImageButton mDeleteButton;

    private final OnClickListener<T> mInstallListener;
    private final OnClickListener<T> mDeleteListener;

    public DictionaryObjectViewHolder(@NonNull View itemView,
                                      boolean aInstallButton,
                                      boolean aDeleteButton,
                                      OnClickListener<T> aInstallListener,
                                      OnClickListener<T> aDeleteListener) {
        super(itemView);

        mDescription = (TextView) itemView.findViewById(R.id.dictionary_card_description);

        mInstallButton =
                (ImageButton) itemView.findViewById(R.id.dictionary_card_install);
        mDeleteButton =
                (ImageButton) itemView.findViewById(R.id.dictionary_card_delete);

        if (!aInstallButton) {
            mInstallButton.setVisibility(View.GONE);
        }

        if (!aDeleteButton) {
            mDeleteButton.setVisibility(View.GONE);
        }

        mInstallListener = aInstallListener;
        mDeleteListener = aDeleteListener;
    }

    public void bindTo(final T aEntry) {
        if (aEntry == null) { return; }

        mDescription.setText(aEntry.description);

        if (mInstallListener != null) {
            mInstallButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mInstallListener.onClick(aEntry);
                }
            });
        }

        if (mDeleteListener != null) {
            mDeleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mDeleteListener.onClick(aEntry);
                }
            });
        }
    }
}
