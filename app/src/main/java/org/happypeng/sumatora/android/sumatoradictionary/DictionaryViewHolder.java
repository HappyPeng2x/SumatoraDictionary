/* Sumatora Dictionary
        Copyright (C) 2018 Nicolas Centa

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

import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import android.view.View;

import android.widget.TextView;

import android.database.sqlite.SQLiteDatabase;

public class DictionaryViewHolder extends ViewHolder {
    private TextView m_textViewView;

    public DictionaryViewHolder(View itemView, SQLiteDatabase pDb) {
        super(itemView);

        m_textViewView = (TextView) itemView.findViewById(R.id.text);
    }

    public void bind(DictionaryElement pEle){
        m_textViewView.setText(pEle.getSpannableStringBuilder(), TextView.BufferType.SPANNABLE);
    }
}
