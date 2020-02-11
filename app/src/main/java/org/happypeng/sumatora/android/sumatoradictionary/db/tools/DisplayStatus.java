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
import android.provider.ContactsContract;

import androidx.annotation.MainThread;
import androidx.arch.core.util.Function;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.paging.PagedList;

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryApplication;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;

import java.util.List;

public class DisplayStatus {
    public String lang;
    public String backupLang;
    public PersistentDatabase database;
    public DisplayTool displayTool;
    public List<InstalledDictionary> installedDictionaries;
    public int ref;

    @MainThread
    public void processChange(final MutableLiveData<DisplayStatus> aLiveData) {
        if (isReady()) {
            aLiveData.setValue(this);
        } else {
            aLiveData.setValue(null);
        }
    }

    @MainThread
    public void performUpdate(final MutableLiveData<DisplayStatus> aLiveData) {
        if (isReady()) {
            displayTool.performDisplayElementInsertion(lang, backupLang);
        } else if (isInitialized()) {
            DisplayTool.create(database, installedDictionaries, ref,
                    new DisplayTool.Callback() {
                        @Override
                        public void execute(DisplayTool aDisplayTool) {
                            displayTool = aDisplayTool;

                            processChange(aLiveData);
                        }
                    });
        }
    }

    private boolean isReady() {
        return isInitialized() && displayTool != null;
    }


    public boolean isInitialized() {
        return lang != null && backupLang != null && database != null && installedDictionaries != null;
    }

    public static MediatorLiveData<DisplayStatus> create(final DictionaryApplication aApp, final int aRef) {
        final MediatorLiveData<DisplayStatus> liveData = new MediatorLiveData<>();
        final DisplayStatus status = new DisplayStatus();
        status.ref = aRef;

        liveData.addSource(aApp.getSettings().getValue(Settings.LANG),
                new Observer<String>() {
                    @Override
                    public void onChanged(String s) {
                        status.lang = s;

                        status.performUpdate(liveData);
                        status.processChange(liveData);
                    }
                });

        liveData.addSource(aApp.getSettings().getValue(Settings.BACKUP_LANG),
                new Observer<String>() {
                    @Override
                    public void onChanged(String s) {
                        status.backupLang = s;

                        status.performUpdate(liveData);
                        status.processChange(liveData);
                    }
                });

        liveData.addSource(aApp.getPersistentDatabase(),
                new Observer<PersistentDatabase>() {
                    @Override
                    public void onChanged(PersistentDatabase persistentDatabase) {
                        status.database = persistentDatabase;

                        status.performUpdate(liveData);
                        status.processChange(liveData);
                    }
                });

        final LiveData<List<InstalledDictionary>> installedDictionariesList =
                Transformations.switchMap(aApp.getPersistentDatabase(),
                        new Function<PersistentDatabase, LiveData<List<InstalledDictionary>>>() {
                            @Override
                            public LiveData<List<InstalledDictionary>> apply(PersistentDatabase input) {
                                if (input != null) {
                                    return input.installedDictionaryDao().getAllLive();
                                }

                                return null;
                            }
                        });

        liveData.addSource(installedDictionariesList,
                new Observer<List<InstalledDictionary>>() {
                    @Override
                    public void onChanged(List<InstalledDictionary> installedDictionaries) {
                        status.installedDictionaries = installedDictionaries;

                        status.performUpdate(liveData);
                        status.processChange(liveData);
                    }
                });

        final LiveData<List<DictionarySearchElement>> displayElements =
                Transformations.switchMap(aApp.getPersistentDatabase(),
                        new Function<PersistentDatabase, LiveData<List<DictionarySearchElement>>>() {
                            @Override
                            public LiveData<List<DictionarySearchElement>> apply(PersistentDatabase input) {
                                if (input != null) {
                                    return input.dictionaryDisplayElementDao().getAllDetailsLive(aRef);
                                }

                                return null;
                            }
                        });

        final LiveData<Long> elements =
                Transformations.switchMap(aApp.getPersistentDatabase(),
                        new Function<PersistentDatabase, LiveData<Long>>() {
                            @Override
                            public LiveData<Long> apply(PersistentDatabase input) {
                                if (input != null) {
                                    return input.dictionaryElementDao().getFirstLive();
                                }

                                return null;
                            }
                        });

        liveData.addSource(elements,
                new Observer<Long>() {
                    @Override
                    public void onChanged(Long aLong) {
                        status.performUpdate(liveData);
                    }
                });

        return liveData;
    }
}
