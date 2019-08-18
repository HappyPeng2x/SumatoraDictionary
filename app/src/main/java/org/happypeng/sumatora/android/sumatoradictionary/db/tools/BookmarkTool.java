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

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;

import org.happypeng.sumatora.android.sumatoradictionary.BuildConfig;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BookmarkTool {
    static private final String SQL_QUERY_DELETE =
            "DELETE FROM DictionaryElement WHERE ref = ?";

    static private final String SQL_QUERY_ALL =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, 0 AS entryOrder, DictionaryEntry.seq "
                    + "FROM jmdict.DictionaryEntry "
                    + "WHERE DictionaryEntry.seq IN (SELECT seq FROM DictionaryBookmark)";

    static private final String SQL_QUERY_EXACT_PRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsPrio MATCH 'writingsPrio:' || ? || ' OR readingsPrio:' || ? "
                    + " AND DictionaryIndex.`rowid` IN (SELECT seq FROM DictionaryBookmark)";

    static private final String SQL_QUERY_EXACT_NONPRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsPrio MATCH 'writings:' || ? || ' OR readings:' || ? "
                    + " AND DictionaryIndex.`rowid` IN (SELECT seq FROM DictionaryBookmark)";

    static private final String SQL_QUERY_BEGIN_PRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsPrio MATCH 'writingsPrio:' || ? || '* OR readingsPrio:' || ? || '*'"
                    + " AND DictionaryIndex.`rowid` IN (SELECT seq FROM DictionaryBookmark)";

    static private final String SQL_QUERY_BEGIN_NONPRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsPrio MATCH 'writings:' || ? || '* OR readings:' || ? || '*'"
                    + " AND DictionaryIndex.`rowid` IN (SELECT seq FROM DictionaryBookmark)";

    static private final String SQL_QUERY_PARTS_PRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsPrio MATCH 'writingsPrioParts:' || ? || '* OR readingsPrioParts:' || ? || '*'"
                    + " AND DictionaryIndex.`rowid` IN (SELECT seq FROM DictionaryBookmark)";

    static private final String SQL_QUERY_PARTS_NONPRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsPrio MATCH 'writingsParts:' || ? || '* OR readingsParts:' || ? || '*'"
                    + " AND DictionaryIndex.`rowid` IN (SELECT seq FROM DictionaryBookmark)";

    public static class QueryStatement {
        private final int ref;
        private final int order;
        private final SupportSQLiteStatement statement;

        private final Logger log;

        private QueryStatement(int aRef, int aOrder, SupportSQLiteStatement aStatement) {
            ref = aRef;
            order = aOrder;
            statement = aStatement;

            if (BuildConfig.DEBUG_QUERYTOOL) {
                log = LoggerFactory.getLogger(getClass());

                log.info(this.hashCode() + " constructor order " + order);
            } else {
                log = null;
            }
        }

        @WorkerThread
        private long execute(String term) {
            if (BuildConfig.DEBUG_QUERYTOOL) {
                log.info(this.hashCode() + " execute " + order + " term " + term);
            }

            statement.bindLong(1, ref);
            statement.bindLong(2, order);
            statement.bindString(3, term);
            statement.bindString(4, term);

            return statement.executeInsert();
        }

        public int getOrder() { return order; }
    }

    private static final int PAGE_SIZE = 30;
    private static final int PREFETCH_DISTANCE = 50;

    public static final int STATUS_PRE_INITIALIZED = 0;
    public static final int STATUS_INITIALIZED = 1;
    public static final int STATUS_SEARCHING = 2;
    public static final int STATUS_RESULTS_FOUND = 3;
    public static final int STATUS_NO_RESULTS_FOUND_ENDED = 4;
    public static final int STATUS_RESULTS_FOUND_ENDED = 5;

    private final Logger mLog;

    private QueryStatement[] mQueries;

    private final PersistentDatabase mDB;

    private final MutableLiveData<Integer> mStatus;

    private volatile String mTerm;
    private volatile long mLastInsert;
    private volatile int mQueriesPosition;

    private final int mRef;

    private SupportSQLiteStatement mInsertAllStatement;
    private SupportSQLiteStatement mDeleteStatement;

    public BookmarkTool(final PersistentDatabase aDB, int aRef) {
        if (BuildConfig.DEBUG_QUERYTOOL) {
            mLog = LoggerFactory.getLogger(getClass());

            mLog.info(hashCode() + " constructor");
        } else {
            mLog = null;
        }

        mDB = aDB;
        mRef = aRef;

        mStatus = new MutableLiveData<>();

        mQueriesPosition = 0;
        mLastInsert = -1;
        mQueries = null;

        mTerm = null;

        mStatus.setValue(STATUS_PRE_INITIALIZED);
    }

    public LiveData<Integer> getStatus() { return mStatus; }

    @WorkerThread
    public void createStatement() {
        if (BuildConfig.DEBUG_QUERYTOOL) {
            mLog.info(this.hashCode() + " creating statements");
        }

        SupportSQLiteDatabase db = mDB.getOpenHelper().getWritableDatabase();

        mInsertAllStatement = db.compileStatement(SQL_QUERY_ALL);
        mDeleteStatement = db.compileStatement(SQL_QUERY_DELETE);

        final SupportSQLiteStatement queryExactPrio = db.compileStatement(SQL_QUERY_EXACT_PRIO);
        final SupportSQLiteStatement queryExactNonPrio = db.compileStatement(SQL_QUERY_EXACT_NONPRIO);
        final SupportSQLiteStatement queryBeginPrio = db.compileStatement(SQL_QUERY_BEGIN_PRIO);
        final SupportSQLiteStatement queryBeginNonPrio = db.compileStatement(SQL_QUERY_BEGIN_NONPRIO);
        final SupportSQLiteStatement queryPartsPrio = db.compileStatement(SQL_QUERY_PARTS_PRIO);
        final SupportSQLiteStatement queryPartsNonPrio = db.compileStatement(SQL_QUERY_PARTS_NONPRIO);

        final QueryStatement[] queriesArray = new QueryStatement[6];

        queriesArray[0] = new QueryStatement(mRef,1, queryExactPrio);
        queriesArray[1] = new QueryStatement(mRef,2, queryExactNonPrio);
        queriesArray[2] = new QueryStatement(mRef,3, queryBeginPrio);
        queriesArray[3] = new QueryStatement(mRef,4, queryBeginNonPrio);
        queriesArray[4] = new QueryStatement(mRef,5, queryPartsPrio);
        queriesArray[5] = new QueryStatement(mRef,6, queryPartsNonPrio);

        mQueries = queriesArray;

        if (BuildConfig.DEBUG_QUERYTOOL) {
            mLog.info(this.hashCode() + " statements creation ended");
        }
    }

    public
    LiveData<PagedList<DictionarySearchElement>> getDisplayElements() {
        final PagedList.Config pagedListConfig =
                (new PagedList.Config.Builder()).setEnablePlaceholders(false)
                        .setPrefetchDistance(PAGE_SIZE)
                        .setPageSize(PREFETCH_DISTANCE).build();

        return new LivePagedListBuilder<Integer, DictionarySearchElement>(mDB.dictionaryDisplayElementDao().getAllDetailsLivePaged(mRef),
                pagedListConfig)
                .setBoundaryCallback(new PagedList.BoundaryCallback<DictionarySearchElement>() {
                    @Override
                    public void onItemAtEndLoaded(@NonNull DictionarySearchElement itemAtEnd) {
                        if (BuildConfig.DEBUG_QUERYTOOL) {
                            mLog.info(this.hashCode() + " boundary callback called");
                        }

                        if (!"".equals(mTerm)) {
                            executeNextStatement(false);
                        }

                        super.onItemAtEndLoaded(itemAtEnd);
                    }
                }).build();
    }

    @WorkerThread
    private synchronized void executNextStatementImplementation(final String aTerm, boolean aReset) {
        if (BuildConfig.DEBUG_QUERYTOOL) {
            mLog.info(this.hashCode() + " executeNextStatementImplementation begin");
            mLog.info(this.hashCode() + " current term " + mTerm + " new term " + aTerm);
        }

        mDB.runInTransaction(new Runnable() {
            @Override
            public void run() {
                long lastInsert = -1;

                if (aTerm == null || "".equals(aTerm)) {
                    mStatus.postValue(QueryTool.QueriesList.STATUS_INITIALIZED);

                    mDeleteStatement.bindLong(1, mRef);
                    mDeleteStatement.executeUpdateDelete();

                    mInsertAllStatement.bindLong(1, mRef);
                    mInsertAllStatement.executeInsert();

                    if (aTerm == null) {
                        mTerm = "";
                    } else {
                        mTerm = aTerm;
                    }

                    mLastInsert = -1;
                    mQueriesPosition = 0;
                } else {
                    if (!mTerm.equals(aTerm)) {
                        mQueriesPosition = 0;
                        mTerm = aTerm;
                    }

                    if (mQueriesPosition == 0) {
                        mDeleteStatement.executeUpdateDelete();
                        mLastInsert = -1;
                    }

                    while (lastInsert == -1 && mQueriesPosition < mQueries.length) {
                        lastInsert = mQueries[mQueriesPosition].execute(mTerm);

                        if (BuildConfig.DEBUG_QUERYTOOL) {
                            mLog.info(this.hashCode() + " position " + mQueriesPosition + " result " + lastInsert);
                        }

                        mQueriesPosition = mQueriesPosition + 1;
                    }

                    if (lastInsert >= 0) {
                        mLastInsert = lastInsert;
                    }

                    if (mQueriesPosition >= mQueries.length) {
                        if (mLastInsert >= 0) {
                            mStatus.postValue(STATUS_RESULTS_FOUND_ENDED);
                        } else {
                            mStatus.postValue(STATUS_NO_RESULTS_FOUND_ENDED);
                        }
                    } else {
                        if (mLastInsert >= 0) {
                            mStatus.postValue(STATUS_RESULTS_FOUND);
                        }
                    }
                }
            }
        });

        if (BuildConfig.DEBUG_QUERYTOOL) {
            mLog.info(this.hashCode() + " executeNextStatementImplementation ends");
        }
    }

    @MainThread
    private void executeNextStatement(final boolean aReset) {
        if (mStatus.getValue() == null || mStatus.getValue() < STATUS_INITIALIZED) {
            return;
        }

        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                executNextStatementImplementation(mTerm, aReset);

                return null;
            }
        }.execute();
    }

    @MainThread
    public void setTerm(final String aTerm, final boolean aReset) {
        if (mStatus.getValue() == null || mStatus.getValue() < STATUS_INITIALIZED) {
            return;
        }

        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                executNextStatementImplementation(aTerm, aReset);

                return null;
            }
        }.execute();
    }

    @MainThread
    public static LiveData<BookmarkTool> create(@NonNull final PersistentDatabase aDB, final int aRef)
    {
        final BookmarkTool tool = new BookmarkTool(aDB, aRef);
        final MutableLiveData<BookmarkTool> liveData = new MutableLiveData<>();

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                tool.createStatement();

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                tool.mStatus.setValue(STATUS_INITIALIZED);

                liveData.setValue(tool);
            }
        }.execute();

        return liveData;
    }
}
