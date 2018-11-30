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
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.Html;

import android.view.View;

import android.widget.TextView;

import android.text.SpannableStringBuilder;

import android.database.sqlite.SQLiteDatabase;

import android.os.AsyncTask;

public class DictionaryViewHolder extends ViewHolder {
    private TextView m_textViewView;
    private GetReadingsWritingsTask m_currentTask;

    public DictionaryViewHolder(View itemView, SQLiteDatabase pDb) {
        super(itemView);

        m_textViewView = (TextView) itemView.findViewById(R.id.text);

        m_currentTask = null;
    }

    private class GetReadingsWritingsTask extends AsyncTask<DictionaryElement, Void, Void> {
        SpannableStringBuilder m_sb;

        GetReadingsWritingsTask() {
            m_sb = new SpannableStringBuilder();
        }

        protected Void doInBackground(DictionaryElement... a_params) {
            if (a_params.length < 1 || isCancelled()) {
                return null;
            }

            DictionaryElement d = a_params[0];

            List<DictionaryElement.Writing> writings = d.getWritings();
            List<DictionaryElement.Reading> readings = d.getReadings();

            int writingsLength = 0;
            int readingsLength = 0;

            for (DictionaryElement.Writing w : writings) {
                if (!d.getWritingsPrio(w.keb_id).isEmpty()) {
                    m_sb.append(w.keb, new BackgroundColorSpan(Color.parseColor("#ccffcc")), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    m_sb.append(w.keb);
                }

                m_sb.append(" ");

                writingsLength = writingsLength + w.keb.length() + 1;
            }

            for (DictionaryElement.Reading r : readings) {
                if (!d.getReadingsPrio(r.reb_id).isEmpty()) {
                    m_sb.append(r.reb, new BackgroundColorSpan(Color.parseColor("#ccffcc")), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    m_sb.append(r.reb);
                }

                m_sb.append(" ");

                readingsLength = readingsLength + r.reb.length() + 1;
            }

            m_sb.append("\n");
            readingsLength = readingsLength + 1;

            List<DictionaryElement.Gloss> gloss = d.getGloss("eng");

            int glossLength = 0;
            int cur_sense = -1;

            for (DictionaryElement.Gloss g : gloss) {
                if (g.sense_id != cur_sense) {
                    cur_sense = g.sense_id;

                    if (cur_sense > 0) {
                        m_sb.append("\n");

                        glossLength = glossLength + 1;
                    }

                    m_sb.append(Integer.toString(cur_sense + 1));
                    m_sb.append(". ");

                    glossLength = glossLength + 2 + Integer.toString(cur_sense + 1).length();
                } else {
                    m_sb.append(", ");

                    glossLength = glossLength + 2;
                }

                m_sb.append(g.gloss);

                glossLength = glossLength + g.gloss.length();
            }

            m_sb.setSpan(new ForegroundColorSpan(Color.GRAY), writingsLength, writingsLength + readingsLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            m_sb.setSpan(new RelativeSizeSpan(1.5f), 0, writingsLength + readingsLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            return null;
        }

        protected void onPostExecute(Void a_param) {
            if (!isCancelled()) {
                m_textViewView.setText(m_sb, TextView.BufferType.SPANNABLE);
            }
        }
    }

    public void bind(DictionaryElement pEle){
        m_textViewView.setText("");

        if (m_currentTask != null) {
            m_currentTask.cancel(true);
        }

        m_currentTask = new GetReadingsWritingsTask();
        m_currentTask.execute(pEle);

        /*SpannableStringBuilder sb = new SpannableStringBuilder();

        List<DictionaryElement.Writing> writings = pEle.getWritings();
        List<DictionaryElement.Reading> readings = pEle.getReadings();

        int writingsLength = 0;
        int readingsLength = 0;

        for (DictionaryElement.Writing w : writings) {
            if (!pEle.getWritingsPrio(w.keb_id).isEmpty()) {
                sb.append(w.keb, new BackgroundColorSpan(Color.parseColor("#ccffcc")), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                sb.append(w.keb);
            }

            sb.append(" ");

            writingsLength = writingsLength + w.keb.length() + 1;
        }

        for (DictionaryElement.Reading r : readings) {
            if (!pEle.getReadingsPrio(r.reb_id).isEmpty()) {
                sb.append(r.reb, new BackgroundColorSpan(Color.parseColor("#ccffcc")), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                sb.append(r.reb);
            }

            sb.append(" ");

            readingsLength = readingsLength + r.reb.length() + 1;
        }

        sb.append("\n");
        readingsLength = readingsLength + 1;

        List<DictionaryElement.Gloss> gloss = pEle.getGloss("eng");

        int glossLength = 0;
        int cur_sense = -1;

        for (DictionaryElement.Gloss g : gloss) {
            if (g.sense_id != cur_sense) {
                cur_sense = g.sense_id;

                if (cur_sense > 0) {
                    sb.append("\n");

                    glossLength = glossLength + 1;
                }

                sb.append(Integer.toString(cur_sense + 1));
                sb.append(". ");

                glossLength = glossLength + 2 + Integer.toString(cur_sense + 1).length();
            } else {
                sb.append(", ");

                glossLength = glossLength + 2;
            }

            sb.append(g.gloss);

            glossLength = glossLength + g.gloss.length();
        }

        sb.setSpan(new ForegroundColorSpan(Color.GRAY), writingsLength, writingsLength + readingsLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new RelativeSizeSpan(1.5f), 0, writingsLength + readingsLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        m_textViewView.setText(sb, TextView.BufferType.SPANNABLE);*/
    }
}
