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

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryApplication;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchResult;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.paging.PagedList;

public class DictionaryViewModel extends AndroidViewModel {
    protected MutableLiveData<PagedList<DictionarySearchResult>> m_searchEntriesLiveData;
    protected LiveData<PagedList<DictionarySearchResult>> m_searchEntries;
    protected Observer<PagedList<DictionarySearchResult>> m_searchObserver;

    protected DictionaryDatabase m_db;
    protected LiveData<DictionaryDatabase> m_dbLiveData;
    protected Observer<DictionaryDatabase> m_dbObserver;
    protected MutableLiveData<Boolean> m_dbReady;

    public LiveData<PagedList<DictionarySearchResult>> getSearchEntries() {
        return m_searchEntriesLiveData;
    }

    public LiveData<Boolean> getDatabaseReady() {
        return m_dbReady;
    }

    public DictionaryViewModel(Application aApp) {
        super(aApp);

        m_dbReady = new MutableLiveData<Boolean>();
        m_searchEntriesLiveData = new MutableLiveData<PagedList<DictionarySearchResult>>();

        DictionaryApplication app = (DictionaryApplication) aApp;

        m_dbLiveData = app.getDictionaryDatabase();

        if (m_dbLiveData.getValue() != null) {
            m_dbReady.setValue(true);
        } else {
            m_dbReady.setValue(false);
        }

        m_dbObserver = new Observer<DictionaryDatabase>() {
            @Override
            public void onChanged(DictionaryDatabase aDictionaryDatabase) {
                cleanSearchEntries();

                m_searchEntriesLiveData.setValue(null);

                m_db = aDictionaryDatabase;

                if (aDictionaryDatabase != null) {
                    m_dbReady.setValue(true);
                } else {
                    m_dbReady.setValue(false);
                }
            }
        };

        m_dbLiveData.observeForever(m_dbObserver);

        m_searchObserver = new Observer<PagedList<DictionarySearchResult>>() {
            @Override
            public void onChanged(PagedList<DictionarySearchResult> aList) {
                m_searchEntriesLiveData.setValue(aList);
            }
        };
    }

    protected void cleanSearchEntries() {
        if (m_searchEntries != null) {
            m_searchEntries.removeObserver(m_searchObserver);
            m_searchEntries = null;
        }
    }

    @Override
    protected void onCleared() {
        cleanSearchEntries();

        m_searchEntriesLiveData.setValue(null);
        m_searchEntriesLiveData = null;

        m_dbLiveData.removeObserver(m_dbObserver);
        m_dbLiveData = null;
        m_db = null;
    }
}
