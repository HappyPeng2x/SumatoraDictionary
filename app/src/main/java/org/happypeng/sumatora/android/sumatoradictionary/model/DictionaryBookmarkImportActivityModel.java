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
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BookmarkImportTool;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Settings;

import java.util.List;

import androidx.annotation.MainThread;
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

    private LiveData<List<DictionarySearchElement>> mBookmarkElements;

    public DictionaryApplication getDictionaryApplication() { return mApp; }
    
    private MutableLiveData<Uri> mUri;

    @MainThread
    public void setUri(final Uri aUri) { mUri.setValue(aUri); }

    private static class DatabaseStatus {
        public String lang;
        public String backupLang;
        public PersistentDatabase database;
        public BookmarkImportTool bookmarkImportTool;
        public Uri uri;
        public int toolStatus;

        void copyFrom(DatabaseStatus aStatus) {
            lang = aStatus.lang;
            backupLang = aStatus.backupLang;
            database = aStatus.database;
            bookmarkImportTool = aStatus.bookmarkImportTool;
            uri = aStatus.uri;
            toolStatus = aStatus.toolStatus;
        }

        public boolean isReadyForProcessing() {
            return lang != null && backupLang != null && database != null && bookmarkImportTool != null &&
                    uri != null && toolStatus == BookmarkImportTool.STATUS_INITIALIZED;
        }

        public boolean isInitialized() {
            return lang != null && backupLang != null && database != null && bookmarkImportTool != null &&
                    uri != null && toolStatus == BookmarkImportTool.STATUS_PROCESSED;
        }

        private boolean isInProcessingOrProcessed() {
            return lang != null && backupLang != null && database != null && bookmarkImportTool != null &&
                    uri != null && (toolStatus == BookmarkImportTool.STATUS_PROCESSED ||
                    toolStatus == BookmarkImportTool.STATUS_PROCESSING);
        }

        @MainThread
        private void processOrReprocessChange(final MutableLiveData<DatabaseStatus> aLiveData) {
            if (isInProcessingOrProcessed()) {
                bookmarkImportTool.processURI(uri, lang, backupLang);
            }

            processChange(aLiveData);
        }

        @MainThread
        private void processChange(final MutableLiveData<DatabaseStatus> aLiveData) {
            if (isReadyForProcessing()) {
                bookmarkImportTool.processURI(uri, lang, backupLang);
            }

            if (isInitialized()) {
                aLiveData.setValue(this);
            }
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

        final MediatorLiveData<DatabaseStatus> liveDatabaseStatus = new MediatorLiveData<>();
        final DatabaseStatus databaseStatus = new DatabaseStatus();

        mUri = new MutableLiveData<>();

        liveDatabaseStatus.addSource(mUri,
                new Observer<Uri>() {
                    @Override
                    public void onChanged(Uri uri) {
                        databaseStatus.uri = uri;

                        databaseStatus.processChange(liveDatabaseStatus);
                    }
                });

        final LiveData<Integer> toolStatus = Transformations.switchMap(mApp.getBookmarkImportTool(),
                new Function<BookmarkImportTool, LiveData<Integer>>() {
                    @Override
                    public LiveData<Integer> apply(BookmarkImportTool input) {
                        return input.getStatus();
                    }
                });

        liveDatabaseStatus.addSource(toolStatus,
                new Observer<Integer>() {
                    @Override
                    public void onChanged(Integer integer) {
                        if (integer == null) {
                            databaseStatus.toolStatus = 0;
                        } else {
                            databaseStatus.toolStatus = integer;
                        }

                        databaseStatus.processChange(liveDatabaseStatus);
                    }
                });

        liveDatabaseStatus.addSource(mApp.getSettings().getValue(Settings.LANG),
                new Observer<String>() {
                    @Override
                    public void onChanged(String s) {
                        databaseStatus.lang = s;

                        databaseStatus.processOrReprocessChange(liveDatabaseStatus);
                    }
                });

        liveDatabaseStatus.addSource(mApp.getSettings().getValue(Settings.BACKUP_LANG),
                new Observer<String>() {
                    @Override
                    public void onChanged(String s) {
                        databaseStatus.backupLang = s;

                        databaseStatus.processOrReprocessChange(liveDatabaseStatus);
                    }
                });

        liveDatabaseStatus.addSource(mApp.getPersistentDatabase(),
                new Observer<PersistentDatabase>() {
                    @Override
                    public void onChanged(PersistentDatabase dictionaryDatabase) {
                        databaseStatus.database = dictionaryDatabase;

                        databaseStatus.processChange(liveDatabaseStatus);
                    }
                });

        liveDatabaseStatus.addSource(mApp.getBookmarkImportTool(),
                new Observer<BookmarkImportTool>() {
                    @Override
                    public void onChanged(BookmarkImportTool bookmarkImportTool) {
                        databaseStatus.bookmarkImportTool = bookmarkImportTool;

                        databaseStatus.processChange(liveDatabaseStatus);
                    }
                });

        LiveData<List<DictionarySearchElement>> bookmarkElements =
                Transformations.switchMap(liveDatabaseStatus,
                        new Function<DatabaseStatus, LiveData<List<DictionarySearchElement>>>() {
                            @Override
                            public LiveData<List<DictionarySearchElement>> apply(DatabaseStatus input) {
                                if (input.isInitialized()) {
                                    return input.database.dictionaryBookmarkImportDao().getAllDetailsLive();
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

    public void cancelImport() {
        Status status = mStatus.getValue();

        if (status == null) {
            return;
        }

        status.bookmarkImportTool.cancelImport();
    }

    public void commitBookmarks() {
        if (mApp.getPersistentDatabase().getValue() == null) {
            System.err.println("Trying to commit bookmarks without DB");

            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    final PersistentDatabase db = mApp.getPersistentDatabase().getValue();

                    db.beginTransaction();

                    SupportSQLiteDatabase sqldb = db.getOpenHelper().getWritableDatabase();

                    sqldb.execSQL("INSERT OR IGNORE INTO DictionaryBookmark SELECT seq, 1 FROM DictionaryBookmarkImport");

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
}
