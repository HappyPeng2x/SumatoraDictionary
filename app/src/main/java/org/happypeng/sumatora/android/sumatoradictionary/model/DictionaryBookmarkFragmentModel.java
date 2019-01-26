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

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmarkElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchResult;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.sqlite.db.SupportSQLiteStatement;

public class DictionaryBookmarkFragmentModel extends DictionaryViewModel {
    protected LiveData<PagedList<DictionaryBookmarkElement>> m_bookmarkElements;
    protected MutableLiveData<Boolean> m_bookmarkElementsReady;

    private SupportSQLiteStatement m_deleteStatement;

    public DictionaryBookmarkFragmentModel(Application aApp) {
        super(aApp);

        if (m_bookmarkElementsReady == null) {
            m_bookmarkElementsReady = new MutableLiveData<>();
            m_bookmarkElementsReady.setValue(false);
        }
    }

    public LiveData<Boolean> getBookmarkElementsReady() {
        return m_bookmarkElementsReady;
    }

    public LiveData<PagedList<DictionaryBookmarkElement>> getBookmarkElements()
    {
        return m_bookmarkElements;
    }

    // Please note that this can be called before the rest of the constructor after super()
    @Override
    protected void connectDatabase() {
        super.connectDatabase();

        final PagedList.Config pagedListConfig =
                (new PagedList.Config.Builder()).setEnablePlaceholders(false)
                        .setPrefetchDistance(100)
                        .setPageSize(100).build();

        if (m_bookmarkElementsReady == null) {
            m_bookmarkElementsReady = new MutableLiveData<>();
            m_bookmarkElementsReady.setValue(true);
        }

        m_bookmarkElements = (new LivePagedListBuilder(m_db.dictionaryBookmarkDao().getAllDetails("eng"),
                pagedListConfig)).build();

        m_deleteStatement = m_db.getOpenHelper().getWritableDatabase().compileStatement("DELETE FROM DictionaryBookmark WHERE seq = ?");
    }

    @Override
    public void disconnectDatabase()
    {
        if (m_deleteStatement != null) {
            try {
                m_deleteStatement.close();
            } catch (Exception e) {
                System.err.println(e.toString());
            }
        }
    }

    public void deleteBookmark(final long aSeq) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                m_db.beginTransaction();

                m_deleteStatement.bindLong(1, aSeq);
                m_deleteStatement.executeUpdateDelete();

                m_db.setTransactionSuccessful();
                m_db.endTransaction();

                return null;
            }
        }.execute();
    }
}
