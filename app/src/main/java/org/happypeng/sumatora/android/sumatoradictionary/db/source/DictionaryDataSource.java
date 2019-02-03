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

package org.happypeng.sumatora.android.sumatoradictionary.db.source;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchResult;

import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.paging.DataSource;
import androidx.paging.ItemKeyedDataSource;
import androidx.room.InvalidationTracker;

public class DictionaryDataSource extends ItemKeyedDataSource<DictionarySearchResultKey, DictionarySearchElement> {
    public abstract static class DictionaryQuery {
        public abstract List<DictionarySearchElement> queryAfter(DictionarySearchResultKey afterKey, int count);
        public abstract List<DictionarySearchElement> queryBefore(DictionarySearchResultKey beforeKey, int count);
    }

    public static class Factory extends DataSource.Factory<DictionarySearchResultKey, DictionarySearchElement>  {
        private DictionaryQuery m_query;
        private DictionaryDatabase m_db;

        public Factory(DictionaryQuery aQuery, DictionaryDatabase aDB) {
            m_query = aQuery;
            m_db = aDB;
        }

        @NonNull
        @Override
        public DataSource<DictionarySearchResultKey, DictionarySearchElement> create() {
            return new DictionaryDataSource(m_query, m_db);
        }
    }

    private DictionaryQuery m_query;
    private DictionaryDatabase m_db;
    private InvalidationTracker.Observer m_observer;

    private DictionaryDataSource(DictionaryQuery aQuery, DictionaryDatabase aDB) {
        m_query = aQuery;
        m_db = aDB;

        m_observer = new InvalidationTracker.Observer("DictionarySearchResult", "DictionaryBookmark") {
            @Override
            public void onInvalidated(@NonNull Set<String> tables) {
                invalidate();

                m_db.getInvalidationTracker().removeObserver(m_observer);
            }
        };

        m_db.getInvalidationTracker().addObserver(m_observer);
    }

    @Override
    public void loadInitial(@NonNull LoadInitialParams<DictionarySearchResultKey> params, @NonNull LoadInitialCallback<DictionarySearchElement> callback) {
        if (params.requestedInitialKey == null) {
            callback.onResult(m_query.queryAfter(new DictionarySearchResultKey(0, 0), params.requestedLoadSize));
        } else {
            System.out.println("INITIAL KEY: " + params.requestedInitialKey.entryOrder + " " + params.requestedInitialKey.seq);

            callback.onResult(m_query.queryAfter(params.requestedInitialKey, params.requestedLoadSize));
        }
    }

    @Override
    public void loadAfter(@NonNull LoadParams<DictionarySearchResultKey> params, @NonNull LoadCallback<DictionarySearchElement> callback) {
        callback.onResult(m_query.queryAfter(params.key, params.requestedLoadSize));
    }

    @Override
    public void loadBefore(@NonNull LoadParams<DictionarySearchResultKey> params, @NonNull LoadCallback<DictionarySearchElement> callback) {
        // We don't load before either
        if (params.key != null) {
            List<DictionarySearchElement> l = m_query.queryBefore(params.key, params.requestedLoadSize);
            callback.onResult(l);

            System.out.println("LOAD BEFORE CALLED: " + params.key.entryOrder + " " + params.key.seq +
                    " (" + params.requestedLoadSize + " : " + l.size() + ")");

        }
    }

    @NonNull
    @Override
    public DictionarySearchResultKey getKey(@NonNull DictionarySearchElement item) {
        return new DictionarySearchResultKey(item.getEntryOrder(), item.getSeq());
    }
}
