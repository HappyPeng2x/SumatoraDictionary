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
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;

import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

class DisplayTool {
    static private final String SQL_QUERY_INSERT_DISPLAY_ELEMENT =
            "INSERT OR IGNORE INTO DictionaryDisplayElement SELECT DictionaryElement.ref, DictionaryElement.entryOrder, DictionaryElement.seq, "
                    + "DictionaryEntry.readingsPrio, DictionaryEntry.readings, "
                    + "DictionaryEntry.writingsPrio, DictionaryEntry.writings, "
                    + "DictionaryEntry.pos, DictionaryEntry.xref, DictionaryEntry.ant, "
                    + "DictionaryEntry.misc, DictionaryEntry.lsource, DictionaryEntry.dial, "
                    + "DictionaryEntry.s_inf, DictionaryEntry.field, "
                    + "?, "
                    + "DictionaryTranslation.gloss, "
                    + "null as example_sentences "
                    + "FROM DictionaryElement, "
                    + " jmdict.DictionaryEntry, "
                    + " %s.DictionaryTranslation "
                    + "WHERE DictionaryElement.seq = DictionaryEntry.seq AND "
                    + " DictionaryElement.seq = DictionaryTranslation.seq AND "
                    + " DictionaryElement.ref = ?";

    static private final String SQL_QUERY_INSERT_DISPLAY_ELEMENT_EXAMPLES =
            "INSERT OR IGNORE INTO DictionaryDisplayElement SELECT DictionaryElement.ref, DictionaryElement.entryOrder, DictionaryElement.seq, "
                    + "DictionaryEntry.readingsPrio, DictionaryEntry.readings, "
                    + "DictionaryEntry.writingsPrio, DictionaryEntry.writings, "
                    + "DictionaryEntry.pos, DictionaryEntry.xref, DictionaryEntry.ant, "
                    + "DictionaryEntry.misc, DictionaryEntry.lsource, DictionaryEntry.dial, "
                    + "DictionaryEntry.s_inf, DictionaryEntry.field, "
                    + "?, "
                    + "DictionaryTranslation.gloss, "
                    + "json_group_array(examples.sentence) as example_sentences "
                    + "FROM DictionaryElement, "
                    + " (jmdict.DictionaryEntry LEFT JOIN (%s.Examples, %s.ExamplesIndex) ON "
                    + " ExamplesIndex.seq = DictionaryEntry.seq AND ExamplesIndex.id = Examples.id), "
                    + " %s.DictionaryTranslation "
                    + "WHERE DictionaryElement.seq = DictionaryEntry.seq AND "
                    + " DictionaryElement.seq = DictionaryTranslation.seq AND "
                    + " DictionaryElement.ref = ? "
                    + "GROUP BY DictionaryElement.seq";

    static private final String SQL_QUERY_DELETE_DISPLAY_ELEMENT =
            "DELETE FROM DictionaryDisplayElement WHERE DictionaryDisplayElement.ref = ?";

    private PersistentDatabase mDB;

    private boolean mInitialized;

    private final int mRef;

    private SupportSQLiteStatement mInsertDisplayElementStatement;
    private SupportSQLiteStatement mInsertDisplayElementStatementBackup;
    private SupportSQLiteStatement mDeleteDisplayElement;

    private String mLang;
    private String mBackupLang;

    DisplayTool(int aRef) {
        mRef = aRef;

        mInitialized = false;
    }

    public boolean isInitialized() {
        return mInitialized;
    }

    @WorkerThread
    private synchronized void insertDisplayElement() {
        if (!isInitialized()) {
            return;
        }

        mDB.runInTransaction(new Runnable() {
            @Override
            public void run() {
                mDeleteDisplayElement.bindLong(1, mRef);
                mDeleteDisplayElement.execute();

                mInsertDisplayElementStatement.bindString(1, mLang);
                mInsertDisplayElementStatement.bindLong(2, mRef);
                mInsertDisplayElementStatement.execute();

                if (mInsertDisplayElementStatementBackup != null) {
                    mInsertDisplayElementStatementBackup.bindString(1, mBackupLang);
                    mInsertDisplayElementStatementBackup.bindLong(2, mRef);
                    mInsertDisplayElementStatementBackup.execute();
                }
            }
        });
    }

    @MainThread
    void performDisplayElementInsertion() {
        new AsyncTask<Void, Void, Void>(){
            @Override
            protected Void doInBackground(Void... voids) {
                insertDisplayElement();

                return null;
            }
        }.execute();
    }

    @WorkerThread
    private void createStatement(final List<InstalledDictionary> aDictionaries) {
        SupportSQLiteDatabase db = mDB.getOpenHelper().getWritableDatabase();

        boolean hasExamples = false;
        boolean hasDictionary = false;
        boolean hasBackupDictionary = false;

        for (InstalledDictionary d : aDictionaries) {
            if ("jmdict_translation".equals(d.type)) {
                if (mLang.equals(d.lang)) {
                    hasDictionary = true;
                }

                if (mBackupLang != null && mBackupLang.equals(d.lang)) {
                    hasBackupDictionary = true;
                }
            } else if ("tatoeba".equals(d.type) && mLang.equals(d.lang)) {
                hasExamples = true;
            }
        }

        if (!hasDictionary) {
            System.err.println("Dictionary for " + mLang + " is not installed.");
        }

        if (mBackupLang != null && !hasBackupDictionary) {
            System.err.println("Dictionary for backup language " + mLang + " is not installed.");
        }

        if (hasExamples) {
            mInsertDisplayElementStatement = db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT_EXAMPLES, "examples_" + mLang, "examples_" + mLang, mLang));
        } else {
            mInsertDisplayElementStatement = db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT, mLang));
        }

        mDeleteDisplayElement = db.compileStatement(SQL_QUERY_DELETE_DISPLAY_ELEMENT);
    }

    public interface Callback {
        void execute();
    }

    @WorkerThread
    void closeStatements() {
        if (mInsertDisplayElementStatement != null) {
            try {
                mInsertDisplayElementStatement.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mInsertDisplayElementStatement = null;
        }

        if (mInsertDisplayElementStatementBackup != null) {
            try {
                mInsertDisplayElementStatementBackup.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mInsertDisplayElementStatementBackup = null;
        }

        if (mDeleteDisplayElement != null) {
            try {
                mDeleteDisplayElement.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mDeleteDisplayElement = null;
        }

        mInitialized = false;
    }

    @MainThread
    void initialize(@NonNull final PersistentDatabase aDB,
                    @NonNull final List<InstalledDictionary> aDictionaries,
                    @NonNull final String aLang,
                    @NonNull final String aBackupLang,
                    final Callback aCallback)
    {
        mDB = aDB;

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                closeStatements();

                mLang = aLang;
                mBackupLang = aBackupLang;

                if (mLang != null) {
                    createStatement(aDictionaries);
                }

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);

                mInitialized = true;

                if (aCallback != null) {
                    aCallback.execute();
                }
            }
        }.execute();
    }
}
