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
import android.util.Log;

import org.happypeng.sumatora.android.sumatoradictionary.BuildConfig;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.model.DictionarySearchFragmentModel;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;

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

        private QueryStatement(int aOrder, SupportSQLiteStatement aStatement) {
            order = aOrder;
            statement = aStatement;
        }

        @WorkerThread
        private long execute(String term, String lang) {
            if (BuildConfig.DEBUG_MESSAGE) {
                Log.d("DICT_QUERY", "Execute query " + order + " for term " + term);
            }

            statement.bindLong(1, order);
            statement.bindString(2, term);
            statement.bindString(3, term);
            statement.bindString(4, lang);

            return statement.executeInsert();
        }
    }

    public static class QueriesList {
        public List<QueryStatement> queriesList;
        public SupportSQLiteStatement deleteStatement;
    }

    private static class InitializeQueryAsyncTask extends AsyncTask<Void, Void, QueriesList> {
        private final DictionaryDatabase mDB;
        private final MutableLiveData<QueriesList> mLiveData;

        private InitializeQueryAsyncTask(@NonNull DictionaryDatabase aDB) {
            super();

            mDB = aDB;
            mLiveData = new MutableLiveData<>();
        }

        private LiveData<QueriesList> getLiveData() { return mLiveData; }

        @Override
        protected QueriesList doInBackground(Void... voids) {
            final SupportSQLiteDatabase sqlDb = mDB.getOpenHelper().getWritableDatabase();

            final SupportSQLiteStatement queryExactPrio = sqlDb.compileStatement(SQL_QUERY_EXACT_PRIO);
            final SupportSQLiteStatement queryExactNonPrio = sqlDb.compileStatement(SQL_QUERY_EXACT_NONPRIO);
            final SupportSQLiteStatement queryBeginPrio = sqlDb.compileStatement(SQL_QUERY_BEGIN_PRIO);
            final SupportSQLiteStatement queryBeginNonPrio = sqlDb.compileStatement(SQL_QUERY_BEGIN_NONPRIO);
            final SupportSQLiteStatement queryPartsPrio = sqlDb.compileStatement(SQL_QUERY_PARTS_PRIO);
            final SupportSQLiteStatement queryPartsNonPrio = sqlDb.compileStatement(SQL_QUERY_PARTS_NONPRIO);

            final LinkedList<QueryStatement> queriesList = new LinkedList<QueryStatement>();

            queriesList.add(new QueryStatement(1, queryExactPrio));
            queriesList.add(new QueryStatement(2, queryExactNonPrio));
            queriesList.add(new QueryStatement(3, queryBeginPrio));
            queriesList.add(new QueryStatement(4, queryBeginNonPrio));
            queriesList.add(new QueryStatement(5, queryPartsPrio));
            queriesList.add(new QueryStatement(6, queryPartsNonPrio));

            final QueriesList result = new QueriesList();

            result.queriesList = Collections.unmodifiableList(queriesList);
            result.deleteStatement = sqlDb.compileStatement(SQL_QUERY_DELETE_RESULTS);

            return result;
        }

        @Override
        protected void onPostExecute(QueriesList aList) {
            mLiveData.setValue(aList);
        }
    }

    private static class ExecuteNextStatementAsyncTask extends AsyncTask<Void, Void, Void>  {
        private final DictionaryDatabase mDB;
        private final String mQueryTerm;
        private final String mLang;
        private final Iterator<QueryStatement> mIterator;

        ExecuteNextStatementAsyncTask(@NonNull DictionaryDatabase aDB,
                                      @NonNull String aQueryTerm, @NonNull String aLang,
                                      @NonNull Iterator<QueryStatement> aIterator) {
            super();

            mDB = aDB;
            mQueryTerm = aQueryTerm;
            mLang = aLang;
            mIterator = aIterator;
        }

        @Override
        protected final Void doInBackground(Void... params) {
            long res = -1;

            mDB.beginTransaction();

            while (mIterator.hasNext() && res < 0) {
                res = mIterator.next().execute(mQueryTerm, mLang);
            }

            mDB.setTransactionSuccessful();
            mDB.endTransaction();

            return null;
        }
    }

    public static LiveData<QueriesList> getQueriesList(@NonNull DictionaryDatabase aDB) {
        final InitializeQueryAsyncTask asyncTask = new InitializeQueryAsyncTask(aDB);

        asyncTask.execute();

        return asyncTask.getLiveData();
    }

    public static void executeNextStatement(@NonNull DictionaryDatabase aDB,
                                            @NonNull String aQueryTerm, @NonNull String aLang,
                                            @NonNull Iterator<QueryStatement> aIterator) {
        new ExecuteNextStatementAsyncTask(aDB, aQueryTerm, aLang, aIterator).execute();
    }
}
