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
import android.net.Uri;
import android.os.AsyncTask;

import androidx.sqlite.db.SupportSQLiteDatabase;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmarkImport;
import org.happypeng.sumatora.android.sumatoradictionary.xml.DictionaryBookmarkXML;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class BookmarkImportModel extends BaseFragmentModel {
    private static String SQL_BOOKMARK_IMPORT_COMMIT = "INSERT OR IGNORE INTO DictionaryBookmark SELECT seq, 1 FROM DictionaryBookmarkImport";

    private boolean m_UriImported;

    public BookmarkImportModel(Application aApp) {
        super(aApp);

        m_UriImported = false;
    }

    public boolean getUriImported() {
        return m_UriImported;
    }

    public void cancelImport() {
        if (mCurrentDatabase == null) {
            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                mCurrentDatabase.dictionaryBookmarkImportDao().deleteAll();

                return null;
            }
        }.execute();
    }

    public void commitBookmarks() {
        if (mCurrentDatabase == null) {
            System.err.println("Trying to commit bookmarks without DB");

            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    mCurrentDatabase.runInTransaction(new Runnable() {
                        @Override
                        public void run() {
                            SupportSQLiteDatabase sqldb = mCurrentDatabase.getOpenHelper().getWritableDatabase();

                            sqldb.execSQL(SQL_BOOKMARK_IMPORT_COMMIT);

                            mCurrentDatabase.dictionaryBookmarkImportDao().deleteAll();
                        }
                    });

                } catch(Exception e) {
                    System.err.println(e.toString());
                }

                return null;
            }
        }.execute();
    }

    public void processUri(final Uri aUri) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    InputStream is = mApp.getContentResolver().openInputStream(aUri);
                    final List<Long> seqs = DictionaryBookmarkXML.readXML(is);
                    is.close();

                    if (seqs == null) {
                        // TBD: error management
                        // mStatus.postValue(STATUS_ERROR);

                        return null;
                    }

                    mCurrentDatabase.runInTransaction(new Runnable() {
                        @Override
                        public void run() {
                            mCurrentDatabase.dictionaryBookmarkImportDao().deleteAll();

                            for (Long seq : seqs) {
                                mCurrentDatabase.dictionaryBookmarkImportDao().insert(
                                        new DictionaryBookmarkImport(mKey, seq));
                            }
                        }
                    });

                    // TBD: status management
                    // mStatus.postValue(STATUS_PROCESSED);
                } catch (IOException e) {
                    System.err.println(e.toString());

                    // TBD: error management
                    //mStatus.postValue(STATUS_ERROR);
                }

                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                m_UriImported = true;
            }
        }.execute();
    }
}
