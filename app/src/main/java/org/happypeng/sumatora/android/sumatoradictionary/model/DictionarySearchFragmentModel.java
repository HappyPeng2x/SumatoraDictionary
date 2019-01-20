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
import android.util.Log;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryEntry;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchResult;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

public class DictionarySearchFragmentModel extends DictionaryViewModel {
    protected MutableLiveData<PagedList<DictionarySearchResult>> m_searchEntriesLiveData;
    protected LiveData<PagedList<DictionarySearchResult>> m_searchEntries;
    protected Observer<PagedList<DictionarySearchResult>> m_searchObserver;

    public DictionarySearchFragmentModel(Application aApp) {
        super(aApp);

        m_searchEntriesLiveData = new MutableLiveData<PagedList<DictionarySearchResult>>();

        m_searchObserver = new Observer<PagedList<DictionarySearchResult>>() {
            @Override
            public void onChanged(PagedList<DictionarySearchResult> aList) {
                m_searchEntriesLiveData.setValue(aList);
            }
        };
    }

    public LiveData<PagedList<DictionarySearchResult>> getSearchEntries() {
        return m_searchEntriesLiveData;
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

    public void updateBookmark(final long seq, final Long bookmark, final Long previousValue) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... aParams) {
                if (m_db != null) {
                    if (bookmark != null) {
                        m_db.dictionaryBookmarkDao().insert(new DictionaryBookmark(seq, bookmark));
                    } else {
                        if (previousValue != null) {
                            m_db.dictionaryBookmarkDao().delete(new DictionaryBookmark(seq, previousValue));
                        }
                    }
                }

                return null;
            }
        }.execute();
    }

    @Override
    public void disconnectDatabase() {
        super.disconnectDatabase();

        cleanSearchEntries();

        if (m_searchEntriesLiveData != null) {
            m_searchEntriesLiveData.setValue(null);
        }
    }

    protected void cleanSearchEntries() {
        if (m_searchEntries != null) {
            m_searchEntries.removeObserver(m_searchObserver);
            m_searchEntries = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        cleanSearchEntries();

        m_searchEntriesLiveData.setValue(null);
        m_searchEntriesLiveData = null;
    }
}
