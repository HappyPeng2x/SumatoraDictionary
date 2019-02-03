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

package org.happypeng.sumatora.android.sumatoradictionary.model;

import android.app.Application;
import android.os.AsyncTask;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;

import java.util.List;

import androidx.lifecycle.LiveData;

public class DictionaryBookmarkFragmentModel extends DictionaryViewModel {
    private LiveData<List<DictionarySearchElement>> m_bookmarkElements;

    public DictionaryBookmarkFragmentModel(Application aApp) {
        super(aApp);
    }

    public LiveData<List<DictionarySearchElement>> getBookmarkElements()
    {
        return m_bookmarkElements;
    }

    // Please note that this can be called before the rest of the constructor after super()
    @Override
    protected void connectDatabase() {
        super.connectDatabase();

        m_bookmarkElements = m_db.dictionaryBookmarkDao().getAllDetailsLive("eng");
    }


    public void deleteBookmark(final long aSeq) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                m_db.dictionaryBookmarkDao().delete(aSeq);

                return null;
            }
        }.execute();
    }
}
