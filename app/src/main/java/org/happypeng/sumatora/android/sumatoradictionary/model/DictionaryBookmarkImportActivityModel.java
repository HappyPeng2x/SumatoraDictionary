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

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryApplication;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmarkImport;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Settings;
import org.happypeng.sumatora.android.sumatoradictionary.xml.DictionaryBookmarkXML;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import androidx.arch.core.util.Function;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class DictionaryBookmarkImportActivityModel extends AndroidViewModel {
    private DictionaryApplication mApp;

    private LiveData<List<DictionarySearchElement>> m_bookmarkElements;
    private MutableLiveData<Integer> mError;

    public DictionaryApplication getDictionaryApplication() { return mApp; }

    private static class DatabaseStatus {
        public String lang;
        public String backupLang;
        public DictionaryDatabase database;

        void copyFrom(DatabaseStatus aStatus) {
            lang = aStatus.lang;
            backupLang = aStatus.backupLang;
            database = aStatus.database;
        }

        public boolean isInitialized() {
            return lang != null && backupLang != null && database != null;
        }
    }

    public static class Status extends DatabaseStatus {
        public List<DictionarySearchElement> bookmarkElements;

        @Override
        public boolean isInitialized() {
            return super.isInitialized() && bookmarkElements != null;
        }
    }

    final private MediatorLiveData<Status> mStatus;
    public LiveData<Status> getStatus() { return mStatus; }

    public DictionaryBookmarkImportActivityModel(Application aApp) {
        super(aApp);

        mApp = (DictionaryApplication) aApp;

        mError = new MutableLiveData<>();

        final MediatorLiveData<DatabaseStatus> liveDatabaseStatus = new MediatorLiveData<>();
        final DatabaseStatus databaseStatus = new DatabaseStatus();

        liveDatabaseStatus.addSource(mApp.getSettings().getValue(Settings.LANG),
                new Observer<String>() {
                    @Override
                    public void onChanged(String s) {
                        databaseStatus.lang = s;

                        if (databaseStatus.isInitialized()) {
                            liveDatabaseStatus.setValue(databaseStatus);
                        }
                    }
                });

        liveDatabaseStatus.addSource(mApp.getSettings().getValue(Settings.BACKUP_LANG),
                new Observer<String>() {
                    @Override
                    public void onChanged(String s) {
                        databaseStatus.backupLang = s;

                        if (databaseStatus.isInitialized()) {
                            liveDatabaseStatus.setValue(databaseStatus);
                        }
                    }
                });

        liveDatabaseStatus.addSource(mApp.getDictionaryDatabase(),
                new Observer<DictionaryDatabase>() {
                    @Override
                    public void onChanged(DictionaryDatabase dictionaryDatabase) {
                        databaseStatus.database = dictionaryDatabase;

                        if (databaseStatus.isInitialized()) {
                            liveDatabaseStatus.setValue(databaseStatus);
                        }
                    }
                });

        LiveData<List<DictionarySearchElement>> bookmarkElements =
                Transformations.switchMap(liveDatabaseStatus,
                        new Function<DatabaseStatus, LiveData<List<DictionarySearchElement>>>() {
                            @Override
                            public LiveData<List<DictionarySearchElement>> apply(DatabaseStatus input) {
                                if (input.isInitialized()) {
                                    return input.database.dictionaryBookmarkImportDao().getAllDetailsLive(input.lang, input.backupLang);
                                } else {
                                    return null;
                                }
                            }
                        });

        final Status status = new Status();
        mStatus = new MediatorLiveData<>();

        mStatus.addSource(liveDatabaseStatus,
                new Observer<DatabaseStatus>() {
                    @Override
                    public void onChanged(DatabaseStatus databaseStatus) {
                        status.copyFrom(databaseStatus);

                        mStatus.setValue(status);
                    }
                });

        mStatus.addSource(bookmarkElements,
                new Observer<List<DictionarySearchElement>>() {
                    @Override
                    public void onChanged(List<DictionarySearchElement> dictionarySearchElements) {
                        status.bookmarkElements = dictionarySearchElements;

                        mStatus.setValue(status);
                    }
                });
    }

    public LiveData<Integer> getError() {
        return mError;
    }

    public void commitBookmarks() {
        if (mApp.getDictionaryDatabase().getValue() == null) {
            System.err.println("Trying to commit bookmarks without DB");

            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    final DictionaryDatabase db = mApp.getDictionaryDatabase().getValue();

                    db.beginTransaction();

                    SupportSQLiteDatabase sqldb = db.getOpenHelper().getWritableDatabase();

                    sqldb.execSQL("INSERT OR IGNORE INTO DictionaryBookmark SELECT * FROM DictionaryBookmarkImport");

                    db.setTransactionSuccessful();
                    db.endTransaction();

                    db.dictionaryBookmarkImportDao().deleteAll();
                } catch(Exception e) {
                    System.err.println(e.toString());
                }

                return null;
            }
        }.execute();
    }

    public void deleteAll() {
        if (mApp.getDictionaryDatabase().getValue() == null) {
            System.err.println("Trying to commit bookmarks without DB");

            return;
        }

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... voids) {
                mApp.getDictionaryDatabase().getValue().dictionaryBookmarkImportDao().deleteAll();

                return null;
            }
        }.execute();
    }

    public void importBookmarks(final Uri aUri) {
        if (mApp.getDictionaryDatabase().getValue() == null) {
            System.err.println("Trying to commit bookmarks without DB");

            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    final DictionaryDatabase db = mApp.getDictionaryDatabase().getValue();

                    db.dictionaryBookmarkImportDao().deleteAll();

                    InputStream is = getApplication().getContentResolver().openInputStream(aUri);
                    List<Long> seqs = DictionaryBookmarkXML.readXML(is);
                    is.close();

                    List<DictionaryBookmarkImport> dbiList = new LinkedList<DictionaryBookmarkImport>();

                    if (seqs == null) {
                        mError.postValue(1);

                        return null;
                    }

                    for (Long seq : seqs) {
                        dbiList.add(new DictionaryBookmarkImport(seq, 1));
                    }

                    db.dictionaryBookmarkImportDao().insertMany(dbiList);
                } catch(Exception e) {
                    mError.postValue(2);

                    System.err.println(e.toString());
                }

                return null;
            }
        }.execute();
    }
}
