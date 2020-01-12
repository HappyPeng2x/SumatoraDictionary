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

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryApplication;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;

import java.util.HashMap;
import java.util.List;

public class DisplayTool {
    static private final String SQL_QUERY_INSERT_DISPLAY_ELEMENT_BEGIN =
            "INSERT OR IGNORE INTO DictionaryDisplayElement SELECT DictionaryElement.ref, DictionaryElement.entryOrder, DictionaryElement.seq, "
                    + "DictionaryEntry.readingsPrio, DictionaryEntry.readings, "
                    + "DictionaryEntry.writingsPrio, DictionaryEntry.writings, "
                    + "DictionaryEntry.pos, DictionaryEntry.xref, DictionaryEntry.ant, "
                    + "DictionaryEntry.misc, DictionaryEntry.lsource, DictionaryEntry.dial, "
                    + "DictionaryEntry.s_inf, DictionaryEntry.field, "
                    + "?, "
                    + "translation.gloss "
                    + "FROM DictionaryElement, jmdict.DictionaryEntry, ";
    static private final String SQL_QUERY_INSERT_DISPLAY_ELEMENT_END = ".DictionaryTranslation AS translation "
                    + "WHERE DictionaryElement.seq = DictionaryEntry.seq AND DictionaryElement.seq = translation.seq AND DictionaryElement.ref = ?";
    static private final String SQL_QUERY_DELETE_DISPLAY_ELEMENT =
            "DELETE FROM DictionaryDisplayElement WHERE DictionaryDisplayElement.ref = ?";

    private final PersistentDatabase mDB;
    private final List<InstalledDictionary> mDictionaries;

    private final int mRef;

    private final HashMap<String, SupportSQLiteStatement> mInsertDisplayElementStatements;

    private SupportSQLiteStatement mDeleteDisplayElement;

    public DisplayTool(final PersistentDatabase aDB, final List<InstalledDictionary> aDictionaries, int aRef) {
        mDB = aDB;
        mRef = aRef;
        mDictionaries = aDictionaries;

        mInsertDisplayElementStatements = new HashMap<>();
    }

    public boolean isInitialized() {
        return mDeleteDisplayElement != null;
    }

    @WorkerThread
    public synchronized void insertDisplayElement(final String aLang, final String aBackupLang) {
        if (!isInitialized()) {
            return;
        }

        mDB.runInTransaction(new Runnable() {
            @Override
            public void run() {
                mDeleteDisplayElement.bindLong(1, mRef);
                mDeleteDisplayElement.execute();

                SupportSQLiteStatement statement = mInsertDisplayElementStatements.get(aLang);

                if (statement != null) {
                    statement.bindString(1, aLang);
                    statement.bindLong(2, mRef);
                    statement.execute();
                }

                statement = mInsertDisplayElementStatements.get(aBackupLang);

                if (statement != null) {
                    statement.bindString(1, aLang);
                    statement.bindLong(2, mRef);
                    statement.execute();
                }
            }
        });
    }

    @MainThread
    public void performDisplayElementInsertion(final String aLang, final String aBackupLang) {
        new AsyncTask<Void, Void, Void>(){
            @Override
            protected Void doInBackground(Void... voids) {
                insertDisplayElement(aLang, aBackupLang);

                return null;
            }
        }.execute();
    }

    @WorkerThread
    public void createStatement() {
        SupportSQLiteDatabase db = mDB.getOpenHelper().getWritableDatabase();

        for (InstalledDictionary d : mDictionaries) {
            if ("jmdict_translation".equals(d.type)) {
                mInsertDisplayElementStatements.put(d.lang, db.compileStatement(SQL_QUERY_INSERT_DISPLAY_ELEMENT_BEGIN +
                        d.lang + SQL_QUERY_INSERT_DISPLAY_ELEMENT_END));
            }
        }

        mDeleteDisplayElement = db.compileStatement(SQL_QUERY_DELETE_DISPLAY_ELEMENT);
    }

    public interface InitCallback {
        void execute(DisplayTool aTool);
    }

    public interface Callback {
        void execute(final DisplayTool aDisplayTool);
    }

    @MainThread
    static void create(@NonNull final PersistentDatabase aDB,
                                        @NonNull final List<InstalledDictionary> aDictionaries,
                                        final int aRef,
                                        final Callback aCallback)
    {
        final DisplayTool tool = new DisplayTool(aDB, aDictionaries, aRef);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                tool.createStatement();

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);

                if (aCallback != null) {
                    aCallback.execute(tool);
                }
            }
        }.execute();
    }
}
