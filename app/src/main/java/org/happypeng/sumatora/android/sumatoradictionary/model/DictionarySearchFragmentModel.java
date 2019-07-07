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
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Settings;

import java.util.HashMap;
import java.util.List;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.paging.PagedList;

public class DictionarySearchFragmentModel extends AndroidViewModel {
    private DictionaryApplication mApp;

    private LiveData<HashMap<Long, Long>> mBookmarksHash;
    public LiveData<HashMap<Long, Long>> getBookmarksHash() { return mBookmarksHash; }

    private final LiveData<PagedList<DictionarySearchElement>> mSearchEntries;
    public LiveData<PagedList<DictionarySearchElement>> getSearchEntries() { return mSearchEntries; }

    private final MutableLiveData<String> mTerm;

    @MainThread
    public void setTerm(@NonNull String aTerm) { mTerm.setValue(aTerm); }

    public static class Status {
        public QueryTool.QueriesList dictionaryQuery;
        public Integer queryStatus;
        public String term;
        public String lang;
        public String backupLang;

        public boolean isInitialized() {
            return lang != null && backupLang != null && queryStatus != null && dictionaryQuery != null &&
                    queryStatus >= QueryTool.QueriesList.STATUS_INITIALIZED;
        }

        void executeSetTerm() {
            if (isInitialized() && term != null) {
                dictionaryQuery.setTerm(term,
                        lang, backupLang);
            }
        }
    }

    private MediatorLiveData<Status> mStatus;
    public LiveData<Status> getStatus() { return mStatus; }

    public DictionarySearchFragmentModel(Application aApp) {
        super(aApp);

        mApp = (DictionaryApplication) aApp;

        final LiveData<QueryTool.QueriesList> dictionaryQuery = Transformations.map(mApp.getPersistentDatabase(),
                new Function<PersistentDatabase, QueryTool.QueriesList>() {
                    @Override
                    public QueryTool.QueriesList apply(PersistentDatabase input) {
                        return new QueryTool.QueriesList(input);
                    }
                });

        mSearchEntries = Transformations.switchMap(dictionaryQuery,
                new Function<QueryTool.QueriesList, LiveData<PagedList<DictionarySearchElement>>>() {
                    @Override
                    public LiveData<PagedList<DictionarySearchElement>> apply(QueryTool.QueriesList input) {
                        return input.getSearchEntries();
                    }
                });

        final LiveData<Integer> queryStatus = Transformations.switchMap(dictionaryQuery,
                new Function<QueryTool.QueriesList, LiveData<Integer>>() {
                    @Override
                    public LiveData<Integer> apply(QueryTool.QueriesList input) {
                        return input.getStatus();
                    }
                });

        final LiveData<List<DictionaryBookmark>> bookmarks = Transformations.switchMap(mApp.getPersistentDatabase(),
                new Function<PersistentDatabase, LiveData<List<DictionaryBookmark>>>() {
                    @Override
                    public LiveData<List<DictionaryBookmark>> apply(PersistentDatabase input) {
                        return input.dictionaryBookmarkDao().getAllLive();
                    }
                });

        mBookmarksHash = Transformations.map(bookmarks, new Function<List<DictionaryBookmark>, HashMap<Long, Long>>() {
            @Override
            public HashMap<Long, Long> apply(List<DictionaryBookmark> input) {
                HashMap<Long, Long> result = new HashMap<>();

                for (DictionaryBookmark b : input) {
                    result.put(b.seq, b.bookmark);
                }

                return result;
            }
        });

        mTerm = new MutableLiveData<>();
        mStatus = new MediatorLiveData<>();

        final Status status = new Status();

        mStatus.addSource(dictionaryQuery,
                new Observer<QueryTool.QueriesList>() {
                    @Override
                    public void onChanged(QueryTool.QueriesList queriesList) {
                        status.dictionaryQuery = queriesList;

                        // queriesList changed, maybe it is initialized
                        status.executeSetTerm();

                        mStatus.setValue(status);
                    }
                });

        mStatus.addSource(mTerm,
                new Observer<String>() {
                    @Override
                    public void onChanged(String s) {
                        status.term = s;

                        // term changed
                        status.executeSetTerm();

                        mStatus.setValue(status);
                    }
                });

        mStatus.addSource(queryStatus,
                new Observer<Integer>() {
                    @Override
                    public void onChanged(Integer integer) {

                        if ((status.queryStatus == null || status.queryStatus < QueryTool.QueriesList.STATUS_INITIALIZED) &&
                            integer >= QueryTool.QueriesList.STATUS_INITIALIZED) {
                            status.queryStatus = integer;

                            // status became initialized
                            status.executeSetTerm();
                        } else {
                            status.queryStatus = integer;
                        }

                        mStatus.setValue(status);
                    }
                });

        mStatus.addSource(mApp.getSettings().getValue(Settings.LANG),
                new Observer<String>() {
                    @Override
                    public void onChanged(String s) {
                        status.lang = s;

                        // lang changed
                        status.executeSetTerm();

                        mStatus.setValue(status);
                    }
                });

        mStatus.addSource(mApp.getSettings().getValue(Settings.BACKUP_LANG),
                new Observer<String>() {
                    @Override
                    public void onChanged(String s) {
                        status.backupLang = s;

                        // backupLang changed
                        status.executeSetTerm();

                        mStatus.setValue(status);
                    }
                });
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

    public LiveData<PersistentDatabase> getPersistentDatabase() {
        return mApp.getPersistentDatabase();
    }

    public DictionaryApplication getDictionaryApplication() { return mApp; }
}
