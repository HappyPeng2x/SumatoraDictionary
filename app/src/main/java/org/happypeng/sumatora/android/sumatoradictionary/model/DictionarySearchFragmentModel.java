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
import android.util.Log;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryTypeConverters;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;

public class DictionarySearchFragmentModel extends DictionaryViewModel {
    protected LiveData<PagedList<DictionarySearchElement>> m_searchEntries;
    protected MutableLiveData<LiveData<PagedList<DictionarySearchElement>>> m_searchEntriesLive;

    private final String SQL_QUERY_EXACT_PRIO =
            "INSERT INTO DictionarySearchResult SELECT ? AS entryOrder, DictionaryEntry.seq, DictionaryEntry.readingsPrio, DictionaryEntry.readings, "
                    + "DictionaryEntry.writingsPrio, DictionaryEntry.writings, DictionaryTranslation.lang, "
                    + "DictionaryTranslation.gloss "
                    + "FROM DictionaryEntry, DictionaryTranslation, "
                    + "("
                    + "SELECT DictionaryIndex.`rowid` AS seq FROM DictionaryIndex "
                    + "WHERE writingsPrio MATCH 'writingsPrio:' || ? || ' OR readingsPrio:' || ? "
                    + ") AS A "
                    + "WHERE A.seq NOT IN (SELECT seq FROM DictionarySearchResult) AND A.seq = DictionaryEntry.seq AND A.seq = DictionaryTranslation.seq AND DictionaryTranslation.lang = ? "
                    + "GROUP BY A.seq";

    private final String SQL_QUERY_EXACT_NONPRIO =
            "INSERT INTO DictionarySearchResult SELECT ? AS entryOrder, DictionaryEntry.seq, DictionaryEntry.readingsPrio, DictionaryEntry.readings, "
                    + "DictionaryEntry.writingsPrio, DictionaryEntry.writings, DictionaryTranslation.lang, "
                    + "DictionaryTranslation.gloss "
                    + "FROM DictionaryEntry, DictionaryTranslation, "
                    + "("
                    + "SELECT DictionaryIndex.`rowid` AS seq FROM DictionaryIndex "
                    + "WHERE writingsPrio MATCH 'writings:' || ? || ' OR readings:' || ? "
                    + ") AS A "
                    + "WHERE A.seq NOT IN (SELECT seq FROM DictionarySearchResult) AND A.seq = DictionaryEntry.seq AND A.seq = DictionaryTranslation.seq AND DictionaryTranslation.lang = ? "
                    + "GROUP BY A.seq";

    private final String SQL_QUERY_BEGIN_PRIO =
            "INSERT INTO DictionarySearchResult SELECT ? AS entryOrder, DictionaryEntry.seq, DictionaryEntry.readingsPrio, DictionaryEntry.readings, "
                    + "DictionaryEntry.writingsPrio, DictionaryEntry.writings, DictionaryTranslation.lang, "
                    + "DictionaryTranslation.gloss "
                    + "FROM DictionaryEntry, DictionaryTranslation, "
                    + "("
                    + "SELECT DictionaryIndex.`rowid` AS seq FROM DictionaryIndex "
                    + "WHERE writingsPrio MATCH 'writingsPrio:' || ? || '* OR readingsPrio:' || ? || '*'"
                    + ") AS A "
                    + "WHERE A.seq NOT IN (SELECT seq FROM DictionarySearchResult) AND A.seq = DictionaryEntry.seq AND A.seq = DictionaryTranslation.seq AND DictionaryTranslation.lang = ? "
                    + "GROUP BY A.seq";

    private final String SQL_QUERY_BEGIN_NONPRIO =
            "INSERT INTO DictionarySearchResult SELECT ? AS entryOrder, DictionaryEntry.seq, DictionaryEntry.readingsPrio, DictionaryEntry.readings, "
                    + "DictionaryEntry.writingsPrio, DictionaryEntry.writings, DictionaryTranslation.lang, "
                    + "DictionaryTranslation.gloss "
                    + "FROM DictionaryEntry, DictionaryTranslation, "
                    + "("
                    + "SELECT DictionaryIndex.`rowid` AS seq FROM DictionaryIndex "
                    + "WHERE writingsPrio MATCH 'writings:' || ? || '* OR readings:' || ? || '*'"
                    + ") AS A "
                    + "WHERE A.seq NOT IN (SELECT seq FROM DictionarySearchResult) AND A.seq = DictionaryEntry.seq AND A.seq = DictionaryTranslation.seq AND DictionaryTranslation.lang = ? "
                    + "GROUP BY A.seq";

    private final String SQL_QUERY_PARTS_PRIO =
            "INSERT INTO DictionarySearchResult SELECT ? AS entryOrder, DictionaryEntry.seq, DictionaryEntry.readingsPrio, DictionaryEntry.readings, "
                    + "DictionaryEntry.writingsPrio, DictionaryEntry.writings, DictionaryTranslation.lang, "
                    + "DictionaryTranslation.gloss "
                    + "FROM DictionaryEntry, DictionaryTranslation, "
                    + "("
                    + "SELECT DictionaryIndex.`rowid` AS seq FROM DictionaryIndex "
                    + "WHERE writingsPrio MATCH 'writingsPrioParts:' || ? || '* OR readingsPrioParts:' || ? || '*'"
                    + ") AS A "
                    + "WHERE A.seq NOT IN (SELECT seq FROM DictionarySearchResult) AND A.seq = DictionaryEntry.seq AND A.seq = DictionaryTranslation.seq AND DictionaryTranslation.lang = ? "
                    + "GROUP BY A.seq";

    private final String SQL_QUERY_PARTS_NONPRIO =
            "INSERT INTO DictionarySearchResult SELECT ? AS entryOrder, DictionaryEntry.seq, DictionaryEntry.readingsPrio, DictionaryEntry.readings, "
                    + "DictionaryEntry.writingsPrio, DictionaryEntry.writings, DictionaryTranslation.lang, "
                    + "DictionaryTranslation.gloss "
                    + "FROM DictionaryEntry, DictionaryTranslation, "
                    + "("
                    + "SELECT DictionaryIndex.`rowid` AS seq FROM DictionaryIndex "
                    + "WHERE writingsPrio MATCH 'writingsParts:' || ? || '* OR readingsParts:' || ? || '*'"
                    + ") AS A "
                    + "WHERE A.seq NOT IN (SELECT seq FROM DictionarySearchResult) AND A.seq = DictionaryEntry.seq AND A.seq = DictionaryTranslation.seq AND DictionaryTranslation.lang = ? "
                    + "GROUP BY A.seq";

    private final String SQL_QUERY_DELETE_RESULTS =
            "DELETE FROM DictionarySearchResult";

    private SupportSQLiteStatement m_queryDeleteResults;

    private static class QueryStatement {
        final int order;
        final SupportSQLiteStatement statement;

        private QueryStatement(int aOrder, SupportSQLiteStatement aStatement) {
            order = aOrder;
            statement = aStatement;
        }

        private long execute(String term, String lang) {
            Log.d("DICT_QUERY", "Execute query " + order + " for term " + term);

            statement.bindLong(1, order);
            statement.bindString(2, term);
            statement.bindString(3, term);
            statement.bindString(4, lang);

            return statement.executeInsert();
        }
    }

    private List<QueryStatement> m_queries;
    private Iterator<QueryStatement> m_queryIterator;

    private String m_queryTerm;
    private String m_queryLang;

    protected LiveData<List<DictionaryBookmark>> m_bookmarksList;
    protected MutableLiveData<HashMap<Long, Long>> m_bookmarksLiveData;
    protected Observer<List<DictionaryBookmark>> m_bookmarksObserver;


    public LiveData<HashMap<Long, Long>> getBookmarks() {
        return m_bookmarksLiveData;
    }


    public DictionarySearchFragmentModel(Application aApp) {
        super(aApp);

        m_bookmarksLiveData = new MutableLiveData<>();
        m_searchEntriesLive = new MutableLiveData<>();
    }

    private Observer<List<DictionaryBookmark>> getBookmarksObserver() {
        if (m_bookmarksObserver == null) {
            m_bookmarksObserver = new Observer<List<DictionaryBookmark>>() {
                @Override
                public void onChanged(List<DictionaryBookmark> dictionaryBookmarks) {
                    m_bookmarksLiveData.setValue(DictionaryTypeConverters.hashMapFromBookmarks(dictionaryBookmarks));
                }
            };
        }

        return m_bookmarksObserver;
    }

    @Override
    protected void connectDatabase()
    {
        super.connectDatabase();

        m_bookmarksList = m_db.dictionaryBookmarkDao().getAllLive();
        m_bookmarksList.observeForever(getBookmarksObserver());

        final SupportSQLiteDatabase sqlDb = m_db.getOpenHelper().getWritableDatabase();

        final SupportSQLiteStatement queryExactPrio = sqlDb.compileStatement(SQL_QUERY_EXACT_PRIO);
        final SupportSQLiteStatement queryExactNonPrio = sqlDb.compileStatement(SQL_QUERY_EXACT_NONPRIO);
        final SupportSQLiteStatement queryBeginPrio = sqlDb.compileStatement(SQL_QUERY_BEGIN_PRIO);
        final SupportSQLiteStatement queryBeginNonPrio = sqlDb.compileStatement(SQL_QUERY_BEGIN_NONPRIO);
        final SupportSQLiteStatement queryPartsPrio = sqlDb.compileStatement(SQL_QUERY_PARTS_PRIO);
        final SupportSQLiteStatement queryPartsNonPrio = sqlDb.compileStatement(SQL_QUERY_PARTS_NONPRIO);

        m_queryDeleteResults = sqlDb.compileStatement(SQL_QUERY_DELETE_RESULTS);

        m_queries = new LinkedList<QueryStatement>();

        m_queries.add(new QueryStatement(1, queryExactPrio));
        m_queries.add(new QueryStatement(2, queryExactNonPrio));
        m_queries.add(new QueryStatement(3, queryBeginPrio));
        m_queries.add(new QueryStatement(4, queryBeginNonPrio));
        m_queries.add(new QueryStatement(5, queryPartsPrio));
        m_queries.add(new QueryStatement(6, queryPartsNonPrio));

        m_queryIterator = m_queries.iterator();

         new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                // Should disappear if we are to implement persistance
                m_db.beginTransaction();

                m_queryDeleteResults.executeUpdateDelete();

                m_db.setTransactionSuccessful();
                m_db.endTransaction();

                return null;
            }

            @Override
            protected void onPostExecute(Void arg) {
                final PagedList.Config pagedListConfig =
                        (new PagedList.Config.Builder()).setEnablePlaceholders(false)
                                .setPrefetchDistance(30)
                                .setPageSize(50).build();

                m_searchEntries = (new LivePagedListBuilder(m_db.dictionarySearchResultDao().getAllPaged(),
                        pagedListConfig))
                        .setBoundaryCallback(new PagedList.BoundaryCallback() {
                            @Override
                            public void onItemAtEndLoaded(@NonNull Object itemAtEnd) {
                                super.onItemAtEndLoaded(itemAtEnd);

                                executeNextQuery();
                            }
                        })
                        .build();

                m_searchEntriesLive.setValue(m_searchEntries);
            }
        }.execute();
    }

    public LiveData<PagedList<DictionarySearchElement>> getSearchEntries() {
        return m_searchEntries;
    }

    public LiveData<LiveData<PagedList<DictionarySearchElement>>> getSearchEntriesLive() {
        return m_searchEntriesLive;
    }

    private synchronized long executeNextQueryBackground() {
        long res = -1;

        m_db.beginTransaction();

        // We will need to persist the terms as well
        res = m_queryIterator.next().execute(m_queryTerm, m_queryLang);

        m_db.setTransactionSuccessful();
        m_db.endTransaction();

        return res;
    }


    public boolean executeNextQuery() {
        if (!m_queryIterator.hasNext() || m_queryTerm == null || m_queryLang == null) {
            return false;
        } else {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    while (executeNextQueryBackground() < 0 &&
                            m_queryIterator.hasNext()) {
                    }

                    return null;
                }
            }.execute();

            return true;
        }
    }

    public void search(final String aExpr, final String aLang) {
        m_queryTerm = aExpr;
        m_queryLang = aLang;

        m_queryIterator = m_queries.iterator();

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                SupportSQLiteDatabase sqlDb = m_db.getOpenHelper().getWritableDatabase();

                m_db.beginTransaction();

                m_queryDeleteResults.executeUpdateDelete();

                while (m_queryIterator.next().execute(m_queryTerm, m_queryLang) < 0 &&
                        m_queryIterator.hasNext()) {
                }

                m_db.setTransactionSuccessful();
                m_db.endTransaction();

                return null;
            }
        }.execute();
    }

    public void updateBookmark(final long seq, final long bookmark) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... aParams) {
                if (m_db != null) {
                    if (bookmark != 0) {
                        m_db.dictionaryBookmarkDao().insert(new DictionaryBookmark(seq, bookmark));
                    } else {
                        m_db.dictionaryBookmarkDao().delete(seq);
                    }
                }

                return null;
            }
        }.execute();
    }

    @Override
    public void disconnectDatabase() {
        if (m_searchEntriesLive != null) {
            m_searchEntriesLive.setValue(null);
        }

        if (m_bookmarksList != null) {
            m_bookmarksList.removeObserver(m_bookmarksObserver);
        }

        super.disconnectDatabase();

        try {
            for (QueryStatement qs : m_queries) {
                qs.statement.close();
            }

            m_queries = null;

            m_queryDeleteResults.close();
        } catch (Exception e) {
            System.err.println(e);
        }

        m_queryDeleteResults = null;
    }

    @Override
    public void onCleared()
    {
        disconnectDatabase();

        m_bookmarksLiveData = null;
        m_searchEntriesLive = null;
    }
}
