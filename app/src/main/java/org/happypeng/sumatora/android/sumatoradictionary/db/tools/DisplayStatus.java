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

import androidx.annotation.MainThread;
import androidx.arch.core.util.Function;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryApplication;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;

import java.util.List;

public class DisplayStatus {
    public String lang;
    public String backupLang;
    public PersistentDatabase database;
    public DisplayTool displayTool;
    public List<DictionarySearchElement> displayElements;

    @MainThread
    public void processChange(final MutableLiveData<DisplayStatus> aLiveData) {
        if (isInitialized()) {
            aLiveData.setValue(this);
        }
    }

    @MainThread
    public void performUpdate() {
        if (isReady()) {
            displayTool.performDisplayElementInsertion(lang, backupLang);
        }
    }

    private boolean isReady() {
        return lang != null && backupLang != null && database != null && displayTool != null;
    }

    public boolean isInitialized() {
        return isReady() && displayElements != null;
    }

    public static MediatorLiveData<DisplayStatus> create(final DictionaryApplication aApp, final int aRef) {
        final MediatorLiveData<DisplayStatus> liveData = new MediatorLiveData<>();
        final DisplayStatus status = new DisplayStatus();

        liveData.addSource(aApp.getSettings().getValue(Settings.LANG),
                new Observer<String>() {
                    @Override
                    public void onChanged(String s) {
                        status.lang = s;

                        status.performUpdate();
                        status.processChange(liveData);
                    }
                });

        liveData.addSource(aApp.getSettings().getValue(Settings.BACKUP_LANG),
                new Observer<String>() {
                    @Override
                    public void onChanged(String s) {
                        status.backupLang = s;

                        status.performUpdate();
                        status.processChange(liveData);
                    }
                });

        liveData.addSource(aApp.getPersistentDatabase(),
                new Observer<PersistentDatabase>() {
                    @Override
                    public void onChanged(PersistentDatabase dictionaryDatabase) {
                        status.database = dictionaryDatabase;

                        status.processChange(liveData);
                    }
                });

        final LiveData<DisplayTool> displayTool =
                Transformations.switchMap(aApp.getPersistentDatabase(),
                        new Function<PersistentDatabase, LiveData<DisplayTool>>() {
                            @Override
                            public LiveData<DisplayTool> apply(PersistentDatabase input) {
                                return DisplayTool.create(input, aRef);
                            }
                        });

        liveData.addSource(displayTool,
                new Observer<DisplayTool>() {
                    @Override
                    public void onChanged(DisplayTool displayTool) {
                        status.displayTool = displayTool;

                        status.performUpdate();
                        status.processChange(liveData);
                    }
                });

        final LiveData<List<DictionarySearchElement>> displayElements =
                Transformations.switchMap(aApp.getPersistentDatabase(),
                        new Function<PersistentDatabase, LiveData<List<DictionarySearchElement>>>() {
                            @Override
                            public LiveData<List<DictionarySearchElement>> apply(PersistentDatabase input) {
                                return input.dictionaryDisplayElementDao().getAllDetailsLive(aRef);
                            }
                        });

        liveData.addSource(displayElements,
                new Observer<List<DictionarySearchElement>>() {
                    @Override
                    public void onChanged(List<DictionarySearchElement> dictionarySearchElements) {
                        status.displayElements = dictionarySearchElements;

                        status.processChange(liveData);
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
                        status.performUpdate();
                    }
                });

        return liveData;
    }
}
