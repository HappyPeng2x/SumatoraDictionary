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
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.QueryTool;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.DisplayStatus;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.paging.PagedList;
import androidx.room.InvalidationTracker;

import java.util.Set;

public class BaseFragmentModel extends AndroidViewModel {
    DictionaryApplication mApp;
    int mKey;

    private MediatorLiveData<DisplayStatus> m_status;

    private QueryTool m_queryTool;

    private LiveData<PagedList<DictionarySearchElement>> m_elementList;

    public LiveData<PagedList<DictionarySearchElement>> getElementList() { return m_elementList; }
    public LiveData<DisplayStatus> getStatus() { return m_status; }

    private Integer m_bookmarkToolStatus;

    PersistentDatabase m_currentDatabase;

    public Integer getBookmarkToolStatus() { return m_bookmarkToolStatus; }

    public void initialize(final int aKey, final String aSearchSet, final boolean aAllowSearchAll,
                           final @NonNull String aTableObserve) {
        if (m_status != null) {
            return;
        }

        mKey = aKey;
        m_status = DisplayStatus.create(mApp, aKey);

        m_currentDatabase = null;

        final InvalidationTracker.Observer observer = new InvalidationTracker.Observer(aTableObserve) {
            @Override
            public void onInvalidated(@NonNull Set<String> tables) {
                if (m_queryTool != null) {
                    m_queryTool.setTerm(m_queryTool.getTerm(), true);
                }
            }
        };

        final LiveData<QueryTool> bookmarkTool =
                Transformations.switchMap(mApp.getPersistentDatabase(),
                        new Function<PersistentDatabase, LiveData<QueryTool>>() {
                            @Override
                            public LiveData<QueryTool> apply(PersistentDatabase input) {
                                if (m_currentDatabase != null) {
                                    m_currentDatabase.getInvalidationTracker().removeObserver(observer);
                                }

                                if (!"".equals(aTableObserve)) {
                                    input.getInvalidationTracker().addObserver(observer);
                                }

                                m_currentDatabase = input;

                                return QueryTool.create(input, aKey, aSearchSet, aAllowSearchAll);
                            }
                        });

        m_elementList =
                Transformations.switchMap(bookmarkTool,
                        new Function<QueryTool, LiveData<PagedList<DictionarySearchElement>>>() {
                            @Override
                            public LiveData<PagedList<DictionarySearchElement>> apply(QueryTool input) {
                                return input.getDisplayElements();
                            }
                        });

        m_status.addSource(bookmarkTool,
                new Observer<QueryTool>() {
                    @Override
                    public void onChanged(QueryTool queryTool) {
                        m_queryTool = queryTool;

                        if (m_queryTool != null) {
                            queryTool.setTerm(queryTool.getTerm(), true);
                        }
                    }
                });

        final LiveData<Integer> bookmarkToolStatus =
                Transformations.switchMap(bookmarkTool,
                        new Function<QueryTool, LiveData<Integer>>() {
                            @Override
                            public LiveData<Integer> apply(QueryTool input) {
                                return input.getStatus();
                            }
                        });

        m_status.addSource(bookmarkToolStatus,
                new Observer<Integer>() {
                    @Override
                    public void onChanged(Integer integer) {
                        m_bookmarkToolStatus = integer;

                        m_status.setValue(m_status.getValue());
                    }
                });
    }

    public BaseFragmentModel(Application aApp) {
        super(aApp);

        mApp = (DictionaryApplication) aApp;

        m_bookmarkToolStatus = null;
        m_queryTool = null;
    }

    public void updateBookmark(final long seq, final long bookmark) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... aParams) {
                if (mApp.getPersistentDatabase().getValue() != null) {
                    if (bookmark != 0) {
                        mApp.getPersistentDatabase().getValue().dictionaryBookmarkDao().insert(new DictionaryBookmark(seq, bookmark));
                    } else {
                        mApp.getPersistentDatabase().getValue().dictionaryBookmarkDao().delete(seq);
                    }
                }

                return null;
            }
        }.execute();
    }

    public DictionaryApplication getDictionaryApplication() { return mApp; }

    @MainThread
    public void setTerm(@NonNull String aTerm) {
        if (m_queryTool != null) {
            m_queryTool.setTerm(aTerm, true);
        }
    }

    public String getTerm() {
        if (m_queryTool != null) {
            return m_queryTool.getTerm();
        }

        return "";
    }
}
