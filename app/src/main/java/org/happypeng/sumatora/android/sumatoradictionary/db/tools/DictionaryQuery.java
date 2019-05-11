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
        along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.happypeng.sumatora.android.sumatoradictionary.db.tools;

import android.os.AsyncTask;
import android.util.Log;

import org.happypeng.sumatora.android.sumatoradictionary.BuildConfig;
import org.happypeng.sumatora.android.sumatoradictionary.DictionaryApplication;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;

import java.util.Iterator;

import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

public class DictionaryQuery {
    private static final int PAGE_SIZE = 30;
    private static final int PREFETCH_DISTANCE = 50;

    private final DictionaryApplication mApp;

    private final LiveData<PagedList<DictionarySearchElement>> mSearchEntries;

    private final LiveData<QueryTool.QueriesList> mQueries;
    private final MediatorLiveData<Iterator<QueryTool.QueryStatement>> mQueryIterator;

    private final MutableLiveData<String> mQueryTerm;

    public DictionaryQuery(final DictionaryApplication aApp) {
        mApp = aApp;

        mQueryTerm = new MutableLiveData<>();

        mQueries = Transformations.switchMap(mApp.getDictionaryDatabase(),
                new Function<DictionaryDatabase, LiveData<QueryTool.QueriesList>>() {
                    @Override
                    public LiveData<QueryTool.QueriesList> apply(DictionaryDatabase input) {
                        return QueryTool.QueriesList.build(input);
                    }
                });

        mSearchEntries = Transformations.switchMap(mApp.getDictionaryDatabase(),
                new Function<DictionaryDatabase, LiveData<PagedList<DictionarySearchElement>>>() {
                    @Override
                    public LiveData<PagedList<DictionarySearchElement>> apply(final DictionaryDatabase input) {
                        if (input != null) {
                            final PagedList.Config pagedListConfig =
                                    (new PagedList.Config.Builder()).setEnablePlaceholders(false)
                                            .setPrefetchDistance(PAGE_SIZE)
                                            .setPageSize(PREFETCH_DISTANCE).build();

                            return (new LivePagedListBuilder<Integer, DictionarySearchElement>(input.dictionarySearchResultDao().getAllPaged(),
                                    pagedListConfig))
                                    .setBoundaryCallback(new PagedList.BoundaryCallback<DictionarySearchElement>() {
                                        @Override
                                        public void onItemAtEndLoaded(@NonNull DictionarySearchElement itemAtEnd) {
                                            super.onItemAtEndLoaded(itemAtEnd);

                                            Settings settings = mApp.getSettings();

                                            if (mQueryTerm.getValue() != null && settings.getLang().getValue() != null
                                                    && mQueryIterator.getValue() != null && mQueries.getValue() != null) {
                                                mQueries.getValue().executeNextStatement(mQueryTerm.getValue(),
                                                        settings.getLang().getValue(), settings.getBackupLang().getValue(), mQueryIterator.getValue(), false);
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
                updateQueryIterator();
            }
        });

        mQueryIterator.addSource(mQueryTerm, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                updateQueryIterator();
            }
        });

        mQueryIterator.addSource(mApp.getSettings().getLang(), new Observer<String>() {
            @Override
            public void onChanged(String s) {
                updateQueryIterator();
            }
        });
    }

    private void updateQueryIterator() {
        if (mQueries.getValue() != null) {
            mQueries.getValue().resetHasResults();

            Settings settings = mApp.getSettings();

            if (mApp.getDictionaryDatabase().getValue() != null && settings != null && settings.getLang().getValue() != null && mQueryTerm.getValue() != null) {
                mQueryIterator.setValue(mQueries.getValue().getIterator());

                if (mQueryIterator.getValue() != null) {
                    mQueries.getValue().executeNextStatement(mQueryTerm.getValue(),
                            settings.getLang().getValue(), settings.getBackupLang().getValue(), mQueryIterator.getValue(), true);
                } else {
                    if (BuildConfig.DEBUG_MESSAGE) {
                        Log.d("DICT_QUERY", "Queries list gave us a null iterator");
                    }
                }
            }
        } else {
            mQueryIterator.setValue(null);
        }
    }

    public LiveData<String> getQueryTerm() {
        return mQueryTerm;
    }

    public void setQueryTerm(String aString) {
        mQueryTerm.setValue(aString);
    }

    public LiveData<PagedList<DictionarySearchElement>> getSearchEntries() {
        return mSearchEntries;
    }

    public void observe(final LifecycleOwner aOwner) {
        mQueryIterator.observe(aOwner, new Observer<Iterator<QueryTool.QueryStatement>>() {
            @Override
            public void onChanged(Iterator<QueryTool.QueryStatement> queryStatementIterator) {
                // nothing to do, we just observe
            }
        });
    }

    public void removeObservers(final LifecycleOwner aOwner) {
        mQueryIterator.removeObservers(aOwner);
    }
}
