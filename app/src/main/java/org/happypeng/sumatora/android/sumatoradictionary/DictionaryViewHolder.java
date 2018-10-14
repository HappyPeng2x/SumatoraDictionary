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

import java.util.List;

import android.graphics.Color;
import android.support.v7.widget.RecyclerView.ViewHolder;

import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.View;

import android.widget.TextView;

import android.text.SpannableString;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.Cursor;

public class DictionaryViewHolder extends ViewHolder {
    private TextView textViewView;
    private TextView textViewView2;

    private SQLiteDatabase mDb;

    public DictionaryViewHolder(View itemView, SQLiteDatabase pDb) {
        super(itemView);

        textViewView = (TextView) itemView.findViewById(R.id.text);

        mDb = pDb;
    }

    public void bind(DictionaryElement pEle){
        List<DictionaryElement.Writing> writings = pEle.getWritings();
        List<DictionaryElement.Reading> readings = pEle.getReadings();

        StringBuffer writingsBuf = new StringBuffer();
        StringBuffer readingsBuf = new StringBuffer();

        for (DictionaryElement.Writing w : writings) {
            writingsBuf.append(w.keb);

            if (!pEle.getWritingsPrio(w.keb_id).isEmpty()) {
                writingsBuf.append("(c)");
            }

            writingsBuf.append(" ");
        }

        for (DictionaryElement.Reading r : readings) {
            readingsBuf.append(r.reb);

            if (!pEle.getReadingsPrio(r.reb_id).isEmpty()) {
                readingsBuf.append("(c)");
            }

            readingsBuf.append(" ");
        }

        List<DictionaryElement.Gloss> gloss = pEle.getGloss("eng");

        StringBuffer glossBuf = new StringBuffer();

        int cur_sense = -1;

        for (DictionaryElement.Gloss g : gloss) {
            if (g.sense_id != cur_sense) {
                cur_sense = g.sense_id;

                if (cur_sense > 0) {
                    glossBuf.append("\n");
                }

                glossBuf.append(cur_sense + 1);
                glossBuf.append(". ");
            } else {
                glossBuf.append(", ");
            }

            glossBuf.append(g.gloss);
        }

        SpannableString titleSpan = new SpannableString(writingsBuf.toString() + " " + readingsBuf.toString() + "\n\n" + glossBuf.toString());
        titleSpan.setSpan(new ForegroundColorSpan(Color.GRAY), writingsBuf.length() + 1, writingsBuf.length() + readingsBuf.length() + 1, 0);
        titleSpan.setSpan(new RelativeSizeSpan(1.5f), 0, writingsBuf.length() + readingsBuf.length() + 1, 0);

        textViewView.setText(titleSpan, TextView.BufferType.SPANNABLE);
    }
}
