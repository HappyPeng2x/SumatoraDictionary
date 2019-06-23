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

package org.happypeng.sumatora.android.sumatoradictionary.db.tools;

import android.os.AsyncTask;

import org.happypeng.sumatora.android.sumatoradictionary.BuildConfig;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import androidx.arch.core.util.Function;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryTool {
    static private final String SQL_QUERY_EXACT_PRIO =
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

    static private final String SQL_QUERY_EXACT_NONPRIO =
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

    static private final String SQL_QUERY_BEGIN_PRIO =
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

    static private final String SQL_QUERY_BEGIN_NONPRIO =
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

    static private final String SQL_QUERY_PARTS_PRIO =
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

    static private final String SQL_QUERY_PARTS_NONPRIO =
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

    static private final String SQL_QUERY_DELETE_RESULTS =
            "DELETE FROM DictionarySearchResult";

    public static class QueryStatement {
        private final int order;
        private final SupportSQLiteStatement statement;

        private final Logger log;

        private QueryStatement(int aOrder, SupportSQLiteStatement aStatement) {
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
        private long execute(String term, String lang) {
            if (BuildConfig.DEBUG_QUERYTOOL) {
                log.info(this.hashCode() + " execute " + order + " term " + term);
            }

            statement.bindLong(1, order);
            statement.bindString(2, term);
            statement.bindString(3, term);
            statement.bindString(4, lang);

            return statement.executeInsert();
        }

        public int getOrder() { return order; }
    }

    public static class QueriesList {
        private static final int PAGE_SIZE = 30;
        private static final int PREFETCH_DISTANCE = 50;

        public static final int STATUS_PRE_INITIALIZED = 0;
        public static final int STATUS_INITIALIZED = 1;
        public static final int STATUS_SEARCHING = 2;
        public static final int STATUS_RESULTS_FOUND = 3;
        public static final int STATUS_NO_RESULTS_FOUND_ENDED = 4;
        public static final int STATUS_RESULTS_FOUND_ENDED = 5;

        private final Logger mLog;

        private static class Statements {
            private QueryStatement[] mQueries;
            private SupportSQLiteStatement mDeleteStatement;
        }

        private final MutableLiveData<Statements> mStatements;

        private final DictionaryDatabase mDB;

        private final MutableLiveData<Integer> mStatus;

        private volatile String mTerm;
        private volatile String mLang;
        private volatile String mBackupLang;

        private volatile long mLastInsert;
        private volatile int mQueriesPosition;

        private final LiveData<PagedList<DictionarySearchElement>> mSearchEntries;

        public QueriesList(final DictionaryDatabase aDB) {
            if (BuildConfig.DEBUG_QUERYTOOL) {
                mLog = LoggerFactory.getLogger(getClass());

                mLog.info(hashCode() + " constructor");
            } else {
                mLog = null;
            }

            mDB = aDB;

            mStatus = new MutableLiveData<>();

            mQueriesPosition = 0;

            mStatements = new MutableLiveData<>();

            mLastInsert = -1;

            mStatus.setValue(STATUS_PRE_INITIALIZED);

            final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    if (BuildConfig.DEBUG_QUERYTOOL) {
                        mLog.info(QueriesList.this.hashCode() + " init task started");
                    }

                    final SupportSQLiteDatabase sqlDb = mDB.getOpenHelper().getWritableDatabase();

                    final SupportSQLiteStatement queryExactPrio = sqlDb.compileStatement(SQL_QUERY_EXACT_PRIO);
                    final SupportSQLiteStatement queryExactNonPrio = sqlDb.compileStatement(SQL_QUERY_EXACT_NONPRIO);
                    final SupportSQLiteStatement queryBeginPrio = sqlDb.compileStatement(SQL_QUERY_BEGIN_PRIO);
                    final SupportSQLiteStatement queryBeginNonPrio = sqlDb.compileStatement(SQL_QUERY_BEGIN_NONPRIO);
                    final SupportSQLiteStatement queryPartsPrio = sqlDb.compileStatement(SQL_QUERY_PARTS_PRIO);
                    final SupportSQLiteStatement queryPartsNonPrio = sqlDb.compileStatement(SQL_QUERY_PARTS_NONPRIO);

                    final QueryStatement[] queriesArray = new QueryStatement[6];

                    queriesArray[0] = new QueryStatement(1, queryExactPrio);
                    queriesArray[1] = new QueryStatement(2, queryExactNonPrio);
                    queriesArray[2] = new QueryStatement(3, queryBeginPrio);
                    queriesArray[3] = new QueryStatement(4, queryBeginNonPrio);
                    queriesArray[4] = new QueryStatement(5, queryPartsPrio);
                    queriesArray[5] = new QueryStatement(6, queryPartsNonPrio);

                    final Statements s = new Statements();

                    s.mQueries = queriesArray;
                    s.mDeleteStatement = sqlDb.compileStatement(SQL_QUERY_DELETE_RESULTS);

                    // We clear the results here. In the future there will be persistence.
                    mDB.beginTransaction();

                    s.mDeleteStatement.executeUpdateDelete();

                    mDB.setTransactionSuccessful();
                    mDB.endTransaction();

                    mStatements.postValue(s);
                    mStatus.postValue(STATUS_INITIALIZED);

                    if (BuildConfig.DEBUG_QUERYTOOL) {
                        mLog.info(QueriesList.this.hashCode() + " init task ended");
                    }

                    return null;
                }
            }.execute();

            final PagedList.Config pagedListConfig =
                    (new PagedList.Config.Builder()).setEnablePlaceholders(false)
                            .setPrefetchDistance(PAGE_SIZE)
                            .setPageSize(PREFETCH_DISTANCE).build();

            mSearchEntries = Transformations.switchMap(mStatements,
                    new Function<Statements, LiveData<PagedList<DictionarySearchElement>>>() {
                        @Override
                        public LiveData<PagedList<DictionarySearchElement>> apply(Statements input) {
                            return new LivePagedListBuilder<Integer, DictionarySearchElement>(aDB.dictionarySearchResultDao().getAllPaged(),
                                    pagedListConfig)
                                    .setBoundaryCallback(new PagedList.BoundaryCallback<DictionarySearchElement>() {
                                        @Override
                                        public void onItemAtEndLoaded(@NonNull DictionarySearchElement itemAtEnd) {
                                            if (BuildConfig.DEBUG_QUERYTOOL) {
                                                mLog.info(QueriesList.this.hashCode() + " boundary callback called");
                                            }

                                            super.onItemAtEndLoaded(itemAtEnd);

                                            executeNextStatement();
                                        }
                                    }).build();
                        }
                    });

            mTerm = "";
        }

        public LiveData<Integer> getStatus() { return mStatus; }

        private SupportSQLiteStatement getDeleteStatement() {
            if (mStatements.getValue() != null) {
                return mStatements.getValue().mDeleteStatement;
            } else {
                return null;
            }
        }

        @WorkerThread
        private synchronized void executNextStatementImplementation(final boolean aResetTerm, final String aTerm, final String aLang,
                                                                    final String aBackupLang) {
            if (BuildConfig.DEBUG_QUERYTOOL) {
                mLog.info(QueriesList.this.hashCode() + " executeNextStatementImplementation begin");
            }

            mDB.runInTransaction(new Runnable() {
                @Override
                public void run() {
                    long lastInsert = -1;
                    long backupLastInsert = -1;

                    if (aResetTerm) {
                        if (!"".equals(aTerm)) {
                            mStatus.postValue(QueriesList.STATUS_SEARCHING);
                        } else {
                            mStatus.postValue(QueriesList.STATUS_INITIALIZED);
                        }

                        mStatements.getValue().mDeleteStatement.executeUpdateDelete();

                        mTerm = aTerm;
                        mLang = aLang;
                        mBackupLang = aBackupLang;

                        mLastInsert = -1;
                        mQueriesPosition = 0;
                    }

                    if (!"".equals(aTerm)) {
                        while (lastInsert == -1 && mQueriesPosition < mStatements.getValue().mQueries.length) {
                            lastInsert = mStatements.getValue().mQueries[mQueriesPosition].execute(aTerm, aLang);

                            if (BuildConfig.DEBUG_QUERYTOOL) {
                                mLog.info(QueriesList.this.hashCode() + " position " + mQueriesPosition + " lang " + aLang + " result " + lastInsert);
                            }

                            if (aBackupLang != null && !mBackupLang.equals(aLang)) {
                                backupLastInsert = mStatements.getValue().mQueries[mQueriesPosition].execute(aTerm, aBackupLang);

                                if (BuildConfig.DEBUG_QUERYTOOL) {
                                    mLog.info(QueriesList.this.hashCode() + " position " + mQueriesPosition + " lang " + aBackupLang + " result " + lastInsert);
                                }

                                if (lastInsert == -1) {
                                    lastInsert = backupLastInsert;
                                }
                            }

                            mQueriesPosition = mQueriesPosition + 1;
                        }

                        if (lastInsert >= 0) {
                            mLastInsert = lastInsert;
                        }

                        if (mQueriesPosition >= mStatements.getValue().mQueries.length) {
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
                mLog.info(QueriesList.this.hashCode() + " executeNextStatementImplementation ends");
            }
        }

        void executeNextStatement() {
            if (mStatus.getValue() < STATUS_INITIALIZED) {
                return;
            }

            final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    executNextStatementImplementation(false, mTerm, mLang, mBackupLang);

                    return null;
                }
            }.execute();
        }

        public LiveData<PagedList<DictionarySearchElement>> getSearchEntries() {
            return mSearchEntries;
        }

        public void setTerm(@NonNull final String aTerm, @NonNull final String aLang, final String aBackupLang) {
            if (mStatements.getValue() == null) {
                return;
            }

            if (mStatus.getValue() < STATUS_INITIALIZED) {
                return;
            }

            final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    executNextStatementImplementation(true, aTerm, aLang, aBackupLang);

                    return null;
                }
            }.execute();
        }
    }
}
