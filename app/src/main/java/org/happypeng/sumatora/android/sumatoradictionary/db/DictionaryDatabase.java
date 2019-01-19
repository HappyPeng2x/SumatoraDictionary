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

package org.happypeng.sumatora.android.sumatoradictionary.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {DictionaryEntry.class, DictionaryControl.class, DictionaryTranslation.class,
        DictionaryIndex.class}, version = 2, exportSchema = false)
abstract public class DictionaryDatabase extends RoomDatabase {
    public static final String DATABASE_NAME = "DictionaryDatabase";

    public abstract DictionaryEntryDao dictionaryEntryDao();
    public abstract DictionaryControlDao dictionaryControlDao();
}
