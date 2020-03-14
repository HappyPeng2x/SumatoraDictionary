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
import androidx.lifecycle.MutableLiveData;

import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistantLanguageSettings;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;

import java.util.List;

public class DisplayStatus {
    public String lang;
    public String backupLang;
    public PersistentDatabase database;
    public DisplayTool displayTool;
    public List<InstalledDictionary> installedDictionaries;
    public int ref;

    public DisplayStatus(int aRef) {
        ref = aRef;
        displayTool = new DisplayTool(aRef);
    }

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
            displayTool.performDisplayElementInsertion();
        } else if (isInitialized()) {
            displayTool.initialize(database, installedDictionaries, lang, backupLang,
                    new DisplayTool.Callback() {
                        @Override
                        public void execute() {
                            processChange(aLiveData);
                        }
                    });
        }
    }

    private boolean isReady() {
        return isInitialized() && displayTool.isInitialized();
    }

    public boolean isInitialized() {
        return lang != null && database != null && installedDictionaries != null && displayTool != null;
    }

    public void setLanguageSettings(PersistantLanguageSettings aSettings,
                                    final MutableLiveData<DisplayStatus> aLiveData) {
        if (aSettings != null) {
            lang = aSettings.lang;
            backupLang = aSettings.backupLang;
        } else {
            lang = null;
            backupLang = null;
        }

        displayTool.initialize(database, installedDictionaries, lang, backupLang,
                new DisplayTool.Callback() {
                    @Override
                    public void execute() {
                        processChange(aLiveData);
                        performUpdate(aLiveData);
                    }
                });
    }

    public void setPersistentDatabase(PersistentDatabase aDatabase) {
        database = aDatabase;
    }
}
