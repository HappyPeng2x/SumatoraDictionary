package org.happypeng.sumatora.android.sumatoradictionary.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {DictionaryEntry.class, DictionaryControl.class}, version = 2, exportSchema = false)
abstract public class DictionaryDatabase extends RoomDatabase {
    public static final String DATABASE_NAME = "DictionaryDatabase";

    public abstract DictionaryEntryDao dictionaryEntryDao();
    public abstract DictionaryControlDao dictionaryControlDao();
}
