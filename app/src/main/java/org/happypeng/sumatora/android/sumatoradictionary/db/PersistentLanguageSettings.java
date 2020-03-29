/* Sumatora Dictionary
        Copyright (C) 2020 Nicolas Centa

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

package org.happypeng.sumatora.android.sumatoradictionary.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(primaryKeys = {"ref"})
public class PersistentLanguageSettings {
    public static final String LANG_DEFAULT = "eng";
    public static final String BACKUP_LANG_DEFAULT = "eng";

    public int ref;
    public @NonNull String lang;
    public String backupLang;

    public PersistentLanguageSettings() {
        lang = "";
        ref = 0;
    }

    public PersistentLanguageSettings(int aRef, final @NonNull String aLang, final String aBackupLang) {
        ref = aRef;
        lang = aLang;
        backupLang = aBackupLang;
    }
}
