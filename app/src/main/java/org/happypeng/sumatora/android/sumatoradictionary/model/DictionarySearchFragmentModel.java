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

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryEntry;

import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

public class DictionarySearchFragmentModel extends DictionaryViewModel {
    public DictionarySearchFragmentModel(Application aApp) {
        super(aApp);
    }

    public void search(String aExpr, String aLang) {
        cleanSearchEntries();

        PagedList.Config pagedListConfig =
                (new PagedList.Config.Builder()).setEnablePlaceholders(false)
                        .setPrefetchDistance(100)
                        .setPageSize(100).build();

        m_searchEntries = (new LivePagedListBuilder(m_db.dictionaryEntryDao().search(aExpr, aLang),
                pagedListConfig)).build();

        m_searchEntries.observeForever(m_searchObserver);
    }

    public void updateBookmark(final long seq, final Integer bookmarkFolder) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... aParams) {
                m_db.dictionaryEntryDao().updateBookmark(seq, bookmarkFolder);

                return null;
            }
        }.execute();
    }
}
