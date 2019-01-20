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

import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

public class DictionaryBookmarkFragmentModel extends DictionaryViewModel {
    public DictionaryBookmarkFragmentModel(Application aApp) {
        super(aApp);
    }

    public void listBookmarks(String aBookmark, String aLang) {
        //cleanSearchEntries();

        PagedList.Config pagedListConfig =
                (new PagedList.Config.Builder()).setEnablePlaceholders(false)
                        .setPrefetchDistance(100)
                        .setPageSize(100).build();

/*        m_searchEntries = (new LivePagedListBuilder(m_db.dictionaryEntryDao().listBookmarks(aBookmark, aLang),
                pagedListConfig)).build();

        m_searchEntries.observeForever(m_searchObserver);*/
    }
}
