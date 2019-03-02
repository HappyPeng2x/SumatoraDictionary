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
import java.util.Iterator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

public class DictionarySearchFragmentModel extends AndroidViewModel {
    private DictionaryApplication mApp;

    private LiveData<PagedList<DictionarySearchElement>> mSearchEntries;

    private LiveData<QueryTool.QueriesList> mQueries;
    private MediatorLiveData<Iterator<QueryTool.QueryStatement>> mQueryIterator;

    private LiveData<HashMap<Long, Long>> mBookmarksHash;

    private MutableLiveData<String> mQueryTerm;
    private MutableLiveData<String> mQueryLang;

    public MutableLiveData<String> getQueryTerm() { return mQueryTerm; }
    public MutableLiveData<String> getQueryLang() { return mQueryLang; }
    public LiveData<HashMap<Long, Long>> getBookmarksHash() { return mBookmarksHash; }
    public LiveData<PagedList<DictionarySearchElement>> getSearchEntries() { return mSearchEntries; }
    public LiveData<Iterator<QueryTool.QueryStatement>> getQueryIterator() { return mQueryIterator; }

    public DictionarySearchFragmentModel(Application aApp) {
        super(aApp);

        mApp = (DictionaryApplication) aApp;

        mQueryTerm = new MutableLiveData<>();
        mQueryLang = new MutableLiveData<>();

        mQueryLang.setValue("eng");

        LiveData<List<DictionaryBookmark>> mBookmarks = Transformations.switchMap(mApp.getDictionaryDatabase(),
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

        mQueries = Transformations.switchMap(mApp.getDictionaryDatabase(),
                new Function<DictionaryDatabase, LiveData<QueryTool.QueriesList>>() {
                    @Override
                    public LiveData<QueryTool.QueriesList> apply(DictionaryDatabase input) {
                        return QueryTool.getQueriesList(input);
                    }
                });

        mSearchEntries = Transformations.switchMap(mApp.getDictionaryDatabase(),
                new Function<DictionaryDatabase, LiveData<PagedList<DictionarySearchElement>>>() {
                    @Override
                    public LiveData<PagedList<DictionarySearchElement>> apply(final DictionaryDatabase input) {
                        if (input != null) {
                            final PagedList.Config pagedListConfig =
                                    (new PagedList.Config.Builder()).setEnablePlaceholders(false)
                                            .setPrefetchDistance(30)
                                            .setPageSize(50).build();

                            return (new LivePagedListBuilder<Integer, DictionarySearchElement>(input.dictionarySearchResultDao().getAllPaged(),
                                    pagedListConfig))
                                    .setBoundaryCallback(new PagedList.BoundaryCallback<DictionarySearchElement>() {
                                        @Override
                                        public void onItemAtEndLoaded(@NonNull DictionarySearchElement itemAtEnd) {
                                            super.onItemAtEndLoaded(itemAtEnd);

                                            if (mQueryTerm.getValue() != null && mQueryLang.getValue() != null
                                                    && mQueryIterator.getValue() != null) {
                                                QueryTool.executeNextStatement(input, mQueryTerm.getValue(),
                                                        mQueryLang.getValue(), mQueryIterator.getValue());
                                            }
                                        }
                                    }).build();
                        } else {
                            return null;
                        }
                    }
                });

        mQueryIterator = new MediatorLiveData<>();

        mQueryIterator.addSource(mQueries, new Observer<QueryTool.QueriesList>() {
            @Override
            public void onChanged(QueryTool.QueriesList queryStatements) {
                resetIterator();
            }
        });

        mQueryIterator.addSource(mQueryTerm, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                resetIterator();

                if (mApp.getDictionaryDatabase().getValue() != null && mQueryTerm.getValue() != null && mQueryLang.getValue() != null
                        && mQueryIterator.getValue() != null) {
                    QueryTool.executeNextStatement(mApp.getDictionaryDatabase().getValue(), mQueryTerm.getValue(),
                            mQueryLang.getValue(), mQueryIterator.getValue());
                }
            }
        });

        mQueryIterator.addSource(mQueryLang, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                resetIterator();

                if (mApp.getDictionaryDatabase().getValue() != null && mQueryTerm.getValue() != null && mQueryLang.getValue() != null
                        && mQueryIterator.getValue() != null) {
                    QueryTool.executeNextStatement(mApp.getDictionaryDatabase().getValue(), mQueryTerm.getValue(),
                            mQueryLang.getValue(), mQueryIterator.getValue());
                }
            }
        });
    }

    private void resetIterator() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                if (mQueries.getValue() != null && mQueries.getValue().deleteStatement != null) {
                    mQueries.getValue().deleteStatement.executeUpdateDelete();
                }

                return null;
            }
        }.execute();

        if (mQueryLang.getValue() != null && mQueryTerm.getValue() != null && mQueries.getValue() != null &&
                mQueries.getValue().queriesList != null) {
            mQueryIterator.setValue(mQueries.getValue().queriesList.iterator());
        } else {
            mQueryIterator.setValue(null);
        }
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
}
