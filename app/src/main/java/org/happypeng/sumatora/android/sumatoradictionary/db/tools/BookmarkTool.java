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
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;

import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;

public class BookmarkTool {
    static private final String SQL_QUERY_INSERT_BOOKMARK_ELEMENTS =
            "INSERT INTO DictionaryElement SELECT ? as ref, 0 AS entryOrder, DictionaryBookmark.seq "
                    + "FROM DictionaryBookmark";
    static private final String SQL_QUERY_DELETE_BOOKMARK_ELEMENTS =
            "DELETE FROM DictionaryElement WHERE ref = ?";

    private final PersistentDatabase mDB;

    private final int mRef;

    private SupportSQLiteStatement mInsertBookmarkElements;
    private SupportSQLiteStatement mDeleteBookmarkElements;

    public BookmarkTool(final PersistentDatabase aDB, int aRef) {
        mDB = aDB;
        mRef = aRef;
    }

    public boolean isInitialized() {
        return mInsertBookmarkElements != null;
    }

    @WorkerThread
    public synchronized void insertBookmarks() {
        if (!isInitialized()) {
            return;
        }

        mDB.runInTransaction(new Runnable() {
            @Override
            public void run() {
                mDeleteBookmarkElements.bindLong(1, mRef);
                mDeleteBookmarkElements.execute();

                mInsertBookmarkElements.bindLong(1, mRef);
                mInsertBookmarkElements.execute();
            }
        });
    }

    @MainThread
    public void performBookmarkInsertion() {
        new AsyncTask<Void, Void, Void>(){
            @Override
            protected Void doInBackground(Void... voids) {
                insertBookmarks();

                return null;
            }
        }.execute();
    }

    @WorkerThread
    public void createStatement() {
        SupportSQLiteDatabase db = mDB.getOpenHelper().getWritableDatabase();

        mInsertBookmarkElements = db.compileStatement(SQL_QUERY_INSERT_BOOKMARK_ELEMENTS);
        mDeleteBookmarkElements = db.compileStatement(SQL_QUERY_DELETE_BOOKMARK_ELEMENTS);
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

                liveData.setValue(tool);
            }
        }.execute();

        return liveData;
    }
}
