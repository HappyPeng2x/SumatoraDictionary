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
import android.renderscript.ScriptGroup;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmarkImport;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.xml.DictionaryBookmarkXML;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class DictionaryBookmarkImportActivityModel extends DictionaryViewModel {
    private LiveData<List<DictionarySearchElement>> m_bookmarkElements;
    private MutableLiveData<Integer> m_errorLive;

    public DictionaryBookmarkImportActivityModel(Application aApp) {
        super(aApp);

        m_errorLive = new MutableLiveData<>();
    }

    public LiveData<Integer> getErrorLive() {
        return m_errorLive;
    }

    public LiveData<List<DictionarySearchElement>> getBookmarkElements()
    {
        return m_bookmarkElements;
    }

    // Please note that this can be called before the rest of the constructor after super()
    @Override
    protected void connectDatabase() {
        super.connectDatabase();

        m_bookmarkElements = m_db.dictionaryBookmarkImportDao().getAllDetailsLive("eng");
    }

    public void commitBookmarks() {
        if (m_db == null) {
            System.err.println("Trying to commit bookmarks without DB");

            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    m_db.beginTransaction();

                    SupportSQLiteDatabase sqldb = m_db.getOpenHelper().getWritableDatabase();

                    sqldb.execSQL("INSERT OR IGNORE INTO DictionaryBookmark SELECT * FROM DictionaryBookmarkImport");

                    m_db.setTransactionSuccessful();
                    m_db.endTransaction();

                    m_db.dictionaryBookmarkImportDao().deleteAll();
                } catch(Exception e) {
                    System.err.println(e.toString());
                }

                return null;
            }
        }.execute();
    }

    public void deleteAll() {
        if (m_db == null) {
            return;
        }

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... voids) {
                m_db.dictionaryBookmarkImportDao().deleteAll();

                return null;
            }
        }.execute();
    }

    public void importBookmarks(final Uri aUri) {
        if (m_db == null) {
            System.err.println("Trying to import bookmarks without DB");

            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    m_db.dictionaryBookmarkImportDao().deleteAll();

                    InputStream is = getApplication().getContentResolver().openInputStream(aUri);
                    List<Long> seqs = DictionaryBookmarkXML.readXML(is);
                    List<DictionaryBookmarkImport> dbiList = new LinkedList<DictionaryBookmarkImport>();

                    if (seqs == null) {
                        m_errorLive.postValue(1);

                        return null;
                    }

                    for (Long seq : seqs) {
                        dbiList.add(new DictionaryBookmarkImport(seq, 1));
                    }

                    m_db.dictionaryBookmarkImportDao().insertMany(dbiList);
                } catch(Exception e) {
                    m_errorLive.postValue(2);

                    System.err.println(e.toString());
                }

                return null;
            }
        }.execute();
    }
}
