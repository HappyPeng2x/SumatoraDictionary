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
import android.net.TrafficStats;
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
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.DictionaryAction;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.DictionaryActionLists;

import java.io.InputStream;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

public class DictionariesManagementActivityModel extends AndroidViewModel {
    public static int STATUS_INITIALIZING = 0;
    public static int STATUS_DOWNLOADING = 1;
    public static int STATUS_DOWNLOAD_ERROR = 2;
    public static int STATUS_READY = 4;

    private final @NonNull DictionaryApplication mApp;

    private final MutableLiveData<List<InstalledDictionary>> mDownloadDictionariesLive;
    private final MutableLiveData<Integer> mStatus;
    private final MediatorLiveData<DictionaryActionLists> mActions;

    private String mDownloadError;
    
    private List<InstalledDictionary> mDownloadDictionaries;
    private List<InstalledDictionary> mInstalledDictionaries;

    public LiveData<DictionaryActionLists> getActions() {
        return mActions;
    }
    public LiveData<Integer> getStatus() { return mStatus; }
    public String getDownloadError() { return mDownloadError; }

    public DictionariesManagementActivityModel(@NonNull Application application) {
        super(application);

        mDownloadDictionaries = null;
        mInstalledDictionaries = null;

        mApp = (DictionaryApplication) application;
        mDownloadDictionariesLive = new MutableLiveData<>();
        mStatus = new MutableLiveData<>();

        mStatus.setValue(STATUS_INITIALIZING);

        final LiveData<List<InstalledDictionary>> installedDictionariesLive = Transformations.switchMap(mApp.getPersistentDatabase(),
                new Function<PersistentDatabase, LiveData<List<InstalledDictionary>>>() {
                    @Override
                    public LiveData<List<InstalledDictionary>> apply(PersistentDatabase input) {
                        return input.installedDictionaryDao().getAllLive();
                    }
                });

        mActions = new MediatorLiveData<>();

        mActions.addSource(installedDictionariesLive,
                new Observer<List<InstalledDictionary>>() {
                    @Override
                    public void onChanged(List<InstalledDictionary> installedDictionaries) {
                        mInstalledDictionaries = installedDictionaries;

                        processDictionaries();
                    }
                });

        mActions.addSource(mDownloadDictionariesLive,
                new Observer<List<InstalledDictionary>>() {
                    @Override
                    public void onChanged(List<InstalledDictionary> downloadDictionaries) {
                        mDownloadDictionaries = downloadDictionaries;

                        processDictionaries();
                    }
                });
    }

    @MainThread
    private void processDictionaries() {
        if (mInstalledDictionaries == null ||
                mDownloadDictionaries == null || mActions == null) {
            return;
        }

        boolean versionMismatch = false;
        int dbVersion = mApp.getResources().getInteger(R.integer.database_version);

        LinkedHashMap<String, DictionaryAction> installedActions =
                new LinkedHashMap<>();

        LinkedList<DictionaryAction> deleteActions = new LinkedList<>();
        LinkedList<DictionaryAction> updateActions = new LinkedList<>();
        LinkedList<DictionaryAction> downloadActions = new LinkedList<>();
        LinkedList<DictionaryAction> versionActions = new LinkedList<>();

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

        mActions.setValue(new DictionaryActionLists(deleteActions, updateActions,
                downloadActions, versionActions));

        mStatus.setValue(STATUS_READY);
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
}
