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

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.arch.core.util.Function;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentSetting;

public class Settings {
    public static final String LANG = "lang";
    public static final String BACKUP_LANG = "backupLang";

    public static final String LANG_DEFAULT = "eng";
    public static final String BACKUP_LANG_DEFAULT = "eng";

    private MutableLiveData<PersistentDatabase> m_db;

    public Settings() {
        m_db = new MutableLiveData<>();
    }

    @WorkerThread
    public void postDatabase(final PersistentDatabase aDB) {
        m_db.postValue(aDB);
    }

    public LiveData<String> getValue(final String aName) {
        return Transformations.switchMap(m_db,
                new Function<PersistentDatabase, LiveData<String>>() {
                    @Override
                    public LiveData<String> apply(PersistentDatabase input) {
                        return input.persistentSettingsDao().getValue(aName);
                    }
                });
    }

    @WorkerThread
    public void postValue(final String aName, final String aValue) {
        if (m_db.getValue() != null) {
            m_db.getValue().persistentSettingsDao().insert(new PersistentSetting(aName, aValue));
        }
    }

    @MainThread
    public void setValue(final String aName, final String aValue) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                postValue(aName, aValue);

                return null;
            }
        }.execute();
    }
}