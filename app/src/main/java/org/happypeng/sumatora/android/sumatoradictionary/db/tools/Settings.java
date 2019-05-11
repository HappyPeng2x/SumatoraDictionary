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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class Settings {
    private final MutableLiveData<String> m_lang;
    private final MutableLiveData<String> m_backupLang;

    public Settings() {
        m_lang = new MutableLiveData<>();
        m_backupLang = new MutableLiveData<>();
    }

    public LiveData<String> getLang() {
        return  m_lang;
    }

    @MainThread
    public void setLang(final String aLang) {
        m_lang.setValue(aLang);
    }

    public LiveData<String> getBackupLang() {
        return m_backupLang;
    }

    @MainThread
    public void setBackupLang(final String aLang) {
        m_backupLang.setValue(aLang);
    }
}
