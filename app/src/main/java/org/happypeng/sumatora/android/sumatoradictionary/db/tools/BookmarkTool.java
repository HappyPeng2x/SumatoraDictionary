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
import androidx.annotation.WorkerThread;
import androidx.lifecycle.MutableLiveData;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;

import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;

public class BookmarkTool {
    static private final String SQL_QUERY_INSERT_BOOKMARK_ELEMENTS =
            "INSERT INTO DictionaryBookmarkElement SELECT 0 AS entryOrder, DictionaryEntry.seq, DictionaryEntry.readingsPrio, DictionaryEntry.readings, "
                    + "DictionaryEntry.writingsPrio, DictionaryEntry.writings, DictionaryTranslation.lang, "
                    + "DictionaryTranslation.gloss, 1 "
                    + "FROM DictionaryBookmark, jmdict.DictionaryEntry, jmdict.DictionaryTranslation "
                    + "WHERE DictionaryBookmark.seq NOT IN (SELECT seq FROM DictionaryBookmarkElement) AND DictionaryBookmark.seq = DictionaryEntry.seq AND DictionaryBookmark.seq = DictionaryTranslation.seq AND DictionaryTranslation.lang = ? ";
    static private final String SQL_QUERY_DELETE_BOOKMARK_ELEMENTS =
            "DELETE FROM DictionaryBookmarkElement";

    private final PersistentDatabase mDB;

    private SupportSQLiteStatement mInsertBookmarkElements;
    private SupportSQLiteStatement mDeleteBookmarkElements;

    public BookmarkTool(PersistentDatabase aDB) {
        mDB = aDB;
    }

    public boolean isInitialized() {
        return mInsertBookmarkElements != null;
    }

    @WorkerThread
    public synchronized void insertBookmarks(final String aLang, final String aBackupLang) {
        if (!isInitialized()) {
            return;
        }

        mDB.runInTransaction(new Runnable() {
            @Override
            public void run() {
                mDeleteBookmarkElements.execute();

                mInsertBookmarkElements.bindString(1, aLang);
                mInsertBookmarkElements.execute();

                mInsertBookmarkElements.bindString(1, aBackupLang);
                mInsertBookmarkElements.execute();
            }
        });
    }

    @MainThread
    public void performBookmarkInsertion(final String aLang, final String aBackupLang) {
        new AsyncTask<Void, Void, Void>(){
            @Override
            protected Void doInBackground(Void... voids) {
                insertBookmarks(aLang, aBackupLang);

                return null;
            }
        }.execute();
    }

    @WorkerThread
    public void createStatements() {
        SupportSQLiteDatabase db = mDB.getOpenHelper().getWritableDatabase();

        mInsertBookmarkElements = db.compileStatement(SQL_QUERY_INSERT_BOOKMARK_ELEMENTS);
        mDeleteBookmarkElements = db.compileStatement(SQL_QUERY_DELETE_BOOKMARK_ELEMENTS);
    }
}
