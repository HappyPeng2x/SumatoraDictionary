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
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchResult;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryTypeConverters;

import java.util.HashMap;
import java.util.List;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.paging.PagedList;

public class DictionaryViewModel extends AndroidViewModel {
    protected DictionaryDatabase m_db;
    protected LiveData<DictionaryDatabase> m_dbLiveData;
    protected Observer<DictionaryDatabase> m_dbObserver;
    protected MutableLiveData<Boolean> m_dbReady;

    public LiveData<Boolean> getDatabaseReady() {
        return m_dbReady;
    }

    public DictionaryViewModel(Application aApp) {
        super(aApp);

        m_dbReady = new MutableLiveData<Boolean>();

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
                if (m_db != null) {
                    disconnectDatabase();
                }

                m_db = aDictionaryDatabase;

                if (m_db != null) {
                    m_dbReady.setValue(true);

                    connectDatabase();
                } else {
                    m_dbReady.setValue(false);
                }
            }
        };

        m_dbLiveData.observeForever(m_dbObserver);
    }

    protected void disconnectDatabase() {
    }

    @Override
    protected void onCleared() {
        m_dbLiveData.removeObserver(m_dbObserver);
        m_dbLiveData = null;
        m_db = null;
    }

    protected void connectDatabase() {}
}
