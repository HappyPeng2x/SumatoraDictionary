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
import android.net.Uri;
import android.os.AsyncTask;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.arch.core.util.Function;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryApplication;
import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryAction;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryActionDao;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import static android.content.Context.DOWNLOAD_SERVICE;

public class DictionariesManagementActivityModel extends AndroidViewModel {
    public static int STATUS_INITIALIZING = 0;
    public static int STATUS_DOWNLOADING = 1;
    public static int STATUS_DOWNLOAD_ERROR = 2;
    public static int STATUS_READY = 4;

    private final @NonNull DictionaryApplication mApp;

    private final MutableLiveData<List<InstalledDictionary>> mDownloadDictionariesLive;

    private final MediatorLiveData<Integer> mStatus;

    private final LiveData<List<DictionaryAction>> mUpdateActions;
    private final LiveData<List<DictionaryAction>> mDownloadActions;
    private final LiveData<List<DictionaryAction>> mDeleteActions;
    private final LiveData<List<DictionaryAction>> mVersionActions;

    public LiveData<List<DictionaryAction>> getUpdateActions() {
        return mUpdateActions;
    }

    public LiveData<List<DictionaryAction>> getDownloadActions() {
        return mDownloadActions;
    }

    public LiveData<List<DictionaryAction>> getDeleteActions() {
        return mDeleteActions;
    }

    public LiveData<List<DictionaryAction>> getVersionActions() {
        return mVersionActions;
    }

    private PersistentDatabase mDb;

    private String mDownloadError;
    
    private List<InstalledDictionary> mDownloadDictionaries;
    private List<InstalledDictionary> mInstalledDictionaries;

    public LiveData<Integer> getStatus() { return mStatus; }
    public String getDownloadError() { return mDownloadError; }

    public DictionariesManagementActivityModel(@NonNull Application application) {
        super(application);

        mDownloadDictionaries = null;
        mInstalledDictionaries = null;

        mApp = (DictionaryApplication) application;
        mDownloadDictionariesLive = new MutableLiveData<>();
        mStatus = new MediatorLiveData<>();

        mStatus.setValue(STATUS_INITIALIZING);

        mDb = null;

        final LiveData<List<InstalledDictionary>> installedDictionariesLive = Transformations.switchMap(mApp.getPersistentDatabase(),
                new Function<PersistentDatabase, LiveData<List<InstalledDictionary>>>() {
                    @Override
                    public LiveData<List<InstalledDictionary>> apply(PersistentDatabase input) {
                        return input.installedDictionaryDao().getAllLive();
                    }
                });

        mStatus.addSource(installedDictionariesLive,
                new Observer<List<InstalledDictionary>>() {
                    @Override
                    public void onChanged(List<InstalledDictionary> installedDictionaries) {
                        mInstalledDictionaries = installedDictionaries;

                        processDictionaries();
                    }
                });

        mStatus.addSource(mDownloadDictionariesLive,
                new Observer<List<InstalledDictionary>>() {
                    @Override
                    public void onChanged(List<InstalledDictionary> downloadDictionaries) {
                        mDownloadDictionaries = downloadDictionaries;

                        processDictionaries();
                    }
                });

        mStatus.addSource(mApp.getPersistentDatabase(),
                new Observer<PersistentDatabase>() {
                    @Override
                    public void onChanged(PersistentDatabase persistentDatabase) {
                        mDb = persistentDatabase;

                        processDictionaries();
                    }
                });

        mUpdateActions = Transformations.switchMap(mApp.getPersistentDatabase(),
                new Function<PersistentDatabase, LiveData<List<DictionaryAction>>>() {
                    @Override
                    public LiveData<List<DictionaryAction>> apply(PersistentDatabase input) {
                        return input.dictionaryActionDao().getUpdateActions();
                    }
                });

        mDownloadActions = Transformations.switchMap(mApp.getPersistentDatabase(),
                new Function<PersistentDatabase, LiveData<List<DictionaryAction>>>() {
                    @Override
                    public LiveData<List<DictionaryAction>> apply(PersistentDatabase input) {
                        return input.dictionaryActionDao().getDownloadActions();
                    }
                });

        mDeleteActions = Transformations.switchMap(mApp.getPersistentDatabase(),
                new Function<PersistentDatabase, LiveData<List<DictionaryAction>>>() {
                    @Override
                    public LiveData<List<DictionaryAction>> apply(PersistentDatabase input) {
                        return input.dictionaryActionDao().getDeleteActions();
                    }
                });

        mVersionActions = Transformations.switchMap(mApp.getPersistentDatabase(),
                new Function<PersistentDatabase, LiveData<List<DictionaryAction>>>() {
                    @Override
                    public LiveData<List<DictionaryAction>> apply(PersistentDatabase input) {
                        return input.dictionaryActionDao().getVersionActions();
                    }
                });
    }

    @MainThread
    private void processDictionaries() {
        if (mDb == null ||
                mInstalledDictionaries == null ||
                mDownloadDictionaries == null) {
            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                boolean versionMismatch = false;
                int dbVersion = mApp.getResources().getInteger(R.integer.database_version);

                LinkedHashMap<String, DictionaryAction> installedActions =
                        new LinkedHashMap<>();

                final LinkedList<DictionaryAction> deleteActions = new LinkedList<>();
                final LinkedList<DictionaryAction> updateActions = new LinkedList<>();
                final LinkedList<DictionaryAction> downloadActions = new LinkedList<>();
                final LinkedList<DictionaryAction> versionActions = new LinkedList<>();

                for (InstalledDictionary d : mInstalledDictionaries) {
                    installedActions.put(d.type + ":" + d.lang,
                            new DictionaryAction(d, null, d.type, d.lang,
                                    dbVersion));
                }

                for (InstalledDictionary d : mDownloadDictionaries) {
                    DictionaryAction a = installedActions.get(d.type + ":" + d.lang);

                    if (a != null) {
                        a.setDownloadDictionary(d);
                    } else {
                        installedActions.put(d.type + ":" + d.lang,
                                new DictionaryAction(null, d, d.type, d.lang,
                                        dbVersion));
                    }
                }

                for (DictionaryAction a : installedActions.values()) {
                    a.calculateAction();

                    switch (a.getAction()) {
                        case DictionaryAction.DICTIONARY_ACTION_MISMATCH:
                            System.out.println("Dictionary action mismatch found...");
                            break;
                        case DictionaryAction.DICTIONARY_ACTION_VERSION:
                            versionMismatch = true;
                            versionActions.add(a);
                            break;
                        case DictionaryAction.DICTIONARY_ACTION_UPDATE:
                            updateActions.add(a);
                            break;
                        case DictionaryAction.DICTIONARY_ACTION_DELETE:
                            deleteActions.add(a);
                            break;
                        case DictionaryAction.DICTIONARY_ACTION_DOWNLOAD:
                            downloadActions.add(a);
                            break;
                    }
                }

                mDb.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        DictionaryActionDao dao = mDb.dictionaryActionDao();

                        dao.deleteMany(dao.getAll());

                        dao.insertMany(versionActions);
                        dao.insertMany(updateActions);
                        dao.insertMany(deleteActions);
                        dao.insertMany(downloadActions);
                    }
                });

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);

                mStatus.setValue(STATUS_READY);
            }
        }.execute();
    }

    @MainThread
    public void fetchDictionariesList() {
        mStatus.setValue(STATUS_DOWNLOADING);

        new AsyncTask<Void, Void, List<InstalledDictionary>>() {
            @Override
            protected List<InstalledDictionary> doInBackground(Void... voids) {
                List<InstalledDictionary> ret = null;

                try {
                    TrafficStats.setThreadStatsTag((int) Thread.currentThread().getId());

                    URL url = new URL(mApp.getString(R.string.dictionaries_url));
                    InputStream is = url.openStream();
                    ret = InstalledDictionary.fromXML(is);
                } catch (Exception e) {
                    mDownloadError = e.toString();
                }

                return ret;
            }

            @Override
            protected void onPostExecute(List<InstalledDictionary> installedDictionaries) {
                super.onPostExecute(installedDictionaries);

                mDownloadDictionariesLive.setValue(installedDictionaries);

                if (installedDictionaries == null) {
                    mStatus.setValue(STATUS_DOWNLOAD_ERROR);
                }
            }
        }.execute();
    }

    public void startDownload(final DictionaryAction aEntry) {
        if (mDb == null) {
            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                mDb.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        File localFile = new File(mApp.getExternalFilesDir(null),
                                aEntry.getType() + "-" + aEntry.getLang());

                        DownloadManager.Request request=new DownloadManager.Request(Uri.parse(aEntry.getDownloadDictionary().file))
                                .setTitle(aEntry.getDownloadDictionary().description)// Title of the Download Notification
                                .setDescription("Downloading")// Description of the Download Notification
                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)// Visibility of the download Notification
                                .setDestinationUri(Uri.fromFile(localFile))// Uri of the destination file
                                .setAllowedOverRoaming(false);

                        DownloadManager downloadManager= (DownloadManager) mApp.getSystemService(DOWNLOAD_SERVICE);

                        aEntry.setDownloadId(downloadManager.enqueue(request));
                        mDb.dictionaryActionDao().update(aEntry);
                    }
                });

                return null;
            }
        }.execute();
    }
}
