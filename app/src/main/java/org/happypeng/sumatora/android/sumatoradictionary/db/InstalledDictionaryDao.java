package org.happypeng.sumatora.android.sumatoradictionary.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface InstalledDictionaryDao {
    @Query("SELECT * FROM InstalledDictionary")
    List<InstalledDictionary> getAll();

    @Query("SELECT * FROM InstalledDictionary")
    LiveData<List<InstalledDictionary>> getAllLive();

    @Query("SELECT * FROM InstalledDictionary " +
            "WHERE InstalledDictionary.type = 'jmdict_translation' " +
            "AND InstalledDictionary.lang != 'eng'")
    LiveData<List<InstalledDictionary>> getRemovableLive();

    @Delete
    void delete(InstalledDictionary aAction);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(InstalledDictionary aSetting);

    @Query("SELECT * FROM InstalledDictionary WHERE InstalledDictionary.type == :aType AND InstalledDictionary.lang == :aLang LIMIT 1")
    InstalledDictionary getForTypeLang(String aType, String aLang);
}
