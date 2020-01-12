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
import org.happypeng.sumatora.jromkan.Romkan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryTool {
    static private final String SQL_QUERY_DELETE =
            "DELETE FROM DictionaryElement WHERE ref = ?";

    static private final String SQL_QUERY_ALL =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, 0 AS entryOrder, DictionaryEntry.seq "
                    + "FROM jmdict.DictionaryEntry";

    static private final String SQL_QUERY_EXACT_WRITING_PRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsPrio MATCH ?";

    static private final String SQL_QUERY_EXACT_READING_PRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsPrioKana MATCH ?";


    static private final String SQL_QUERY_EXACT_WRITING_NONPRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writings MATCH ?";

    static private final String SQL_QUERY_EXACT_READING_NONPRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsKana MATCH ?";

    static private final String SQL_QUERY_BEGIN_WRITING_PRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsPrio MATCH ? || '*'";

    static private final String SQL_QUERY_BEGIN_READING_PRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsPrioKana MATCH ? || '*'";

    static private final String SQL_QUERY_BEGIN_WRITING_NONPRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writings MATCH ? || '*'";

    static private final String SQL_QUERY_BEGIN_READING_NONPRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsKana MATCH ? || '*'";

    static private final String SQL_QUERY_PARTS_WRITING_PRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsPrioParts MATCH ? || '*'";

    static private final String SQL_QUERY_PARTS_READING_PRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsPrioKanaParts MATCH ? || '*'";

    static private final String SQL_QUERY_PARTS_WRITING_NONPRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsParts MATCH ? || '*'";

    static private final String SQL_QUERY_PARTS_READING_NONPRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsKanaParts MATCH ? || '*'";

    static private final String SQL_QUERY_TRANSLATION_START =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryTranslationIndex.`rowid` AS seq "
                    + "FROM ";
    static private final String SQL_QUERY_TRANSLATION_END =
            ".DictionaryTranslationIndex WHERE gloss MATCH ?";
    static private final String SQL_QUERY_TRANSLATION_BEGIN_END =
            ".DictionaryTranslationIndex WHERE gloss MATCH ? || '*'";

    public static class QueryStatement {
        private final int ref;
        private final int order;
        private final SupportSQLiteStatement statement;
        private final boolean kana;
        private final Romkan romkan;

        private final Logger log;

        private QueryStatement(int aRef, int aOrder, final SupportSQLiteStatement aStatement,
                               boolean aKana, final Romkan aRomkan) {
            ref = aRef;
            order = aOrder;
            statement = aStatement;
            kana = aKana;
            romkan = aRomkan;

            if (BuildConfig.DEBUG_QUERYTOOL) {
                log = LoggerFactory.getLogger(getClass());

                log.info(this.hashCode() + " constructor order " + order);
            } else {
                log = null;
            }
        }

        @WorkerThread
        private long execute(String term) {
            String bindTerm = term;

            if (BuildConfig.DEBUG_QUERYTOOL) {
                log.info(this.hashCode() + " execute " + order + " term " + term);
            }

            if (kana) {
                bindTerm = romkan.to_katakana(romkan.to_hepburn(term));
            }

            statement.bindLong(1, ref);
            statement.bindLong(2, order);
            statement.bindString(3, bindTerm);

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
    private final Romkan mRomkan;

    private final MutableLiveData<Integer> mStatus;

    private volatile String mTerm;
    private volatile long mLastInsert;
    private volatile int mQueriesPosition;

    private final int mRef;

    private SupportSQLiteStatement mInsertAllStatement;
    private SupportSQLiteStatement mDeleteStatement;

    final private String mSearchSet;

    final private boolean mAllowQueryAll;

    public QueryTool(final PersistentDatabase aDB, int aRef, String aSearchSet, boolean aAllowQueryAll,
                     final Romkan aRomkan) {
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

        mTerm = "";

        mStatus.setValue(STATUS_PRE_INITIALIZED);

        mSearchSet = aSearchSet;

        mAllowQueryAll = aAllowQueryAll;

        mRomkan = aRomkan;
    }

    public LiveData<Integer> getStatus() { return mStatus; }

    @MainThread
    public void setLang(final String aLang) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                setLangImplementation(aLang);

                return null;
            }
        }.execute();
    }

    @WorkerThread
    private void setLangImplementation(final String aLang) {
        final String translationStatementStart =
                SQL_QUERY_TRANSLATION_START + aLang + SQL_QUERY_TRANSLATION_END;
        final SupportSQLiteStatement queryTranslation =
                mDB.compileStatement(mSearchSet == null ? translationStatementStart :
                        translationStatementStart + " AND DictionaryTranslationIndex.`rowid` IN (" + mSearchSet + ")");

        final String translationBeginStatementStart =
                SQL_QUERY_TRANSLATION_START + aLang + SQL_QUERY_TRANSLATION_BEGIN_END;
        final SupportSQLiteStatement queryTranslationBegin =
                mDB.compileStatement(mSearchSet == null ? translationBeginStatementStart :
                        translationBeginStatementStart + " AND DictionaryTranslationIndex.`rowid` IN (" + mSearchSet + ")");

        mQueries[12] = new QueryStatement(mRef, 13, queryTranslation, false, mRomkan);
        mQueries[13] = new QueryStatement(mRef, 14, queryTranslationBegin, false, mRomkan);
    }

    @WorkerThread
    public void createStatement() {
        if (BuildConfig.DEBUG_QUERYTOOL) {
            mLog.info(this.hashCode() + " creating statements");
        }

        SupportSQLiteDatabase db = mDB.getOpenHelper().getWritableDatabase();

        if (mAllowQueryAll) {
            mInsertAllStatement = db.compileStatement(mSearchSet == null ? SQL_QUERY_ALL :
                    SQL_QUERY_ALL + " WHERE DictionaryEntry.seq IN (" + mSearchSet + ")");
        }

        mDeleteStatement = db.compileStatement(SQL_QUERY_DELETE);

        final SupportSQLiteStatement queryExactPrioWriting =
                db.compileStatement(mSearchSet == null ? SQL_QUERY_EXACT_WRITING_PRIO :
                        SQL_QUERY_EXACT_WRITING_PRIO + " AND DictionaryIndex.`rowid` IN (" + mSearchSet + ")");
        final SupportSQLiteStatement queryExactPrioReading =
                db.compileStatement(mSearchSet == null ? SQL_QUERY_EXACT_READING_PRIO :
                        SQL_QUERY_EXACT_READING_PRIO + " AND DictionaryIndex.`rowid` IN (" + mSearchSet + ")");
        final SupportSQLiteStatement queryExactNonPrioWriting =
                db.compileStatement(mSearchSet == null ? SQL_QUERY_EXACT_WRITING_NONPRIO :
                        SQL_QUERY_EXACT_WRITING_NONPRIO + " AND DictionaryIndex.`rowid` IN (" + mSearchSet + ")");
        final SupportSQLiteStatement queryExactNonPrioReading =
                db.compileStatement(mSearchSet == null ? SQL_QUERY_EXACT_READING_NONPRIO :
                        SQL_QUERY_EXACT_READING_NONPRIO + " AND DictionaryIndex.`rowid` IN (" + mSearchSet + ")");
        final SupportSQLiteStatement queryBeginPrioWriting =
                db.compileStatement(mSearchSet == null ? SQL_QUERY_BEGIN_WRITING_PRIO :
                        SQL_QUERY_BEGIN_WRITING_PRIO + " AND DictionaryIndex.`rowid` IN (" + mSearchSet + ")");
        final SupportSQLiteStatement queryBeginPrioReading =
                db.compileStatement(mSearchSet == null ? SQL_QUERY_BEGIN_READING_PRIO :
                        SQL_QUERY_BEGIN_READING_PRIO + " AND DictionaryIndex.`rowid` IN (" + mSearchSet + ")");
        final SupportSQLiteStatement queryBeginNonPrioWriting =
                db.compileStatement(mSearchSet == null ? SQL_QUERY_BEGIN_WRITING_NONPRIO :
                        SQL_QUERY_BEGIN_WRITING_NONPRIO + " AND DictionaryIndex.`rowid` IN (" + mSearchSet + ")");
        final SupportSQLiteStatement queryBeginNonPrioReading =
                db.compileStatement(mSearchSet == null ? SQL_QUERY_BEGIN_READING_NONPRIO :
                        SQL_QUERY_BEGIN_READING_NONPRIO + " AND DictionaryIndex.`rowid` IN (" + mSearchSet + ")");
        final SupportSQLiteStatement queryPartsPrioWriting =
                db.compileStatement(mSearchSet == null ? SQL_QUERY_PARTS_WRITING_PRIO :
                        SQL_QUERY_PARTS_WRITING_PRIO + " AND DictionaryIndex.`rowid` IN (" + mSearchSet + ")");
        final SupportSQLiteStatement queryPartsPrioReading =
                db.compileStatement(mSearchSet == null ? SQL_QUERY_PARTS_READING_PRIO :
                        SQL_QUERY_PARTS_READING_PRIO + " AND DictionaryIndex.`rowid` IN (" + mSearchSet + ")");
        final SupportSQLiteStatement queryPartsNonPrioWriting =
                db.compileStatement(mSearchSet == null ? SQL_QUERY_PARTS_WRITING_NONPRIO :
                        SQL_QUERY_PARTS_WRITING_NONPRIO + " AND DictionaryIndex.`rowid` IN (" + mSearchSet + ")");
        final SupportSQLiteStatement queryPartsNonPrioReading =
                db.compileStatement(mSearchSet == null ? SQL_QUERY_PARTS_READING_NONPRIO :
                        SQL_QUERY_PARTS_READING_NONPRIO + " AND DictionaryIndex.`rowid` IN (" + mSearchSet + ")");

        final QueryStatement[] queriesArray = new QueryStatement[14];

        queriesArray[0] = new QueryStatement(mRef,1, queryExactPrioWriting, false, mRomkan);
        queriesArray[1] = new QueryStatement(mRef,2, queryExactPrioReading, true, mRomkan);
        queriesArray[2] = new QueryStatement(mRef,3, queryExactNonPrioWriting, false, mRomkan);
        queriesArray[3] = new QueryStatement(mRef,4, queryExactNonPrioReading, true, mRomkan);
        queriesArray[4] = new QueryStatement(mRef,5, queryBeginPrioWriting, false, mRomkan);
        queriesArray[5] = new QueryStatement(mRef,6, queryBeginPrioReading, true, mRomkan);
        queriesArray[6] = new QueryStatement(mRef,7, queryBeginNonPrioWriting, false, mRomkan);
        queriesArray[7] = new QueryStatement(mRef,8, queryBeginNonPrioReading, true, mRomkan);
        queriesArray[8] = new QueryStatement(mRef,9, queryPartsPrioWriting, false, mRomkan);
        queriesArray[9] = new QueryStatement(mRef,10, queryPartsPrioReading, true, mRomkan);
        queriesArray[10] = new QueryStatement(mRef,11, queryPartsNonPrioWriting, false, mRomkan);
        queriesArray[11] = new QueryStatement(mRef,12, queryPartsNonPrioReading, true, mRomkan);

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

        return new LivePagedListBuilder<>
                (new RoomFactoryWrapper<>(mDB.dictionaryDisplayElementDao().getAllDetailsLivePaged(mRef)), pagedListConfig)
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
    private synchronized void executNextStatementImplementation(@NonNull final String aTerm, final boolean aReset) {
        if (BuildConfig.DEBUG_QUERYTOOL) {
            mLog.info(this.hashCode() + " executeNextStatementImplementation begin");
            mLog.info(this.hashCode() + " current term " + mTerm + " new term " + aTerm);
        }

        mDB.runInTransaction(new Runnable() {
            @Override
            public void run() {
                long lastInsert = -1;

                if ("".equals(aTerm) && (!mTerm.equals(aTerm) || aReset)) {
                    mStatus.postValue(STATUS_INITIALIZED);

                    mDeleteStatement.bindLong(1, mRef);
                    mDeleteStatement.executeUpdateDelete();

                    if (mAllowQueryAll) {

                        mInsertAllStatement.bindLong(1, mRef);
                        mInsertAllStatement.executeInsert();
                    }

                    mTerm = aTerm;

                    mLastInsert = -1;
                    mQueriesPosition = 0;
                } else {
                    if (!mTerm.equals(aTerm) || aReset) {
                        mQueriesPosition = 0;
                        mTerm = aTerm;
                    }

                    if (mQueriesPosition == 0) {
                        mDeleteStatement.bindLong(1, mRef);
                        mDeleteStatement.executeUpdateDelete();
                        mLastInsert = -1;
                    }

                    while (lastInsert == -1 && mQueriesPosition < mQueries.length) {
                        if (mQueries[mQueriesPosition] != null) {
                            lastInsert = mQueries[mQueriesPosition].execute(mTerm);
                        }

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
        if (mStatus.getValue() == null || mStatus.getValue() < STATUS_INITIALIZED || mQueriesPosition >= mQueries.length) {
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
    public void setTerm(@NonNull final String aTerm, final boolean aReset) {
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

    public String getTerm() {
        return mTerm;
    }

    @MainThread
    public static LiveData<QueryTool> create(@NonNull final PersistentDatabase aDB, final int aRef,
                                             String aSearchSet, boolean aAllowQueryAll, final Romkan aRomkan)
    {
        final QueryTool tool = new QueryTool(aDB, aRef, aSearchSet, aAllowQueryAll, aRomkan);
        final MutableLiveData<QueryTool> liveData = new MutableLiveData<>();

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                tool.createStatement();

                tool.mDB.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        tool.mDeleteStatement.bindLong(1, aRef);
                        tool.mDeleteStatement.executeUpdateDelete();
                    }
                });

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
