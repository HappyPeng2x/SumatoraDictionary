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

import android.net.Uri;
import android.os.AsyncTask;

import androidx.annotation.MainThread;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryApplication;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.xml.DictionaryBookmarkXML;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class BookmarkImportTool {
    static private final String SQL_QUERY_INSERT_BOOKMARK_IMPORT =
            "INSERT INTO DictionaryBookmarkImport SELECT 0 AS entryOrder, DictionaryEntry.seq, DictionaryEntry.readingsPrio, DictionaryEntry.readings, "
                    + "DictionaryEntry.writingsPrio, DictionaryEntry.writings, DictionaryTranslation.lang, "
                    + "DictionaryTranslation.gloss, 1 "
                    + "FROM jmdict.DictionaryEntry, jmdict.DictionaryTranslation "
                    + "WHERE DictionaryEntry.seq = ? AND (DictionaryEntry.seq NOT IN (SELECT seq FROM DictionaryBookmarkImport)) AND DictionaryEntry.seq = DictionaryTranslation.seq AND DictionaryTranslation.lang = ? ";
    static private final String SQL_QUERY_DELETE_BOOKMARK_IMPORT =
            "DELETE FROM DictionaryBookmarkImport";

    public static final int STATUS_INITIALIZED = 1;
    public static final int STATUS_PROCESSING = 2;
    public static final int STATUS_PROCESSED = 3;
    public static final int STATUS_ERROR = -1;

    private final PersistentDatabase mDB;
    private final DictionaryApplication mApp;

    private SupportSQLiteStatement mInsertBookmarkElements;
    private SupportSQLiteStatement mDeleteBookmarkElements;

    private MutableLiveData<Integer> mStatus;

    public LiveData<Integer> getStatus() { return mStatus; }

    public BookmarkImportTool(final PersistentDatabase aDB,
                              final DictionaryApplication aApp) {
        mDB = aDB;
        mApp = aApp;

        mStatus = new MutableLiveData<>();
    }

    // Must be executed in a transaction
    @WorkerThread
    private void deleteBookmarks() {
        if (mDeleteBookmarkElements == null) {
            return;
        }

        mDeleteBookmarkElements.execute();
    }

    @MainThread
    public void cancelImport() {
        if (mDeleteBookmarkElements == null) {
            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                mDB.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        deleteBookmarks();
                    }
                });

                mStatus.postValue(STATUS_INITIALIZED);

                return null;
            }
        }.execute();
    }

    @MainThread
    public void processURI(final Uri aUri, final String aLang, final String aBackupLang) {
        if (mDeleteBookmarkElements == null || mInsertBookmarkElements == null) {
            return;
        }

        mStatus.setValue(STATUS_PROCESSING);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    InputStream is = mApp.getContentResolver().openInputStream(aUri);
                    final List<Long> seqs = DictionaryBookmarkXML.readXML(is);
                    is.close();

                    if (seqs == null) {
                        mStatus.postValue(STATUS_ERROR);

                        return null;
                    }

                    mDB.runInTransaction(new Runnable() {
                        @Override
                        public void run() {
                            deleteBookmarks();

                            for (Long seq : seqs) {
                                insertBookmarks(seq, aLang, aBackupLang);
                            }
                        }
                    });

                    mStatus.postValue(STATUS_PROCESSED);
                } catch (IOException e) {
                    System.err.println(e.toString());

                    mStatus.postValue(STATUS_ERROR);
                }

                return null;
            }
        }.execute();
    }

    // Must be executed in a transaction
    @WorkerThread
    private void insertBookmarks(final long aSeq, final String aLang, final String aBackupLang) {
        if (mInsertBookmarkElements == null) {
            return;
        }

        mInsertBookmarkElements.bindLong(1, aSeq);
        mInsertBookmarkElements.bindString(2, aLang);
        mInsertBookmarkElements.execute();

        mInsertBookmarkElements.bindLong(1, aSeq);
        mInsertBookmarkElements.bindString(2, aBackupLang);
        mInsertBookmarkElements.execute();

    }

    @WorkerThread
    public void createStatements() {
        SupportSQLiteDatabase db = mDB.getOpenHelper().getWritableDatabase();

        mInsertBookmarkElements = db.compileStatement(SQL_QUERY_INSERT_BOOKMARK_IMPORT);
        mDeleteBookmarkElements = db.compileStatement(SQL_QUERY_DELETE_BOOKMARK_IMPORT);

        mStatus.postValue(STATUS_INITIALIZED);
    }
}
