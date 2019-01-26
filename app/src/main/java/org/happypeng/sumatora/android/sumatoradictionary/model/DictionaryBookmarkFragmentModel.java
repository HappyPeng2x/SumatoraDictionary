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

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmarkElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchResult;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

public class DictionaryBookmarkFragmentModel extends DictionaryViewModel {
    protected LiveData<PagedList<DictionaryBookmarkElement>> m_bookmarkElements;
    protected MutableLiveData<Boolean> m_bookmarkElementsReady;

    public DictionaryBookmarkFragmentModel(Application aApp) {
        super(aApp);

        m_bookmarkElementsReady = new MutableLiveData<>();
        m_bookmarkElementsReady.setValue(false);
    }

    public LiveData<Boolean> getBookmarkElementsReady() {
        return m_bookmarkElementsReady;
    }

    public LiveData<PagedList<DictionaryBookmarkElement>> getBookmarkElements()
    {
        return m_bookmarkElements;
    }

    @Override
    protected void connectDatabase() {
        super.connectDatabase();

        final PagedList.Config pagedListConfig =
                (new PagedList.Config.Builder()).setEnablePlaceholders(false)
                        .setPrefetchDistance(100)
                        .setPageSize(100).build();

        m_bookmarkElements = (new LivePagedListBuilder(m_db.dictionaryBookmarkDao().getAllDetails("eng"),
                pagedListConfig)).build();
    }
}
