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
import android.os.AsyncTask;

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryApplication;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Settings;

import java.util.List;

import androidx.arch.core.util.Function;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

public class DictionaryBookmarkFragmentModel extends AndroidViewModel {
    private DictionaryApplication mApp;

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

    final private MediatorLiveData<Status> m_status;

    public LiveData<Status> getStatus() { return m_status; }

    public DictionaryBookmarkFragmentModel(Application aApp) {
        super(aApp);

        mApp = (DictionaryApplication) aApp;

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
                                    return input.database.dictionaryBookmarkDao().getAllDetailsLive(input.lang, input.backupLang);
                                } else {
                                    return null;
                                }
                            }
                        });

        m_status = new MediatorLiveData<>();
        final Status status = new Status();

        m_status.addSource(liveDatabaseStatus,
                new Observer<DatabaseStatus>() {
                    @Override
                    public void onChanged(DatabaseStatus databaseStatus) {
                        status.copyFrom(databaseStatus);

                        m_status.setValue(status);
                    }
                });

        m_status.addSource(bookmarkElements,
                new Observer<List<DictionarySearchElement>>() {
                    @Override
                    public void onChanged(List<DictionarySearchElement> dictionarySearchElements) {
                        status.bookmarkElements = dictionarySearchElements;

                        m_status.setValue(status);
                    }
                });
    }

    public void deleteBookmark(final long aSeq) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                if (mApp.getDictionaryDatabase().getValue() != null) {
                    mApp.getDictionaryDatabase().getValue().dictionaryBookmarkDao().delete(aSeq);
                }

                return null;
            }
        }.execute();
    }

    public DictionaryApplication getDictionaryApplication() { return mApp; }
}
