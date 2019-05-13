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

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryApplication;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.QueryTool;

import java.util.HashMap;
import java.util.List;

import androidx.arch.core.util.Function;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.paging.PagedList;

public class DictionarySearchFragmentModel extends AndroidViewModel {
    private DictionaryApplication mApp;

    private LiveData<HashMap<Long, Long>> mBookmarksHash;
    public LiveData<HashMap<Long, Long>> getBookmarksHash() { return mBookmarksHash; }

    private final LiveData<QueryTool.QueriesList> mDictionaryQuery;
    public LiveData<QueryTool.QueriesList> getDictionaryQuery() {
        return mDictionaryQuery;
    }

    private final LiveData<PagedList<DictionarySearchElement>> mSearchEntries;
    public LiveData<PagedList<DictionarySearchElement>> getSearchEntries() { return mSearchEntries; }

    private final LiveData<Integer> mQueryStatus;
    public LiveData<Integer> getQueryStatus() { return mQueryStatus; }

    public DictionarySearchFragmentModel(Application aApp) {
        super(aApp);

        mApp = (DictionaryApplication) aApp;

        mDictionaryQuery = Transformations.map(mApp.getDictionaryDatabase(),
                new Function<DictionaryDatabase, QueryTool.QueriesList>() {
                    @Override
                    public QueryTool.QueriesList apply(DictionaryDatabase input) {
                        return new QueryTool.QueriesList(input);
                    }
                });

        mSearchEntries = Transformations.switchMap(mDictionaryQuery,
                new Function<QueryTool.QueriesList, LiveData<PagedList<DictionarySearchElement>>>() {
                    @Override
                    public LiveData<PagedList<DictionarySearchElement>> apply(QueryTool.QueriesList input) {
                        return input.getSearchEntries();
                    }
                });

        mQueryStatus = Transformations.switchMap(mDictionaryQuery,
                new Function<QueryTool.QueriesList, LiveData<Integer>>() {
                    @Override
                    public LiveData<Integer> apply(QueryTool.QueriesList input) {
                        return input.getStatus();
                    }
                });

        final LiveData<List<DictionaryBookmark>> mBookmarks = Transformations.switchMap(mApp.getDictionaryDatabase(),
                new Function<DictionaryDatabase, LiveData<List<DictionaryBookmark>>>() {
                    @Override
                    public LiveData<List<DictionaryBookmark>> apply(DictionaryDatabase input) {
                        return input.dictionaryBookmarkDao().getAllLive();
                    }
                });

        mBookmarksHash = Transformations.map(mBookmarks, new Function<List<DictionaryBookmark>, HashMap<Long, Long>>() {
            @Override
            public HashMap<Long, Long> apply(List<DictionaryBookmark> input) {
                HashMap<Long, Long> result = new HashMap<>();

                for (DictionaryBookmark b : input) {
                    result.put(b.seq, b.bookmark);
                }

                return result;
            }
        });
    }

    public void updateBookmark(final long seq, final long bookmark) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... aParams) {
                if (mApp.getDictionaryDatabase().getValue() != null) {
                    if (bookmark != 0) {
                        mApp.getDictionaryDatabase().getValue().dictionaryBookmarkDao().insert(new DictionaryBookmark(seq, bookmark));
                    } else {
                        mApp.getDictionaryDatabase().getValue().dictionaryBookmarkDao().delete(seq);
                    }
                }

                return null;
            }
        }.execute();
    }

    public LiveData<DictionaryDatabase> getDictionaryDatabase() {
        return mApp.getDictionaryDatabase();
    }
}
