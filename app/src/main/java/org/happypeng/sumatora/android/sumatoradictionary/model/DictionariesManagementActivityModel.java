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

package org.happypeng.sumatora.android.sumatoradictionary.model;

import android.app.Application;
import android.app.DownloadManager;
import android.net.TrafficStats;
import android.os.AsyncTask;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryApplication;
import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.RemoteDictionaryObject;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BaseDictionaryObject;

import java.io.InputStream;
import java.net.URL;
import java.util.List;

import static android.content.Context.DOWNLOAD_SERVICE;

public class DictionariesManagementActivityModel extends AndroidViewModel {
    public static int STATUS_INITIALIZING = 0;
    public static int STATUS_DOWNLOADING = 1;
    public static int STATUS_DOWNLOAD_ERROR = 2;
    public static int STATUS_READY = 4;

    private final @NonNull DictionaryApplication mApp;

    private final MediatorLiveData<Integer> mStatus;

    private List<RemoteDictionaryObject> mRemoteDictionaryObjectList;

    private PersistentDatabase mDb;

    private String mDownloadError;

    private LiveData<List<RemoteDictionaryObject>> mRemoteDictionaryObjects;
    private LiveData<List<InstalledDictionary>> mInstalledDictionaries;

    public LiveData<Integer> getStatus() { return mStatus; }
    public String getDownloadError() { return mDownloadError; }

    public LiveData<List<RemoteDictionaryObject>> getRemoteDictionaryObjects() {
        return mRemoteDictionaryObjects;
    }

    public LiveData<List<InstalledDictionary>> getInstalledDictionaries() {
        return mInstalledDictionaries;
    }

    public DictionariesManagementActivityModel(@NonNull Application application) {
        super(application);

        mApp = (DictionaryApplication) application;
        mStatus = new MediatorLiveData<>();

        mStatus.setValue(STATUS_INITIALIZING);

        mDb = null;

        mRemoteDictionaryObjectList = null;

        mDownloadError = null;

        mInstalledDictionaries = Transformations.switchMap(mApp.getPersistentDatabase(),
                new Function<PersistentDatabase, LiveData<List<InstalledDictionary>>>() {
                    @Override
                    public LiveData<List<InstalledDictionary>> apply(PersistentDatabase input) {
                        return input.installedDictionaryDao().getAllLive();
                    }
                });

        mRemoteDictionaryObjects = Transformations.switchMap(mApp.getPersistentDatabase(),
                new Function<PersistentDatabase, LiveData<List<RemoteDictionaryObject>>>() {
                    @Override
                    public LiveData<List<RemoteDictionaryObject>> apply(PersistentDatabase input) {
                        return input.remoteDictionaryObjectDao().getAllLive();
                    }
                });

        mStatus.addSource(mApp.getPersistentDatabase(),
                new Observer<PersistentDatabase>() {
                    @Override
                    public void onChanged(PersistentDatabase persistentDatabase) {
                        mDb = persistentDatabase;

                        processDictionaryList();
                    }
                });
    }

    @MainThread
    private void processDictionaryList() {
        if (mDb != null && mRemoteDictionaryObjectList != null) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    mDb.remoteDictionaryObjectDao().insertMany(mRemoteDictionaryObjectList);
                    mRemoteDictionaryObjectList = null;

                    return null;
                }
            }.execute();
        }
    }

    @MainThread
    public void fetchDictionariesList() {
        mDownloadError = null;
        mStatus.setValue(STATUS_DOWNLOADING);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                List<RemoteDictionaryObject> ret = null;

                try {
                    TrafficStats.setThreadStatsTag((int) Thread.currentThread().getId());

                    URL url = new URL(mApp.getString(R.string.dictionaries_url));
                    InputStream is = url.openStream();
                    ret = RemoteDictionaryObject.fromXML(is,
                            new BaseDictionaryObject.Constructor<RemoteDictionaryObject>() {
                                @Override
                                public RemoteDictionaryObject create(@NonNull String aFile, String aDescription, @NonNull String aType, @NonNull String aLang, int aVersion, int aDate) {
                                    return new RemoteDictionaryObject(aFile, aDescription,
                                            aType, aLang, aVersion, aDate);
                                }
                            });
                } catch (Exception e) {
                    mDownloadError = e.toString();

                    return null;
                }

                mRemoteDictionaryObjectList = ret;

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);

                if (mDownloadError != null) {
                    mStatus.setValue(STATUS_DOWNLOAD_ERROR);
                } else {
                    mStatus.setValue(STATUS_READY);
                }

                if (mDb != null) {
                    processDictionaryList();
                }
            }
        }.execute();
    }

    @MainThread
    public void startDownload(final RemoteDictionaryObject aEntry) {
        if (mDb == null) {
            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                mDb.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        aEntry.download((DownloadManager) mApp.getSystemService(DOWNLOAD_SERVICE),
                                mApp.getExternalFilesDir(null));

                        mDb.remoteDictionaryObjectDao().update(aEntry);
                    }
                });

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);

                if (mApp != null) {
                    mApp.updateDownloadService();
                }
            }
        }.execute();
    }
}
